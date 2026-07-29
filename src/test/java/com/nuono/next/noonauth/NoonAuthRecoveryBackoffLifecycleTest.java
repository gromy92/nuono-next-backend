package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryBackoffLifecycleTest
        extends AbstractNoonAuthRecoveryWorkerTestSupport {

    @Test
    void transientBackoffAcrossClaimsSkipsActiveHoldThenRetriesAndRecovers() {
        MutableClock mutableClock = new MutableClock(
                Instant.parse("2026-07-16T05:00:00Z"),
                ZoneOffset.UTC
        );
        worker = new NoonAuthRecoveryWorker(
                repository,
                properties,
                gateway,
                new NoonAuthTransientBackoffGuard(transientBackoffRepository, mutableClock),
                mutableClock,
                "worker-test",
                "shared@example.com",
                "imap-secret"
        );
        NoonAuthIdentityRecoveryRecord first = recovery(
                26L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        NoonAuthIdentityRecoveryRecord held = recovery(
                26L, NoonAuthRecoveryStatus.WAITING_COOLDOWN, 4L, 1, 1
        );
        NoonAuthIdentityRecoveryRecord retry = recovery(
                26L, NoonAuthRecoveryStatus.WAITING_COOLDOWN, 6L, 1, 1
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(261L, 26L, 307L, "PRJ307", "STORE307", 2601L, 3L)
        );
        AtomicReference<NoonAuthTransientBackoffState> state = new AtomicReference<>();
        AtomicInteger gatewayCalls = new AtomicInteger();
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(
                List.of(first),
                List.of(held),
                List.of(retry)
        );
        when(repository.listPendingItems(26L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 26L, 3L));
        when(transientBackoffRepository.listActiveHolds(eq(7001L), any()))
                .thenAnswer(invocation -> {
                    NoonAuthTransientBackoffState current = state.get();
                    LocalDateTime checkedAt = invocation.getArgument(1);
                    return current != null
                            && current.getAttemptCount() > 0
                            && current.getBlockedUntil().isAfter(checkedAt)
                            ? List.of(current)
                            : List.of();
                });
        when(transientBackoffRepository.incrementFailure(any(), any(), any()))
                .thenAnswer(invocation -> {
                    NoonAuthTransientBackoffState failure = invocation.getArgument(0);
                    state.set(failure);
                    return failure;
                });
        when(transientBackoffRepository.resetForRecovery(
                eq(7001L),
                eq(26L),
                any(NoonAuthTransientBackoffWriteFence.class),
                any()
        )).thenAnswer(invocation -> {
            state.get().setAttemptCount(0);
            return true;
        });
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            if (gatewayCalls.getAndIncrement() == 0) {
                return NoonAuthRecoveryAttemptResult.authenticated(
                        "first-transient-message",
                        List.of(NoonAuthRecoveryProjectResult.transientFailure(
                                command.getProjectTargets().get(0),
                                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                                NoonTransientErrorType.NETWORK_EOF,
                                "CATALOG_VALIDATION: transient NETWORK_EOF"
                        ))
                );
            }
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "second-success-message",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=recovered",
                            "user-recovered"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());
        assertEquals(1, worker.runOnce());
        verify(gateway, times(1)).attempt(any());

        mutableClock.setInstant(Instant.parse("2026-07-16T05:03:00Z"));
        assertEquals(1, worker.runOnce());

        verify(gateway, times(2)).attempt(any());
        verify(repository, times(2)).recordSendIntent(
                eq(26L), any(), anyLong(), anyString(), any(), any()
        );
        verify(transientBackoffRepository, times(1))
                .incrementFailure(any(), any(), any());
        verify(transientBackoffRepository, times(1)).resetForRecovery(
                eq(7001L),
                eq(26L),
                any(NoonAuthTransientBackoffWriteFence.class),
                any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(2601L), eq(26L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
    }
}
