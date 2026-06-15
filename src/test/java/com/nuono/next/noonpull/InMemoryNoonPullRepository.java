package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class InMemoryNoonPullRepository implements NoonPullRepository {
    private long nextId = 1000L;
    private Integer recentTaskLimit;
    final Map<Long, NoonPullPlanRecord> plans = new LinkedHashMap<>();
    final Map<Long, NoonPullTaskRecord> tasks = new LinkedHashMap<>();

    void limitListTasksToRecent(int limit) {
        this.recentTaskLimit = limit;
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
                        || task.getStatus() == NoonPullTaskStatus.RUNNING)
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
    public void updateTask(NoonPullTaskRecord task) {
        tasks.put(task.getId(), task.copy());
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
        List<NoonPullTaskRecord> result = new ArrayList<>();
        for (NoonPullTaskRecord task : tasks.values()) {
            if (task.getStatus() == NoonPullTaskStatus.QUEUED
                    || task.getStatus() == NoonPullTaskStatus.RUNNING) {
                result.add(task.copy());
            }
        }
        return result;
    }
}
