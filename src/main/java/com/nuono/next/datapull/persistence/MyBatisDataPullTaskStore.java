package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.DataPullRuntimeMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskRepairMapper;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** Production persistence Adapter; every worker mutation is a single fenced SQL CAS. */
public class MyBatisDataPullTaskStore implements DataPullTaskStore {

    private final DataPullRuntimeMapper mapper;
    private final DataPullTaskCompactionMapper compactionMapper;
    private final DataPullTaskRepairMapper repairMapper;

    public MyBatisDataPullTaskStore(
            DataPullRuntimeMapper mapper,
            DataPullTaskCompactionMapper compactionMapper,
            DataPullTaskRepairMapper repairMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.compactionMapper = Objects.requireNonNull(compactionMapper, "compactionMapper");
        this.repairMapper = Objects.requireNonNull(repairMapper, "repairMapper");
    }

    @Override
    public long nextTaskId() {
        IdSequenceCommand command = new IdSequenceCommand("dp_pull_task", 0L);
        int changed = mapper.allocateTaskId(command);
        requireSingleRow(changed, "task id allocation");
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0L) {
            throw new IllegalStateException("task id sequence returned no valid allocation");
        }
        return command.getAllocatedId();
    }

    @Override
    public DataPullTask enqueue(DataPullTask task) {
        DataPullTaskContract.requireEnqueueable(task);
        mapper.insertTaskIfAbsent(task);
        DataPullTask stored = mapper.selectByStableKey(
                task.getOperationCode(),
                task.getScopeKey(),
                task.getBusinessWindowKey()
        );
        if (stored == null) {
            throw new IllegalStateException("idempotent enqueue did not resolve its stable task key");
        }
        DataPullTaskContract.requirePersistedScopeSnapshot(stored);
        DataPullTaskContract.requireSameImmutablePayload(stored, task);
        return stored;
    }

    @Override
    @Transactional
    public DataPullTask enqueueCatchUp(
            DataPullTask proposed,
            DataPullTaskCatchUpMode mode,
            LocalDateTime now
    ) {
        DataPullTaskContract.requireEnqueueable(proposed);
        Objects.requireNonNull(now, "now");
        if (compactionMapper.lockCompactionAnchor() == null) {
            throw new IllegalStateException("DP task compaction anchor is not initialized");
        }
        List<DataPullTask> candidates = compactionMapper.lockStrictlyNeverStarted(
                proposed.getOperationCode(), proposed.getScopeKey()
        );
        DataPullTaskCompaction.Resolution resolution = DataPullTaskCompaction.resolve(
                proposed, candidates, mode
        );
        DataPullTask replacement = resolution.isInsertReplacement()
                ? enqueue(resolution.getReplacement())
                : requireTask(resolution.getReplacement().getId(), "compaction replacement disappeared");
        if (!DataPullTaskContract.isStrictlyNeverStarted(replacement)) {
            throw new IllegalStateException("durable compaction replacement is not never-started");
        }
        for (DataPullTask candidate : resolution.getSuperseded()) {
            if (candidate.getId().equals(replacement.getId())) {
                continue;
            }
            requireSingleRow(
                    compactionMapper.supersedeStrictlyNeverStarted(
                            candidate.getId(), candidate.getVersion(), now
                    ),
                    "never-started task supersede CAS"
            );
        }
        return requireTask(replacement.getId(), "compaction replacement disappeared after CAS");
    }

    @Override
    public Optional<LocalDateTime> latestScheduleSlot(
            OperationCode operationCode,
            String scopeKey
    ) {
        Objects.requireNonNull(operationCode, "operationCode");
        DataPullTaskContract.requireIdentity(scopeKey, "scopeKey");
        return Optional.ofNullable(mapper.selectLatestScheduleSlot(operationCode, scopeKey));
    }

    @Override
    public List<DataPullTask> dueCandidates(LocalDateTime now, int limit) {
        return dueCandidatesAfter(now, null, null, limit);
    }

    @Override
    public List<DataPullTask> dueCandidatesAfter(
            LocalDateTime now,
            LocalDateTime afterScheduleSlot,
            Long afterTaskId,
            int limit
    ) {
        Objects.requireNonNull(now, "now");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if ((afterScheduleSlot == null) != (afterTaskId == null)
                || (afterTaskId != null && afterTaskId <= 0L)) {
            throw new IllegalArgumentException("candidate cursor must be complete and positive");
        }
        return mapper.selectDueCandidatesAfter(now, afterScheduleSlot, afterTaskId, limit);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Optional<DataPullTask> claim(
            long taskId,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime leaseUntil,
            LocalDateTime now,
            DataPullRuntimeLeaderLease leaderLease
    ) {
        DataPullTaskContract.requireClaimRequest(taskId, expectedVersion, leaseOwner, leaseUntil, now);
        Objects.requireNonNull(leaderLease, "leaderLease");
        if (!leaseOwner.equals(leaderLease.getOwner())) {
            throw new IllegalArgumentException("task lease owner must match runtime leader owner");
        }
        int changed = mapper.tryClaim(
                taskId,
                expectedVersion,
                leaseOwner,
                leaseUntil,
                now,
                leaderLease
        );
        if (changed == 0) {
            return Optional.empty();
        }
        requireSingleRow(changed, "claim");
        return Optional.of(requireTask(taskId, "claimed task disappeared"));
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public boolean releaseUnstartedClaim(DataPullUnstartedClaimRelease release) {
        DataPullUnstartedClaimRelease command = Objects.requireNonNull(release, "release");
        int changed = mapper.releaseUnstartedClaim(
                command.getTaskId(),
                command.getExpectedFenceEpoch(),
                command.getExpectedVersion(),
                command.getLeaseOwner(),
                command.getNow()
        );
        requireAtMostOneRow(changed, "unstarted claim release");
        return changed == 1;
    }

    @Override
    public boolean transition(DataPullTaskTransition transition) {
        Objects.requireNonNull(transition, "transition");
        int changed = mapper.transitionTask(transition);
        requireAtMostOneRow(changed, "transition");
        return changed == 1;
    }

    @Override
    @Transactional
    public Optional<DataPullTask> repairFailed(
            DataPullTaskRepairCommand command,
            LocalDateTime now
    ) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(now, "now");
        int changed = repairMapper.requeueFailed(command, now);
        requireAtMostOneRow(changed, "failed task repair");
        return changed == 0
                ? Optional.empty()
                : Optional.of(requireTask(command.getTaskId(), "repaired task disappeared"));
    }

    @Override
    public Optional<DataPullTask> heartbeat(
            long taskId,
            long expectedFenceEpoch,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime leaseUntil,
            LocalDateTime now
    ) {
        DataPullTaskContract.requireHeartbeatRequest(
                taskId,
                expectedFenceEpoch,
                expectedVersion,
                leaseOwner,
                leaseUntil,
                now
        );
        int changed = mapper.heartbeat(
                taskId,
                expectedFenceEpoch,
                expectedVersion,
                leaseOwner,
                leaseUntil,
                now
        );
        if (changed == 0) {
            return Optional.empty();
        }
        requireSingleRow(changed, "heartbeat");
        return Optional.of(requireTask(taskId, "heartbeat task disappeared"));
    }

    @Override
    public Optional<DataPullTask> find(long taskId) {
        if (taskId <= 0L) {
            throw new IllegalArgumentException("task id must be positive");
        }
        return Optional.ofNullable(mapper.selectById(taskId));
    }

    private DataPullTask requireTask(long taskId, String message) {
        DataPullTask task = mapper.selectById(taskId);
        if (task == null) {
            throw new IllegalStateException(message);
        }
        DataPullTaskContract.requirePersistedScopeSnapshot(task);
        return task;
    }

    private static void requireSingleRow(int changed, String action) {
        if (changed != 1) {
            throw new IllegalStateException(action + " must affect exactly one task");
        }
    }

    private static void requireAtMostOneRow(int changed, String action) {
        if (changed < 0 || changed > 1) {
            throw new IllegalStateException(action + " affected an invalid number of tasks: " + changed);
        }
    }
}
