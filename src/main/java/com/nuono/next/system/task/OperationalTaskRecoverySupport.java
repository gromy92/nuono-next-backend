package com.nuono.next.system.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class OperationalTaskRecoverySupport {
    private final OperationalTaskRepository repository;
    private final Clock clock;

    OperationalTaskRecoverySupport(OperationalTaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    Optional<OperationalTask> prepareQueuedPayload(
            OperationalTask expected,
            String payloadJson,
            String message
    ) {
        if (expected == null || expected.getId() == null) {
            throw new IllegalArgumentException("expected task is required");
        }
        String normalizedPayload = normalize(payloadJson);
        if (!Objects.equals(expected.getPayloadJson(), normalizedPayload)) {
            repository.compareAndSetQueuedPayload(
                    expected.getId(),
                    expected.getPayloadJson(),
                    normalizedPayload,
                    normalize(message),
                    now()
            );
        }
        OperationalTask task = repository.selectById(expected.getId());
        return task == null || task.getStatus() != OperationalTaskStatus.QUEUED
                ? Optional.empty()
                : Optional.of(task.copy());
    }

    boolean checkpointRunning(
            Long taskId,
            String payloadJson,
            Integer progressPercent,
            String message
    ) {
        requireTaskId(taskId);
        return repository.checkpointRunning(
                taskId,
                normalize(payloadJson),
                clampProgress(progressPercent),
                normalize(message),
                now()
        );
    }

    boolean requeueRunning(
            Long taskId,
            String payloadJson,
            Integer progressPercent,
            String errorCode,
            String message
    ) {
        requireTaskId(taskId);
        return repository.requeueRunning(
                taskId,
                normalize(payloadJson),
                clampProgress(progressPercent),
                normalize(errorCode),
                normalize(message),
                now()
        );
    }

    boolean failStaleRunning(
            Long taskId,
            LocalDateTime staleBefore,
            String errorCode,
            String message
    ) {
        requireStaleArguments(taskId, staleBefore);
        return repository.failStaleRunning(
                taskId,
                staleBefore,
                normalize(errorCode),
                normalize(message),
                now()
        );
    }

    boolean failStaleQueued(
            Long taskId,
            LocalDateTime staleBefore,
            String errorCode,
            String message
    ) {
        requireStaleArguments(taskId, staleBefore);
        return repository.failStaleQueued(
                taskId,
                staleBefore,
                normalize(errorCode),
                normalize(message),
                now()
        );
    }

    Optional<OperationalTask> findLatestByBatchKey(
            String taskType,
            String naturalKey,
            String batchKey
    ) {
        OperationalTask task = repository.selectLatestByNaturalKeyAndBatchKey(
                requireText(taskType, "taskType"),
                requireText(naturalKey, "naturalKey"),
                requireText(batchKey, "batchKey")
        );
        return task == null ? Optional.empty() : Optional.of(task.copy());
    }

    List<OperationalTask> listActiveAfter(
            String taskType,
            Long afterTaskId,
            int limit
    ) {
        return repository.listActiveByTaskTypeAfterId(
                        requireText(taskType, "taskType"),
                        afterTaskId == null ? 0L : Math.max(0L, afterTaskId),
                        Math.max(1, Math.min(limit, 1000))
                ).stream()
                .map(OperationalTask::copy)
                .collect(Collectors.toList());
    }

    private static void requireTaskId(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId is required");
        }
    }

    private static void requireStaleArguments(Long taskId, LocalDateTime staleBefore) {
        if (taskId == null || staleBefore == null) {
            throw new IllegalArgumentException("taskId and staleBefore are required");
        }
    }

    private static int clampProgress(Integer progressPercent) {
        return progressPercent == null ? 0 : Math.max(0, Math.min(progressPercent, 100));
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
