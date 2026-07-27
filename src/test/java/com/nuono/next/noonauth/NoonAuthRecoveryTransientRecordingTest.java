package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthTransientFailure;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NoonAuthRecoveryTransientRecordingTest
        extends AbstractNoonAuthRecoveryWorkerTestSupport {

    @Test
    void transientProjectFailureKeepsTaskPendingAndMovesRecoveryToCooldown() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                16L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(161L, 16L, 307L, "PRJ307", "STORE307", 1601L, 3L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(16L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 16L, 3L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "transient-project-message",
                    List.of(NoonAuthRecoveryProjectResult.transientFailure(
                            command.getProjectTargets().get(0),
                            NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                            NoonTransientErrorType.NETWORK_EOF,
                            "CATALOG_VALIDATION: transient NETWORK_EOF"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
        verify(repository, never()).transitionRecoveryItem(
                anyLong(), anyLong(), any(), any(), any(), anyLong(), anyString(),
                any(), any(), any(), any()
        );
        verify(transientBackoffRepository).incrementFailure(
                any(NoonAuthTransientBackoffState.class),
                any(NoonAuthTransientBackoffWriteFence.class),
                any()
        );
        verify(repository).transitionRecovery(
                eq(16L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), eq("NETWORK_EOF"), any(), eq(null), eq(true), any()
        );
    }

    @Test
    void staleLeaseCannotRecordTransientFailureOrMoveRecoveryToCooldown() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                160L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1601L, 160L, 307L, "PRJ307", "STORE307", 16001L, 3L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(160L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 160L, 3L));
        when(transientBackoffRepository.incrementFailure(any(), any(), any()))
                .thenReturn(null);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "stale-fence-message",
                    List.of(NoonAuthRecoveryProjectResult.transientFailure(
                            command.getProjectTargets().get(0),
                            NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                            NoonTransientErrorType.NETWORK_EOF,
                            "CATALOG_VALIDATION: transient NETWORK_EOF"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository, never()).transitionRecovery(
                eq(160L), any(), eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN),
                anyLong(), anyString(), any(), any(), any(), any(), anyBoolean(), any()
        );
        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        );
    }

    @Test
    void identityTransientFailureRecordsEveryDueStoreAndDoesNotConsumeOtpSend() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                22L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(221L, 22L, 307L, "PRJ307", "STORE307", 2201L, 3L),
                item(222L, 22L, 308L, "PRJ308", "STORE308", 2202L, 4L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(22L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString())).thenAnswer(invocation ->
                blockedState(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        22L,
                        "PRJ307".equals(invocation.getArgument(1)) ? 3L : 4L
                )
        );
        when(gateway.attempt(any())).thenReturn(
                NoonAuthRecoveryAttemptResult.transientFailure(
                        NoonAuthRecoveryFailureStage.IDENTITY_PREPARATION,
                        NoonTransientErrorType.CONNECT_TIMEOUT,
                        null,
                        "IDENTITY_PREPARATION: transient CONNECT_TIMEOUT"
                )
        );

        assertEquals(1, worker.runOnce());

        verify(repository, never()).recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        );
        verify(transientBackoffRepository, times(2)).incrementFailure(
                any(NoonAuthTransientBackoffState.class),
                any(NoonAuthTransientBackoffWriteFence.class),
                any()
        );
        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(repository).transitionRecovery(
                eq(22L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), eq("CONNECT_TIMEOUT"), any(), eq(null), eq(true), any()
        );
    }

    @Test
    void oneIdentityAttemptPersistsEachExactTransientTypeForTheStore() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                27L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(271L, 27L, 307L, "PRJ307", "STORE307", 2701L, 3L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(27L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 27L, 3L));
        when(gateway.attempt(any())).thenReturn(
                NoonAuthRecoveryAttemptResult.transientFailures(
                        List.of(
                                new NoonAuthTransientFailure(
                                        NoonAuthRecoveryFailureStage.OTP_SEND,
                                        NoonTransientErrorType.HTTP_503,
                                        "OTP_SEND: transient HTTP_503"
                                ),
                                new NoonAuthTransientFailure(
                                        NoonAuthRecoveryFailureStage.MAILBOX_POLLING,
                                        NoonTransientErrorType.NETWORK_EOF,
                                        "MAILBOX_POLLING: transient NETWORK_EOF"
                                )
                        ),
                        null,
                        "multiple exact transient failures"
                )
        );

        assertEquals(1, worker.runOnce());

        ArgumentCaptor<NoonAuthTransientBackoffState> failures =
                ArgumentCaptor.forClass(NoonAuthTransientBackoffState.class);
        verify(transientBackoffRepository, times(2)).incrementFailure(
                failures.capture(),
                any(NoonAuthTransientBackoffWriteFence.class),
                any()
        );
        assertEquals(NoonTransientErrorType.HTTP_503, failures.getAllValues().get(0).getErrorType());
        assertEquals(
                NoonTransientErrorType.NETWORK_EOF,
                failures.getAllValues().get(1).getErrorType()
        );
        verify(repository).transitionRecovery(
                eq(27L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), eq("PROJECT_TRANSIENT_BACKOFF"), any(), eq(null), eq(true), any()
        );
    }
}
