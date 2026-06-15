package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullFailurePolicyTest {

    private Clock clock;
    private NoonPullFailurePolicy policy;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-22T04:00:00Z"), ZoneOffset.UTC);
        policy = new NoonPullFailurePolicy(clock);
    }

    @Test
    void shouldClassifyKnownFailuresWithoutRelyingOnRawStrings() {
        assertEquals(NoonPullFailureType.PROVIDER_UNAVAILABLE, policy.classify("HTTP 503 provider unavailable"));
        assertEquals(NoonPullFailureType.PROVIDER_NOT_CONFIGURED, policy.classify("provider is not configured"));
        assertEquals(NoonPullFailureType.INVALID_PROJECT_CODE, policy.classify(
                "provider unavailable: report export create failed: HTTP 400 {\"error\":\"Invalid project code\"}"
        ));
        assertEquals(NoonPullFailureType.AUTH_REQUIRED, policy.classify("401 auth required"));
        assertEquals(NoonPullFailureType.EMPTY_REPORT, policy.classify("empty report"));
        assertEquals(NoonPullFailureType.EMPTY_REPORT_PENDING_CONFIRMATION, policy.classify("empty report pending confirmation"));
        assertEquals(NoonPullFailureType.REPORT_NOT_READY, policy.classify("export report not ready"));
        assertEquals(NoonPullFailureType.PROVIDER_RETENTION_LIMIT, policy.classify("outside provider retention limit"));
        assertEquals(NoonPullFailureType.MISSING_COLUMNS, policy.classify("missing columns: sku"));
        assertEquals(NoonPullFailureType.MAPPING_FAILED, policy.classify("mapping failed for row 3"));
        assertEquals(NoonPullFailureType.TIMEOUT, policy.classify("socket timeout"));
        assertEquals(NoonPullFailureType.RATE_LIMITED, policy.classify("429 too many requests"));
        assertEquals(NoonPullFailureType.BLOCKED_BY_RISK_CONTROL, policy.classify("blocked by risk control"));
        assertEquals(NoonPullFailureType.CAPTCHA_REQUIRED, policy.classify("captcha required"));
        assertEquals(NoonPullFailureType.PARTIAL_SUCCESS, policy.classify("imported with partial success"));
        assertEquals(NoonPullFailureType.UNKNOWN_FAILURE, policy.classify("unexpected provider response"));
    }

    @Test
    void shouldRetryTimeoutAndTemporaryProviderFailureWithBoundedBackoff() {
        NoonPullFailureDecision timeout = policy.decide(NoonPullFailureType.TIMEOUT, 2);
        NoonPullFailureDecision unavailable = policy.decide(NoonPullFailureType.PROVIDER_UNAVAILABLE, 1);

        assertEquals(NoonPullRetryAction.RETRY, timeout.getAction());
        assertEquals(NoonPullRetryAction.RETRY, unavailable.getAction());
        assertTrue(timeout.isRetryable());
        assertTrue(unavailable.isRetryable());
        assertFalse(timeout.requiresManualAction());
        assertTrue(Duration.between(now(), timeout.getNextRetryAt()).toMinutes() >= 10);
        assertTrue(Duration.between(now(), timeout.getNextRetryAt()).toHours() <= 2);
        assertTrue(Duration.between(now(), unavailable.getNextRetryAt()).toMinutes() >= 5);
    }

    @Test
    void shouldDelayReportNotReadyWithoutTreatingItAsEmptyReport() {
        NoonPullFailureDecision decision = policy.decide(NoonPullFailureType.REPORT_NOT_READY, 1);

        assertEquals(NoonPullRetryAction.DELAY, decision.getAction());
        assertTrue(decision.isRetryable());
        assertFalse(decision.requiresManualAction());
        assertTrue(decision.getSummary().contains("report_not_ready"));
        assertTrue(Duration.between(now(), decision.getNextRetryAt()).toMinutes() >= 30);
        assertTrue(Duration.between(now(), decision.getNextRetryAt()).toHours() <= 3);
    }

    @Test
    void shouldDelayEmptyReportPendingConfirmationAcrossLateNoonSalesWindow() {
        NoonPullFailureDecision decision = policy.decide(NoonPullFailureType.EMPTY_REPORT_PENDING_CONFIRMATION, 1);

        assertEquals(NoonPullRetryAction.DELAY, decision.getAction());
        assertTrue(decision.isRetryable());
        assertFalse(decision.requiresManualAction());
        assertTrue(decision.getSummary().contains("empty_report_pending_confirmation"));
        assertTrue(Duration.between(now(), decision.getNextRetryAt()).toHours() >= 6);
    }

    @Test
    void shouldPauseRiskControlCaptchaAndRateLimitInsteadOfTightRetrying() {
        assertPauseDecision(policy.decide(NoonPullFailureType.RATE_LIMITED, 1));
        assertPauseDecision(policy.decide(NoonPullFailureType.BLOCKED_BY_RISK_CONTROL, 1));
        assertPauseDecision(policy.decide(NoonPullFailureType.CAPTCHA_REQUIRED, 1));
    }

    @Test
    void shouldRequireManualActionForAuthSchemaAndRetentionFailures() {
        assertManualNonRetry(policy.decide(NoonPullFailureType.AUTH_REQUIRED, 1));
        assertManualNonRetry(policy.decide(NoonPullFailureType.MISSING_COLUMNS, 1));
        assertManualNonRetry(policy.decide(NoonPullFailureType.PROVIDER_RETENTION_LIMIT, 1));
    }

    @Test
    void shouldRequireManualActionForInvalidProjectCodeWithoutPausingFutureSchedule() {
        NoonPullFailureDecision decision = policy.decide(NoonPullFailureType.INVALID_PROJECT_CODE, 1);

        assertEquals(NoonPullRetryAction.MANUAL_ACTION, decision.getAction());
        assertFalse(decision.isRetryable());
        assertTrue(decision.requiresManualAction());
        assertFalse(decision.shouldPausePlan());
        assertTrue(decision.getSummary().contains("invalid_project_code"));
    }

    private void assertPauseDecision(NoonPullFailureDecision decision) {
        assertEquals(NoonPullRetryAction.PAUSE, decision.getAction());
        assertFalse(decision.isRetryable());
        assertTrue(decision.requiresManualAction());
        assertTrue(decision.shouldPausePlan());
        assertTrue(Duration.between(now(), decision.getNextRetryAt()).toHours() >= 6);
    }

    private void assertManualNonRetry(NoonPullFailureDecision decision) {
        assertEquals(NoonPullRetryAction.MANUAL_ACTION, decision.getAction());
        assertFalse(decision.isRetryable());
        assertTrue(decision.requiresManualAction());
        assertFalse(decision.shouldPausePlan());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
