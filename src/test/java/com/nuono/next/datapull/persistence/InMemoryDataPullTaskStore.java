package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Test-only Adapter with the same CAS rules as the MyBatis Adapter. */
public final class InMemoryDataPullTaskStore implements DataPullTaskStore {
    private final Map<Long, DataPullTask> tasksById = new HashMap<>();
    private final Map<String, Long> idByStableKey = new HashMap<>();
    private long lastAllocatedTaskId;

    @Override
    public synchronized long nextTaskId() {
        if (lastAllocatedTaskId == Long.MAX_VALUE) {
            throw new IllegalStateException("DP task id sequence is exhausted");
        }
        lastAllocatedTaskId++;
        return lastAllocatedTaskId;
    }

    @Override
    public synchronized DataPullTask enqueue(DataPullTask task) {
        DataPullTaskContract.requireEnqueueable(task);
        String stableKey = DataPullTaskContract.stableKey(task);
        Long existingId = idByStableKey.get(stableKey);
        if (existingId != null) {
            DataPullTask existing = tasksById.get(existingId);
            DataPullTaskContract.requireSameImmutablePayload(existing, task);
            return existing.copy();
        }
        if (tasksById.containsKey(task.getId())) {
            throw new IllegalStateException("task id is already bound to another stable task key");
        }
        DataPullTask stored = task.copy();
        tasksById.put(stored.getId(), stored);
        idByStableKey.put(stableKey, stored.getId());
        lastAllocatedTaskId = Math.max(lastAllocatedTaskId, stored.getId());
        return stored.copy();
    }

    @Override
    public synchronized DataPullTask enqueueCatchUp(
            DataPullTask proposed,
            DataPullTaskCatchUpMode mode,
            LocalDateTime now
    ) {
        Objects.requireNonNull(now, "now");
        List<DataPullTask> candidates = tasksById.values().stream()
                .filter(DataPullTaskContract::isStrictlyNeverStarted)
                .filter((task) -> task.getOperationCode() == proposed.getOperationCode())
                .filter((task) -> task.getScopeKey().equals(proposed.getScopeKey()))
                .map(DataPullTask::copy)
                .collect(Collectors.toCollection(ArrayList::new));
        DataPullTaskCompaction.Resolution resolution = DataPullTaskCompaction.resolve(
                proposed, candidates, mode
        );
        DataPullTask replacement = resolution.isInsertReplacement()
                ? enqueue(resolution.getReplacement())
                : requireStoredNeverStarted(resolution.getReplacement().getId());
        for (DataPullTask candidate : resolution.getSuperseded()) {
            if (candidate.getId().equals(replacement.getId())) {
                continue;
            }
            DataPullTask stored = tasksById.get(candidate.getId());
            if (!DataPullTaskContract.isStrictlyNeverStarted(stored)
                    || !stored.getVersion().equals(candidate.getVersion())) {
                throw new IllegalStateException("never-started compaction CAS lost its locked candidate");
            }
            stored.setState(TaskState.SUPERSEDED);
            stored.setFinishedAt(now);
            stored.setVersion(Math.addExact(stored.getVersion(), 1L));
            stored.setUpdatedAt(now);
        }
        return replacement.copy();
    }

    private DataPullTask requireStoredNeverStarted(long taskId) {
        DataPullTask stored = tasksById.get(taskId);
        if (!DataPullTaskContract.isStrictlyNeverStarted(stored)) {
            throw new IllegalStateException("durable compaction replacement is not never-started");
        }
        return stored;
    }

    @Override
    public synchronized Optional<LocalDateTime> latestScheduleSlot(
            OperationCode operationCode,
            String scopeKey
    ) {
        Objects.requireNonNull(operationCode, "operationCode");
        DataPullTaskContract.requireIdentity(scopeKey, "scopeKey");
        return tasksById.values().stream()
                .filter((task) -> task.getOperationCode() == operationCode)
                .filter((task) -> task.getScopeKey().equals(scopeKey))
                .map(DataPullTask::getScheduleSlot)
                .max(Comparator.naturalOrder());
    }

    @Override
    public synchronized List<DataPullTask> dueCandidates(LocalDateTime now, int limit) {
        return dueCandidatesAfter(now, null, null, limit);
    }

    @Override
    public synchronized List<DataPullTask> dueCandidatesAfter(
            LocalDateTime now,
            LocalDateTime afterScheduleSlot,
            Long afterTaskId,
            int limit
    ) {
        if (now == null || limit <= 0) {
            throw new IllegalArgumentException("now and a positive limit are required");
        }
        if ((afterScheduleSlot == null) != (afterTaskId == null)
                || (afterTaskId != null && afterTaskId <= 0L)) {
            throw new IllegalArgumentException("candidate cursor must be complete and positive");
        }
        return tasksById.values().stream()
                .filter((task) -> DataPullTaskExecutionPolicy.isDue(task, now))
                .filter(this::hasNoUnfinishedPredecessor)
                .filter((task) -> isAfter(task, afterScheduleSlot, afterTaskId))
                .sorted(Comparator.comparing(DataPullTask::getScheduleSlot)
                        .thenComparing(DataPullTask::getId))
                .limit(limit)
                .map(DataPullTask::copy)
                .collect(Collectors.toCollection(ArrayList::new));
    }
    private boolean hasNoUnfinishedPredecessor(DataPullTask candidate) {
        return tasksById.values().stream().noneMatch((predecessor) ->
                predecessor.getOperationCode() == candidate.getOperationCode()
                        && predecessor.getScopeKey().equals(candidate.getScopeKey())
                        && precedes(predecessor, candidate)
                        && !predecessor.getState().isTerminal()
        );
    }
    private static boolean precedes(DataPullTask left, DataPullTask right) {
        int slotOrder = left.getScheduleSlot().compareTo(right.getScheduleSlot());
        return slotOrder < 0 || (slotOrder == 0 && left.getId() < right.getId());
    }
    private static boolean isAfter(
            DataPullTask task,
            LocalDateTime afterScheduleSlot,
            Long afterTaskId
    ) {
        if (afterScheduleSlot == null) {
            return true;
        }
        int slotOrder = task.getScheduleSlot().compareTo(afterScheduleSlot);
        return slotOrder > 0 || (slotOrder == 0 && task.getId() > afterTaskId);
    }

    @Override
    public synchronized Optional<DataPullTask> claim(
            long taskId, long expectedVersion, String leaseOwner,
            LocalDateTime leaseUntil, LocalDateTime now,
            DataPullRuntimeLeaderLease leaderLease
    ) {
        Objects.requireNonNull(leaderLease, "leaderLease");
        if (!leaseOwner.equals(leaderLease.getOwner())) {
            throw new IllegalArgumentException("task lease owner must match runtime leader owner");
        }
        return claim(taskId, expectedVersion, leaseOwner, leaseUntil, now);
    }
    public synchronized Optional<DataPullTask> claim(
            long taskId, long expectedVersion, String leaseOwner,
            LocalDateTime leaseUntil, LocalDateTime now
    ) {
        DataPullTaskContract.requireClaimRequest(taskId, expectedVersion, leaseOwner, leaseUntil, now);
        DataPullTask task = tasksById.get(taskId);
        if (task == null || task.getVersion() != expectedVersion
                || !DataPullTaskExecutionPolicy.isDue(task, now)
                || !hasNoUnfinishedPredecessor(task)) {
            return Optional.empty();
        }
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner(leaseOwner);
        task.setLeaseUntil(leaseUntil);
        task.setRetryNotBefore(null);
        task.setFenceEpoch(task.getFenceEpoch() + 1L);
        task.setVersion(task.getVersion() + 1L);
        task.setFinishedAt(null);
        task.setUpdatedAt(now);
        return Optional.of(task.copy());
    }
    @Override public synchronized boolean releaseUnstartedClaim(DataPullUnstartedClaimRelease release) {
        return InMemoryUnstartedClaimRelease.apply(tasksById,
                Objects.requireNonNull(release, "release"));
    }
    @Override
    public synchronized boolean transition(DataPullTaskTransition transition) {
        Objects.requireNonNull(transition, "transition");
        DataPullTask task = tasksById.get(transition.getTaskId());
        if (!DataPullTaskExecutionPolicy.ownsLiveEpoch(
                task, transition.getExpectedFenceEpoch(), transition.getExpectedVersion(),
                transition.getLeaseOwner(), transition.getNow())) {
            return false;
        }
        task.setState(transition.getNextState());
        task.setStepCode(transition.getStepCode());
        task.setRemoteHandle(transition.getRemoteHandle());
        task.setCheckpoint(transition.getCheckpoint());
        task.setRetryNotBefore(transition.getRetryNotBefore());
        task.setSanitizedFailureCode(transition.getSanitizedFailureCode());
        task.setFinishedAt(transition.getFinishedAt());
        task.setAttempt(DataPullTaskExecutionPolicy.nextAttempt(
                task.getAttempt(), transition.getNextState()
        ));
        task.setLeaseOwner(null);
        task.setLeaseUntil(null);
        task.setVersion(task.getVersion() + 1L);
        task.setUpdatedAt(transition.getNow());
        return true;
    }
    @Override
    public synchronized Optional<DataPullTask> repairFailed(
            DataPullTaskRepairCommand command,
            LocalDateTime now
    ) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(now, "now");
        DataPullTask task = tasksById.get(command.getTaskId());
        if (!matchesFailedRepair(task, command) || hasLiveSuccessor(task)) {
            return Optional.empty();
        }
        task.setState(TaskState.QUEUED);
        task.setRetryNotBefore(null);
        task.setAttempt(0);
        task.setSanitizedFailureCode(null);
        task.setFinishedAt(null);
        task.setVersion(Math.addExact(task.getVersion(), 1L));
        task.setUpdatedAt(now);
        return Optional.of(task.copy());
    }
    private boolean matchesFailedRepair(DataPullTask task, DataPullTaskRepairCommand command) {
        return task != null
                && task.getState() == TaskState.FAILED
                && task.getVersion() == command.getExpectedVersion()
                && task.getFenceEpoch() == command.getExpectedFenceEpoch()
                && command.getExpectedFailureCode().equals(task.getSanitizedFailureCode())
                && task.getLeaseOwner() == null
                && task.getLeaseUntil() == null;
    }

    private boolean hasLiveSuccessor(DataPullTask target) {
        return tasksById.values().stream().anyMatch((candidate) ->
                candidate.getOperationCode() == target.getOperationCode()
                        && candidate.getScopeKey().equals(target.getScopeKey())
                        && precedes(target, candidate)
                        && !candidate.getState().isTerminal()
        );
    }
    @Override
    public synchronized Optional<DataPullTask> heartbeat(
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
        DataPullTask task = tasksById.get(taskId);
        if (!DataPullTaskExecutionPolicy.ownsLiveEpoch(
                task, expectedFenceEpoch, expectedVersion, leaseOwner, now)
                || leaseUntil.compareTo(task.getLeaseUntil()) <= 0) {
            return Optional.empty();
        }
        task.setLeaseUntil(leaseUntil);
        task.setVersion(task.getVersion() + 1L);
        task.setUpdatedAt(now);
        return Optional.of(task.copy());
    }

    @Override
    public synchronized Optional<DataPullTask> find(long taskId) {
        if (taskId <= 0L) {
            throw new IllegalArgumentException("task id must be positive");
        }
        DataPullTask task = tasksById.get(taskId);
        return task == null ? Optional.empty() : Optional.of(task.copy());
    }

}
