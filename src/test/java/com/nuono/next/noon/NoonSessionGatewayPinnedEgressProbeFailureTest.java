package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayPinnedEgressProbeFailureTest {

    @Test
    void rotatesAfterWhoamiConnectionRefusalInExplicitProviderModeWithLegacyProxyDisabled() throws Exception {
        try (ProbeProxy refusedAfterPreflight = ProbeProxy.closeAfterConnect();
                ProbeProxy healthy = ProbeProxy.whoamiStatus(200);
                ProxyProvider provider = new ProxyProvider(refusedAfterPreflight.port(), healthy.port())) {
            NoonSessionGateway gateway = gateway(provider.url(), false);
            gateway.setProxyMode("PROVIDER");

            NoonSessionGateway.NoonSession session = openPinned(gateway);

            assertEquals("PRJ108065", session.getProjectCode());
            assertEquals(2, provider.requestCount());
            assertEquals(1, refusedAfterPreflight.connectCount());
            assertEquals(0, refusedAfterPreflight.httpCount());
            assertEquals(1, healthy.connectCount());
            assertEquals(1, healthy.httpCount());
        }
    }

    @Test
    void doesNotRotateAfterNonTransientWhoamiFailure() throws Exception {
        try (ProbeProxy rejected = ProbeProxy.whoamiStatus(401);
                ProbeProxy unused = ProbeProxy.whoamiStatus(200);
                ProxyProvider provider = new ProxyProvider(rejected.port(), unused.port())) {
            assertThrows(
                    NoonSessionGateway.NoonCookieAuthRequiredException.class,
                    () -> openPinned(gateway(provider.url(), true))
            );

            assertEquals(1, provider.requestCount());
            assertEquals(1, rejected.connectCount());
            assertEquals(1, rejected.httpCount());
            assertEquals(0, unused.connectCount());
        }
    }

    private NoonSessionGateway.NoonSession openPinned(NoonSessionGateway gateway) {
        return gateway.loginWithPersistedCookiePinnedEgress(
                307L,
                "merchant@example.com",
                "sid=existing",
                "PRJ108065",
                "STR108065-NSA",
                "fbn.noon.partners",
                443
        );
    }

    private NoonSessionGateway gateway(String providerUrl, boolean proxyEnabled) {
        return new NoonSessionGateway(
                new ObjectMapper(), mock(StoreSyncMapper.class), 0L, true,
                "", "", "", "", false, "http://noon.test/whoami",
                "", "", "", "", "", "", proxyEnabled, "HTTP", "", 0, providerUrl
        );
    }

    private static final class ProxyProvider implements AutoCloseable {
        private final HttpServer server;
        private final Deque<Integer> ports = new ArrayDeque<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final int fallbackPort;

        private ProxyProvider(int... proxyPorts) throws IOException {
            Arrays.stream(proxyPorts).forEach(ports::addLast);
            fallbackPort = proxyPorts[proxyPorts.length - 1];
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/proxy", exchange -> {
                requestCount.incrementAndGet();
                Integer port = ports.pollFirst();
                byte[] body = ("{\"data\":[{\"ip\":\"127.0.0.1\",\"port\":\""
                        + (port == null ? fallbackPort : port) + "\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
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

        private int requestCount() {
            return requestCount.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class ProbeProxy implements AutoCloseable {
        private final ServerSocket server;
        private final int whoamiStatus;
        private final boolean closeAfterConnect;
        private final AtomicInteger connectCount = new AtomicInteger();
        private final AtomicInteger httpCount = new AtomicInteger();
        private volatile boolean running = true;

        private ProbeProxy(int whoamiStatus, boolean closeAfterConnect) throws IOException {
            this.whoamiStatus = whoamiStatus;
            this.closeAfterConnect = closeAfterConnect;
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::acceptLoop, "pinned-probe-proxy-" + server.getLocalPort());
            thread.setDaemon(true);
            thread.start();
        }

        private static ProbeProxy closeAfterConnect() throws IOException {
            return new ProbeProxy(200, true);
        }

        private static ProbeProxy whoamiStatus(int status) throws IOException {
            return new ProbeProxy(status, false);
        }

        private int port() {
            return server.getLocalPort();
        }

        private int connectCount() {
            return connectCount.get();
        }

        private int httpCount() {
            return httpCount.get();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    handle(server.accept());
                } catch (IOException exception) {
                    if (running) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            try (Socket accepted = socket) {
                String request = readHeaders(accepted);
                if (request.startsWith("CONNECT ")) {
                    connectCount.incrementAndGet();
                    respond(accepted, 200, "");
                    if (closeAfterConnect) {
                        running = false;
                        server.close();
                    }
                    return;
                }
                httpCount.incrementAndGet();
                respond(accepted, whoamiStatus, whoamiStatus == 200 ? "{}" : "");
            }
        }

        private static String readHeaders(Socket socket) throws IOException {
            socket.setSoTimeout(2000);
            StringBuilder headers = new StringBuilder();
            int matched = 0;
            int current;
            while ((current = socket.getInputStream().read()) != -1 && headers.length() < 8192) {
                headers.append((char) current);
                char expected = "\r\n\r\n".charAt(matched);
                matched = current == expected ? matched + 1 : current == '\r' ? 1 : 0;
                if (matched == 4) {
                    break;
                }
            }
            return headers.toString();
        }

        private static void respond(Socket socket, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write((
                    "HTTP/1.1 " + status + " Test\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + bytes.length + "\r\n"
                            + "Connection: close\r\n\r\n"
            ).getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bytes);
        }

        @Override
        public void close() throws IOException {
            running = false;
            server.close();
        }
    }
}
