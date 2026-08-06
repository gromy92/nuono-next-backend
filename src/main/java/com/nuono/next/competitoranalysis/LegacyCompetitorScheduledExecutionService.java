package com.nuono.next.competitoranalysis;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Isolates predecessor DP-08 scheduling and recovery from runtime/manual task consumers. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
class LegacyCompetitorScheduledExecutionService {
    private static final int SCOPE_PAGE_SIZE = 100;
    private static final int PRODUCT_PAGE_SIZE = 500;
    private static final int RECOVERY_PAGE_SIZE = 1000;
    private static final int MAX_RECOVERY_PAGES = 10;
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final String RUNNING_MESSAGE = "竞品刷新正在后台执行。";
    private final CompetitorMonitoringMapper monitoringMapper;
    private final CompetitorAnalysisMapper analysisMapper;
    private final OperationalTaskService tasks;
    private final CompetitorRefreshTaskFactory taskFactory;
    private final LegacyCompetitorScheduledTaskFactory scheduledTaskFactory;
    private final CompetitorRefreshTaskLocator taskLocator;
    private final CompetitorRefreshTaskDispatcher dispatcher;
    private final CompetitorAnalysisRefreshService refreshService;
    private final Clock clock;

    LegacyCompetitorScheduledExecutionService(
            CompetitorMonitoringMapper monitoringMapper,
            CompetitorAnalysisMapper analysisMapper,
            OperationalTaskService tasks,
            CompetitorRefreshTaskFactory taskFactory,
            LegacyCompetitorScheduledTaskFactory scheduledTaskFactory,
            CompetitorRefreshExecutionFinalizer finalizer,
            CompetitorAnalysisRefreshService refreshService,
            ObjectProvider<NoonAccountTaskQueue> queueProvider
    ) {
        this.monitoringMapper = monitoringMapper;
        this.analysisMapper = analysisMapper;
        this.tasks = tasks;
        this.taskFactory = taskFactory;
        this.scheduledTaskFactory = scheduledTaskFactory;
        this.taskLocator = new CompetitorRefreshTaskLocator(tasks);
        NoonAccountTaskQueue queue = queueProvider.getIfAvailable();
        CompetitorTaskSubmitter submitter = queue == null
                ? (accountKey, work) -> work.run()
                : queue::submit;
        this.dispatcher = new CompetitorRefreshTaskDispatcher(
                analysisMapper, tasks, submitter, finalizer);
        this.refreshService = refreshService;
        this.clock = Clock.systemUTC();
    }

    int runScheduledRankCycle() {
        return runScheduledCycle(CompetitorRefreshExecutionMode.SCHEDULED_RANK);
    }

    int runScheduledDetailCycle() {
        return runScheduledCycle(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL);
    }

    int retryRecentTransientRankKeywordFailures(Duration lookback, int limit) {
        return 0;
    }

    int resumeQueuedRefreshTasks() {
        int resumed = 0;
        long afterTaskId = 0L;
        for (int page = 0; page < MAX_RECOVERY_PAGES; page++) {
            List<OperationalTask> active = tasks.listActiveAfter(
                    CompetitorAnalysisRefreshService.TASK_TYPE,
                    afterTaskId,
                    RECOVERY_PAGE_SIZE
            );
            if (active.isEmpty()) break;
            for (OperationalTask task : active) {
                afterTaskId = task.getId();
                if (task.getStatus() != OperationalTaskStatus.QUEUED) continue;
                if (dispatch(task)) resumed++;
            }
        }
        return resumed;
    }

    int recoverStaleRefreshTasks() {
        int recovered = 0;
        long afterTaskId = 0L;
        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(STALE_AFTER);
        for (int page = 0; page < MAX_RECOVERY_PAGES; page++) {
            List<OperationalTask> active = tasks.listActiveAfter(
                    CompetitorAnalysisRefreshService.TASK_TYPE,
                    afterTaskId,
                    RECOVERY_PAGE_SIZE
            );
            if (active.isEmpty()) break;
            for (OperationalTask task : active) {
                afterTaskId = task.getId();
                if (task.getStatus() != OperationalTaskStatus.RUNNING
                        || !isStale(task, staleBefore)) continue;
                CompetitorSearchRunRow run = analysisMapper.selectSearchRunByTaskId(task.getId());
                CompetitorRefreshExecutionMode mode = scheduledMode(run);
                CompetitorWatchProductRow product = run == null
                        ? null
                        : analysisMapper.selectWatchProductForRefresh(run.getWatchProductId());
                if (mode == null || product == null) continue;
                String batchKey = CompetitorRefreshRecoveryPayload.batchKey(task);
                if (taskFactory.failStale(
                        task, run, staleBefore, "FAILED_STALE",
                        "刷新任务超过 30 分钟未完成，已由 LEGACY 调度重新排队。"
                ) && enqueue(product, mode, batchKey)) {
                    recovered++;
                }
            }
        }
        return recovered;
    }

    private int runScheduledCycle(CompetitorRefreshExecutionMode mode) {
        CompetitorMonitoringBoundaryRow boundary =
                monitoringMapper.selectRefreshableScopeBoundary();
        if (boundary == null || boundary.getUpperWatchProductId() == null
                || boundary.getEligibleTotal() == null
                || boundary.getEligibleTotal() <= 0L) return 0;
        CompetitorWatchProductScopeRow upper =
                monitoringMapper.selectRefreshableScopeUpperBound(
                        boundary.getUpperWatchProductId());
        if (upper == null) return 0;
        String batchKey = UUID.randomUUID().toString();
        Long afterOwner = null;
        String afterStore = null;
        String afterSite = null;
        int submitted = 0;
        while (true) {
            List<CompetitorWatchProductScopeRow> scopes =
                    monitoringMapper.listRefreshableWatchProductScopes(
                            boundary.getUpperWatchProductId(), afterOwner, afterStore, afterSite,
                            upper.getOwnerUserId(), upper.getStoreCode(), upper.getSiteCode(),
                            SCOPE_PAGE_SIZE);
            if (scopes.isEmpty()) return submitted;
            for (CompetitorWatchProductScopeRow scope : scopes) {
                submitted += enqueueScope(scope, boundary.getUpperWatchProductId(), mode, batchKey);
                afterOwner = scope.getOwnerUserId();
                afterStore = scope.getStoreCode();
                afterSite = scope.getSiteCode();
            }
        }
    }

    private int enqueueScope(
            CompetitorWatchProductScopeRow scope,
            Long upperWatchProductId,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        long afterWatchProductId = 0L;
        int submitted = 0;
        while (true) {
            List<CompetitorWatchProductRow> products =
                    monitoringMapper.listRefreshableWatchProducts(
                            scope.getOwnerUserId(), scope.getStoreCode(), scope.getSiteCode(),
                            afterWatchProductId, upperWatchProductId, PRODUCT_PAGE_SIZE);
            if (products.isEmpty()) return submitted;
            for (CompetitorWatchProductRow product : products) {
                if (enqueue(product, mode, batchKey)) submitted++;
                afterWatchProductId = product.getId();
            }
        }
    }

    private boolean enqueue(
            CompetitorWatchProductRow product,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        CompetitorRefreshTaskLocator.Keys keys = taskLocator.keys(
                product.getId(), mode, batchKey);
        OperationalTask active = taskLocator.active(keys, batchKey);
        if (active != null) return active.getStatus() == OperationalTaskStatus.QUEUED
                && dispatch(active);
        int keywordTotal = mode.runsRank()
                ? analysisMapper.listActiveKeywordsByWatchProductId(product.getId()).size()
                : 0;
        if (mode.runsRank() && keywordTotal == 0) return false;
        OperationalTask task = scheduledTaskFactory.persist(
                product, mode, keys.current, batchKey, keywordTotal);
        return dispatch(task);
    }

    private boolean dispatch(OperationalTask task) {
        CompetitorSearchRunRow run = task == null
                ? null
                : analysisMapper.selectSearchRunByTaskId(task.getId());
        CompetitorRefreshExecutionMode mode = scheduledMode(run);
        CompetitorWatchProductRow product = run == null
                ? null
                : analysisMapper.selectWatchProductForRefresh(run.getWatchProductId());
        if (mode == null || product == null) return false;
        try {
            CompetitorRefreshRecoveryIdentity.validate(task, run, product, mode);
        } catch (RuntimeException invalidIdentity) {
            return false;
        }
        return dispatcher.submit(
                product.getOwnerUserId() + "::" + product.getStoreCode(),
                task, run, RUNNING_MESSAGE,
                () -> refreshService.runLegacyScheduledRefresh(
                        task.getId(), run.getId(), product.getId(), mode)
        );
    }

    private CompetitorRefreshExecutionMode scheduledMode(CompetitorSearchRunRow run) {
        if (run == null) return null;
        try {
            CompetitorRefreshExecutionMode mode =
                    CompetitorRefreshExecutionMode.strictFromTriggerMode(run.getTriggerMode());
            return mode.isManual() ? null : mode;
        } catch (RuntimeException invalidMode) {
            return null;
        }
    }

    private boolean isStale(OperationalTask task, LocalDateTime staleBefore) {
        LocalDateTime updatedAt = task.getUpdatedAt() == null
                ? task.getStartedAt()
                : task.getUpdatedAt();
        return updatedAt != null && !updatedAt.isAfter(staleBefore);
    }
}
