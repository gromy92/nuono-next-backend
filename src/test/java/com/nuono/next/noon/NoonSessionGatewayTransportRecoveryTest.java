package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayTransportRecoveryTest {
    @Test
    void shouldRotateProxyWhenInitialPersistedCookieWhoamiDropsConnection() throws Exception {
        try (DropProxy staleProxy = new DropProxy();
                SuccessProxy refreshedProxy = new SuccessProxy();
                ProxyProvider provider = new ProxyProvider(staleProxy.port(), refreshedProxy.port())) {
            NoonSessionGateway.NoonSession session = gateway(provider.url()).loginWithPersistedCookie(
                    307L, "merchant@example.com", "sid=existing", "PRJ69486", "STR69486-NSA");

            assertEquals("PRJ69486", session.getProjectCode());
            assertTrue(staleProxy.requestCount() >= 3);
            assertTrue(refreshedProxy.requestCount() >= 1);
        }
    }

    private NoonSessionGateway gateway(String providerUrl) {
        return new NoonSessionGateway(
                new ObjectMapper(), mock(StoreSyncMapper.class), false, 0L, true,
                "", "", "", "", false, false, "", "http://noon.test/whoami",
                "", "", "", "", "", "", true, "HTTP", "", 0, providerUrl
        );
    }

    private static final class ProxyProvider implements AutoCloseable {
        private final HttpServer server;
        private final ConcurrentLinkedQueue<Integer> ports = new ConcurrentLinkedQueue<>();

        private ProxyProvider(int firstPort, int secondPort) throws IOException {
            ports.add(firstPort);
            ports.add(secondPort);
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/proxy", exchange -> {
                Integer port = ports.poll();
                byte[] body = ("{\"data\":[{\"ip\":\"127.0.0.1\",\"port\":\""
                        + (port == null ? secondPort : port) + "\"}]}").getBytes(StandardCharsets.UTF_8);
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

    private abstract static class TestProxy implements AutoCloseable {
        private final ServerSocket server;
        private final AtomicInteger requests = new AtomicInteger();
        private volatile boolean running = true;

        private TestProxy() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::acceptLoop, getClass().getSimpleName());
            thread.setDaemon(true);
            thread.start();
        }

        protected final int port() {
            return server.getLocalPort();
        }

        protected final int requestCount() {
            return requests.get();
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

        protected void readAndCount(Socket socket) throws IOException {
            socket.setSoTimeout(2000);
            while (socket.getInputStream().read() != -1 && socket.getInputStream().available() > 0) {
                // Drain the request headers before responding or dropping the connection.
            }
            requests.incrementAndGet();
        }

        protected abstract void handle(Socket socket) throws IOException;

        @Override
        public void close() throws IOException {
            running = false;
            server.close();
        }
    }

    private static final class DropProxy extends TestProxy {
        private DropProxy() throws IOException {
            super();
        }

        @Override
        protected void handle(Socket socket) throws IOException {
            try (Socket accepted = socket) {
                readAndCount(accepted);
            }
        }
    }

    private static final class SuccessProxy extends TestProxy {
        private SuccessProxy() throws IOException {
            super();
        }

        @Override
        protected void handle(Socket socket) throws IOException {
            try (Socket accepted = socket) {
                readAndCount(accepted);
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                accepted.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                accepted.getOutputStream().write(body);
            }
        }
    }
}
