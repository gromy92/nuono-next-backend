package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Objects;

/** Exact fence for releasing one claim before any DP job advance has started. */
public final class DataPullUnstartedClaimRelease {
    private final long taskId;
    private final long expectedFenceEpoch;
    private final long expectedVersion;
    private final String leaseOwner;
    private final LocalDateTime now;

    private DataPullUnstartedClaimRelease(
            long taskId,
            long expectedFenceEpoch,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime now
    ) {
        if (taskId <= 0L || expectedFenceEpoch <= 0L || expectedVersion < 0L) {
            throw new IllegalArgumentException("unstarted claim release fence is invalid");
        }
        this.taskId = taskId;
        this.expectedFenceEpoch = expectedFenceEpoch;
        this.expectedVersion = expectedVersion;
        this.leaseOwner = requireText(leaseOwner, "leaseOwner");
        this.now = Objects.requireNonNull(now, "now");
    }

    public static DataPullUnstartedClaimRelease from(DataPullTask claimed, LocalDateTime now) {
        DataPullTask task = Objects.requireNonNull(claimed, "claimed");
        if (task.getState() != TaskState.RUNNING
                || task.getId() == null
                || task.getFenceEpoch() == null
                || task.getVersion() == null
                || task.getLeaseUntil() == null
                || !task.getLeaseUntil().isAfter(Objects.requireNonNull(now, "now"))) {
            throw new IllegalArgumentException("unstarted claim release requires a live claim");
        }
        return new DataPullUnstartedClaimRelease(
                task.getId(), task.getFenceEpoch(), task.getVersion(), task.getLeaseOwner(), now
        );
    }

    public long getTaskId() { return taskId; }
    public long getExpectedFenceEpoch() { return expectedFenceEpoch; }
    public long getExpectedVersion() { return expectedVersion; }
    public String getLeaseOwner() { return leaseOwner; }
    public LocalDateTime getNow() { return now; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
