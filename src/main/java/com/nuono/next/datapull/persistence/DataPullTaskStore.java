package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Deep persistence Interface for deterministic tasks and fenced worker epochs. */
public interface DataPullTaskStore {

    long nextTaskId();

    DataPullTask enqueue(DataPullTask task);

    /** Atomically keeps one replacement and supersedes only strictly never-started predecessors. */
    DataPullTask enqueueCatchUp(
            DataPullTask proposed,
            DataPullTaskCatchUpMode mode,
            LocalDateTime now
    );

    Optional<LocalDateTime> latestScheduleSlot(OperationCode operationCode, String scopeKey);

    List<DataPullTask> dueCandidates(LocalDateTime now, int limit);

    List<DataPullTask> dueCandidatesAfter(
            LocalDateTime now,
            LocalDateTime afterScheduleSlot,
            Long afterTaskId,
            int limit
    );

    Optional<DataPullTask> claim(
            long taskId,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime leaseUntil,
            LocalDateTime now,
            DataPullRuntimeLeaderLease leaderLease
    );

    /** Releases only a still-owned claim whose DP job advance has not started. */
    boolean releaseUnstartedClaim(DataPullUnstartedClaimRelease release);

    boolean transition(DataPullTaskTransition transition);

    /** Requeues one investigated FAILED task without changing its window or checkpoint. */
    Optional<DataPullTask> repairFailed(
            DataPullTaskRepairCommand command,
            LocalDateTime now
    );

    Optional<DataPullTask> heartbeat(
            long taskId,
            long expectedFenceEpoch,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime leaseUntil,
            LocalDateTime now
    );

    Optional<DataPullTask> find(long taskId);
}
