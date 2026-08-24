package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonProjectSessionRecoveryTest {

    @Test
    void successfulProviderResponseWithoutCookieRaisesTypedRetryableFailure() throws Exception {
        try (MissingCookieSessionServer server = new MissingCookieSessionServer()) {
            NoonSessionGateway gateway = identityGateway(server);
            NoonSessionGateway.EmailOtpGeneration generation =
                    gateway.prepareEmailOtpGeneration("merchant@example.com");
            gateway.sendEmailOtp(generation);
            NoonSessionGateway.EmailIdentityGrant grant =
                    gateway.validateEmailOtp(generation, "654321");

            NoonProjectSessionCookieMissingException failure =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            NoonProjectSessionCookieMissingException.class,
                            () -> gateway.createEmailOtpProjectSession(
                                    grant, "PRJ7001", "STR7001-NAE"
                            )
                    );

            assertEquals("Noon session/create 未返回有效 Cookie。", failure.getMessage());
            assertEquals(1, server.sessionCreateCount.get());
        }
    }

    @Test
    void missingCookieKeepsProjectRecoverableInsteadOfCreatingManualHold() {
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonSessionGateway.EmailIdentityGrant grant = identityGrant();
        NoonAuthRecoveryProjectTarget target = new NoonAuthRecoveryProjectTarget(
                307L, "PRJ7001", "STR7001-NAE", "AE", 9L
        );
        when(gateway.createEmailOtpProjectSession(grant, "PRJ7001", "STR7001-NAE"))
                .thenThrow(new NoonProjectSessionCookieMissingException());

        NoonAuthRecoveryProjectResult result = new NoonProjectSessionRecovery(gateway)
                .recover(
                        grant,
                        "merchant@example.com",
                        List.of(target),
                        new NoonAuthRecoveryAttemptCommand(
                                91L,
                                1,
                                Instant.parse("2026-08-24T08:00:00Z"),
                                Set.of(),
                                List.of(target),
                                () -> true,
                                () -> true
                        )
                )
                .get(0);

        assertTrue(result.isTransientFailure());
        assertEquals(
                NoonAuthRecoveryFailureStage.PROJECT_SESSION_CREATE,
                result.getFailureStage()
        );
        assertEquals(
                NoonTransientErrorType.PROJECT_SESSION_COOKIE_MISSING,
                result.getTransientErrorType()
        );
    }

    private NoonSessionGateway.EmailIdentityGrant identityGrant() {
        NoonSessionGateway fixture = new NoonSessionGateway(
                new ObjectMapper(), mock(StoreSyncMapper.class), 0L, true,
                "", "", "", "", true,
                "http://noon.test/whoami", "http://noon.test/lookup",
                "http://noon.test/pkce", "http://noon.test/generate",
                "http://noon.test/validate", "http://noon.test/projects",
                "http://noon.test/session-create", false, "HTTP", "", 0, ""
        );
        return fixture.restoreEmailIdentityGrant(new NoonAuthGatewayCheckpointCodec.GrantSnapshot(
                new NoonAuthGatewayCheckpointCodec.GenerationSnapshot(
                        "merchant@example.com", "USER-1", "verifier", "pkce", ""
                ),
                "access-token",
                List.of("PRJ7001")
        ));
    }

    private NoonSessionGateway identityGateway(MissingCookieSessionServer server) {
        return new NoonSessionGateway(
                new ObjectMapper(), mock(StoreSyncMapper.class), 0L, true,
                "", "", "", "", true,
                server.url("/whoami"), server.url("/lookup"),
                server.url("/pkce"), server.url("/generate"),
                server.url("/validate"), server.url("/projects"),
                server.url("/session-create"), false, "HTTP", "", 0, ""
        );
    }

    private static final class MissingCookieSessionServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger sessionCreateCount = new AtomicInteger();

        private MissingCookieSessionServer() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0
            );
            server.createContext("/", this::handle);
            server.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/lookup".equals(path)) {
                send(exchange, "[{\"userCode\":\"USER-1\",\"channels\":[{\"channelCode\":\"emailotp\"}]}]");
                return;
            }
            if ("/pkce".equals(path)) {
                send(exchange, "{\"success\":true,\"pkce_key\":\"pkce-1\"}");
                return;
            }
            if ("/generate".equals(path)) {
                send(exchange, "{\"emailotp\":\"ok\"}");
                return;
            }
            if ("/validate".equals(path)) {
                send(exchange, "{\"success\":true,\"access_token\":\"token-1\"}");
                return;
            }
            if ("/projects".equals(path)) {
                send(exchange, "{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}");
                return;
            }
            if ("/session-create".equals(path)) {
                sessionCreateCount.incrementAndGet();
                send(exchange, "{\"success\":true}");
                return;
            }
            send(exchange, "{}");
        }

        private void send(HttpExchange exchange, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(bytes);
            }
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
