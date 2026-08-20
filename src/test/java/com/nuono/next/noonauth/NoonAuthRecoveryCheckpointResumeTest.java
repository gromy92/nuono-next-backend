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
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryCheckpointResumeTest extends AbstractNoonAuthRecoveryWorkerTestSupport {

    @Test
    void interruptedGenerationResumesEncryptedCheckpointWithoutSendingAnotherOtp() {
        NoonAuthIdentityRecoveryRecord interrupted = recovery(
                62L, NoonAuthRecoveryStatus.WAITING_EMAIL, 4L, 1, 1
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(621L, 62L, 307L, "PRJ307", "STORE307", 6201L, 9L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(interrupted));
        when(repository.listPendingItems(62L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 62L, 9L));
        when(gateway.canResume(62L)).thenReturn(true);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = invocation.getArgument(0);
            assertEquals(1, command.getGeneration());
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "checkpoint-message",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0), "sid=checkpoint", "user-checkpoint"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        verify(gateway).attempt(any());
        verify(repository, never()).recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(6201L), eq(62L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(gateway).clearCheckpoint(62L);
    }
}

