package com.nuono.next.noonauth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryRateLimitRetryTest extends AbstractNoonAuthRecoveryWorkerTestSupport {

    @Test
    void firstRateLimitSchedulesOneAutomaticRetryAfterProviderCooldown() {
        properties.setRateLimitRetrySeconds(60);
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                30L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 30L, 308L, "PRJ308", "STORE308", 3001L, 5L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery), List.of());
        when(repository.listPendingItems(30L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString()))
                .thenReturn(blockedState(308L, "PRJ308", 30L, 5L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED,
                    null,
                    "provider rate limited the send"
            );
        });

        worker.runOnce();
        worker.runOnce();

        verify(gateway, times(1)).attempt(any());
        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                eq(NoonProjectAuthStatus.MANUAL_HOLD), anyString(), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(repository, never()).requeueBlockedTaskAfterRecoveryCas(
                anyLong(), anyLong(), any(), anyLong(), anyString(), any()
        );
        verify(repository, atLeastOnce()).transitionRecovery(
                eq(30L), any(), eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                eq(LocalDateTime.of(2026, 7, 16, 5, 30)), eq("SEND_RATE_LIMITED"),
                any(), any(), eq(true), any()
        );
    }

    @Test
    void secondRateLimitExhaustsTheTwoSendBudgetAndEntersManualHold() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                31L, NoonAuthRecoveryStatus.WAITING_COOLDOWN, 4L, 1, 1
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 31L, 308L, "PRJ308", "STORE308", 3101L, 5L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery), List.of());
        when(repository.listPendingItems(31L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString()))
                .thenReturn(blockedState(308L, "PRJ308", 31L, 5L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED,
                    null,
                    "provider rate limited the second send"
            );
        });

        worker.runOnce();
        worker.runOnce();

        verify(gateway, times(1)).attempt(any());
        verify(repository).markProjectRecoveryFailed(
                eq(308L), eq("PRJ308"), eq(31L), eq(5L), any(), anyLong(), anyString(),
                eq(NoonProjectAuthStatus.MANUAL_HOLD), eq("SEND_RATE_LIMITED"), any(), any()
        );
        verify(repository, atLeastOnce()).transitionRecovery(
                eq(31L), any(), eq(NoonAuthRecoveryStatus.MANUAL_HOLD), anyLong(), anyString(),
                any(), eq("SEND_RATE_LIMITED"), any(), any(), eq(true), any()
        );
    }

    @Test
    void manualHoldStopsTheTimedRetryOfADpRuntimeTaskButKeepsItsRecoveryItem() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                32L, NoonAuthRecoveryStatus.WAITING_COOLDOWN, 4L, 1, 1
        );
        NoonAuthRecoveryItemRecord dpItem =
                item(2L, 32L, 308L, "PRJ308", "STORE308", 3201L, 5L);
        dpItem.setSourceDomain("DP_RUNTIME");
        NoonAuthWaitingTaskHandler handler = mock(NoonAuthWaitingTaskHandler.class);
        when(handler.supports("DP_RUNTIME")).thenReturn(true);
        when(handler.hold(
                eq(dpItem), any(), anyLong(), anyString(), eq("SEND_RATE_LIMITED"),
                any(), any()
        )).thenReturn(NoonAuthWaitingTaskOutcome.MANUAL_REVIEW);
        worker.setWaitingTaskHandlers(List.of(handler));
        when(repository.listDueRecoveries(any(), anyInt()))
                .thenReturn(List.of(recovery), List.of());
        when(repository.listPendingItems(32L, Integer.MAX_VALUE)).thenReturn(List.of(dpItem));
        when(repository.selectProjectAuthState(anyLong(), anyString()))
                .thenReturn(blockedState(308L, "PRJ308", 32L, 5L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED,
                    null,
                    "provider rate limited the second send"
            );
        });

        worker.runOnce();

        verify(handler).hold(
                eq(dpItem), any(), anyLong(), anyString(), eq("SEND_RATE_LIMITED"),
                any(), any()
        );
        verify(handler, never()).fail(
                any(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(repository, never()).transitionRecoveryItem(
                eq(2L), eq(32L), any(), any(), any(), anyLong(), anyString(),
                any(), any(), any(), any()
        );
    }

    @Test
    void workerPassesTheExactHistoricalRateLimitCutoffToTheReleasePath() {
        worker = new NoonAuthRecoveryWorker(
                repository,
                properties,
                gateway,
                Clock.fixed(Instant.parse("2026-07-16T05:00:00Z"), ZoneOffset.UTC),
                "worker-test",
                "shared@example.com",
                "new-imap-secret"
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of());

        worker.runOnce();

        verify(repository).releaseEligibleManualHolds(
                eq(NoonAuthIdentityKey.fromEmail("shared@example.com")),
                eq(NoonAuthIdentityKey.configFingerprint(
                        "shared@example.com",
                        "new-imap-secret",
                        properties.normalizedTrustedSenderDomains()
                )),
                eq(LocalDateTime.of(2026, 7, 16, 4, 30)),
                eq(LocalDateTime.of(2026, 7, 16, 5, 1)),
                eq(LocalDateTime.of(2026, 7, 16, 5, 0))
        );
    }
}
