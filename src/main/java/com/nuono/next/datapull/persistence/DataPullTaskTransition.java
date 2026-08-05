package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Objects;

/** Fence- and version-bound request to leave one RUNNING epoch. */
public final class DataPullTaskTransition {

    private static final int LEASE_OWNER_MAX_LENGTH = 200;
    private static final int STEP_CODE_MAX_LENGTH = 80;
    private static final int REMOTE_HANDLE_MAX_LENGTH = 512;

    private final long taskId;
    private final long expectedFenceEpoch;
    private final long expectedVersion;
    private final String leaseOwner;
    private final TaskState nextState;
    private final String stepCode;
    private final String remoteHandle;
    private final String checkpoint;
    private final LocalDateTime retryNotBefore;
    private final String sanitizedFailureCode;
    private final LocalDateTime finishedAt;
    private final LocalDateTime now;

    public DataPullTaskTransition(
            long taskId,
            long expectedFenceEpoch,
            long expectedVersion,
            String leaseOwner,
            TaskState nextState,
            String stepCode,
            String remoteHandle,
            String checkpoint,
            LocalDateTime retryNotBefore,
            String sanitizedFailureCode,
            LocalDateTime finishedAt,
            LocalDateTime now
    ) {
        this.taskId = taskId;
        this.expectedFenceEpoch = expectedFenceEpoch;
        this.expectedVersion = expectedVersion;
        this.leaseOwner = DataPullTaskContract.requireIdentity(
                leaseOwner, "leaseOwner", LEASE_OWNER_MAX_LENGTH
        );
        this.nextState = validateNextState(nextState, retryNotBefore, finishedAt);
        this.stepCode = DataPullTaskContract.requireIdentity(
                stepCode, "stepCode", STEP_CODE_MAX_LENGTH
        );
        this.remoteHandle = optionalIdentity(remoteHandle, "remoteHandle", REMOTE_HANDLE_MAX_LENGTH);
        this.checkpoint = checkpoint;
        this.retryNotBefore = retryNotBefore;
        this.sanitizedFailureCode = DataPullTaskContract.optionalSanitizedCode(sanitizedFailureCode);
        validateFailureCode(this.nextState, this.sanitizedFailureCode);
        this.finishedAt = finishedAt;
        this.now = Objects.requireNonNull(now, "now");
    }

    private static TaskState validateNextState(
            TaskState nextState,
            LocalDateTime retryNotBefore,
            LocalDateTime finishedAt
    ) {
        TaskState state = Objects.requireNonNull(nextState, "nextState");
        if (state == TaskState.SUPERSEDED) {
            throw new IllegalArgumentException("SUPERSEDED is reserved for atomic catch-up compaction");
        }
        if (state == TaskState.RUNNING) {
            throw new IllegalArgumentException("advance must release RUNNING");
        }
        boolean waiting = state == TaskState.WAITING_REMOTE
                || state == TaskState.WAITING_BACKOFF
                || state == TaskState.WAITING_AUTH;
        if (waiting != (retryNotBefore != null)) {
            throw new IllegalArgumentException(
                    "retryNotBefore must be present exactly for timed waiting states"
            );
        }
        boolean terminal = state.isTerminal();
        if (terminal != (finishedAt != null)) {
            throw new IllegalArgumentException("finishedAt must be present exactly for terminal states");
        }
        return state;
    }

    private static void validateFailureCode(TaskState state, String failureCode) {
        boolean failedOrWaiting = state == TaskState.FAILED
                || state == TaskState.WAITING_REMOTE
                || state == TaskState.WAITING_BACKOFF
                || state == TaskState.WAITING_AUTH;
        if (failedOrWaiting != (failureCode != null)) {
            throw new IllegalArgumentException(
                    "sanitizedFailureCode must be present exactly for failure and waiting states"
            );
        }
    }

    static String requireIdentity(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(name + " must be a non-blank stable identity");
        }
        return nonNull;
    }

    private static String optionalIdentity(String value, String name, int maxLength) {
        return value == null
                ? null
                : DataPullTaskContract.requireIdentity(value, name, maxLength);
    }

    public long getTaskId() {
        return taskId;
    }

    public long getExpectedFenceEpoch() {
        return expectedFenceEpoch;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public TaskState getNextState() {
        return nextState;
    }

    public String getStepCode() {
        return stepCode;
    }

    public String getRemoteHandle() {
        return remoteHandle;
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public LocalDateTime getRetryNotBefore() {
        return retryNotBefore;
    }

    public String getSanitizedFailureCode() {
        return sanitizedFailureCode;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public LocalDateTime getNow() {
        return now;
    }
}
