package com.nuono.next.noonpull;

import com.nuono.next.officialwarehouse.OfficialWarehouseInventorySyncService;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InventorySyncResultView;
import java.util.Optional;

/** Executes the legacy official-warehouse inventory interface task. */
final class LegacyNoonInventoryTaskExecutor implements LegacyNoonTaskExecutor {
    private static final String REQUEST_NAME = "official-warehouse-fbn-inventory";

    private final NoonPullFoundationService foundationService;
    private final OfficialWarehouseInventorySyncService inventorySyncService;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final LegacyNoonPullFailureRecorder failureRecorder;

    LegacyNoonInventoryTaskExecutor(
            NoonPullFoundationService foundationService,
            OfficialWarehouseInventorySyncService inventorySyncService,
            NoonRiskBackoffGuard riskBackoffGuard,
            LegacyNoonPullFailureRecorder failureRecorder
    ) {
        this.foundationService = foundationService;
        this.inventorySyncService = inventorySyncService;
        this.riskBackoffGuard = riskBackoffGuard == null
                ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failureRecorder = failureRecorder;
    }

    @Override
    public boolean accepts(NoonPullTaskRecord task) {
        return task != null
                && task.getPullType() == NoonPullType.INTERFACE
                && task.getDataDomain()
                    == NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY;
    }

    @Override
    public void execute(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (inventorySyncService == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled official warehouse inventory sync service is disabled",
                    1
            );
            result.failed();
            return;
        }
        NoonInterfacePullRequest request = LegacyNoonTaskContext.warehouseRequest(
                task, REQUEST_NAME
        );
        NoonRiskBackoffScope riskScope = NoonRiskBackoffScope.interfacePull(request);
        Optional<NoonRiskBackoffHold> activeHold = riskBackoffGuard.currentHold(riskScope);
        if (activeHold.isPresent()) {
            foundationService.recordInterfaceRiskBackoffDelay(
                    task.getId(), activeHold.get(), "official warehouse inventory"
            );
            result.failed();
            return;
        }
        NoonPullTaskRecord running = foundationService.markRunning(
                task.getId(), "official-warehouse-inventory-sync"
        );
        if (running.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            result.skipped();
            return;
        }
        try {
            InventorySyncCommand command = new InventorySyncCommand();
            command.storeCode = task.getStoreCode();
            command.siteCode = task.getSiteCode();
            InventorySyncResultView syncResult = inventorySyncService.sync(
                    LegacyNoonTaskContext.businessAccess(task), command
            );
            String sourceBatchId = "official-warehouse-inventory-" + task.getId()
                    + "-" + NoonPullScheduledExecutionSupport.valueOrUnknown(
                            syncResult.syncBatchId
                    );
            foundationService.markSucceeded(
                    task.getId(), sourceBatchId,
                    "official warehouse inventory synced; fetched="
                            + syncResult.fetchedRows + "; inserted="
                            + syncResult.insertedRows
            );
            riskBackoffGuard.recordSuccess(riskScope, task.getDataDomain().name());
            result.executed();
        } catch (RuntimeException exception) {
            failureRecorder.interfaceFailure(
                    task, request, failureRecorder.safeMessage(exception)
            );
            result.failed();
        }
    }
}
