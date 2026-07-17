package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonEmailOtpReader.OtpCandidate;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NoonSessionGatewayAuthRecoveryGatewayTest {

    private static final Instant ATTEMPTED_AT = Instant.parse("2026-07-16T00:00:00Z");
    private static final String TARGET_PROJECT = "PRJ7001";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldClassifyInvalidOrExpiredOtpHttpResponsesWithoutLeakingProviderBody() throws Exception {
        List<RejectedOtpResponse> responses = List.of(
                new RejectedOtpResponse(400, "{\"error\":\"invalid otp\",\"detail\":\"provider-secret-invalid\"}"),
                new RejectedOtpResponse(401, "{\"error\":\"otp expired\",\"detail\":\"provider-secret-expired\"}"),
                new RejectedOtpResponse(401, "{\"message\":\"验证码失效\",\"detail\":\"provider-secret-cn\"}")
        );

        for (RejectedOtpResponse response : responses) {
            NoonHttpCallLogService logService = mock(NoonHttpCallLogService.class);
            try (RecoveryServer server = new RecoveryServer(
                    response.statusCode(),
                    response.body(),
                    "{\"projectCode\":\"" + TARGET_PROJECT + "\"}"
            )) {
                NoonSessionGateway gateway = identityGateway(server);
                gateway.setNoonHttpCallLogService(logService);

                NoonAuthRecoveryAttemptResult result = recoveryGateway(gateway).attempt(command());

                assertFalse(result.isIdentityAuthenticated());
                assertEquals(NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED, result.getFailureCode());
                assertTrue(result.getFailureCode().isResendEligible());
                assertEquals("otp validation: invalid or expired", result.getSafeDiagnostic());
                assertFalse(result.getSafeDiagnostic().contains("provider-secret"));
                assertEquals(1, server.validateCount());

                ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
                verify(logService, atLeastOnce()).record(
                        any(HttpRequest.class),
                        nullable(Integer.class),
                        responseBodyCaptor.capture(),
                        nullable(Long.class),
                        nullable(String.class),
                        nullable(String.class),
                        errorCaptor.capture()
                );
                assertTrue(responseBodyCaptor.getAllValues().stream().noneMatch(this::containsProviderSecret));
                assertTrue(errorCaptor.getAllValues().stream().noneMatch(this::containsProviderSecret));
            }
        }
    }

    @Test
    void rateOrRiskResponsesDuringOtpValidationAreNeverResendEligible() throws Exception {
        List<RejectedOtpResponse> responses = List.of(
                new RejectedOtpResponse(429, "{\"error\":\"invalid otp rate limit\"}"),
                new RejectedOtpResponse(418, "{\"error\":\"invalid otp ip_channel\"}")
        );

        for (RejectedOtpResponse response : responses) {
            try (RecoveryServer server = new RecoveryServer(
                    response.statusCode(),
                    response.body(),
                    "{\"projectCode\":\"" + TARGET_PROJECT + "\"}"
            )) {
                NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command());

                assertFalse(result.isIdentityAuthenticated());
                assertEquals(NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED, result.getFailureCode());
                assertFalse(result.getFailureCode().isResendEligible());
                assertEquals(1, server.validateCount());
            }
        }
    }

    @Test
    void delayedPriorGenerationInvalidThenNewCandidateSucceedsWithoutResending() throws Exception {
        SequencedOtpReader otpReader = new SequencedOtpReader(List.of(
                otpCandidate("111111", "old-message-hash", 101L),
                otpCandidate("222222", "new-message-hash", 102L)
        ));
        try (RecoveryServer server = new RecoveryServer(
                List.of(
                        new RejectedOtpResponse(400, "{\"error\":\"invalid otp\"}"),
                        new RejectedOtpResponse(200, "{\"success\":true,\"access_token\":\"token-1\"}")
                ),
                "{\"projectCode\":\"" + TARGET_PROJECT + "\"}"
        )) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(
                    identityGateway(server),
                    otpReader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC),
                    millis -> {
                        throw new AssertionError("both OTP candidates are immediately available");
                    }
            ).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertEquals("new-message-hash", result.getMessageKeyHash());
            assertEquals(1, server.generateCount());
            assertEquals(2, server.validateCount());
            assertEquals(List.of("new-message-hash"), otpReader.acknowledgedMessageKeyHashes());
        }
    }

    @Test
    void invalidCandidateThenAbsoluteDeadlineReturnsLastInvalidWithoutAcknowledging() throws Exception {
        MutableClock clock = new MutableClock(ATTEMPTED_AT);
        SequencedOtpReader otpReader = new SequencedOtpReader(List.of(
                otpCandidate("111111", "old-message-hash", 101L)
        ));
        try (RecoveryServer server = new RecoveryServer(
                400,
                "{\"error\":\"otp expired\"}",
                "{\"projectCode\":\"" + TARGET_PROJECT + "\"}"
        )) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(
                    identityGateway(server),
                    otpReader,
                    Duration.ofMillis(1),
                    Duration.ofMillis(2),
                    clock,
                    clock::advanceMillis
            ).attempt(command());

            assertFalse(result.isIdentityAuthenticated());
            assertEquals(NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED, result.getFailureCode());
            assertEquals("old-message-hash", result.getMessageKeyHash());
            assertEquals(1, server.generateCount());
            assertEquals(1, server.validateCount());
            assertTrue(otpReader.acknowledgedMessageKeyHashes().isEmpty());
            assertEquals(ATTEMPTED_AT.plusMillis(2), clock.instant());
        }
    }

    @Test
    void rateLimitedFirstCandidateStopsWithoutValidatingSecondCandidate() throws Exception {
        SequencedOtpReader otpReader = new SequencedOtpReader(List.of(
                otpCandidate("111111", "first-message-hash", 101L),
                otpCandidate("222222", "second-message-hash", 102L)
        ));
        try (RecoveryServer server = new RecoveryServer(
                List.of(
                        new RejectedOtpResponse(429, "{\"error\":\"rate limit\"}"),
                        new RejectedOtpResponse(200, "{\"success\":true,\"access_token\":\"token-1\"}")
                ),
                "{\"projectCode\":\"" + TARGET_PROJECT + "\"}"
        )) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(
                    identityGateway(server),
                    otpReader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC),
                    millis -> {
                        throw new AssertionError("rate limiting must stop before another mailbox poll");
                    }
            ).attempt(command());

            assertFalse(result.isIdentityAuthenticated());
            assertEquals(NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED, result.getFailureCode());
            assertEquals(1, server.generateCount());
            assertEquals(1, server.validateCount());
            assertTrue(otpReader.acknowledgedMessageKeyHashes().isEmpty());
        }
    }

    @Test
    void shouldRecoverOnlyWhenWhoamiContainsExactTargetProject() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"data\":{\"current_project\":{\"project_code\":\"  PRJ7001  \"}}}"
        )) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertEquals(1, result.getProjectResults().size());
            assertTrue(result.getProjectResults().get(0).isRecovered());
            assertTrue(result.getProjectResults().get(0).getCookie().contains("sid=recovered"));
        }
    }

    @Test
    void shouldRecoverWhenWhoamiConfirmsIdentityAndSessionHasExactTargetContext() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"MERCHANT@example.com\"}"
        )) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertEquals(1, result.getProjectResults().size());
            assertTrue(result.getProjectResults().get(0).isRecovered());
            assertTrue(result.getProjectResults().get(0).getCookie().contains("projectCode=PRJ7001"));
        }
    }

    @Test
    void shouldRetryWhoamiOnceAfterTransientTransportEof() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            NoonSessionGateway gateway = spy(identityGateway(server));
            doThrow(new IllegalStateException(
                    "Noon WHOAMI 验证失败：HTTP/1.1 header parser received no bytes | EOF reached"
            )).doCallRealMethod().when(gateway).whoamiWithCookie(anyString(), anyString(), anyString());

            NoonAuthRecoveryAttemptResult result = recoveryGateway(gateway).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertTrue(result.getProjectResults().get(0).isRecovered());
            verify(gateway, times(2)).whoamiWithCookie(anyString(), anyString(), anyString());
            assertEquals(1, server.whoamiCount());
        }
    }

    @Test
    void shouldRejectWhoamiWithMissingIdentityOrWrongProject() throws Exception {
        List<String> rejectedWhoamiBodies = List.of(
                "{\"ok\":true}",
                "{\"ok\":true,\"email\":\"another@example.com\"}",
                "{\"projectCode\":\"PRJ9999\",\"email\":\"merchant@example.com\"}"
        );

        for (String whoamiBody : rejectedWhoamiBodies) {
            try (RecoveryServer server = new RecoveryServer(
                    200,
                    "{\"success\":true,\"access_token\":\"token-1\"}",
                    whoamiBody
            )) {
                NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command());

                assertTrue(result.isIdentityAuthenticated());
                assertEquals(1, result.getProjectResults().size());
                NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
                assertEquals(NoonAuthRecoveryProjectResult.Code.COOKIE_VALIDATION_FAILED, projectResult.getCode());
                assertFalse(projectResult.isRecovered());
                assertNull(projectResult.getCookie());
                assertEquals(
                        "project cookie validation: identity or target project not confirmed",
                        projectResult.getSafeDiagnostic()
                );
            }
        }
    }

    @Test
    void shouldFailClosedForAmbiguousOrCaseMismatchedWhoamiProjectFields() throws Exception {
        assertTrue(NoonSessionGatewayAuthRecoveryGateway.whoamiMatchesTargetProject(
                objectMapper.readTree("{\"projectCode\":\" PRJ7001 \"}"),
                TARGET_PROJECT
        ));
        assertTrue(NoonSessionGatewayAuthRecoveryGateway.whoamiMatchesTargetProject(
                objectMapper.readTree("{\"context\":{\"project\":{\"code\":\"PRJ7001\"}}}"),
                TARGET_PROJECT
        ));
        assertTrue(NoonSessionGatewayAuthRecoveryGateway.whoamiMatchesTargetProject(
                objectMapper.readTree("{\"current_project_code\":\"PRJ7001\"}"),
                TARGET_PROJECT
        ));

        assertFalse(NoonSessionGatewayAuthRecoveryGateway.whoamiMatchesTargetProject(
                objectMapper.readTree("{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}"),
                TARGET_PROJECT
        ));
        assertFalse(NoonSessionGatewayAuthRecoveryGateway.whoamiMatchesTargetProject(
                objectMapper.readTree("{\"projectCode\":\"PRJ7001\",\"context\":{\"project_code\":\"PRJ9999\"}}"),
                TARGET_PROJECT
        ));
        assertFalse(NoonSessionGatewayAuthRecoveryGateway.whoamiMatchesTargetProject(
                objectMapper.readTree("{\"projectCode\":\"prj7001\"}"),
                TARGET_PROJECT
        ));
    }

    @Test
    void shouldNotSendOtpWhenBeforeSendReservationLosesItsFenceAfterSnapshot() throws Exception {
        AtomicBoolean snapshotTaken = new AtomicBoolean(false);
        AtomicInteger beforeSendCount = new AtomicInteger();
        NoonEmailOtpReader snapshotOnlyReader = new NoonEmailOtpReader() {
            @Override
            public String readOtp(String email, String mailAuthCode) {
                throw new AssertionError("central recovery must use generation-aware mailbox reads");
            }

            @Override
            public MailboxCursor snapshot(String email, String mailAuthCode) {
                snapshotTaken.set(true);
                return new MailboxCursor(7L, 100L, ATTEMPTED_AT);
            }

            @Override
            public Optional<OtpCandidate> pollAfter(
                    String email,
                    String mailAuthCode,
                    MailboxCursor cursor,
                    Instant notBefore,
                    Set<String> excludedMessageKeyHashes
            ) {
                throw new AssertionError("failed send reservation must abort before mailbox polling");
            }
        };

        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            NoonSessionGateway gateway = identityGateway(server);
            gateway.setConfiguredMerchantEmailOtpCredential("merchant@example.com", "imap-secret");
            NoonSessionGatewayAuthRecoveryGateway recoveryGateway = new NoonSessionGatewayAuthRecoveryGateway(
                    gateway,
                    snapshotOnlyReader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC),
                    millis -> {
                        throw new AssertionError("failed send reservation must abort before sleeping");
                    }
            );

            assertThrows(
                    LeaseLostException.class,
                    () -> recoveryGateway.attempt(command(
                            projectTargets(1),
                            () -> true,
                            () -> {
                                assertTrue(snapshotTaken.get());
                                beforeSendCount.incrementAndGet();
                                return false;
                            }
                    ))
            );

            assertEquals(1, beforeSendCount.get());
            assertEquals(0, server.generateCount());
            assertEquals(0, server.validateCount());
            assertEquals(0, server.sessionCreateCount());
            assertEquals(0, server.whoamiCount());
        }
    }

    @Test
    void shouldHeartbeatThroughoutLongProjectBatch() throws Exception {
        List<NoonAuthRecoveryProjectTarget> targets = projectTargets(12);
        AtomicInteger heartbeatCount = new AtomicInteger();
        try (RecoveryServer server = RecoveryServer.forProjects(projectCodes(targets))) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command(
                    targets,
                    () -> {
                        heartbeatCount.incrementAndGet();
                        return true;
                    }
            ));

            assertTrue(result.isIdentityAuthenticated());
            assertEquals(targets.size(), result.getProjectResults().size());
            assertTrue(result.getProjectResults().stream().allMatch(NoonAuthRecoveryProjectResult::isRecovered));
            assertEquals(1, server.generateCount());
            assertEquals(1, server.validateCount());
            assertEquals(targets.size(), server.sessionCreateCount());
            assertEquals(targets.size(), server.whoamiCount());
            assertTrue(heartbeatCount.get() >= targets.size() * 3 + 8);
        }
    }

    @Test
    void shouldStopProjectCallsAndReturnFailureWhenLeaseIsLostAfterSessionCreate() throws Exception {
        List<NoonAuthRecoveryProjectTarget> targets = projectTargets(3);
        try (RecoveryServer server = RecoveryServer.forProjects(projectCodes(targets))) {
            LeaseLostException exception = assertThrows(
                    LeaseLostException.class,
                    () -> recoveryGateway(identityGateway(server)).attempt(command(
                            targets,
                            () -> server.sessionCreateCount() == 0
                    ))
            );

            assertEquals("auth recovery lease lost", exception.getMessage());
            assertEquals(1, server.generateCount());
            assertEquals(1, server.validateCount());
            assertEquals(1, server.sessionCreateCount());
            assertEquals(0, server.whoamiCount());
        }
    }

    @Test
    void shouldStopOtpPollingImmediatelyWhenLeaseIsLost() throws Exception {
        AtomicInteger pollCount = new AtomicInteger();
        NoonEmailOtpReader pollingReader = new NoonEmailOtpReader() {
            @Override
            public String readOtp(String email, String mailAuthCode) {
                throw new AssertionError("central recovery must use generation-aware mailbox reads");
            }

            @Override
            public MailboxCursor snapshot(String email, String mailAuthCode) {
                return new MailboxCursor(7L, 100L, ATTEMPTED_AT);
            }

            @Override
            public Optional<OtpCandidate> pollAfter(
                    String email,
                    String mailAuthCode,
                    MailboxCursor cursor,
                    Instant notBefore,
                    Set<String> excludedMessageKeyHashes
            ) {
                pollCount.incrementAndGet();
                return Optional.empty();
            }
        };

        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            NoonSessionGateway gateway = identityGateway(server);
            gateway.setConfiguredMerchantEmailOtpCredential("merchant@example.com", "imap-secret");
            NoonSessionGatewayAuthRecoveryGateway recoveryGateway = new NoonSessionGatewayAuthRecoveryGateway(
                    gateway,
                    pollingReader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    Clock.fixed(ATTEMPTED_AT, ZoneOffset.UTC),
                    millis -> {
                        throw new AssertionError("lease loss after poll must abort before sleeping");
                    }
            );

            LeaseLostException exception = assertThrows(
                    LeaseLostException.class,
                    () -> recoveryGateway.attempt(command(
                            projectTargets(1),
                            () -> pollCount.get() == 0
                    ))
            );

            assertEquals("auth recovery lease lost", exception.getMessage());
            assertEquals(1, pollCount.get());
            assertEquals(1, server.generateCount());
            assertEquals(0, server.validateCount());
            assertEquals(0, server.sessionCreateCount());
            assertEquals(0, server.whoamiCount());
        }
    }

    private NoonSessionGatewayAuthRecoveryGateway recoveryGateway(NoonSessionGateway gateway) {
        MutableClock clock = new MutableClock(ATTEMPTED_AT);
        return recoveryGateway(
                gateway,
                immediateOtpReader(),
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                clock,
                clock::advanceMillis
        );
    }

    private NoonSessionGatewayAuthRecoveryGateway recoveryGateway(
            NoonSessionGateway gateway,
            NoonEmailOtpReader otpReader,
            Duration pollInterval,
            Duration pollTimeout,
            Clock clock,
            NoonSessionGatewayAuthRecoveryGateway.Sleeper sleeper
    ) {
        gateway.setConfiguredMerchantEmailOtpCredential("merchant@example.com", "imap-secret");
        return new NoonSessionGatewayAuthRecoveryGateway(
                gateway,
                otpReader,
                pollInterval,
                pollTimeout,
                clock,
                sleeper
        );
    }

    private NoonSessionGateway identityGateway(RecoveryServer server) {
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
    }

    private NoonEmailOtpReader immediateOtpReader() {
        return new NoonEmailOtpReader() {
            @Override
            public String readOtp(String email, String mailAuthCode) {
                throw new AssertionError("central recovery must use generation-aware mailbox reads");
            }

            @Override
            public MailboxCursor snapshot(String email, String mailAuthCode) {
                return new MailboxCursor(7L, 100L, ATTEMPTED_AT);
            }

            @Override
            public Optional<OtpCandidate> pollAfter(
                    String email,
                    String mailAuthCode,
                    MailboxCursor cursor,
                    Instant notBefore,
                    Set<String> excludedMessageKeyHashes
            ) {
                if (excludedMessageKeyHashes.contains("message-key-hash")) {
                    return Optional.empty();
                }
                return Optional.of(new OtpCandidate(
                        "654321",
                        "message-key-hash",
                        ATTEMPTED_AT.plusSeconds(1),
                        7L,
                        101L
                ));
            }

            @Override
            public void acknowledge(String email, String mailAuthCode, OtpCandidate candidate) {
                // no-op
            }
        };
    }

    private OtpCandidate otpCandidate(String code, String messageKeyHash, long uid) {
        return new OtpCandidate(
                code,
                messageKeyHash,
                ATTEMPTED_AT.plusSeconds(1),
                7L,
                uid
        );
    }

    private NoonAuthRecoveryAttemptCommand command() {
        return command(
                List.of(new NoonAuthRecoveryProjectTarget(307L, TARGET_PROJECT, "STR7001-NAE", 0L)),
                () -> true
        );
    }

    private NoonAuthRecoveryAttemptCommand command(
            List<NoonAuthRecoveryProjectTarget> targets,
            NoonAuthRecoveryAttemptCommand.LeaseHeartbeat leaseHeartbeat
    ) {
        return command(targets, leaseHeartbeat, () -> true);
    }

    private NoonAuthRecoveryAttemptCommand command(
            List<NoonAuthRecoveryProjectTarget> targets,
            NoonAuthRecoveryAttemptCommand.LeaseHeartbeat leaseHeartbeat,
            NoonAuthRecoveryAttemptCommand.BeforeOtpSend beforeOtpSend
    ) {
        return new NoonAuthRecoveryAttemptCommand(
                9001L,
                1,
                ATTEMPTED_AT,
                Set.of(),
                targets,
                leaseHeartbeat,
                beforeOtpSend
        );
    }

    private List<NoonAuthRecoveryProjectTarget> projectTargets(int count) {
        java.util.ArrayList<NoonAuthRecoveryProjectTarget> targets = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            String suffix = String.format("%04d", 7001 + index);
            targets.add(new NoonAuthRecoveryProjectTarget(
                    307L + index,
                    "PRJ" + suffix,
                    "STR" + suffix + "-NAE",
                    0L
            ));
        }
        return List.copyOf(targets);
    }

    private List<String> projectCodes(List<NoonAuthRecoveryProjectTarget> targets) {
        java.util.ArrayList<String> projectCodes = new java.util.ArrayList<>();
        for (NoonAuthRecoveryProjectTarget target : targets) {
            projectCodes.add(target.getProjectCode());
        }
        return List.copyOf(projectCodes);
    }

    private boolean containsProviderSecret(String value) {
        return value != null && value.contains("provider-secret");
    }

    private static final class RejectedOtpResponse {
        private final int statusCode;
        private final String body;

        private RejectedOtpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        private int statusCode() {
            return statusCode;
        }

        private String body() {
            return body;
        }
    }

    private static final class SequencedOtpReader implements NoonEmailOtpReader {
        private final List<OtpCandidate> candidates;
        private final List<String> acknowledgedMessageKeyHashes = new ArrayList<>();

        private SequencedOtpReader(List<OtpCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public String readOtp(String email, String mailAuthCode) {
            throw new AssertionError("central recovery must use generation-aware mailbox reads");
        }

        @Override
        public MailboxCursor snapshot(String email, String mailAuthCode) {
            return new MailboxCursor(7L, 100L, ATTEMPTED_AT);
        }

        @Override
        public Optional<OtpCandidate> pollAfter(
                String email,
                String mailAuthCode,
                MailboxCursor cursor,
                Instant notBefore,
                Set<String> excludedMessageKeyHashes
        ) {
            return candidates.stream()
                    .filter(candidate -> !excludedMessageKeyHashes.contains(candidate.getMessageKeyHash()))
                    .findFirst();
        }

        @Override
        public void acknowledge(String email, String mailAuthCode, OtpCandidate candidate) {
            acknowledgedMessageKeyHashes.add(candidate.getMessageKeyHash());
        }

        private List<String> acknowledgedMessageKeyHashes() {
            return List.copyOf(acknowledgedMessageKeyHashes);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advanceMillis(long millis) {
            current = current.plusMillis(millis);
        }
    }

    private static final class RecoveryServer implements AutoCloseable {
        private final HttpServer server;
        private final List<RejectedOtpResponse> validateResponses;
        private final String whoamiBody;
        private final String projectsBody;
        private final AtomicInteger generateCount = new AtomicInteger();
        private final AtomicInteger validateCount = new AtomicInteger();
        private final AtomicInteger sessionCreateCount = new AtomicInteger();
        private final AtomicInteger whoamiCount = new AtomicInteger();
        private volatile String lastSessionProjectCode;

        private RecoveryServer(int validateStatus, String validateBody, String whoamiBody) throws IOException {
            this(
                    validateStatus,
                    validateBody,
                    whoamiBody,
                    "{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}"
            );
        }

        private RecoveryServer(
                int validateStatus,
                String validateBody,
                String whoamiBody,
                String projectsBody
        ) throws IOException {
            this(
                    List.of(new RejectedOtpResponse(validateStatus, validateBody)),
                    whoamiBody,
                    projectsBody
            );
        }

        private RecoveryServer(
                List<RejectedOtpResponse> validateResponses,
                String whoamiBody
        ) throws IOException {
            this(
                    validateResponses,
                    whoamiBody,
                    "{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}"
            );
        }

        private RecoveryServer(
                List<RejectedOtpResponse> validateResponses,
                String whoamiBody,
                String projectsBody
        ) throws IOException {
            if (validateResponses == null || validateResponses.isEmpty()) {
                throw new IllegalArgumentException("validateResponses must not be empty");
            }
            this.validateResponses = List.copyOf(validateResponses);
            this.whoamiBody = whoamiBody;
            this.projectsBody = projectsBody;
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.server.createContext("/", this::handle);
            this.server.start();
        }

        private static RecoveryServer forProjects(List<String> projectCodes) throws IOException {
            return new RecoveryServer(
                    200,
                    "{\"success\":true,\"access_token\":\"token-1\"}",
                    null,
                    projectsBody(projectCodes)
            );
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/lookup".equals(path)) {
                sendJson(exchange, 200,
                        "[{\"userCode\":\"merchant@example.com\",\"channels\":[{\"channelCode\":\"emailotp\"}]}]",
                        null);
                return;
            }
            if ("/pkce".equals(path)) {
                sendJson(exchange, 200, "{\"success\":true,\"pkce_key\":\"pkce-1\"}", null);
                return;
            }
            if ("/generate".equals(path)) {
                generateCount.incrementAndGet();
                sendJson(exchange, 200, "{\"emailotp\":\"ok\"}", null);
                return;
            }
            if ("/validate".equals(path)) {
                int invocation = validateCount.incrementAndGet();
                RejectedOtpResponse response = validateResponses.get(Math.min(
                        invocation - 1,
                        validateResponses.size() - 1
                ));
                sendJson(exchange, response.statusCode(), response.body(), null);
                return;
            }
            if ("/projects".equals(path)) {
                sendJson(exchange, 200, projectsBody, null);
                return;
            }
            if ("/session-create".equals(path)) {
                sessionCreateCount.incrementAndGet();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                lastSessionProjectCode = new ObjectMapper().readTree(body).path("projectCode").asText(null);
                sendJson(exchange, 200, "{\"success\":true}", "sid=recovered; Path=/");
                return;
            }
            if ("/whoami".equals(path)) {
                whoamiCount.incrementAndGet();
                String body = whoamiBody != null
                        ? whoamiBody
                        : "{\"projectCode\":\"" + lastSessionProjectCode + "\"}";
                sendJson(exchange, 200, body, null);
                return;
            }
            sendJson(exchange, 404, "{\"error\":\"not found\"}", null);
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private int validateCount() {
            return validateCount.get();
        }

        private int generateCount() {
            return generateCount.get();
        }

        private int sessionCreateCount() {
            return sessionCreateCount.get();
        }

        private int whoamiCount() {
            return whoamiCount.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void sendJson(HttpExchange exchange, int status, String body, String setCookie) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (setCookie != null) {
                exchange.getResponseHeaders().add("Set-Cookie", setCookie);
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        private static String projectsBody(List<String> projectCodes) {
            StringBuilder body = new StringBuilder("{\"projects\":[");
            for (int index = 0; index < projectCodes.size(); index++) {
                if (index > 0) {
                    body.append(',');
                }
                body.append("{\"projectCode\":\"")
                        .append(projectCodes.get(index))
                        .append("\"}");
            }
            return body.append("]}").toString();
        }
    }
}
