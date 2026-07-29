package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import org.springframework.util.StringUtils;

final class CompetitorRefreshTaskLocator {
    private final OperationalTaskService taskService;

    CompetitorRefreshTaskLocator(OperationalTaskService taskService) {
        this.taskService = taskService;
    }

    Keys keys(
            Long watchProductId,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        CompetitorRefreshExecutionMode safeMode = mode == null
                ? CompetitorRefreshExecutionMode.FULL_MANUAL
                : mode;
        return new Keys(
                CompetitorRefreshRecoveryIdentity.naturalKey(
                        watchProductId, safeMode, batchKey
                ),
                CompetitorRefreshRecoveryIdentity.naturalKey(
                        watchProductId, safeMode
                )
        );
    }

    OperationalTask active(Keys keys, String batchKey) {
        OperationalTask task = taskService.findActive(
                CompetitorAnalysisRefreshService.TASK_TYPE, keys.current
        ).orElse(null);
        if (task != null || keys.current.equals(keys.legacy)) {
            return task;
        }
        OperationalTask legacy = taskService.findActive(
                CompetitorAnalysisRefreshService.TASK_TYPE, keys.legacy
        ).orElse(null);
        return hasBatchKey(legacy, batchKey) ? legacy : null;
    }

    OperationalTask latest(Keys keys, String batchKey) {
        OperationalTask task = taskService.findLatestByBatchKey(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                keys.current,
                batchKey
        ).orElse(null);
        if (task != null || keys.current.equals(keys.legacy)) {
            return task;
        }
        return taskService.findLatestByBatchKey(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                keys.legacy,
                batchKey
        ).orElse(null);
    }

    boolean hasBatchKey(OperationalTask task, String batchKey) {
        return task != null
                && StringUtils.hasText(batchKey)
                && batchKey.trim().equals(
                        CompetitorRefreshRecoveryPayload.batchKey(task)
                );
    }

    static final class Keys {
        final String current;
        final String legacy;

        private Keys(String current, String legacy) {
            this.current = current;
            this.legacy = legacy;
        }
    }
}
