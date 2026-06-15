package com.nuono.next.system.task;

import java.util.List;

public interface OperationalTaskRepository {
    Long nextId(String sequenceName, Long initialValue);

    void insert(OperationalTask task);

    OperationalTask selectById(Long taskId);

    OperationalTask selectActiveByNaturalKey(String taskType, String naturalKey);

    OperationalTask selectLatestByNaturalKey(String taskType, String naturalKey);

    void update(OperationalTask task);

    List<OperationalTask> listActiveByTaskType(String taskType, int limit);

    List<OperationalTask> listRecent(String taskType, int limit);
}
