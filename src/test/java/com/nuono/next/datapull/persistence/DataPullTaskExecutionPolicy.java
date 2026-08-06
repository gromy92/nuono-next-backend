package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;

/** Test-only state predicates used by the in-memory persistence Adapter. */
final class DataPullTaskExecutionPolicy {
    private DataPullTaskExecutionPolicy() {
    }

    static boolean isDue(DataPullTask task, LocalDateTime now) {
        if (task.getScheduleSlot().isAfter(now)) {
            return false;
        }
        if (task.getLeaseUntil() != null && task.getLeaseUntil().isAfter(now)) {
            return false;
        }
        if (task.getState() == TaskState.QUEUED) {
            return true;
        }
        if (task.getState() == TaskState.RUNNING) {
            return task.getLeaseUntil() != null;
        }
        if (task.getState() == TaskState.WAITING_REMOTE
                || task.getState() == TaskState.WAITING_BACKOFF
                || task.getState() == TaskState.WAITING_AUTH) {
            return task.getRetryNotBefore() != null
                    && !task.getRetryNotBefore().isAfter(now);
        }
        return false;
    }

    static int nextAttempt(Integer attempt, TaskState nextState) {
        int current = attempt == null ? 0 : attempt;
        if (nextState == TaskState.WAITING_REMOTE
                || nextState == TaskState.WAITING_BACKOFF
                || nextState == TaskState.WAITING_AUTH) {
            return current == Integer.MAX_VALUE ? current : current + 1;
        }
        if (nextState == TaskState.QUEUED || nextState == TaskState.SUCCEEDED) {
            return 0;
        }
        return current;
    }

    static boolean ownsLiveEpoch(
            DataPullTask task,
            long expectedFenceEpoch,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime now
    ) {
        return task != null
                && task.getState() == TaskState.RUNNING
                && task.getFenceEpoch() == expectedFenceEpoch
                && task.getVersion() == expectedVersion
                && leaseOwner.equals(task.getLeaseOwner())
                && task.getLeaseUntil() != null
                && task.getLeaseUntil().isAfter(now);
    }
}
