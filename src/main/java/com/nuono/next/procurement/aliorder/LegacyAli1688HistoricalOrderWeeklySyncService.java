package com.nuono.next.procurement.aliorder;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.infrastructure.mapper.LegacyAli1688HistoricalOrderSyncMapper;
import org.springframework.stereotype.Service;

/** Predecessor DP-10 executor; runtime DP-10 has a separate task/fence/checkpoint model. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
class LegacyAli1688HistoricalOrderWeeklySyncService {
    static final String OPEN_API_PROVIDER_CODE = Ali1688HistoricalOrderOAuthService.PROVIDER_CODE;
    static final String SCHEDULED_WEEKLY_TASK_TYPE = "scheduled_weekly";
    private final Ali1688HistoricalOrderMapper facts;
    private final LegacyAli1688HistoricalOrderSyncMapper syncTasks;
    private final Ali1688HistoricalOrderProvider provider;
    private final LegacyAli1688HistoricalOrderFactWriter writer;

    LegacyAli1688HistoricalOrderWeeklySyncService(
            Ali1688HistoricalOrderMapper facts,
            LegacyAli1688HistoricalOrderSyncMapper syncTasks,
            Ali1688HistoricalOrderProvider provider,
            LegacyAli1688HistoricalOrderFactWriter writer
    ) {
        this.facts = facts;
        this.syncTasks = syncTasks;
        this.provider = provider;
        this.writer = writer;
    }

    Ali1688HistoricalOrderSyncTaskRow runScheduledWeekly(
            Long ownerUserId,
            Long authorizationId,
            Long operatorUserId
    ) {
        if (ownerUserId == null || authorizationId == null) return null;
        Ali1688HistoricalOrderAuthorizationRow authorization =
                facts.selectAuthorizationById(ownerUserId, authorizationId);
        if (authorization == null
                || !OPEN_API_PROVIDER_CODE.equals(authorization.getProviderCode())) return null;
        Ali1688HistoricalOrderSyncTaskRow task = createTask(
                ownerUserId, authorizationId, operatorUserId);
        execute(authorization, task);
        return task;
    }

    private Ali1688HistoricalOrderSyncTaskRow createTask(
            Long ownerUserId,
            Long authorizationId,
            Long operatorUserId
    ) {
        Ali1688HistoricalOrderSyncTaskRow task = new Ali1688HistoricalOrderSyncTaskRow();
        task.setId(facts.nextId("procurement_ali1688_order_sync_task", 92000L));
        task.setOwnerUserId(ownerUserId);
        task.setAuthorizationId(authorizationId);
        task.setTaskType(SCHEDULED_WEEKLY_TASK_TYPE);
        task.setStatus("running");
        task.setProcessedCount(0);
        task.setImportedCount(0);
        task.setFailedCount(0);
        task.setProgressPercent(0);
        task.setCheckpointJson(checkpoint(null));
        task.setCreatedBy(operatorUserId);
        task.setUpdatedBy(operatorUserId);
        syncTasks.insertSyncTask(task);
        return task;
    }

    private void execute(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderSyncTaskRow task
    ) {
        int processed = 0;
        int imported = 0;
        int failed = 0;
        String cursor = null;
        String lastBusinessFailure = null;
        try {
            while (true) {
                Ali1688HistoricalOrderProvider.Page page =
                        provider.fetchPage(authorization, cursor);
                if (page == null) throw new IllegalStateException("LEGACY_DP10_PAGE_MISSING");
                for (Ali1688HistoricalOrderProvider.OrderSnapshot order : page.getOrders()) {
                    processed++;
                    LegacyAli1688HistoricalOrderFactWriter.WriteResult result =
                            writer.write(task.getOwnerUserId(), authorization, order);
                    if (result.isSkipped()) {
                        failed++;
                        lastBusinessFailure = result.getFailureCode();
                        continue;
                    }
                    imported += result.getItemCount();
                }
                String checkpoint = checkpoint(page.getNextCursor());
                if (page.hasFailure()) {
                    failed++;
                    Ali1688HistoricalOrderFailureCode code =
                            Ali1688HistoricalOrderFailureCode.fromCode(page.getFailureCode());
                    finishFailure(
                            task.getId(), processed, imported, failed, code.getCode(),
                            shrink(page.getFailureMessage()), checkpoint,
                            page.isRetryableFailure() || code.isRetryable(),
                            code.isRequiresManualAction());
                    return;
                }
                if (!page.isHasMore()) {
                    if (failed == 0) {
                        syncTasks.markSyncTaskSuccess(
                                task.getId(), processed, imported, 0, checkpoint);
                    } else {
                        syncTasks.markSyncTaskPartialSuccess(
                                task.getId(), processed, imported, failed,
                                lastBusinessFailure == null
                                        ? "LEGACY_DP10_BUSINESS_ITEM_SKIPPED"
                                        : lastBusinessFailure,
                                "单条 1688 业务数据不满足事实合同，已跳过，其余数据已继续。",
                                checkpoint, false, false);
                    }
                    return;
                }
                syncTasks.updateSyncTaskCheckpoint(
                        task.getId(), checkpoint, page.getProgressPercent(),
                        processed, imported, failed);
                cursor = page.getNextCursor();
            }
        } catch (RuntimeException systemFailure) {
            finishFailure(
                    task.getId(), processed, imported, failed + 1,
                    "LEGACY_DP10_SYSTEM_FAILURE",
                    systemFailure.getClass().getSimpleName(),
                    checkpoint(cursor), true, false);
        }
    }

    private void finishFailure(
            Long taskId,
            int processed,
            int imported,
            int failed,
            String code,
            String message,
            String checkpoint,
            boolean retryable,
            boolean manual
    ) {
        if (processed == 0 && imported == 0) {
            syncTasks.markSyncTaskFailed(
                    taskId, processed, imported, failed, code, message,
                    checkpoint, retryable, manual);
        } else {
            syncTasks.markSyncTaskPartialSuccess(
                    taskId, processed, imported, failed, code, message,
                    checkpoint, retryable, manual);
        }
    }

    private String checkpoint(String nextCursor) {
        if (nextCursor == null || nextCursor.isBlank()) return "{\"nextCursor\":null}";
        return "{\"nextCursor\":\"" + nextCursor.replace("\"", "\\\"") + "\"}";
    }

    private String shrink(String value) {
        if (value == null) return null;
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
