package com.nuono.next.system.task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    default boolean checkpointRunning(
            Long taskId,
            String payloadJson,
            int progressPercent,
            String message,
            LocalDateTime updatedAt
    ) {
        OperationalTask task = selectById(taskId);
        if (task == null || task.getStatus() != OperationalTaskStatus.RUNNING) {
            return false;
        }
        task.setPayloadJson(payloadJson);
        task.setProgressPercent(progressPercent);
        task.setMessage(message);
        task.setUpdatedAt(updatedAt);
        update(task);
        return true;
    }

    default boolean failStaleRunning(
            Long taskId,
            LocalDateTime staleBefore,
            String errorCode,
            String message,
            LocalDateTime finishedAt
    ) {
        OperationalTask task = selectById(taskId);
        if (task == null || task.getStatus() != OperationalTaskStatus.RUNNING) {
            return false;
        }
        LocalDateTime updatedAt = task.getUpdatedAt() == null ? task.getStartedAt() : task.getUpdatedAt();
        if (updatedAt == null || updatedAt.isAfter(staleBefore)) {
            return false;
        }
        task.setStatus(OperationalTaskStatus.FAILED);
        task.setErrorCode(errorCode);
        task.setMessage(message);
        task.setFinishedAt(finishedAt);
        task.setUpdatedAt(finishedAt);
        update(task);
        return true;
    }

    default boolean failStaleQueued(
            Long taskId,
            LocalDateTime staleBefore,
            String errorCode,
            String message,
            LocalDateTime finishedAt
    ) {
        OperationalTask task = selectById(taskId);
        if (task == null || task.getStatus() != OperationalTaskStatus.QUEUED) {
            return false;
        }
        LocalDateTime updatedAt = task.getUpdatedAt() == null ? task.getCreatedAt() : task.getUpdatedAt();
        if (updatedAt == null || updatedAt.isAfter(staleBefore)) {
            return false;
        }
        task.setStatus(OperationalTaskStatus.FAILED);
        task.setErrorCode(errorCode);
        task.setMessage(message);
        task.setFinishedAt(finishedAt);
        task.setUpdatedAt(finishedAt);
        update(task);
        return true;
    }

    List<OperationalTask> listActiveByTaskType(String taskType, int limit);

    default List<OperationalTask> listActiveByTaskTypeAfterId(
            String taskType,
            Long afterTaskId,
            int limit
    ) {
        long cursor = afterTaskId == null ? 0L : afterTaskId;
        return listActiveByTaskType(taskType, Integer.MAX_VALUE).stream()
                .filter(task -> task.getId() != null && task.getId() > cursor)
                .limit(limit)
                .collect(Collectors.toList());
    }

    List<OperationalTask> listRecent(String taskType, int limit);
}
