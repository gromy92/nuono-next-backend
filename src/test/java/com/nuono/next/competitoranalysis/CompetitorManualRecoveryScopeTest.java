package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.system.task.OperationalTask;
import org.junit.jupiter.api.Test;

class CompetitorManualRecoveryScopeTest {

    private final CompetitorMonitoringPlanFactory plans =
            new CompetitorMonitoringPlanFactory();

    @Test
    void productRecoveryAcceptsOnlyExactManualTriggerModes() {
        assertTrue(CompetitorManualRecoveryScope.includes(run("MANUAL_REFRESH")));
        assertTrue(CompetitorManualRecoveryScope.includes(run("MANUAL_MONITOR")));
        assertFalse(CompetitorManualRecoveryScope.includes(run("SCHEDULED_RANK_MONITOR")));
        assertFalse(CompetitorManualRecoveryScope.includes(run("SCHEDULED_DETAIL_MONITOR")));
        assertFalse(CompetitorManualRecoveryScope.includes(run("UNKNOWN")));
        assertFalse(CompetitorManualRecoveryScope.includes(null));
    }

    @Test
    void missingRunUsesStrictPersistedTaskIdentityWithoutRevivingScheduledWork() {
        OperationalTask manual = task("OPERATIONS_COMPETITOR_REFRESH",
                CompetitorRefreshRecoveryPayload.fresh(
                        7L, 1, CompetitorRefreshExecutionMode.FULL_MANUAL, null
                ));
        OperationalTask scheduled = task("OPERATIONS_COMPETITOR_REFRESH",
                CompetitorRefreshRecoveryPayload.fresh(
                        7L, 1, CompetitorRefreshExecutionMode.SCHEDULED_RANK, "batch"
                ));

        assertTrue(CompetitorManualRecoveryScope.includes(manual, null));
        assertFalse(CompetitorManualRecoveryScope.includes(scheduled, null));
        assertFalse(CompetitorManualRecoveryScope.includes(task(
                "OPERATIONS_COMPETITOR_REFRESH", "{}"
        ), null));
    }

    @Test
    void batchRecoveryAcceptsManualStoreButRejectsCyclesAndUnknownPayloads() {
        assertTrue(CompetitorManualRecoveryScope.includesBatch(
                task(CompetitorMonitoringBatchService.STORE_TASK_TYPE, checkpoint("full-monitor")),
                plans
        ));
        assertFalse(CompetitorManualRecoveryScope.includesBatch(
                task(CompetitorMonitoringBatchService.STORE_TASK_TYPE, checkpoint("rank")),
                plans
        ));
        assertFalse(CompetitorManualRecoveryScope.includesBatch(
                task(CompetitorMonitoringBatchService.STORE_TASK_TYPE,
                        checkpoint("full-monitor").replace("MANUAL_MONITOR", "SCHEDULED_RANK_MONITOR")),
                plans
        ));
        assertFalse(CompetitorManualRecoveryScope.includesBatch(
                task(CompetitorMonitoringBatchService.CYCLE_TASK_TYPE, checkpoint("full-monitor")),
                plans
        ));
        assertFalse(CompetitorManualRecoveryScope.includesBatch(
                task(CompetitorMonitoringBatchService.STORE_TASK_TYPE, "{}"),
                plans
        ));
    }

    private CompetitorSearchRunRow run(String triggerMode) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setTriggerMode(triggerMode);
        return row;
    }

    private OperationalTask task(String taskType, String payload) {
        OperationalTask task = new OperationalTask();
        task.setTaskType(taskType);
        task.setPayloadJson(payload);
        return task;
    }

    private String checkpoint(String executionMode) {
        return "{\"batchKind\":\"STORE\",\"batchKey\":\"b\","
                + "\"triggerMode\":\"MANUAL_MONITOR\",\"executionMode\":\""
                + executionMode + "\",\"upperWatchProductId\":1}";
    }
}
