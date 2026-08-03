package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class NoonSessionGatewayPinnedEgressTest {

    @Test
    void rejectsBadConnectNodeThenPinsHealthyNodeForWholeOperation() throws Exception {
        try (ScriptedProxy rejected = ScriptedProxy.connectStatus(407);
                ScriptedProxy healthy = ScriptedProxy.connectStatus(200);
                ProxyProvider provider = new ProxyProvider(rejected.port(), healthy.port())) {
            NoonSessionGateway.NoonSession session = gateway(provider.url())
                    .loginWithPersistedCookiePinnedEgress(
                            307L,
                            "merchant@example.com",
                            "sid=existing",
                            "PRJ108065",
                            "STR108065-NSA",
                            "fbn.noon.partners",
                            443
                    );

            ObjectNode body = new ObjectMapper().createObjectNode().put("probe", true);
            session.postWriteJson("http://noon.test/write-one", body, true);
            session.postWriteJson("http://noon.test/write-two", body, true);

            assertEquals(2, provider.requestCount());
            assertEquals(1, rejected.connectCount());
            assertEquals(0, rejected.httpCount());
            assertEquals(1, healthy.connectCount());
            assertEquals(3, healthy.httpCount());
            assertFalse(session.getEgressFingerprint().contains("127.0.0.1"));
            assertFalse(session.getEgressFingerprint().contains(String.valueOf(healthy.port())));
        }
    }

    @Test
    void stopsAfterThreeRejectedDynamicNodesWithoutOpeningBusinessSession() throws Exception {
        try (ScriptedProxy first = ScriptedProxy.connectStatus(407);
                ScriptedProxy second = ScriptedProxy.connectStatus(407);
                ScriptedProxy third = ScriptedProxy.connectStatus(407);
                ProxyProvider provider = new ProxyProvider(first.port(), second.port(), third.port())) {
            NoonEgressUnavailableException failure = assertThrows(
                    NoonEgressUnavailableException.class,
                    () -> gateway(provider.url()).loginWithPersistedCookiePinnedEgress(
                            307L,
                            "merchant@example.com",
                            "sid=existing",
                            "PRJ108065",
                            "STR108065-NSA",
                            "fbn.noon.partners",
                            443
                    )
            );

            assertEquals(3, provider.requestCount());
            assertEquals(0, first.httpCount() + second.httpCount() + third.httpCount());
            assertEquals(NoonEgressUnavailableException.BLOCKED_FAILURE_CODE, failure.getFailureCode());
            assertTrue(failure.getMessage().contains("3"));
            assertTrue(failure.getMessage().contains("CONNECT_STATUS_407"));
            assertFalse(failure.getMessage().contains("127.0.0.1"));
            assertFalse(failure.getMessage().contains(provider.url()));
        }
    }

    @Test
    void pinnedReadFailureDoesNotRotateToAnotherNode() throws Exception {
        try (ScriptedProxy selected = ScriptedProxy.connectStatusThenHttpFailure(200, 407);
                ScriptedProxy unused = ScriptedProxy.connectStatus(200);
                ProxyProvider provider = new ProxyProvider(selected.port(), unused.port())) {
            NoonSessionGateway.NoonSession session = gateway(provider.url())
                    .loginWithPersistedCookiePinnedEgress(
                            307L,
                            "merchant@example.com",
                            "sid=existing",
                            "PRJ108065",
                            "STR108065-NSA",
                            "fbn.noon.partners",
                            443
                    );

            ObjectNode body = new ObjectMapper().createObjectNode().put("read", true);
            assertThrows(
                    NoonHttpException.class,
                    () -> session.postJson("http://noon.test/read", body, true)
            );

            assertEquals(1, provider.requestCount());
            assertEquals(1, selected.connectCount());
            assertEquals(2, selected.httpCount());
            assertEquals(0, unused.connectCount());
            assertEquals(0, unused.httpCount());
        }
    }

    private NoonSessionGateway gateway(String providerUrl) {
        return new NoonSessionGateway(
                new ObjectMapper(),
                mock(StoreSyncMapper.class),
                0L,
                true,
                "",
                "",
                "",
                "",
                false,
                "http://noon.test/whoami",
                "",
                "",
                "",
                "",
                "",
                "",
                true,
                "HTTP",
                "",
                0,
                providerUrl
        );
    }

    private static final class ProxyProvider implements AutoCloseable {
        private final HttpServer server;
        private final Deque<Integer> ports;
        private final AtomicInteger requestCount = new AtomicInteger();
        private final int fallbackPort;

        private ProxyProvider(int... proxyPorts) throws IOException {
            ports = new ArrayDeque<>();
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

    private static final class ScriptedProxy implements AutoCloseable {
        private final ServerSocket server;
        private final int connectStatus;
        private final Integer httpFailureStatus;
        private final AtomicInteger connectCount = new AtomicInteger();
        private final AtomicInteger httpCount = new AtomicInteger();
        private volatile boolean running = true;

        private ScriptedProxy(int connectStatus, Integer httpFailureStatus) throws IOException {
            this.connectStatus = connectStatus;
            this.httpFailureStatus = httpFailureStatus;
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::acceptLoop, "pinned-egress-proxy-" + server.getLocalPort());
            thread.setDaemon(true);
            thread.start();
        }

        private static ScriptedProxy connectStatus(int status) throws IOException {
            return new ScriptedProxy(status, null);
        }

        private static ScriptedProxy connectStatusThenHttpFailure(
                int connectStatus,
                int httpFailureStatus
        ) throws IOException {
            return new ScriptedProxy(connectStatus, httpFailureStatus);
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
                    respond(accepted, connectStatus, "");
                    return;
                }
                int currentHttpCount = httpCount.incrementAndGet();
                int status = httpFailureStatus != null && currentHttpCount > 1
                        ? httpFailureStatus
                        : 200;
                respond(accepted, status, status == 200 ? "{}" : "");
            }
        }

        private static String readHeaders(Socket socket) throws IOException {
            socket.setSoTimeout(2000);
            StringBuilder builder = new StringBuilder();
            int matched = 0;
            int current;
            while ((current = socket.getInputStream().read()) != -1 && builder.length() < 8192) {
                builder.append((char) current);
                char expected = "\r\n\r\n".charAt(matched);
                matched = current == expected ? matched + 1 : current == '\r' ? 1 : 0;
                if (matched == 4) { break; }
            }
            String headers = builder.toString();
            int lengthMarker = headers.toLowerCase().indexOf("content-length:");
            if (lengthMarker >= 0) {
                int start = lengthMarker + "content-length:".length();
                int end = headers.indexOf("\r\n", start);
                int remaining = Integer.parseInt(headers.substring(start, end).trim());
                while (remaining-- > 0 && socket.getInputStream().read() != -1) {
                    // Drain the request before closing the scripted connection.
                }
            }
            return headers;
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
