package com.nuono.next.noonpull;

import java.time.LocalDateTime;
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

    int updateTask(NoonPullTaskRecord task);

    default int blockTaskForAuth(Long taskId, Long recoveryId, String diagnosticSummary, LocalDateTime now) {
        throw new UnsupportedOperationException("Noon pull auth recovery persistence is not configured.");
    }

    List<NoonPullPlanRecord> listPlans();

    List<NoonPullTaskRecord> listTasks();

    default List<NoonPullTaskRecord> listActiveTasks() {
        return listTasks().stream()
                .filter((task) -> task.getStatus() == NoonPullTaskStatus.QUEUED
                        || task.getStatus() == NoonPullTaskStatus.RUNNING
                        || task.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH)
                .collect(Collectors.toList());
    }
}
