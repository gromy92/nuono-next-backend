package com.nuono.next.system.task;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationalTaskRepository {
    Long nextId(String sequenceName, Long initialValue);

    void insert(OperationalTask task);

    OperationalTask selectById(Long taskId);

    OperationalTask selectActiveByNaturalKey(String taskType, String naturalKey);

    OperationalTask selectLatestByNaturalKey(String taskType, String naturalKey);

    void update(OperationalTask task);

    default boolean claimQueued(Long taskId, String message, LocalDateTime startedAt) {
        OperationalTask task = selectById(taskId);
        if (task == null || task.getStatus() != OperationalTaskStatus.QUEUED) {
            return false;
        }
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setMessage(message);
        task.setStartedAt(startedAt);
        task.setUpdatedAt(startedAt);
        update(task);
        return true;
    }

    List<OperationalTask> listActiveByTaskType(String taskType, int limit);

    List<OperationalTask> listRecent(String taskType, int limit);
}
