package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FileParseWorkflowStatePolicyTest {

    private final FileParseWorkflowStatePolicy policy = new FileParseWorkflowStatePolicy();

    @Test
    void shouldResolveWorkflowStepStatusWithoutMapperState() {
        assertEquals("succeeded", policy.stepStatus("failed", "parsing", true));
        assertEquals("failed", policy.stepStatus("failed", "parsing", false));
        assertEquals("running", policy.stepStatus("parsing", "parsing", false));
        assertEquals("pending", policy.stepStatus("reading", "parsing", false));
        assertEquals("pending", policy.stepStatus(null, "parsing", false));
    }

    @Test
    void shouldChooseStaleRetryUntilMaxAttemptsThenFinalFailure() {
        FileParseWorkflowStatePolicy.StaleRecoveryDecision retry = policy.staleRecoveryDecision(1, 3, 900);

        assertFalse(retry.isFinalFailure());
        assertEquals("PARSE_STALE_RETRYING", retry.getFailureCode());
        assertTrue(retry.getFailureMessage().contains("系统正在自动重试第 2 次"));

        FileParseWorkflowStatePolicy.StaleRecoveryDecision finalFailure = policy.staleRecoveryDecision(3, 3, 900);

        assertTrue(finalFailure.isFinalFailure());
        assertEquals("PARSE_STALE_TIMEOUT", finalFailure.getFailureCode());
        assertTrue(finalFailure.getFailureMessage().contains("已达到最大自动重试次数 3 次"));
    }

    @Test
    void shouldScheduleRetryOnlyForTransientAiFailuresWithRemainingAttempts() {
        assertTrue(policy.shouldScheduleRetry(1, "OPENAI_REQUEST_TIMEOUT", "socket timeout", 3));
        assertTrue(policy.shouldScheduleRetry(0, "OPENAI_HTTP_503", "service unavailable", 3));
        assertTrue(policy.shouldScheduleRetry(0, "OPENAI_HTTP_550", "provider temporary failure", 3));

        assertFalse(policy.shouldScheduleRetry(2, "OPENAI_REQUEST_TIMEOUT", "socket timeout", 3));
        assertFalse(policy.shouldScheduleRetry(0, "OPENAI_HTTP_400", "bad request", 3));
        assertFalse(policy.shouldScheduleRetry(0, "OPENAI_HTTP_429", "usage_limit_reached", 3));
    }
}
