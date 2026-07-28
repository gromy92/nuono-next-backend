package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class InMemoryOperationalTaskRepository implements OperationalTaskRepository {
    private long nextId = 150000L;
    final Map<Long, OperationalTask> tasks = new LinkedHashMap<>();

    @Override
    public Long nextId(String sequenceName, Long initialValue) {
        return nextId++;
    }

    @Override
    public void insert(OperationalTask task) {
        tasks.put(task.getId(), task.copy());
        if (task.getId() != null) {
            nextId = Math.max(nextId, task.getId() + 1);
        }
    }

    @Override
    public OperationalTask selectById(Long taskId) {
        OperationalTask task = tasks.get(taskId);
        return task == null ? null : task.copy();
    }

    @Override
    public OperationalTask selectActiveByNaturalKey(String taskType, String naturalKey) {
        return tasks.values().stream()
                .filter(task -> taskType.equals(task.getTaskType()))
                .filter(task -> naturalKey.equals(task.getNaturalKey()))
                .filter(task -> task.getStatus() != null && task.getStatus().isActive())
                .findFirst()
                .map(OperationalTask::copy)
                .orElse(null);
    }

    @Override
    public OperationalTask selectLatestByNaturalKey(String taskType, String naturalKey) {
        return tasks.values().stream()
                .filter(task -> taskType.equals(task.getTaskType()))
                .filter(task -> naturalKey.equals(task.getNaturalKey()))
                .max(Comparator.comparing(OperationalTask::getId))
                .map(OperationalTask::copy)
                .orElse(null);
    }

    @Override
    public void update(OperationalTask task) {
        tasks.put(task.getId(), task.copy());
    }

    @Override
    public List<OperationalTask> listActiveByTaskType(String taskType, int limit) {
        return tasks.values().stream()
                .filter(task -> taskType.equals(task.getTaskType()))
                .filter(task -> task.getStatus() != null && task.getStatus().isActive())
                .sorted(Comparator.comparing(OperationalTask::getId))
                .limit(limit)
                .map(OperationalTask::copy)
                .collect(Collectors.toList());
    }

    @Override
    public List<OperationalTask> listRecent(String taskType, int limit) {
        return tasks.values().stream()
                .filter(task -> taskType == null || taskType.equals(task.getTaskType()))
                .sorted(Comparator.comparing(OperationalTask::getId).reversed())
                .limit(limit)
                .map(OperationalTask::copy)
                .collect(Collectors.toList());
    }
}
