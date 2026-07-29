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
import java.util.Arrays;
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
                CompetitorProductDetailTarget.competitor(88002L, "ZRISK001", null);
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        result.recordAttempt(ready);
        result.recordFailure(ready, "RATE_LIMITED", "HTTP 429");
        OperationalTask task = task(retryPayload());
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
        assertEquals(2, state(payload, "ZRISK001").getRetryAttempt());
        assertEquals(
                LocalDateTime.parse("2026-07-28T09:00:00"),
                state(payload, "ZRISK001").getRetryNotBefore()
        );
        assertEquals(1, state(payload, "ZLATER01").getRetryAttempt());
        assertEquals(
                LocalDateTime.parse("2026-07-28T10:00:00"),
                state(payload, "ZLATER01").getRetryNotBefore()
        );
    }

    private static String retryPayload() {
        CompetitorDetailRetryPayload payload = CompetitorDetailRetryPayload.empty();
        payload.setRetryStates(Arrays.asList(
                state(
                        CompetitorProductDetailTarget.competitor(
                                88002L,
                                "ZRISK001",
                                null
                        ),
                        "2026-07-28T08:00:00",
                        "DETAIL_REFRESH_FAILED"
                ),
                state(
                        CompetitorProductDetailTarget.competitor(
                                88003L,
                                "ZLATER01",
                                null
                        ),
                        "2026-07-28T10:00:00",
                        "PUBLIC_DETAIL_NOT_FOUND"
                )
        ));
        return payload.toJson();
    }

    private static CompetitorDetailRetryState state(
            CompetitorProductDetailTarget target,
            String notBefore,
            String errorCode
    ) {
        return new CompetitorDetailRetryState(
                target,
                1,
                LocalDateTime.parse(notBefore),
                errorCode,
                null
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
