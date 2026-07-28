package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import java.util.Objects;

final class CompetitorRefreshRecoveryIdentity {
    private static final String NATURAL_KEY_PREFIX = "watchProduct:";

    private CompetitorRefreshRecoveryIdentity() {
    }

    static String naturalKey(
            Long watchProductId,
            CompetitorRefreshExecutionMode mode
    ) {
        if (watchProductId == null || mode == null) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Competitor refresh identity is incomplete."
            );
        }
        String key = NATURAL_KEY_PREFIX + watchProductId;
        return mode == CompetitorRefreshExecutionMode.FULL_MANUAL
                ? key
                : key + ":" + mode.taskKey();
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
        String expectedNaturalKey = naturalKey(watchProduct.getId(), runMode);
        if (!Objects.equals(task.getId(), run.getTaskId())
                || !Objects.equals(watchProduct.getId(), run.getWatchProductId())
                || runMode != mode
                || !expectedNaturalKey.equals(task.getNaturalKey())) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Competitor refresh task, run, watch product, and mode do not match."
            );
        }
    }
}
