package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayEmailOtpPacingTest {

    @Test
    void identityOneShotCallsWaitForPacingAndStillRunOnlyOnce() throws Exception {
        try (IdentityServer server = new IdentityServer()) {
            NoonSessionGateway gateway = gateway(server, 200L);

            NoonSessionGateway.EmailOtpGeneration generation =
                    gateway.prepareEmailOtpGeneration("merchant@example.com");
            gateway.sendEmailOtp(generation);
            NoonSessionGateway.EmailIdentityGrant grant =
                    gateway.validateEmailOtp(generation, "654321");
            NoonSessionGateway.ProjectSessionCookie projectSession =
                    gateway.createEmailOtpProjectSession(grant, "PRJ741", "STR741-NAE");

            assertTrue(projectSession.getCookie().contains("sid=recovered"));
            assertEquals(1, server.count("/lookup"));
            assertEquals(1, server.count("/pkce"));
            assertEquals(1, server.count("/generate"));
            assertEquals(1, server.count("/validate"));
            assertEquals(1, server.count("/projects"));
            assertEquals(1, server.count("/session-create"));
        }
    }

    @Test
    void otpGenerationWaitsForPacingButDoesNotReplayARejectedRequest() throws Exception {
        try (IdentityServer server = new IdentityServer(503)) {
            NoonSessionGateway gateway = gateway(server, 200L);
            NoonSessionGateway.EmailOtpGeneration generation =
                    gateway.prepareEmailOtpGeneration("merchant@example.com");

            NoonHttpException failure = assertThrows(
                    NoonHttpException.class,
                    () -> gateway.sendEmailOtp(generation)
            );

            assertEquals(503, failure.getStatusCode());
            assertEquals(1, server.count("/generate"));
        }
    }

    private NoonSessionGateway gateway(IdentityServer server, long minimumIntervalMillis) {
        return new NoonSessionGateway(
                new ObjectMapper(),
                mock(StoreSyncMapper.class),
                minimumIntervalMillis,
                true,
                "",
                "",
                "",
                "",
                true,
                server.url("/whoami"),
                server.url("/lookup"),
                server.url("/pkce"),
                server.url("/generate"),
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

    private static final class IdentityServer implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
        private final int generateStatus;

        private IdentityServer() throws IOException {
            this(200);
        }

        private IdentityServer(int generateStatus) throws IOException {
            this.generateStatus = generateStatus;
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    0
            );
            server.createContext("/", this::handle);
            server.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            requestCounts.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
            switch (path) {
                case "/lookup":
                    send(exchange, 200,
                            "[{\"userCode\":\"merchant@example.com\",\"channels\":[{\"channelCode\":\"emailotp\"}]}]",
                            null);
                    return;
                case "/pkce":
                    send(exchange, 200,
                            "{\"success\":true,\"pkce_key\":\"pkce-741\"}", null);
                    return;
                case "/generate":
                    send(
                            exchange,
                            generateStatus,
                            generateStatus == 200
                                    ? "{\"emailotp\":\"ok\"}"
                                    : "{\"message\":\"temporary failure\"}",
                            null
                    );
                    return;
                case "/validate":
                    send(exchange, 200,
                            "{\"success\":true,\"access_token\":\"access-741\"}", null);
                    return;
                case "/projects":
                    send(exchange, 200,
                            "{\"projects\":[{\"projectCode\":\"PRJ741\",\"projectName\":\"Recovery\"}]}",
                            null);
                    return;
                case "/session-create":
                    send(exchange, 200, "{\"success\":true}", "sid=recovered; Path=/");
                    return;
                default:
                    send(exchange, 404, "{\"message\":\"not found\"}", null);
            }
        }

        private int count(String path) {
            AtomicInteger count = requestCounts.get(path);
            return count == null ? 0 : count.get();
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private void send(HttpExchange exchange, int status, String body, String setCookie)
                throws IOException {
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

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
