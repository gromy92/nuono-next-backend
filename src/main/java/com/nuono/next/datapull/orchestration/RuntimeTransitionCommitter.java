package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.persistence.DataPullTaskTransition;
import com.nuono.next.datapull.persistence.DataPullUnstartedClaimRelease;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Short transaction containing only the fenced task CAS and its optional shared backoff hold. */
public class RuntimeTransitionCommitter {
    private final DataPullTaskStore store;
    private final BackoffHoldRecorder backoffHoldRecorder;
    private final DataPullAuthRecoveryQueue authRecoveryQueue;

    public RuntimeTransitionCommitter(
            DataPullTaskStore store,
            BackoffHoldRecorder backoffHoldRecorder
    ) {
        this(store, backoffHoldRecorder, DataPullAuthRecoveryQueue.noop());
    }

    public RuntimeTransitionCommitter(
            DataPullTaskStore store,
            BackoffHoldRecorder backoffHoldRecorder,
            DataPullAuthRecoveryQueue authRecoveryQueue
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.backoffHoldRecorder = Objects.requireNonNull(
                backoffHoldRecorder,
                "backoffHoldRecorder"
        );
        this.authRecoveryQueue = Objects.requireNonNull(authRecoveryQueue, "authRecoveryQueue");
    }

    @Transactional(timeout = (int) DataPullRuntimeProperties.TRANSITION_BUDGET_SECONDS)
    public boolean commit(
            DataPullTask task,
            AdvanceResult result,
            LocalDateTime completedAtUtc
    ) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(completedAtUtc, "completedAtUtc");
        if (!store.transition(transition(task, result, completedAtUtc))) {
            return false;
        }
        if (result.getNextState() == TaskState.WAITING_BACKOFF) {
            if (result.getRetryAfter() == null || result.getBackoffShareLevel() == null) {
                throw new IllegalStateException("a backoff result requires delay and share level");
            }
            backoffHoldRecorder.record(
                    result.getBackoffShareLevel(),
                    DataPullBackoffIdentity.from(task, result.getBackoffProviderChannel()),
                    completedAtUtc.plus(result.getRetryAfter()),
                    result.getSanitizedCode(),
                    completedAtUtc
            );
        }
        if (result.getNextState() == TaskState.WAITING_AUTH) {
            authRecoveryQueue.enqueue(task, task.getVersion() + 1L, completedAtUtc);
        }
        return true;
    }

    /** Best-effort exact CAS used only before the first job advance invocation. */
    public boolean releaseUnstartedClaim(DataPullTask task, LocalDateTime observedAtUtc) {
        return store.releaseUnstartedClaim(DataPullUnstartedClaimRelease.from(task, observedAtUtc));
    }

    private DataPullTaskTransition transition(
            DataPullTask task,
            AdvanceResult result,
            LocalDateTime nowUtc
    ) {
        LocalDateTime retryNotBefore = result.getRetryAfter() == null
                ? null
                : nowUtc.plus(result.getRetryAfter());
        boolean terminal = result.getNextState() == TaskState.SUCCEEDED
                || result.getNextState() == TaskState.FAILED;
        return new DataPullTaskTransition(
                task.getId(),
                task.getFenceEpoch(),
                task.getVersion(),
                task.getLeaseOwner(),
                result.getNextState(),
                valueOrExisting(result.getStepCode(), task.getStepCode()),
                valueOrExisting(result.getRemoteHandle(), task.getRemoteHandle()),
                result.getCheckpoint(),
                retryNotBefore,
                result.getSanitizedCode(),
                terminal ? nowUtc : null,
                nowUtc
        );
    }

    private String valueOrExisting(String requested, String existing) {
        return requested == null ? existing : requested;
    }
}
