package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InMemoryNoonPullRepository implements NoonPullRepository {
    private long nextId = 1000L;
    final Map<Long, NoonPullPlanRecord> plans = new LinkedHashMap<>();
    final Map<Long, NoonPullTaskRecord> tasks = new LinkedHashMap<>();

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
        for (NoonPullTaskRecord task : tasks.values()) {
            result.add(task.copy());
        }
        return result;
    }
}
