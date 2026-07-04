package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParsePartnerIdentityLookupPasswordChannel() throws Exception {
        JsonNode root = objectMapper.readTree(
                "[{\"user_code\":\"prd-user@idp.noon.partners\",\"channels\":[{\"channel_code\":\"password\"}]}]"
        );

        NoonSessionGateway.PartnerIdentityUser user = NoonSessionGateway.extractPartnerIdentityUser(root);

        assertEquals("prd-user@idp.noon.partners", user.getUserCode());
    }

    @Test
    void shouldRejectPartnerIdentityLookupWithoutPasswordChannel() throws Exception {
        JsonNode root = objectMapper.readTree(
                "[{\"userCode\":\"prd-user@idp.noon.partners\",\"channels\":[{\"channelCode\":\"emailotp\"}]}]"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> NoonSessionGateway.extractPartnerIdentityUser(root)
        );
        assertTrue(exception.getMessage().contains("密码登录"));
    }

    @Test
    void shouldSelectRequestedProjectFromPartnerIdentityProjectList() throws Exception {
        JsonNode root = objectMapper.readTree(
                "{\"projects\":[{\"project_code\":\"PRJ108065\"},{\"project_code\":\"PRJ245027\"}]}"
        );

        assertEquals("PRJ245027", NoonSessionGateway.selectPartnerIdentityProjectCode(root, "prj245027"));
    }

    @Test
    void shouldRejectMissingRequestedProjectFromPartnerIdentityProjectList() throws Exception {
        JsonNode root = objectMapper.readTree(
                "{\"projects\":[{\"projectCode\":\"PRJ108065\"}]}"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> NoonSessionGateway.selectPartnerIdentityProjectCode(root, "PRJ245027")
        );
        assertTrue(exception.getMessage().contains("PRJ245027"));
    }

    @Test
    void shouldGenerateSpecLengthPkceVerifierAndKnownChallenge() {
        String verifier = NoonSessionGateway.generateCodeVerifier();

        assertEquals(128, verifier.length());
        assertEquals(
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                NoonSessionGateway.generateCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        );
    }

    @Test
    void shouldRefreshProxySessionWhenCachedProxyDropsConnection() throws Exception {
        try (OneGoodThenDropProxy staleProxy = new OneGoodThenDropProxy();
                StaticHttpProxy refreshedProxy = new StaticHttpProxy();
                ProxyProviderServer provider = new ProxyProviderServer(staleProxy.port(), refreshedProxy.port())) {
            NoonSessionGateway gateway = gateway(provider.url());
            NoonSessionGateway.NoonSession session = gateway.login(
                    10001L,
                    "merchant@example.com",
                    "password",
                    "sid=existing",
                    "PRJ1",
                    "STORE1"
            );

            String body = session.getText("http://noon.test/report.csv", false, null);

            assertEquals("download-ok", body);
            assertTrue(staleProxy.requestCount() >= 2);
            assertTrue(refreshedProxy.awaitRequests(2));
        }
    }

    @Test
    void shouldRefreshProxySessionForTunnelFailureStatus() throws Exception {
        NoonSessionGateway gateway = gateway("http://127.0.0.1:1/proxy");
        Method method = NoonSessionGateway.class.getDeclaredMethod(
                "shouldRefreshAfterTransientTransportFailure",
                IllegalStateException.class
        );
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(gateway, new IllegalStateException("请求 Noon 失败：Tunnel failed, got: 435")));
        assertTrue((Boolean) method.invoke(gateway, new IllegalStateException("请求 Noon 失败：Tunnel failed, got: 436")));
    }

    @Test
    void shouldRejectNoonRequestWhenProxyEnabledWithoutEndpoint() {
        NoonSessionGateway gateway = gatewayWithSignin("");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> gateway.login(
                        10001L,
                        "merchant@example.com",
                        "password",
                        "sid=existing",
                        "PRJ1",
                        "STORE1"
                )
        );

        assertTrue(exception.getMessage().contains("Noon 代理已启用但未配置"));
    }

    @Test
    void shouldPersistSessionCookieForRequestedProjectOnly() {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        NoonSessionGateway gateway = gateway(mapper, "");

        gateway.persistCookie(308L, "PRJ100085", "sid=project-session");

        verify(mapper).updateProjectSessionCookie(308L, "PRJ100085", "sid=project-session", 308L);
    }

    @Test
    void shouldPersistCookieWhenRefreshingExpiredRuntimeSession() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer()) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            NoonSessionGateway.NoonSession session = gateway.login(
                    10001L,
                    "merchant@example.com",
                    "password",
                    "sid=old",
                    "PRJ1",
                    "STORE1"
            );

            JsonNode response = session.getJson(server.url("/protected"), false);

            assertTrue(response.path("ok").asBoolean(false));
            verify(mapper).updateProjectSessionCookie(
                    eq(10001L),
                    eq("PRJ1"),
                    argThat(cookie -> cookie != null && cookie.contains("sid=new")),
                    eq(10001L)
            );
        }
    }

    @Test
    void shouldSkipPersistingCookieWhenProjectCodeIsMissing() {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        NoonSessionGateway gateway = gateway(mapper, "");

        gateway.persistCookie(308L, null, "sid=project-session");

        verifyNoInteractions(mapper);
    }

    private NoonSessionGateway gateway(String proxyProviderUrl) {
        return gateway(mock(StoreSyncMapper.class), proxyProviderUrl);
    }

    private NoonSessionGateway gateway(StoreSyncMapper storeSyncMapper, String proxyProviderUrl) {
        return new NoonSessionGateway(
                objectMapper,
                storeSyncMapper,
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                false,
                false,
                "",
                "http://noon.test/whoami",
                "",
                "",
                "",
                "",
                "",
                true,
                "HTTP",
                "",
                0,
                proxyProviderUrl
        );
    }

    private NoonSessionGateway gatewayWithSignin(String proxyProviderUrl) {
        return new NoonSessionGateway(
                objectMapper,
                mock(StoreSyncMapper.class),
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                false,
                true,
                "http://noon.test/signin",
                "http://noon.test/whoami",
                "",
                "",
                "",
                "",
                "",
                true,
                "HTTP",
                "",
                0,
                proxyProviderUrl
        );
    }

    private NoonSessionGateway identityGateway(StoreSyncMapper mapper, AuthRefreshServer server) {
        return new NoonSessionGateway(
                objectMapper,
                mapper,
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                true,
                false,
                "",
                server.url("/whoami"),
                server.url("/lookup"),
                server.url("/pkce"),
                server.url("/validate"),
                server.url("/projects"),
                server.url("/session-create"),
                false,
                "HTTP",
                "",
                0,
                ""
        );
    }

    private static final class AuthRefreshServer implements AutoCloseable {
        private final HttpServer server;

        private AuthRefreshServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", (exchange) -> {
                String path = exchange.getRequestURI().getPath();
                if ("/whoami".equals(path)) {
                    sendJson(exchange, 200, "{\"ok\":true}", null);
                    return;
                }
                if ("/protected".equals(path)) {
                    String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                    if (cookie != null && cookie.contains("sid=new")) {
                        sendJson(exchange, 200, "{\"ok\":true}", null);
                    } else {
                        sendJson(exchange, 403, "{\"message\":\"invalid session\"}", null);
                    }
                    return;
                }
                if ("/lookup".equals(path)) {
                    sendJson(
                            exchange,
                            200,
                            "[{\"userCode\":\"merchant@example.com\",\"channels\":[{\"channelCode\":\"password\"}]}]",
                            null
                    );
                    return;
                }
                if ("/pkce".equals(path)) {
                    sendJson(exchange, 200, "{\"success\":true,\"pkce_key\":\"pkce-1\"}", null);
                    return;
                }
                if ("/validate".equals(path)) {
                    sendJson(exchange, 200, "{\"success\":true,\"access_token\":\"token-1\"}", null);
                    return;
                }
                if ("/projects".equals(path)) {
                    sendJson(exchange, 200, "{\"projects\":[{\"projectCode\":\"PRJ1\"}]}", null);
                    return;
                }
                if ("/session-create".equals(path)) {
                    sendJson(exchange, 200, "{\"success\":true}", "sid=new; Path=/");
                    return;
                }
                sendJson(exchange, 404, "{\"message\":\"not found\"}", null);
            });
            server.start();
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void sendJson(
                com.sun.net.httpserver.HttpExchange exchange,
                int status,
                String body,
                String setCookie
        ) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (setCookie != null) {
                exchange.getResponseHeaders().add("Set-Cookie", setCookie);
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(bytes);
            }
        }
    }

    private static final class ProxyProviderServer implements AutoCloseable {
        private final HttpServer server;
        private final ConcurrentLinkedQueue<Integer> ports = new ConcurrentLinkedQueue<>();

        private ProxyProviderServer(int firstPort, int secondPort) throws IOException {
            ports.add(firstPort);
            ports.add(secondPort);
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/proxy", (exchange) -> {
                Integer port = ports.poll();
                if (port == null) {
                    port = secondPort;
                }
                byte[] body = ("{\"data\":[{\"ip\":\"127.0.0.1\",\"port\":\"" + port + "\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream response = exchange.getResponseBody()) {
                    response.write(body);
                }
            });
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/proxy";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static class StaticHttpProxy implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final AtomicInteger requestCount = new AtomicInteger();
        private final CountDownLatch requestLatch = new CountDownLatch(2);
        private volatile boolean running = true;
        private final Thread acceptThread;

        private StaticHttpProxy() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            acceptThread = new Thread(this::acceptLoop, getClass().getSimpleName() + "-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        protected int port() {
            return serverSocket.getLocalPort();
        }

        protected int requestCount() {
            return requestCount.get();
        }

        private boolean awaitRequests(int count) throws InterruptedException {
            if (requestCount.get() >= count) {
                return true;
            }
            return requestLatch.await(2, TimeUnit.SECONDS);
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (IOException exception) {
                    if (running) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }

        protected void handle(Socket socket) throws IOException {
            try (Socket accepted = socket) {
                String request = readHeaders(accepted);
                incrementRequestCount();
                requestLatch.countDown();
                byte[] body = responseBody(request).getBytes(StandardCharsets.UTF_8);
                accepted.getOutputStream().write((
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/plain\r\n"
                                + "Content-Length: " + body.length + "\r\n"
                                + "Connection: close\r\n"
                                + "\r\n"
                ).getBytes(StandardCharsets.US_ASCII));
                accepted.getOutputStream().write(body);
            }
        }

        protected String responseBody(String request) {
            return request.contains("whoami") ? "{}" : "download-ok";
        }

        protected void incrementRequestCount() {
            requestCount.incrementAndGet();
        }

        private String readHeaders(Socket socket) throws IOException {
            socket.setSoTimeout(2000);
            StringBuilder builder = new StringBuilder();
            int previous = -1;
            int current;
            int matched = 0;
            while ((current = socket.getInputStream().read()) != -1) {
                builder.append((char) current);
                if ((previous == '\r' && current == '\n') || current == '\n') {
                    matched++;
                } else if (current != '\r') {
                    matched = 0;
                }
                if (matched >= 2 || builder.length() > 8192) {
                    break;
                }
                previous = current;
            }
            return builder.toString();
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
        }
    }

    private static final class OneGoodThenDropProxy extends StaticHttpProxy {
        private OneGoodThenDropProxy() throws IOException {
            super();
        }

        @Override
        protected void handle(Socket socket) throws IOException {
            if (requestCount() == 0) {
                super.handle(socket);
                return;
            }
            try (Socket accepted = socket) {
                readAndDrop(accepted);
            }
        }

        private void readAndDrop(Socket socket) throws IOException {
            socket.setSoTimeout(2000);
            while (socket.getInputStream().read() != -1) {
                if (socket.getInputStream().available() == 0) {
                    break;
                }
            }
            incrementRequestCount();
        }
    }
}
