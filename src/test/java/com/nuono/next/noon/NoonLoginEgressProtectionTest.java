package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonLoginEgressProtectionTest {

    private static final String EDGE_ACCESS_DENIED =
            "<HTML><HEAD><TITLE>Access Denied</TITLE></HEAD><BODY>"
                    + "<H1>Access Denied</H1>"
                    + "You don't have permission to access "
                    + "\"http&#58;&#47;&#47;login&#45;alt&#46;noon&#46;partners&#47;lookup\" "
                    + "on this server."
                    + "<P>https&#58;&#47;&#47;errors&#46;edgesuite&#46;net&#47;18.example</P>"
                    + "</BODY></HTML>";

    @Test
    void edgeAccessDeniedStopsSigninFallbackAndStartsLocalCooldown() throws Exception {
        try (LoginServer server = new LoginServer(EDGE_ACCESS_DENIED)) {
            NoonSessionGateway gateway = directGateway(server);

            IllegalStateException firstFailure = assertThrows(
                    IllegalStateException.class,
                    () -> login(gateway)
            );

            assertTrue(firstFailure.getMessage().contains("出口"));
            assertTrue(firstFailure.getMessage().contains("30 分钟"));
            assertFalse(firstFailure.getMessage().contains("Access Denied"));
            assertFalse(firstFailure.getMessage().contains("edgesuite"));
            assertFalse(firstFailure.getMessage().contains("merchant@example.com"));
            assertEquals(1, server.lookupCount());
            assertEquals(0, server.signinCount());

            IllegalStateException cooldownFailure = assertThrows(
                    IllegalStateException.class,
                    () -> login(gateway, "another-merchant@example.com")
            );

            assertTrue(cooldownFailure.getMessage().contains("冷却"));
            assertEquals(1, server.lookupCount());
            assertEquals(0, server.signinCount());
        }
    }

    @Test
    void ordinaryAuth403KeepsExistingSigninFallback() throws Exception {
        try (LoginServer server = new LoginServer("{\"message\":\"invalid session\"}")) {
            NoonSessionGateway.NoonSession session = login(directGateway(server));

            assertEquals("PRJ1", session.getProjectCode());
            assertEquals(1, server.lookupCount());
            assertEquals(1, server.signinCount());
        }
    }

    @Test
    void fixedModeChoosesConfiguredEndpointEvenWhenProviderUrlExists() {
        NoonSessionGateway gateway = proxyGateway("127.0.0.1", 4444, "http://127.0.0.1:1/provider");
        gateway.setProxyMode("FIXED");

        Proxy proxy = gateway.resolveProxy();

        assertEquals(Proxy.Type.HTTP, proxy.type());
        assertEquals(new InetSocketAddress("127.0.0.1", 4444), proxy.address());
    }

    @Test
    void directModeIgnoresLegacyProxyConfiguration() {
        NoonSessionGateway gateway = proxyGateway("127.0.0.1", 4444, "http://127.0.0.1:1/provider");
        gateway.setProxyMode("DIRECT");

        assertNull(gateway.resolveProxy());
    }

    @Test
    void fixedModeFailsBeforeNoonIoWhenEndpointIsMissing() {
        NoonSessionGateway gateway = proxyGateway("", 0, "http://127.0.0.1:1/provider");
        gateway.setProxyMode("FIXED");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                gateway::resolveProxy
        );

        assertTrue(failure.getMessage().contains("FIXED"));
        assertFalse(failure.getMessage().contains("127.0.0.1:1"));
    }

    @Test
    void autoModePreservesLegacyProviderPrecedence() {
        assertEquals(
                NoonProxyMode.PROVIDER,
                NoonProxyMode.resolve("AUTO", true, true, true)
        );
    }

    @Test
    void providerModeRequiresProviderConfiguration() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> NoonProxyMode.resolve("PROVIDER", true, true, false)
        );

        assertTrue(failure.getMessage().contains("PROVIDER"));
    }

    @Test
    void unsupportedModeFailsClosed() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> NoonProxyMode.resolve("RANDOM", true, true, true)
        );

        assertTrue(failure.getMessage().contains("不支持"));
    }

    private NoonSessionGateway.NoonSession login(NoonSessionGateway gateway) {
        return login(gateway, "merchant@example.com");
    }

    private NoonSessionGateway.NoonSession login(
            NoonSessionGateway gateway,
            String noonUser
    ) {
        return gateway.login(
                10001L,
                noonUser,
                "password",
                "",
                "PRJ1",
                "STORE1"
        );
    }

    private NoonSessionGateway directGateway(LoginServer server) {
        return gateway(
                server,
                false,
                "",
                0,
                ""
        );
    }

    private NoonSessionGateway proxyGateway(String proxyHost, int proxyPort, String providerUrl) {
        return gateway(
                null,
                true,
                proxyHost,
                proxyPort,
                providerUrl
        );
    }

    private NoonSessionGateway gateway(
            LoginServer server,
            boolean proxyEnabled,
            String proxyHost,
            int proxyPort,
            String providerUrl
    ) {
        String baseUrl = server == null ? "http://noon.test" : server.baseUrl();
        return new NoonSessionGateway(
                new ObjectMapper(),
                mock(StoreSyncMapper.class),
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                true,
                true,
                baseUrl + "/signin",
                baseUrl + "/whoami",
                baseUrl + "/lookup",
                baseUrl + "/pkce",
                baseUrl + "/generate",
                baseUrl + "/validate",
                baseUrl + "/projects",
                baseUrl + "/session-create",
                proxyEnabled,
                "HTTP",
                proxyHost,
                proxyPort,
                providerUrl
        );
    }

    private static final class LoginServer implements AutoCloseable {
        private final HttpServer server;
        private final String lookupFailureBody;
        private final AtomicInteger lookupCount = new AtomicInteger();
        private final AtomicInteger signinCount = new AtomicInteger();

        private LoginServer(String lookupFailureBody) throws IOException {
            this.lookupFailureBody = lookupFailureBody;
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    0
            );
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ("/lookup".equals(path)) {
                    lookupCount.incrementAndGet();
                    send(exchange, 403, this.lookupFailureBody);
                    return;
                }
                if ("/signin".equals(path)) {
                    signinCount.incrementAndGet();
                    send(exchange, 200, "{}");
                    return;
                }
                send(exchange, 404, "{}");
            });
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private int lookupCount() {
            return lookupCount.get();
        }

        private int signinCount() {
            return signinCount.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void send(
                com.sun.net.httpserver.HttpExchange exchange,
                int status,
                String body
        ) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
