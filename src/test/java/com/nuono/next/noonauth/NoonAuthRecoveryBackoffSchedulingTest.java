package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryBackoffSchedulingTest
        extends AbstractNoonAuthRecoveryWorkerTestSupport {

    @Test
    void activeTransientBackoffSkipsGatewayAndOtpSendIntent() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                17L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(171L, 17L, 307L, "PRJ307", "STORE307", 1701L, 3L)
        );
        NoonAuthTransientBackoffState hold = transientHold(
                7001L,
                NoonTransientErrorType.HTTP_503,
                LocalDateTime.of(2026, 7, 16, 5, 8),
                17L
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(17L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 17L, 3L));
        when(transientBackoffRepository.listActiveHolds(eq(7001L), any()))
                .thenReturn(List.of(hold));

        assertEquals(1, worker.runOnce());

        verify(gateway, never()).attempt(any());
        verify(repository, never()).recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        );
        verify(repository).transitionRecovery(
                eq(17L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                eq(LocalDateTime.of(2026, 7, 16, 5, 8)),
                eq("TRANSIENT_BACKOFF_ACTIVE"), any(), eq(null), eq(true), any()
        );
    }

    @Test
    void allHeldStoresWakeAtTheEarliestStoreDeadline() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                171L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1711L, 171L, 307L, "PRJ307", "STORE307", 17101L, 3L),
                item(1712L, 171L, 308L, "PRJ308", "STORE308", 17102L, 4L)
        );
        NoonAuthTransientBackoffState twoMinuteHold = transientHold(
                7001L,
                NoonTransientErrorType.NETWORK_EOF,
                LocalDateTime.of(2026, 7, 16, 5, 2),
                171L
        );
        NoonAuthTransientBackoffState sixteenMinuteHold = transientHold(
                7002L,
                NoonTransientErrorType.HTTP_503,
                LocalDateTime.of(2026, 7, 16, 5, 16),
                171L
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(171L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString())).thenAnswer(invocation ->
                blockedState(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        171L,
                        "PRJ307".equals(invocation.getArgument(1)) ? 3L : 4L
                )
        );
        when(transientBackoffRepository.listActiveHolds(eq(7001L), any()))
                .thenReturn(List.of(twoMinuteHold));
        when(transientBackoffRepository.listActiveHolds(eq(7002L), any()))
                .thenReturn(List.of(sixteenMinuteHold));

        assertEquals(1, worker.runOnce());

        verify(gateway, never()).attempt(any());
        verify(repository).transitionRecovery(
                eq(171L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                eq(LocalDateTime.of(2026, 7, 16, 5, 2)),
                eq("TRANSIENT_BACKOFF_ACTIVE"), any(), eq(null), eq(true), any()
        );
    }

    @Test
    void activeBackoffForOneStoreDoesNotBlockAnotherStoreInTheSameIdentity() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                23L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(231L, 23L, 307L, "PRJ307", "STORE307", 2301L, 3L),
                item(232L, 23L, 308L, "PRJ308", "STORE308", 2302L, 4L)
        );
        NoonAuthTransientBackoffState hold = transientHold(
                7001L,
                NoonTransientErrorType.NETWORK_EOF,
                LocalDateTime.of(2026, 7, 16, 5, 8),
                23L
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(23L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString())).thenAnswer(invocation ->
                blockedState(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        23L,
                        "PRJ307".equals(invocation.getArgument(1)) ? 3L : 4L
                )
        );
        when(transientBackoffRepository.listActiveHolds(eq(7001L), any()))
                .thenReturn(List.of(hold));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            assertEquals(1, command.getProjectTargets().size());
            assertEquals("PRJ308", command.getProjectTargets().get(0).getProjectCode());
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "unblocked-store-message",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=recovered-308"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository, never()).markProjectRecovering(
                eq(307L), eq("PRJ307"), anyLong(), anyLong(), any(),
                anyLong(), anyString(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(2302L), eq(23L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository).transitionRecovery(
                eq(23L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                eq(LocalDateTime.of(2026, 7, 16, 5, 8)),
                eq("PROJECT_TRANSIENT_BACKOFF"), any(), eq(null), eq(true), any()
        );
    }
}
