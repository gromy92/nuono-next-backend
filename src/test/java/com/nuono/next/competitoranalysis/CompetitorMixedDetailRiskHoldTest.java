package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.system.task.OperationalTask;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompetitorMixedDetailRiskHoldTest {

    @Test
    void sharedRiskHoldDelaysReadyTargetWithoutShorteningLaterTarget() {
        CompetitorRefreshTaskFactory taskFactory = mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator coordinator = new CompetitorDetailRetryCoordinator(
                taskFactory,
                Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC)
        );
        CompetitorProductDetailTarget ready =
                CompetitorProductDetailTarget.competitor(88002L, "ZRISK", null);
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        result.recordAttempt(ready);
        result.recordFailure(ready, "RATE_LIMITED", "HTTP 429");
        OperationalTask task = task(
                "{\"detailRetryStates\":[{"
                        + "\"subjectType\":\"COMPETITOR\","
                        + "\"competitorProductId\":88002,"
                        + "\"noonProductCode\":\"ZRISK\","
                        + "\"retryAttempt\":1,"
                        + "\"retryNotBefore\":\"2026-07-28T08:00:00\","
                        + "\"errorCode\":\"DETAIL_REFRESH_FAILED\""
                        + "},{"
                        + "\"subjectType\":\"COMPETITOR\","
                        + "\"competitorProductId\":88003,"
                        + "\"noonProductCode\":\"ZLATER\","
                        + "\"retryAttempt\":1,"
                        + "\"retryNotBefore\":\"2026-07-28T10:00:00\","
                        + "\"errorCode\":\"PUBLIC_DETAIL_NOT_FOUND\""
                        + "}]}"
        );
        NoonRiskBackoffHold hold = new NoonRiskBackoffHold();
        hold.setBlockedUntil(LocalDateTime.parse("2026-07-28T09:00:00"));
        when(taskFactory.requeueDetailRetry(
                eq(150001L),
                eq(220123L),
                anyString(),
                eq("RATE_LIMITED"),
                anyString()
        )).thenReturn(true);

        assertTrue(coordinator.scheduleFailure(
                task,
                220123L,
                result,
                "RATE_LIMITED",
                "HTTP 429",
                hold
        ));

        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        verify(taskFactory).requeueDetailRetry(
                eq(150001L),
                eq(220123L),
                payloadJson.capture(),
                eq("RATE_LIMITED"),
                anyString()
        );
        CompetitorDetailRetryPayload payload =
                CompetitorDetailRetryPayload.fromJson(payloadJson.getValue());
        assertEquals(LocalDateTime.parse("2026-07-28T09:00:00"), payload.getRetryNotBefore());
        assertEquals(2, state(payload, "ZRISK").getRetryAttempt());
        assertEquals(
                LocalDateTime.parse("2026-07-28T09:00:00"),
                state(payload, "ZRISK").getRetryNotBefore()
        );
        assertEquals(1, state(payload, "ZLATER").getRetryAttempt());
        assertEquals(
                LocalDateTime.parse("2026-07-28T10:00:00"),
                state(payload, "ZLATER").getRetryNotBefore()
        );
    }

    private static CompetitorDetailRetryState state(
            CompetitorDetailRetryPayload payload,
            String noonProductCode
    ) {
        return payload.getRetryStates().stream()
                .filter(value -> noonProductCode.equals(
                        value.getTarget().getNoonProductCode()
                ))
                .findFirst()
                .orElseThrow();
    }

    private static OperationalTask task(String payloadJson) {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setPayloadJson(payloadJson);
        return task;
    }
}
