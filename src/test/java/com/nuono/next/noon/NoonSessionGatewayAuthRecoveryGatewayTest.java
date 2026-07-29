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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonEmailOtpReader.OtpCandidate;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpConnectTimeoutException;
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
import javax.mail.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NoonSessionGatewayAuthRecoveryGatewayTest
        extends AbstractNoonSessionGatewayAuthRecoveryTestSupport {

    @Test
    void exactTransientHttpStatusTakesPrecedenceOverAuthMarkersInResponseBody() {
        for (int status : List.of(408, 500, 502, 503, 504)) {
            assertFalse(NoonSessionResponseClassifier.isAuthExpiredResponse(
                    status,
                    "{\"error\":\"unauthorized invalid session signin\"}",
                    "/_svc/auth-v1/whoami",
                    "https://login.noon.partners/"
            ));
        }
        assertTrue(NoonSessionResponseClassifier.isAuthExpiredResponse(
                401,
                "{\"error\":\"unauthorized\"}",
                "/catalog",
                null
        ));
    }

    @Test
    void mailboxSnapshotAndPollingTransportFailuresKeepTheirExactStageAndType() throws Exception {
        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            NoonSessionGateway gateway = identityGateway(server);
            NoonEmailOtpReader snapshotFailureReader = mock(NoonEmailOtpReader.class);
            when(snapshotFailureReader.snapshot(anyString(), anyString())).thenThrow(
                    new IllegalStateException("mailbox snapshot failed", new EOFException("EOF"))
            );
            MutableClock snapshotClock = new MutableClock(ATTEMPTED_AT);

            NoonAuthRecoveryAttemptResult snapshotResult = recoveryGateway(
                    gateway,
                    snapshotFailureReader,
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    snapshotClock,
                    snapshotClock::advanceMillis
            ).attempt(command());

            assertTrue(snapshotResult.isTransientFailure());
            assertEquals(
                    NoonAuthRecoveryFailureStage.MAILBOX_SNAPSHOT,
                    snapshotResult.getFailureStage()
            );
            assertEquals(
                    NoonTransientErrorType.NETWORK_EOF,
                    snapshotResult.getTransientErrorType()
            );
            assertEquals(0, server.generateCount());

            NoonEmailOtpReader pollingFailureReader = mock(NoonEmailOtpReader.class);
            when(pollingFailureReader.snapshot(anyString(), anyString()))
                    .thenReturn(new NoonEmailOtpReader.MailboxCursor(7L, 100L, ATTEMPTED_AT));
            when(pollingFailureReader.pollAfter(anyString(), anyString(), any(), any(), any()))
                    .thenThrow(new IllegalStateException(
                            "mailbox poll failed",
                            new HttpConnectTimeoutException("connect timed out")
                    ));
            MutableClock pollingClock = new MutableClock(ATTEMPTED_AT);

            NoonAuthRecoveryAttemptResult pollingResult = recoveryGateway(
                    gateway,
                    pollingFailureReader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    pollingClock,
                    pollingClock::advanceMillis
            ).attempt(command());

            assertTrue(pollingResult.isTransientFailure());
            assertEquals(
                    NoonAuthRecoveryFailureStage.MAILBOX_POLLING,
                    pollingResult.getFailureStage()
            );
            assertEquals(
                    NoonTransientErrorType.CONNECT_TIMEOUT,
                    pollingResult.getTransientErrorType()
            );
            assertEquals(1, server.generateCount());
        }
    }

    @Test
    void mailboxAuthenticationFailureStaysDeterministicEvenIfItsMessageMentionsEof()
            throws Exception {
        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            NoonSessionGateway gateway = identityGateway(server);
            NoonEmailOtpReader reader = mock(NoonEmailOtpReader.class);
            when(reader.snapshot(anyString(), anyString())).thenThrow(
                    new IllegalStateException(
                            "EOF",
                            new AuthenticationFailedException("authentication failed")
                    )
            );
            MutableClock clock = new MutableClock(ATTEMPTED_AT);

            NoonAuthRecoveryAttemptResult result = recoveryGateway(
                    gateway,
                    reader,
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    clock,
                    clock::advanceMillis
            ).attempt(command());

            assertFalse(result.isTransientFailure());
            assertEquals(NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED, result.getFailureCode());
            assertEquals(0, server.generateCount());
        }
    }

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
    void identityPreparationTransportFailureIsTypedWithoutAnOuterReplay() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            NoonSessionGateway gateway = spy(identityGateway(server));
            doThrow(new IllegalStateException(
                    "Noon request failed",
                    new HttpConnectTimeoutException("connect timed out")
            )).when(gateway).prepareEmailOtpGeneration(anyString());

            NoonAuthRecoveryAttemptResult result = recoveryGateway(gateway).attempt(command());

            assertFalse(result.isIdentityAuthenticated());
            assertTrue(result.isTransientFailure());
            assertEquals(
                    NoonAuthRecoveryFailureCode.PROVIDER_TRANSIENT_FAILURE,
                    result.getFailureCode()
            );
            assertEquals(
                    NoonAuthRecoveryFailureStage.IDENTITY_PREPARATION,
                    result.getFailureStage()
            );
            assertEquals(NoonTransientErrorType.CONNECT_TIMEOUT, result.getTransientErrorType());
            verify(gateway).prepareEmailOtpGeneration(anyString());
            assertEquals(0, server.generateCount());
        }
    }

    @Test
    void otpSendExactTransientStatusWinsOverRateOrRiskWordsInProviderBody() throws Exception {
        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            server.failGenerate(
                    500,
                    "{\"error\":\"captcha rate limit unauthorized\"}"
            );
            NoonSessionGateway gateway = identityGateway(server);
            NoonEmailOtpReader noMailReader = mock(NoonEmailOtpReader.class);
            when(noMailReader.snapshot(anyString(), anyString()))
                    .thenReturn(new NoonEmailOtpReader.MailboxCursor(7L, 100L, ATTEMPTED_AT));
            when(noMailReader.pollAfter(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            MutableClock clock = new MutableClock(ATTEMPTED_AT);

            NoonAuthRecoveryAttemptResult result = recoveryGateway(
                    gateway,
                    noMailReader,
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    clock,
                    clock::advanceMillis
            ).attempt(command());

            assertTrue(result.isTransientFailure());
            assertEquals(NoonAuthRecoveryFailureStage.OTP_SEND, result.getFailureStage());
            assertEquals(NoonTransientErrorType.HTTP_500, result.getTransientErrorType());
            assertEquals(1, server.generateCount());
        }
    }

    @Test
    void otpSendUnauthorizedStatusIsDeterministicAndNeverResent() throws Exception {
        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            server.failGenerate(401, "{\"error\":\"unauthorized\"}");
            NoonSessionGateway gateway = identityGateway(server);
            NoonEmailOtpReader reader = mock(NoonEmailOtpReader.class);
            when(reader.snapshot(anyString(), anyString()))
                    .thenReturn(new NoonEmailOtpReader.MailboxCursor(7L, 100L, ATTEMPTED_AT));
            MutableClock clock = new MutableClock(ATTEMPTED_AT);

            NoonAuthRecoveryAttemptResult result = recoveryGateway(
                    gateway,
                    reader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    clock,
                    clock::advanceMillis
            ).attempt(command());

            assertFalse(result.isTransientFailure());
            assertEquals(NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED, result.getFailureCode());
            verify(reader, never()).pollAfter(anyString(), anyString(), any(), any(), any());
            assertEquals(1, server.generateCount());
        }
    }

    @Test
    void otpSendAndMailboxPollingTransientFailuresAreBothPreserved() throws Exception {
        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            server.failGenerate(503, "");
            NoonSessionGateway gateway = identityGateway(server);
            NoonEmailOtpReader reader = mock(NoonEmailOtpReader.class);
            when(reader.snapshot(anyString(), anyString()))
                    .thenReturn(new NoonEmailOtpReader.MailboxCursor(7L, 100L, ATTEMPTED_AT));
            when(reader.pollAfter(anyString(), anyString(), any(), any(), any()))
                    .thenThrow(new IllegalStateException(
                            "mailbox poll eof",
                            new EOFException("EOF")
                    ));
            MutableClock clock = new MutableClock(ATTEMPTED_AT);

            NoonAuthRecoveryAttemptResult result = recoveryGateway(
                    gateway,
                    reader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    clock,
                    clock::advanceMillis
            ).attempt(command());

            assertTrue(result.isTransientFailure());
            assertEquals(2, result.getTransientFailures().size());
            assertEquals(
                    NoonAuthRecoveryFailureStage.OTP_SEND,
                    result.getTransientFailures().get(0).getStage()
            );
            assertEquals(
                    NoonTransientErrorType.HTTP_503,
                    result.getTransientFailures().get(0).getErrorType()
            );
            assertEquals(
                    NoonAuthRecoveryFailureStage.MAILBOX_POLLING,
                    result.getTransientFailures().get(1).getStage()
            );
            assertEquals(
                    NoonTransientErrorType.NETWORK_EOF,
                    result.getTransientFailures().get(1).getErrorType()
            );
        }
    }

    @Test
    void whoamiTransportEofIsTypedWithoutAnOuterReplay() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            NoonSessionGateway gateway = spy(identityGateway(server));
            doThrow(new IllegalStateException(
                    "Noon WHOAMI 验证失败",
                    new EOFException("HTTP/1.1 header parser received no bytes")
            )).when(gateway).whoamiWithProjectSession(
                    any(NoonSessionGateway.ProjectSessionCookie.class),
                    anyString()
            );

            NoonAuthRecoveryAttemptResult result = recoveryGateway(gateway).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertTrue(projectResult.isTransientFailure());
            assertEquals(
                    NoonAuthRecoveryFailureStage.WHOAMI_VALIDATION,
                    projectResult.getFailureStage()
            );
            assertEquals(NoonTransientErrorType.NETWORK_EOF, projectResult.getTransientErrorType());
            verify(gateway).whoamiWithProjectSession(
                    any(NoonSessionGateway.ProjectSessionCookie.class),
                    anyString()
            );
            assertEquals(0, server.whoamiCount());
        }
    }

    @Test
    void continuousWhoami503StopsAfterThreeTransportAttemptsAndStaysTyped() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            server.failWhoami(503);

            NoonAuthRecoveryAttemptResult result =
                    recoveryGateway(identityGateway(server)).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertTrue(projectResult.isTransientFailure());
            assertEquals(
                    NoonAuthRecoveryFailureStage.WHOAMI_VALIDATION,
                    projectResult.getFailureStage()
            );
            assertEquals(NoonTransientErrorType.HTTP_503, projectResult.getTransientErrorType());
            assertEquals(3, server.whoamiCount());
            assertEquals(1, server.generateCount());
            assertEquals(1, server.sessionCreateCount());
        }
    }

    @Test
    void catalogEofIsReturnedAsTypedTransientProjectFailureWithoutAnOuterReplay() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            NoonSessionGateway gateway = spy(identityGateway(server));
            IllegalStateException catalogEof = new IllegalStateException(
                    "Noon catalog bootstrap failed",
                    new EOFException("HTTP/1.1 header parser received no bytes")
            );
            doThrow(catalogEof).when(gateway).validateCatalogProjectSession(
                    any(NoonSessionGateway.ProjectSessionCookie.class),
                    anyString()
            );

            NoonAuthRecoveryAttemptResult result = recoveryGateway(gateway).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertEquals(
                    NoonAuthRecoveryProjectResult.Code.TRANSIENT_PROVIDER_FAILURE,
                    projectResult.getCode()
            );
            assertTrue(projectResult.isTransientFailure());
            assertEquals(NoonAuthRecoveryFailureStage.CATALOG_VALIDATION, projectResult.getFailureStage());
            assertEquals(NoonTransientErrorType.NETWORK_EOF, projectResult.getTransientErrorType());
            assertFalse(projectResult.isRecovered());
            assertNull(projectResult.getCookie());
            assertFalse(projectResult.getSafeDiagnostic().contains("header parser"));
            verify(gateway).validateCatalogProjectSession(
                    any(NoonSessionGateway.ProjectSessionCookie.class),
                    anyString()
            );
            assertEquals(1, server.generateCount());
            assertEquals(1, server.sessionCreateCount());
        }
    }

    @Test
    void shouldRejectRecoveredCookieWhenCatalogRedirectsToLogin() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            server.redirectCatalogToLogin();

            NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertEquals(1, result.getProjectResults().size());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertEquals(NoonAuthRecoveryProjectResult.Code.COOKIE_VALIDATION_FAILED, projectResult.getCode());
            assertFalse(projectResult.isRecovered());
            assertNull(projectResult.getCookie());
            assertEquals(1, server.whoamiCount());
            assertEquals(1, server.catalogCount());
        }
    }

    @Test
    void shouldHandoffLoginHostCookieToCatalogHostBeforeValidation() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            server.requireCatalogSessionCookie();

            NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertEquals(1, result.getProjectResults().size());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertTrue(projectResult.isRecovered());
            assertTrue(projectResult.getCookie().contains("sid=recovered"));
            assertEquals(1, server.catalogCount());
            assertTrue(server.lastCatalogCookieHeader().contains("sid=recovered"));
        }
    }

    @Test
    void shouldBootstrapCatalogWebSessionBeforeCapabilityValidation() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            server.requireCatalogWebSessionBootstrap();

            NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            assertEquals(1, result.getProjectResults().size());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertTrue(projectResult.isRecovered());
            assertTrue(projectResult.getCookie().contains("catalog_sid=ready"));
            assertEquals(1, server.catalogBootstrapCount());
            assertEquals(1, server.catalogCount());
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
        assertTrue(NoonProjectSessionValidator.matchesTargetProject(
                objectMapper.readTree("{\"projectCode\":\" PRJ7001 \"}"),
                TARGET_PROJECT
        ));
        assertTrue(NoonProjectSessionValidator.matchesTargetProject(
                objectMapper.readTree("{\"context\":{\"project\":{\"code\":\"PRJ7001\"}}}"),
                TARGET_PROJECT
        ));
        assertTrue(NoonProjectSessionValidator.matchesTargetProject(
                objectMapper.readTree("{\"current_project_code\":\"PRJ7001\"}"),
                TARGET_PROJECT
        ));

        assertFalse(NoonProjectSessionValidator.matchesTargetProject(
                objectMapper.readTree("{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}"),
                TARGET_PROJECT
        ));
        assertFalse(NoonProjectSessionValidator.matchesTargetProject(
                objectMapper.readTree("{\"projectCode\":\"PRJ7001\",\"context\":{\"project_code\":\"PRJ9999\"}}"),
                TARGET_PROJECT
        ));
        assertFalse(NoonProjectSessionValidator.matchesTargetProject(
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
            assertTrue(result.getProjectResults().stream().allMatch(project -> project.isRecovered() && "merchant@example.com".equals(project.getUserCode())));
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

}
