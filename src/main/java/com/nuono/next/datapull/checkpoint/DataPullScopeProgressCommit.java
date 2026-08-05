package com.nuono.next.datapull.checkpoint;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Objects;

/** One fenced, monotonic scope-progress commit performed inside the fact-write transaction. */
public final class DataPullScopeProgressCommit {

    private final long taskId;
    private final long taskFenceEpoch;
    private final long taskVersion;
    private final String leaseOwner;
    private final com.nuono.next.datapull.runtime.OperationCode operationCode;
    private final String scopeKey;
    private final String businessWindowKey;
    private final long expectedProgressVersion;
    private final boolean initialFullCompleted;
    private final LocalDateTime officialModifiedHighWaterUtc;
    private final LocalDateTime nowUtc;

    public DataPullScopeProgressCommit(
            DataPullTask claimedTask,
            long expectedProgressVersion,
            boolean initialFullCompleted,
            LocalDateTime officialModifiedHighWaterUtc,
            LocalDateTime nowUtc
    ) {
        DataPullTask task = Objects.requireNonNull(claimedTask, "claimedTask");
        if (task.getState() != TaskState.RUNNING
                || task.getFenceEpoch() == null
                || task.getFenceEpoch() <= 0L
                || task.getVersion() == null
                || task.getLeaseOwner() == null) {
            throw new IllegalArgumentException("scope progress commit requires a claimed task epoch");
        }
        if (expectedProgressVersion < 0L) {
            throw new IllegalArgumentException("expectedProgressVersion must be non-negative");
        }
        this.taskId = task.getId();
        this.taskFenceEpoch = task.getFenceEpoch();
        this.taskVersion = task.getVersion();
        this.leaseOwner = DataPullScopeProgress.requireIdentity(task.getLeaseOwner(), "leaseOwner");
        this.operationCode = Objects.requireNonNull(task.getOperationCode(), "operationCode");
        this.scopeKey = DataPullScopeProgress.requireIdentity(task.getScopeKey(), "scopeKey");
        this.businessWindowKey = DataPullScopeProgress.requireIdentity(
                task.getBusinessWindowKey(),
                "businessWindowKey"
        );
        this.expectedProgressVersion = expectedProgressVersion;
        this.initialFullCompleted = initialFullCompleted;
        this.officialModifiedHighWaterUtc = officialModifiedHighWaterUtc;
        this.nowUtc = Objects.requireNonNull(nowUtc, "nowUtc");
    }

    public long getTaskId() { return taskId; }
    public long getTaskFenceEpoch() { return taskFenceEpoch; }
    public long getTaskVersion() { return taskVersion; }
    public String getLeaseOwner() { return leaseOwner; }
    public com.nuono.next.datapull.runtime.OperationCode getOperationCode() { return operationCode; }
    public String getScopeKey() { return scopeKey; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public long getExpectedProgressVersion() { return expectedProgressVersion; }
    public boolean isInitialFullCompleted() { return initialFullCompleted; }
    public LocalDateTime getOfficialModifiedHighWaterUtc() { return officialModifiedHighWaterUtc; }
    public LocalDateTime getNowUtc() { return nowUtc; }
}
