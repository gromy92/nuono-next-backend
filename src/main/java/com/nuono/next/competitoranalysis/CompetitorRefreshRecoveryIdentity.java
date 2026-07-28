package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import java.util.Objects;
import org.springframework.util.StringUtils;

final class CompetitorRefreshRecoveryIdentity {
    private static final String NATURAL_KEY_PREFIX = "watchProduct:";

    private CompetitorRefreshRecoveryIdentity() {
    }

    static String naturalKey(
            Long watchProductId,
            CompetitorRefreshExecutionMode mode
    ) {
        return naturalKey(watchProductId, mode, null);
    }

    static String naturalKey(
            Long watchProductId,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        if (watchProductId == null || mode == null) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Competitor refresh identity is incomplete."
            );
        }
        String key = NATURAL_KEY_PREFIX + watchProductId;
        if (mode == CompetitorRefreshExecutionMode.FULL_MANUAL) {
            return key;
        }
        key += ":" + mode.taskKey();
        return mode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                && StringUtils.hasText(batchKey)
                ? key + ":" + batchKey.trim()
                : key;
    }

    static void validate(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct,
            CompetitorRefreshExecutionMode mode
    ) {
        if (task == null
                || task.getId() == null
                || !CompetitorAnalysisRefreshService.TASK_TYPE.equals(task.getTaskType())
                || run == null
                || run.getId() == null
                || watchProduct == null
                || watchProduct.getId() == null
                || mode == null) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Competitor refresh identity is incomplete."
            );
        }
        CompetitorRefreshExecutionMode runMode =
                CompetitorRefreshExecutionMode.strictFromTriggerMode(run.getTriggerMode());
        String legacyNaturalKey = naturalKey(watchProduct.getId(), runMode);
        String expectedNaturalKey = naturalKey(
                watchProduct.getId(),
                runMode,
                CompetitorRefreshRecoveryPayload.batchKey(task)
        );
        boolean naturalKeyMatches = expectedNaturalKey.equals(task.getNaturalKey())
                || legacyNaturalKey.equals(task.getNaturalKey());
        if (!Objects.equals(task.getId(), run.getTaskId())
                || !Objects.equals(watchProduct.getId(), run.getWatchProductId())
                || runMode != mode
                || !naturalKeyMatches) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Competitor refresh task, run, watch product, and mode do not match."
            );
        }
    }
}
