package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;

/** Fail-closed classifier that keeps legacy scheduled DP08 work out of runtime recovery. */
final class CompetitorManualRecoveryScope {
    private static final String MANUAL_REFRESH = "MANUAL_REFRESH";
    private static final String MANUAL_MONITOR = "MANUAL_MONITOR";
    private static final String FULL_MONITOR = "full-monitor";

    private CompetitorManualRecoveryScope() {
    }

    static boolean includes(CompetitorSearchRunRow run) {
        return run != null && (MANUAL_REFRESH.equals(run.getTriggerMode())
                || MANUAL_MONITOR.equals(run.getTriggerMode()));
    }

    static boolean includes(OperationalTask task, CompetitorSearchRunRow run) {
        if (run != null) {
            return includes(run);
        }
        try {
            Long watchProductId = CompetitorRefreshRecoveryPayload.watchProductId(task);
            return watchProductId != null
                    && (CompetitorRefreshRecoveryPayload.matchesIdentity(
                            task, watchProductId, CompetitorRefreshExecutionMode.FULL_MANUAL
                    ) || CompetitorRefreshRecoveryPayload.matchesIdentity(
                            task, watchProductId,
                            CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR
                    ));
        } catch (RuntimeException invalidPayload) {
            return false;
        }
    }

    static boolean includesBatch(
            OperationalTask task,
            CompetitorMonitoringPlanFactory plans
    ) {
        if (task == null
                || !CompetitorMonitoringBatchService.STORE_TASK_TYPE.equals(task.getTaskType())) {
            return false;
        }
        CompetitorRefreshExecutionMode legacy = plans.legacyStoreMode(task.getPayloadJson());
        if (legacy != null) {
            return legacy == CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR;
        }
        try {
            CompetitorMonitoringCheckpoint checkpoint =
                    CompetitorMonitoringCheckpoint.fromJson(task.getPayloadJson());
            return "STORE".equals(checkpoint.getBatchKind())
                    && MANUAL_MONITOR.equals(checkpoint.getTriggerMode())
                    && FULL_MONITOR.equals(checkpoint.getExecutionMode());
        } catch (RuntimeException invalidCheckpoint) {
            return false;
        }
    }
}
