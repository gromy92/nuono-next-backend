package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nuono.next.system.task.OperationalTask;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CompetitorDetailTargetlessRetryTest {
    @Test
    void targetlessFailureCannotCreateOrContinueADetailRetry() {
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator coordinator = new CompetitorDetailRetryCoordinator(
                taskFactory,
                Clock.fixed(Instant.parse("2026-07-28T02:00:00Z"), ZoneOffset.UTC)
        );
        CompetitorProductDetailRefreshResult failure =
                CompetitorProductDetailRefreshResult.empty();
        failure.recordFailure("DETAIL_REFRESH_FAILED", "target resolution failed");

        assertFalse(coordinator.scheduleFailure(
                task(initialPayload()),
                220123L,
                failure,
                "DETAIL_REFRESH_FAILED",
                "target resolution failed",
                null
        ));
        assertFalse(coordinator.scheduleFailure(
                task(retryPayload()),
                220124L,
                failure,
                "DETAIL_REFRESH_FAILED",
                "target resolution failed",
                null
        ));
        verify(taskFactory, never()).requeueDetailRetry(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    private static String initialPayload() {
        return "{\"watchProductId\":180123}";
    }

    private static String retryPayload() {
        return CompetitorDetailRetryPayload.fromJson(
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T01:59:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"}]}"
        ).toJson();
    }

    private static OperationalTask task(String payloadJson) {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setPayloadJson(payloadJson);
        return task;
    }
}
