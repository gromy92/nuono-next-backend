package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonEmailOtpReader.MailboxCursor;
import com.nuono.next.noon.NoonEmailOtpReader.OtpCandidate;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonOwner308ProjectCookieIsolationTest {
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-07-23T07:00:00Z");
    private static final List<NoonAuthRecoveryProjectTarget> OWNER_308_TARGETS = List.of(
            new NoonAuthRecoveryProjectTarget(308L, "PRJ100085", "STR100085-NAE", 2L),
            new NoonAuthRecoveryProjectTarget(308L, "PRJ101128", "STR101128-NAE", 5L),
            new NoonAuthRecoveryProjectTarget(308L, "PRJ102858", "STR102858-NAE", 8L)
    );

    @Test
    void oneSharedGrantFreezesTheCorrectCookieForEveryOwner308Project() throws Exception {
        try (ProjectAwareRecoveryServer server = new ProjectAwareRecoveryServer()) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(server).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertEquals(OWNER_308_TARGETS.size(), result.getProjectResults().size());
            for (int index = 0; index < OWNER_308_TARGETS.size(); index++) {
                assertIsolatedCookie(
                        result.getProjectResults().get(index),
                        OWNER_308_TARGETS.get(index)
                );
            }
            assertEquals(1, server.generateCount.get());
            assertEquals(1, server.validateCount.get());
            assertEquals(OWNER_308_TARGETS.size(), server.sessionCreateCount.get());
            assertEquals(OWNER_308_TARGETS.size(), server.whoamiCount.get());
            assertEquals(OWNER_308_TARGETS.size(), server.catalogCount.get());
        }
    }

    private void assertIsolatedCookie(
            NoonAuthRecoveryProjectResult result,
            NoonAuthRecoveryProjectTarget expectedTarget
    ) {
        assertTrue(result.isRecovered());
        assertEquals(expectedTarget.getOwnerUserId(), result.getTarget().getOwnerUserId());
        assertEquals(expectedTarget.getProjectCode(), result.getTarget().getProjectCode());
        assertEquals(expectedTarget.getStoreCode(), result.getTarget().getStoreCode());
        assertTrue(result.getCookie().contains("sid=" + expectedTarget.getProjectCode()));
        assertTrue(result.getCookie().contains("projectCode=" + expectedTarget.getProjectCode()));
        assertEquals(1, occurrences(result.getCookie(), "projectCode="));
        OWNER_308_TARGETS.stream()
                .filter(target -> !target.getProjectCode().equals(expectedTarget.getProjectCode()))
                .forEach(target -> {
                    assertFalse(result.getCookie().contains("sid=" + target.getProjectCode()));
                    assertFalse(result.getCookie().contains("projectCode=" + target.getProjectCode()));
                });
    }

    private NoonSessionGatewayAuthRecoveryGateway recoveryGateway(ProjectAwareRecoveryServer server) {
        NoonSessionGateway gateway = new NoonSessionGateway(
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
                false,
                "",
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
        gateway.setConfiguredMerchantEmailOtpCredential("owner308@example.com", "test-only-secret");
        gateway.setCatalogSessionBootstrapUrl(server.catalogUrl("/catalog-bootstrap"));
        gateway.setCatalogCapabilityProbeUrl(server.catalogUrl("/catalog"));
        return new NoonSessionGatewayAuthRecoveryGateway(
                gateway,
                immediateOtpReader(),
                Duration.ofMillis(1),
                Duration.ofSeconds(1),
                Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC),
                millis -> {
                    throw new AssertionError("test OTP is immediately available");
                }
        );
    }

    private NoonAuthRecoveryAttemptCommand command() {
        return new NoonAuthRecoveryAttemptCommand(
                308001L,
                1,
                ATTEMPTED_AT,
                Set.of(),
                OWNER_308_TARGETS,
                () -> true,
                () -> true
        );
    }

    private NoonEmailOtpReader immediateOtpReader() {
        return new NoonEmailOtpReader() {
            @Override
            public String readOtp(String email, String mailAuthCode) {
                throw new AssertionError("generation-aware recovery must not use legacy mailbox reads");
            }

            @Override
            public MailboxCursor snapshot(String email, String mailAuthCode) {
                return new MailboxCursor(308L, 1000L, ATTEMPTED_AT);
            }

            @Override
            public Optional<OtpCandidate> pollAfter(
                    String email,
                    String mailAuthCode,
                    MailboxCursor cursor,
                    Instant notBefore,
                    Set<String> excludedMessageKeyHashes
            ) {
                return Optional.of(new OtpCandidate(
                        "308308",
                        "owner-308-message-hash",
                        ATTEMPTED_AT.plusSeconds(1),
                        308L,
                        1001L
                ));
            }
        };
    }

    private int occurrences(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static final class ProjectAwareRecoveryServer implements AutoCloseable {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final HttpServer server;
        private final AtomicInteger generateCount = new AtomicInteger();
        private final AtomicInteger validateCount = new AtomicInteger();
        private final AtomicInteger sessionCreateCount = new AtomicInteger();
        private final AtomicInteger whoamiCount = new AtomicInteger();
        private final AtomicInteger catalogCount = new AtomicInteger();
        private volatile String currentProjectCode;

        private ProjectAwareRecoveryServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/lookup".equals(path)) {
                respond(exchange,
                        "[{\"userCode\":\"owner308@example.com\","
                                + "\"channels\":[{\"channelCode\":\"emailotp\"}]}]");
            } else if ("/pkce".equals(path)) {
                respond(exchange, "{\"success\":true,\"pkce_key\":\"owner-308-pkce\"}");
            } else if ("/generate".equals(path)) {
                generateCount.incrementAndGet();
                respond(exchange, "{\"emailotp\":\"ok\"}");
            } else if ("/validate".equals(path)) {
                validateCount.incrementAndGet();
                respond(exchange, "{\"success\":true,\"access_token\":\"owner-308-grant\"}");
            } else if ("/projects".equals(path)) {
                respond(exchange, projectsBody());
            } else if ("/session-create".equals(path)) {
                sessionCreateCount.incrementAndGet();
                currentProjectCode = objectMapper.readTree(exchange.getRequestBody())
                        .path("projectCode")
                        .asText();
                respond(exchange, "{\"success\":true}", "sid=" + currentProjectCode + "; Path=/");
            } else if ("/whoami".equals(path)) {
                whoamiCount.incrementAndGet();
                respond(exchange, "{\"projectCode\":\"" + currentProjectCode + "\"}");
            } else if ("/catalog-bootstrap".equals(path)) {
                respond(exchange, "<html>owner 308 catalog</html>");
            } else if ("/catalog".equals(path)) {
                catalogCount.incrementAndGet();
                respond(exchange, "{\"data\":{\"hits\":[],\"total\":0}}");
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        }

        private String projectsBody() {
            return "{\"projects\":["
                    + "{\"projectCode\":\"PRJ100085\"},"
                    + "{\"projectCode\":\"PRJ101128\"},"
                    + "{\"projectCode\":\"PRJ102858\"}]}";
        }

        private void respond(HttpExchange exchange, String body) throws IOException {
            respond(exchange, body, null);
        }

        private void respond(HttpExchange exchange, String body, String cookie) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (cookie != null) {
                exchange.getResponseHeaders().add("Set-Cookie", cookie);
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private String catalogUrl(String path) {
            return "http://localhost:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
