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
    void capturesRetryAfterAndDoesNotReplayOneShotResponse() throws Exception {
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
                    () -> session.postWriteJson(
                            url(server),
                            new ObjectMapper().createObjectNode(),
                            false
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
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/request";
    }
}
