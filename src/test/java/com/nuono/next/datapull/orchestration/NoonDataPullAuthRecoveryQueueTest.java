package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthRetrySuppressedException;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NoonDataPullAuthRecoveryQueueTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);

    @Test
    void submitsTheExactWaitingVersionAsTheSharedQueueCheckpoint() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        DataPullTask task = task(OperationCode.DP04);
        when(queue.enqueue(any(NoonAuthWaitRequest.class))).thenReturn(Optional.of(414L));

        new NoonDataPullAuthRecoveryQueue(queue).enqueue(task, 8L, NOW);

        ArgumentCaptor<NoonAuthWaitRequest> request =
                ArgumentCaptor.forClass(NoonAuthWaitRequest.class);
        verify(queue).enqueue(request.capture());
        assertEquals(307L, request.getValue().getOwnerUserId());
        assertEquals("PRJ108065", request.getValue().getProjectCode());
        assertEquals("STR108065-NSA", request.getValue().getStoreCode());
        assertEquals("SA", request.getValue().getSiteCode());
        assertEquals("DP_RUNTIME", request.getValue().getSourceDomain());
        assertEquals(1L, request.getValue().getSourceTaskId());
        assertEquals("8", request.getValue().getCheckpoint());
        assertEquals(NoonAuthResumePolicy.AUTO_RESUME, request.getValue().getResumePolicy());
        assertEquals(NOW.minusMinutes(1), request.getValue().getSourceStartedAt());
    }

    @Test
    void duplicateOtpSuppressionLeavesTheDurableWaitingTaskUntouched() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        when(queue.enqueue(any(NoonAuthWaitRequest.class))).thenThrow(
                new NoonAuthRetrySuppressedException("already recovered")
        );

        new NoonDataPullAuthRecoveryQueue(queue).enqueue(
                task(OperationCode.DP04),
                8L,
                NOW
        );

        assertWaitingAuthHasADurableRetry();
    }

    @Test
    void leavesAli1688AuthWaitsToTheRuntimeRetryWithoutUsingTheNoonOtpQueue() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);

        new NoonDataPullAuthRecoveryQueue(queue).enqueue(
                task(OperationCode.DP10),
                8L,
                NOW
        );

        assertWaitingAuthHasADurableRetry();
        verifyNoInteractions(queue);
    }

    @Test
    void emptyQueueResultLeavesTheWaitingTaskToItsDurableRuntimeRetry() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        when(queue.enqueue(any(NoonAuthWaitRequest.class))).thenReturn(Optional.empty());

        new NoonDataPullAuthRecoveryQueue(queue).enqueue(
                task(OperationCode.DP04),
                8L,
                NOW
        );

        assertWaitingAuthHasADurableRetry();
    }

    @Test
    void rejectsAWaitingVersionThatDoesNotFollowTheClaimedSnapshot() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new NoonDataPullAuthRecoveryQueue(queue)
                        .enqueue(task(OperationCode.DP04), 9L, NOW)
        );
        verifyNoInteractions(queue);
    }

    private void assertWaitingAuthHasADurableRetry() {
        AdvanceResult waiting = AdvanceResult.waitingAuth("checkpoint", "AUTH_REQUIRED");
        assertEquals(TaskState.WAITING_AUTH, waiting.getNextState());
        assertEquals(Duration.ofMinutes(5), waiting.getRetryAfter());
    }

    private DataPullTask task(OperationCode operationCode) {
        DataPullTask task = DataPullTask.queued(
                1L,
                operationCode,
                operationCode == OperationCode.DP10
                        ? "ALI1688_OPEN_API"
                        : "NOON_PARTNER_PRODUCT_LIST",
                307L,
                108065L,
                "account-307",
                "egress-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa",
                NOW,
                operationCode.name() + ":2026-08-02",
                "FETCH",
                NOW.minusMinutes(1)
        );
        task.setVersion(7L);
        return task;
    }
}
