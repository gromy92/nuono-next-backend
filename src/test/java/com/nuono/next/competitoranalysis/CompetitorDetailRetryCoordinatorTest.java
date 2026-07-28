package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.system.task.OperationalTask;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompetitorDetailRetryCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-07-28T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void ordinaryFailureRequeuesOnlyFailedTargetAfterTwoMinutesAndKeepsCumulativeCounts() {
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator coordinator =
                new CompetitorDetailRetryCoordinator(taskFactory, CLOCK);
        OperationalTask task = task(
                "{\"watchProductId\":180123,"
                        + "\"detailTargetTotal\":5,"
                        + "\"detailSucceededCount\":2}"
        );
        CompetitorProductDetailTarget succeeded =
                CompetitorProductDetailTarget.self("ZSELF001");
        CompetitorProductDetailTarget failed =
                CompetitorProductDetailTarget.competitor(
                        88002L,
                        "ZFAIL002",
                        "https://www.noon.com/saudi-en/zfail002/p"
                );
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        result.recordAttempt(succeeded);
        result.recordSuccess(succeeded);
        result.recordAttempt(failed);
        result.recordFailure(failed, "DETAIL_REFRESH_FAILED", "detail parse failed");
        when(taskFactory.requeueDetailRetry(
                eq(150001L),
                eq(220123L),
                anyString(),
                eq("DETAIL_REFRESH_FAILED"),
                anyString()
        )).thenReturn(true);

        assertTrue(coordinator.scheduleFailure(
                task,
                220123L,
                result,
                "DETAIL_REFRESH_FAILED",
                "detail parse failed",
                null
        ));

        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        verify(taskFactory).requeueDetailRetry(
                eq(150001L),
                eq(220123L),
                payloadJson.capture(),
                eq("DETAIL_REFRESH_FAILED"),
                anyString()
        );
        CompetitorDetailRetryPayload payload =
                CompetitorDetailRetryPayload.fromJson(payloadJson.getValue());
        assertEquals(1, payload.getRetryAttempt());
        assertEquals(4, payload.getMaxRetryAttempts());
        assertEquals(LocalDateTime.parse("2026-07-28T02:02:00"), payload.getRetryNotBefore());
        assertEquals(220123L, payload.getRootRunId());
        assertEquals(220123L, payload.getRetryOfRunId());
        assertEquals(5, payload.getDetailTargetTotal());
        assertEquals(2, payload.getDetailRequestAttemptCount());
        assertEquals(3, payload.getDetailSucceededCount());
        assertEquals(1, payload.getFailedDetailTargets().size());
        assertEquals(failed, payload.getFailedDetailTargets().get(0));
    }

    @Test
    void queuedRetryIsNotReadyBeforeNotBeforeAndBecomesReadyAtBoundary() {
        OperationalTask task = task(
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\""
                        + "}]}"
        );
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);

        assertFalse(new CompetitorDetailRetryCoordinator(taskFactory, CLOCK).isReady(task));
        assertTrue(new CompetitorDetailRetryCoordinator(
                taskFactory,
                Clock.fixed(
                        Instant.parse("2026-07-28T02:02:00Z"),
                        ZoneOffset.UTC
                )
        ).isReady(task));
    }

    @Test
    void initialTaskIsNotRetryAndCannotYieldAnEmptyRetryTargetBatch() {
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator coordinator =
                new CompetitorDetailRetryCoordinator(taskFactory, CLOCK);
        OperationalTask task = task("{\"watchProductId\":180123}");

        assertFalse(coordinator.isRetry(task));
        assertThrows(IllegalStateException.class, () -> coordinator.retryTargets(task));
    }

    @Test
    void fourthFailedRetryIsExhaustedAndDoesNotRequeueAgain() {
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator coordinator =
                new CompetitorDetailRetryCoordinator(taskFactory, CLOCK);
        CompetitorProductDetailTarget failed =
                CompetitorProductDetailTarget.self("ZSELF001");
        OperationalTask task = task(
                "{\"retryAttempt\":4,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T01:50:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\""
                        + "}]}"
        );
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        result.recordAttempt(failed);
        result.recordFailure(failed, "DETAIL_REFRESH_FAILED", "still failing");

        assertFalse(coordinator.scheduleFailure(
                task,
                220127L,
                result,
                "DETAIL_REFRESH_FAILED",
                "still failing",
                null
        ));
        verify(taskFactory, never()).requeueDetailRetry(
                eq(150001L),
                eq(220127L),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void activeRiskHoldParksSameAttemptWithoutConsumingRetryBudget() {
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator coordinator =
                new CompetitorDetailRetryCoordinator(taskFactory, CLOCK);
        OperationalTask task = task(
                "{\"retryAttempt\":2,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T01:50:00\","
                        + "\"rootRunId\":220123,"
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"COMPETITOR\","
                        + "\"competitorProductId\":88002,"
                        + "\"noonProductCode\":\"ZFAIL002\""
                        + "}]}"
        );
        NoonRiskBackoffHold hold = new NoonRiskBackoffHold();
        hold.setBlockedUntil(LocalDateTime.parse("2026-07-28T02:11:00"));
        when(taskFactory.requeueDetailRetry(
                eq(150001L),
                eq(220125L),
                anyString(),
                eq("COMPETITOR_RISK_BACKOFF"),
                anyString()
        )).thenReturn(true);

        coordinator.parkForRiskHold(task, 220125L, hold, "shared hold active");

        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        verify(taskFactory).requeueDetailRetry(
                eq(150001L),
                eq(220125L),
                payloadJson.capture(),
                eq("COMPETITOR_RISK_BACKOFF"),
                anyString()
        );
        CompetitorDetailRetryPayload payload =
                CompetitorDetailRetryPayload.fromJson(payloadJson.getValue());
        assertEquals(2, payload.getRetryAttempt());
        assertEquals(4, payload.getMaxRetryAttempts());
        assertEquals(LocalDateTime.parse("2026-07-28T02:11:00"), payload.getRetryNotBefore());
        assertEquals(220123L, payload.getRootRunId());
        assertEquals(220125L, payload.getRetryOfRunId());
        assertEquals(1, payload.getFailedDetailTargets().size());
        assertEquals("ZFAIL002", payload.getFailedDetailTargets().get(0).getNoonProductCode());
    }

    @Test
    void invalidCodeIsNotRetriedWhileOtherFailureContinuesAndRemainsTerminal() {
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator coordinator =
                new CompetitorDetailRetryCoordinator(taskFactory, CLOCK);
        CompetitorProductDetailTarget invalid =
                CompetitorProductDetailTarget.self("ZINVALID001");
        CompetitorProductDetailTarget retryable =
                CompetitorProductDetailTarget.competitor(88002L, "ZFAIL002", null);
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        result.recordAttempt(invalid);
        result.recordFailure(
                invalid,
                "INVALID_NOON_PRODUCT_CODE",
                "invalid product code"
        );
        result.recordAttempt(retryable);
        result.recordFailure(
                retryable,
                "DETAIL_REFRESH_FAILED",
                "detail timeout"
        );
        when(taskFactory.requeueDetailRetry(
                eq(150001L),
                eq(220123L),
                anyString(),
                eq("DETAIL_REFRESH_FAILED"),
                anyString()
        )).thenReturn(true);

        assertTrue(coordinator.scheduleFailure(
                task("{}"),
                220123L,
                result,
                "INVALID_NOON_PRODUCT_CODE",
                "invalid product code",
                null
        ));

        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        verify(taskFactory).requeueDetailRetry(
                eq(150001L),
                eq(220123L),
                payloadJson.capture(),
                eq("DETAIL_REFRESH_FAILED"),
                anyString()
        );
        CompetitorDetailRetryPayload payload =
                CompetitorDetailRetryPayload.fromJson(payloadJson.getValue());
        assertEquals(List.of(retryable), payload.getFailedDetailTargets());
        assertEquals(1, payload.getDetailTerminalFailedCount());
        assertEquals("INVALID_NOON_PRODUCT_CODE", payload.getDetailTerminalErrorCode());

        CompetitorProductDetailRefreshResult recovered =
                CompetitorProductDetailRefreshResult.empty();
        recovered.recordAttempt(retryable);
        recovered.recordSuccess(retryable);
        coordinator.addPriorCounts(task(payloadJson.getValue()), recovered);
        assertEquals(2, recovered.getAttemptedCount());
        assertEquals(3, recovered.getRequestAttemptCount());
        assertEquals(1, recovered.getSucceededCount());
        assertEquals(1, recovered.getFailedCount());
        assertEquals("INVALID_NOON_PRODUCT_CODE", recovered.getFirstErrorCode());
    }

    private OperationalTask task(String payloadJson) {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setPayloadJson(payloadJson);
        return task;
    }
}
