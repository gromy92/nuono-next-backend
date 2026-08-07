package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayOneShotTransportTest {

    @Test
    void pacedOneShotCapturesRetryAfterAndDoesNotReplayResponse() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(503, "17", requests);
        try {
            NoonSessionGateway.NoonSession session = gateway(0L)
                    .openWithPersistedCookieWithoutProbe(
                            307L,
                            "project-user",
                            "sid=persisted",
                            "PRJ108065",
                            "STR108065-NSA"
                    );

            NoonHttpException failure = assertThrows(
                    NoonHttpException.class,
                    () -> session.postWriteJsonAfterPacing(
                            url(server),
                            new ObjectMapper().createObjectNode(),
                            false,
                            null
                    )
            );

            assertEquals(503, failure.getStatusCode());
            assertEquals(Duration.ofSeconds(17), failure.getRetryAfter());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void localOneShotPacingReturnsImmediatelyWithoutASecondRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, null, requests);
        try {
            NoonSessionGateway.NoonSession session = gateway(5_000L)
                    .openWithPersistedCookieWithoutProbe(
                            307L,
                            "project-user",
                            "sid=persisted",
                            "PRJ108065",
                            "STR108065-NSA"
                    );
            session.postWriteJson(
                    url(server),
                    new ObjectMapper().createObjectNode(),
                    false
            );

            long startedNanos = System.nanoTime();
            NoonRequestPacingException pacing = assertThrows(
                    NoonRequestPacingException.class,
                    () -> session.postWriteJson(
                            url(server),
                            new ObjectMapper().createObjectNode(),
                            false
                    )
            );
            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;

            assertTrue(elapsedMillis < 1_000L);
            assertTrue(pacing.getRetryAfter().compareTo(Duration.ZERO) > 0);
            assertTrue(pacing.getRetryAfter().compareTo(Duration.ofSeconds(5)) <= 0);
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void officialWarehouseCreateWaitsAfterSixtyProductPreflightCallsAndSendsOnce() throws Exception {
        AtomicInteger preflightRequests = new AtomicInteger();
        AtomicInteger createRequests = new AtomicInteger();
        HttpServer server = workflowServer(preflightRequests, createRequests);
        try {
            NoonSessionGateway.NoonSession session = gateway(30L)
                    .openWithPersistedCookieWithoutProbe(
                            307L, "project-user", "sid=persisted",
                            "PRJ69486", "STR69486-NSA"
                    );
            ObjectMapper objectMapper = new ObjectMapper();
            for (int sku = 0; sku < 20; sku++) {
                for (int page = 1; page <= 3; page++) {
                    session.postJson(
                            url(server, "/preflight"),
                            objectMapper.createObjectNode()
                                    .put("search", "SKU-" + sku).put("page", page),
                            true
                    );
                }
            }

            long startedNanos = System.nanoTime();
            session.postWriteJsonAfterPacing(
                    url(server, "/create"),
                    objectMapper.createObjectNode().put("totalQty", 453),
                    true,
                    null
            );
            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;

            assertTrue(elapsedMillis >= 10L);
            assertEquals(60, preflightRequests.get());
            assertEquals(1, createRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void pacedOneShotDoesNotReplayAnUnknownTransportResult() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0
        );
        server.createContext("/request", exchange -> {
            requests.incrementAndGet();
            exchange.close();
        });
        server.start();
        try {
            NoonSessionGateway.NoonSession session = gateway(0L)
                    .openWithPersistedCookieWithoutProbe(
                            307L, "project-user", "sid=persisted",
                            "PRJ69486", "STR69486-NSA"
                    );

            assertThrows(IllegalStateException.class, () -> session.postWriteJsonAfterPacing(
                    url(server), new ObjectMapper().createObjectNode(), false, null
            ));

            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void edgeDeniedResponseKeepsOnlyTheParsedRetryHintOnItsHttpCause() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(
                403,
                "29",
                "<title>Access Denied</title> errors.edgesuite.net",
                requests
        );
        try {
            NoonSessionGateway.NoonSession session = gateway(0L)
                    .openWithPersistedCookieWithoutProbe(
                            307L,
                            "project-user",
                            "sid=persisted",
                            "PRJ108065",
                            "STR108065-NSA"
                    );

            NoonEdgeAccessDeniedException failure = assertThrows(
                    NoonEdgeAccessDeniedException.class,
                    () -> session.postWriteJson(
                            url(server),
                            new ObjectMapper().createObjectNode(),
                            false
                    )
            );

            NoonHttpException http = (NoonHttpException) failure.getCause();
            assertEquals(Duration.ofSeconds(29), http.getRetryAfter());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(
            int status,
            String retryAfter,
            AtomicInteger requests
    ) throws Exception {
        return server(status, retryAfter, "{}", requests);
    }

    private HttpServer server(
            int status,
            String retryAfter,
            String responseBody,
            AtomicInteger requests
    ) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        server.createContext("/request", exchange -> {
            requests.incrementAndGet();
            if (retryAfter != null) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter);
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        return server;
    }

    private HttpServer workflowServer(
            AtomicInteger preflightRequests,
            AtomicInteger createRequests
    ) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0
        );
        server.createContext("/preflight", exchange -> {
            preflightRequests.incrementAndGet();
            send(exchange, 200, "{\"data\":{\"total\":0,\"hits\":[]}}");
        });
        server.createContext("/create", exchange -> {
            createRequests.incrementAndGet();
            send(exchange, 200, "{\"data\":{\"asn_nr\":\"A058831PN\"}}");
        });
        server.start();
        return server;
    }

    private void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }

    private NoonSessionGateway gateway(long minimumIntervalMillis) {
        return new NoonSessionGateway(
                new ObjectMapper(),
                mock(StoreSyncMapper.class),
                minimumIntervalMillis,
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
                false,
                "HTTP",
                "",
                0,
                ""
        );
    }

    private String url(HttpServer server) {
        return url(server, "/request");
    }

    private String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
