package com.nuono.next.system.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationalTaskService {
    private static final String TASK_SEQUENCE = "operational_task";
    private static final Long TASK_ID_INITIAL_VALUE = 150000L;

    private final OperationalTaskRepository repository;
    private final Clock clock;

    @Autowired
    public OperationalTaskService(OperationalTaskRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public OperationalTaskService(OperationalTaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public OperationalTask start(String taskType, String naturalKey, OperationalTaskPayload payload) {
        String normalizedTaskType = requireText(taskType, "taskType");
        String normalizedNaturalKey = requireText(naturalKey, "naturalKey");
        OperationalTask activeTask = repository.selectActiveByNaturalKey(normalizedTaskType, normalizedNaturalKey);
        if (activeTask != null) {
            return activeTask.copy();
        }

        OperationalTaskPayload safePayload = payload == null ? OperationalTaskPayload.empty() : payload;
        LocalDateTime now = now();
        OperationalTask task = new OperationalTask();
        task.setId(repository.nextId(TASK_SEQUENCE, TASK_ID_INITIAL_VALUE));
        task.setTaskType(normalizedTaskType);
        task.setNaturalKey(normalizedNaturalKey);
        task.setOwnerUserId(safePayload.getOwnerUserId());
        task.setStoreCode(normalize(safePayload.getStoreCode()));
        task.setSiteCode(normalize(safePayload.getSiteCode()));
        task.setPayloadJson(normalize(safePayload.getPayloadJson()));
        task.setMessage(normalize(safePayload.getMessage()));
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setProgressPercent(0);
        task.setStartedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        repository.insert(task);
        return task.copy();
    }

    public Optional<OperationalTask> find(Long taskId) {
        OperationalTask task = repository.selectById(taskId);
        return task == null ? Optional.empty() : Optional.of(task.copy());
    }

    public Optional<OperationalTask> findActive(String taskType, String naturalKey) {
        OperationalTask task = repository.selectActiveByNaturalKey(
                requireText(taskType, "taskType"),
                requireText(naturalKey, "naturalKey")
        );
        return task == null ? Optional.empty() : Optional.of(task.copy());
    }

    public Optional<OperationalTask> findLatest(String taskType, String naturalKey) {
        OperationalTask task = repository.selectLatestByNaturalKey(
                requireText(taskType, "taskType"),
                requireText(naturalKey, "naturalKey")
        );
        return task == null ? Optional.empty() : Optional.of(task.copy());
    }

    public List<OperationalTask> listRecent(String taskType, int limit) {
        return repository.listRecent(normalize(taskType), Math.max(1, Math.min(limit, 200))).stream()
                .map(OperationalTask::copy)
                .collect(Collectors.toList());
    }

    public List<OperationalTask> listActive(String taskType, int limit) {
        return repository.listActiveByTaskType(
                        requireText(taskType, "taskType"),
                        Math.max(1, Math.min(limit, 1000))
                ).stream()
                .map(OperationalTask::copy)
                .collect(Collectors.toList());
    }

    public OperationalTask progress(Long taskId, Integer progressPercent, String message) {
        OperationalTask task = requireMutableTask(taskId);
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setProgressPercent(clampProgress(progressPercent));
        task.setMessage(normalize(message));
        task.setUpdatedAt(now());
        repository.update(task);
        return task.copy();
    }

    public OperationalTask complete(Long taskId, String resultJson, String message) {
        OperationalTask task = requireMutableTask(taskId);
        LocalDateTime now = now();
        task.setStatus(OperationalTaskStatus.SUCCEEDED);
        task.setProgressPercent(100);
        task.setResultJson(normalize(resultJson));
        task.setMessage(normalize(message));
        task.setErrorCode(null);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.update(task);
        return task.copy();
    }

    public OperationalTask fail(Long taskId, String errorCode, String message) {
        return fail(taskId, errorCode, message, null);
    }

    public OperationalTask fail(Long taskId, String errorCode, String message, String resultJson) {
        OperationalTask task = requireMutableTask(taskId);
        LocalDateTime now = now();
        task.setStatus(OperationalTaskStatus.FAILED);
        task.setErrorCode(normalize(errorCode));
        task.setMessage(normalize(message));
        task.setResultJson(normalize(resultJson));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.update(task);
        return task.copy();
    }

    public OperationalTask cancel(Long taskId, String message) {
        OperationalTask task = requireMutableTask(taskId);
        LocalDateTime now = now();
        task.setStatus(OperationalTaskStatus.CANCELLED);
        task.setMessage(normalize(message));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.update(task);
        return task.copy();
    }

    private OperationalTask requireMutableTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId is required");
        }
        OperationalTask task = repository.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Operational task not found: " + taskId);
        }
        if (task.getStatus() != null && task.getStatus().isTerminal()) {
            throw new IllegalStateException("Operational task has already finished: " + taskId);
        }
        return task;
    }

    private int clampProgress(Integer progressPercent) {
        if (progressPercent == null) {
            return 0;
        }
        return Math.max(0, Math.min(progressPercent, 100));
    }

    private String requireText(String value, String field) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
