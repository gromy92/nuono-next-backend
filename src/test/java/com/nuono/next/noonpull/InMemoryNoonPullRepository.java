package com.nuono.next.noonpull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class InMemoryNoonPullRepository implements NoonPullRepository {
    private long nextId = 1000L;
    private Integer recentTaskLimit;
    private List<NoonPullTaskRecord> activeTaskSnapshotOverride;
    final Map<Long, NoonPullPlanRecord> plans = new LinkedHashMap<>();
    final Map<Long, NoonPullTaskRecord> tasks = new LinkedHashMap<>();

    void limitListTasksToRecent(int limit) {
        this.recentTaskLimit = limit;
    }

    void useActiveTaskSnapshot(List<NoonPullTaskRecord> tasks) {
        this.activeTaskSnapshotOverride = tasks == null
                ? null
                : tasks.stream().map(NoonPullTaskRecord::copy).collect(Collectors.toList());
    }

    @Override
    public Long nextId(String sequenceName, Long initialValue) {
        return nextId++;
    }

    @Override
    public void insertPlan(NoonPullPlanRecord plan) {
        plans.put(plan.getId(), plan.copy());
    }

    @Override
    public NoonPullPlanRecord selectPlan(Long planId) {
        NoonPullPlanRecord plan = plans.get(planId);
        return plan == null ? null : plan.copy();
    }

    @Override
    public void updatePlan(NoonPullPlanRecord plan) {
        plans.put(plan.getId(), plan.copy());
    }

    @Override
    public void insertTask(NoonPullTaskRecord task) {
        tasks.put(task.getId(), task.copy());
    }

    @Override
    public NoonPullTaskRecord selectTask(Long taskId) {
        NoonPullTaskRecord task = tasks.get(taskId);
        return task == null ? null : task.copy();
    }

    @Override
    public NoonPullTaskRecord selectActiveTaskByLockKey(String activeLockKey) {
        return tasks.values().stream()
                .filter((task) -> activeLockKey.equals(task.getActiveLockKey()))
                .filter((task) -> task.getStatus() == NoonPullTaskStatus.QUEUED
                        || task.getStatus() == NoonPullTaskStatus.RUNNING
                        || task.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH)
                .findFirst()
                .map(NoonPullTaskRecord::copy)
                .orElse(null);
    }

    @Override
    public NoonPullTaskRecord selectLatestTaskByLockKey(String activeLockKey) {
        NoonPullTaskRecord latest = null;
        for (NoonPullTaskRecord task : tasks.values()) {
            if (!activeLockKey.equals(task.getActiveLockKey())) {
                continue;
            }
            if (latest == null || task.getId() > latest.getId()) {
                latest = task;
            }
        }
        return latest == null ? null : latest.copy();
    }

    @Override
    public synchronized int updateTask(NoonPullTaskRecord task) {
        NoonPullTaskRecord current = tasks.get(task.getId());
        if (current == null || current.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            return 0;
        }
        tasks.put(task.getId(), task.copy());
        return 1;
    }

    @Override
    public int blockTaskForAuth(
            Long taskId,
            Long recoveryId,
            String diagnosticSummary,
            LocalDateTime now
    ) {
        NoonPullTaskRecord task = tasks.get(taskId);
        if (task == null || (task.getStatus() != NoonPullTaskStatus.QUEUED
                && task.getStatus() != NoonPullTaskStatus.RUNNING)) {
            return 0;
        }
        task.setStatus(NoonPullTaskStatus.BLOCKED_AUTH);
        task.setAuthRecoveryId(recoveryId);
        task.setFailureType(NoonPullFailureType.AUTH_REQUIRED.code());
        task.setRetryAction(NoonPullRetryAction.WAIT_FOR_AUTH.name());
        task.setRetryable(Boolean.TRUE);
        task.setRequiresManualAction(Boolean.FALSE);
        task.setDiagnosticSummary(diagnosticSummary);
        task.setReadinessState("auth_recovery_queued");
        task.setLockedBy(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(now);
        return 1;
    }

    int requeueBlockedTaskAfterAuthForTest(Long taskId, Long recoveryId, LocalDateTime now) {
        NoonPullTaskRecord task = tasks.get(taskId);
        if (task == null
                || task.getStatus() != NoonPullTaskStatus.BLOCKED_AUTH
                || !java.util.Objects.equals(task.getAuthRecoveryId(), recoveryId)) {
            return 0;
        }
        task.setStatus(NoonPullTaskStatus.QUEUED);
        task.setFailureType(null);
        task.setRetryAction(null);
        task.setRetryable(null);
        task.setRequiresManualAction(null);
        task.setDiagnosticSummary("auth recovery completed; original pull task requeued");
        task.setReadinessState("auth_recovered");
        task.setLockedBy(null);
        task.setQueuedAt(now);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(now);
        return 1;
    }

    @Override
    public List<NoonPullPlanRecord> listPlans() {
        List<NoonPullPlanRecord> result = new ArrayList<>();
        for (NoonPullPlanRecord plan : plans.values()) {
            result.add(plan.copy());
        }
        return result;
    }

    @Override
    public List<NoonPullTaskRecord> listTasks() {
        List<NoonPullTaskRecord> result = new ArrayList<>();
        List<NoonPullTaskRecord> source = new ArrayList<>(tasks.values());
        if (recentTaskLimit != null) {
            source = source.stream()
                    .sorted(Comparator.comparing(NoonPullTaskRecord::getId).reversed())
                    .limit(recentTaskLimit)
                    .collect(Collectors.toList());
        }
        for (NoonPullTaskRecord task : source) {
            result.add(task.copy());
        }
        return result;
    }

    @Override
    public List<NoonPullTaskRecord> listActiveTasks() {
        if (activeTaskSnapshotOverride != null) {
            return activeTaskSnapshotOverride.stream()
                    .map(NoonPullTaskRecord::copy)
                    .collect(Collectors.toList());
        }
        List<NoonPullTaskRecord> result = new ArrayList<>();
        for (NoonPullTaskRecord task : tasks.values()) {
            if (task.getStatus() == NoonPullTaskStatus.QUEUED
                    || task.getStatus() == NoonPullTaskStatus.RUNNING
                    || task.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
                result.add(task.copy());
            }
        }
        return result;
    }
}
