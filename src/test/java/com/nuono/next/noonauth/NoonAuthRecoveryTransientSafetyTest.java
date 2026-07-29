package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class NoonAuthRecoveryTransientSafetyTest
        extends AbstractNoonAuthRecoveryWorkerTestSupport {

    @Test
    void unmappedProjectIsHeldBeforeOtpWhileMappedProjectStillRecovers() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                25L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(251L, 25L, 307L, "PRJ307", "STORE307", 2501L, 3L),
                item(252L, 25L, 308L, "PRJ308", "STORE308", 2502L, 4L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(25L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString())).thenAnswer(invocation ->
                blockedState(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        25L,
                        "PRJ307".equals(invocation.getArgument(1)) ? 3L : 4L
                )
        );
        when(transientBackoffRepository.resolveLogicalStoreId(307L, "PRJ307"))
                .thenReturn(null);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            assertEquals(1, command.getProjectTargets().size());
            assertEquals("PRJ308", command.getProjectTargets().get(0).getProjectCode());
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "mapped-project-message",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=recovered-308",
                            "user-PRJ308"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository).markProjectRecoveryFailed(
                eq(307L), eq("PRJ307"), eq(25L), eq(3L), any(), anyLong(), anyString(),
                eq(NoonProjectAuthStatus.MANUAL_HOLD),
                eq("LOGICAL_STORE_MAPPING_MISSING"), any(), any()
        );
        verify(repository).failBlockedTaskAfterRecovery(
                eq(2501L), eq(25L), any(), anyLong(), anyString(),
                eq("LOGICAL_STORE_MAPPING_MISSING"), any(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(2502L), eq(25L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(gateway, times(1)).attempt(any());
    }

    @Test
    void mixedRecoveredAndTransientProjectsCommitSuccessBeforeStartingCooldown() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                18L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(181L, 18L, 307L, "PRJ307", "STORE307", 1801L, 3L),
                item(182L, 18L, 308L, "PRJ308", "STORE308", 1802L, 4L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(18L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString())).thenAnswer(invocation ->
                blockedState(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        18L,
                        "PRJ307".equals(invocation.getArgument(1)) ? 3L : 4L
                )
        );
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "mixed-project-message",
                    List.of(
                            NoonAuthRecoveryProjectResult.transientFailure(
                                    command.getProjectTargets().get(0),
                                    NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                                    NoonTransientErrorType.HTTP_503,
                                    "CATALOG_VALIDATION: transient HTTP_503"
                            ),
                            NoonAuthRecoveryProjectResult.recovered(
                                    command.getProjectTargets().get(1),
                                    "sid=recovered-308",
                                    "user-PRJ308"
                            )
                    )
            );
        });

        assertEquals(1, worker.runOnce());

        InOrder order = inOrder(repository, transientBackoffRepository);
        order.verify(repository).persistRecoveredProjectCookieCas(
                eq(308L), eq("PRJ308"), eq(18L), eq(4L), any(), anyLong(), anyString(),
                eq("sid=recovered-308"), eq("user-PRJ308"), eq(308L), any()
        );
        order.verify(transientBackoffRepository).resetForRecovery(
                eq(7002L), eq(18L), any(NoonAuthTransientBackoffWriteFence.class), any()
        );
        order.verify(transientBackoffRepository).incrementFailure(
                any(NoonAuthTransientBackoffState.class),
                any(NoonAuthTransientBackoffWriteFence.class),
                any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(1802L), eq(18L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                eq(1801L), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(repository).transitionRecovery(
                eq(18L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), eq("HTTP_503"), any(), eq(null), eq(true), any()
        );
    }

    @Test
    void expiredTransientBackoffWithTwoSendsUsesExplicitHoldWithoutThirdOtp() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                19L, NoonAuthRecoveryStatus.WAITING_COOLDOWN, 7L, 2, 2
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(191L, 19L, 307L, "PRJ307", "STORE307", 1901L, 3L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(19L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 19L, 3L));
        when(transientBackoffRepository.hasFailureForRecovery(7001L, 19L))
                .thenReturn(true);

        assertEquals(1, worker.runOnce());

        verify(gateway, never()).attempt(any());
        verify(repository, never()).recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        );
        verify(repository).markProjectRecoveryFailed(
                eq(307L), eq("PRJ307"), eq(19L), eq(3L), any(), anyLong(), anyString(),
                eq(NoonProjectAuthStatus.MANUAL_HOLD),
                eq("PROJECT_TRANSIENT_RETRY_EXHAUSTED"), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(repository).transitionRecovery(
                eq(19L), any(), eq(NoonAuthRecoveryStatus.MANUAL_HOLD),
                anyLong(), anyString(), eq(null),
                eq("PROJECT_TRANSIENT_RETRY_EXHAUSTED"),
                any(), any(), eq(true), any()
        );
    }
}
