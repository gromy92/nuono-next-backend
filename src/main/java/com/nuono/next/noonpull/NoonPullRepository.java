package com.nuono.next.noonpull;

import java.util.List;
import java.util.stream.Collectors;

public interface NoonPullRepository {
    Long nextId(String sequenceName, Long initialValue);

    void insertPlan(NoonPullPlanRecord plan);

    NoonPullPlanRecord selectPlan(Long planId);

    void updatePlan(NoonPullPlanRecord plan);

    void insertTask(NoonPullTaskRecord task);

    NoonPullTaskRecord selectTask(Long taskId);

    NoonPullTaskRecord selectActiveTaskByLockKey(String activeLockKey);

    NoonPullTaskRecord selectLatestTaskByLockKey(String activeLockKey);

    void updateTask(NoonPullTaskRecord task);

    List<NoonPullPlanRecord> listPlans();

    List<NoonPullTaskRecord> listTasks();

    default List<NoonPullTaskRecord> listActiveTasks() {
        return listTasks().stream()
                .filter((task) -> task.getStatus() == NoonPullTaskStatus.QUEUED
                        || task.getStatus() == NoonPullTaskStatus.RUNNING)
                .collect(Collectors.toList());
    }
}
