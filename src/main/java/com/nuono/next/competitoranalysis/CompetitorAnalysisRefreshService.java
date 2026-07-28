package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompetitorAnalysisRefreshService {
    public static final String TASK_TYPE = "OPERATIONS_COMPETITOR_REFRESH";
    public static final String MONITOR_TASK_TYPE = CompetitorMonitoringBatchService.STORE_TASK_TYPE;
    public static final String MONITOR_CYCLE_TASK_TYPE = CompetitorMonitoringBatchService.CYCLE_TASK_TYPE;

    private static final Logger log = LoggerFactory.getLogger(CompetitorAnalysisRefreshService.class);
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final String RUNNING_MESSAGE = "竞品刷新正在后台执行。";
    private static final String FAILED_MESSAGE = "竞品刷新失败，请稍后重试。";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    private static final String COMPETITOR_RISK_BACKOFF = "COMPETITOR_RISK_BACKOFF";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorTaskSubmitter taskSubmitter;
    private final CompetitorRefreshTaskDispatcher refreshTaskDispatcher;
    private final CompetitorRefreshTaskFactory refreshTaskFactory;
    private final CompetitorRefreshRecoveryCoordinator refreshRecoveryCoordinator;
    private final CompetitorAnalysisTaskRecovery taskRecovery;
    private final CompetitorMonitoringBatchService monitoringBatchService;
    private final CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner;
    private final CompetitorProductDetailRefreshService productDetailRefreshService;
    private final CompetitorRiskBackoffSupport riskBackoff;
    private final CompetitorDetailRetryCoordinator detailRetryCoordinator;
    private CompetitorRefreshExecutionFinalizer executionFinalizer;
    private final Clock clock;
    @Autowired
    public CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            ObjectProvider<NoonAccountTaskQueue> noonAccountTaskQueueProvider,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            ObjectProvider<CompetitorProductDetailRefreshService> productDetailRefreshServiceProvider,
            ObjectProvider<NoonRiskBackoffGuard> riskBackoffGuardProvider,
            CompetitorRefreshTaskFactory refreshTaskFactory,
            CompetitorMonitoringRecoveryService monitoringRecoveryService,
            CompetitorMonitoringTaskExecutor monitoringTaskExecutor,
            CompetitorRefreshExecutionFinalizer executionFinalizer
    ) {
        this(
                mapper,
                monitoringMapper,
                operationalTaskService,
                queueSubmitter(noonAccountTaskQueueProvider == null ? null : noonAccountTaskQueueProvider.getIfAvailable()),
                keywordRefreshRunner,
                productDetailRefreshServiceProvider == null ? null : productDetailRefreshServiceProvider.getIfAvailable(),
                Clock.systemUTC(),
                riskBackoffGuardProvider == null
                        ? NoonRiskBackoffGuard.disabled()
                        : riskBackoffGuardProvider.getIfAvailable(NoonRiskBackoffGuard::disabled),
                refreshTaskFactory,
                monitoringRecoveryService,
                monitoringTaskExecutor
        );
        this.executionFinalizer = executionFinalizer;
    }
    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            Clock clock
    ) {
        this(
                mapper,
                monitoringMapper,
                operationalTaskService,
                taskSubmitter,
                new CompetitorKeywordRefreshTransactionRunner(mapper, new NoopCompetitorKeywordRefreshRunner()),
                null,
                clock,
                NoonRiskBackoffGuard.disabled()
        );
    }
    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            Clock clock
    ) {
        this(
                mapper,
                monitoringMapper,
                operationalTaskService,
                taskSubmitter,
                keywordRefreshRunner,
                null,
                clock,
                NoonRiskBackoffGuard.disabled()
        );
    }
    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            CompetitorProductDetailRefreshService productDetailRefreshService,
            Clock clock
    ) {
        this(
                mapper,
                monitoringMapper,
                operationalTaskService,
                taskSubmitter,
                keywordRefreshRunner,
                productDetailRefreshService,
                clock,
                NoonRiskBackoffGuard.disabled()
        );
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            CompetitorProductDetailRefreshService productDetailRefreshService,
            Clock clock,
            NoonRiskBackoffGuard riskBackoffGuard
    ) {
        this(
                mapper,
                monitoringMapper,
                operationalTaskService,
                taskSubmitter,
                keywordRefreshRunner,
                productDetailRefreshService,
                clock,
                riskBackoffGuard,
                new CompetitorRefreshTaskFactory(mapper, operationalTaskService),
                new CompetitorMonitoringRecoveryService(operationalTaskService)
        );
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            CompetitorProductDetailRefreshService productDetailRefreshService,
            Clock clock,
            NoonRiskBackoffGuard riskBackoffGuard,
            CompetitorRefreshTaskFactory refreshTaskFactory
    ) {
        this(
                mapper,
                monitoringMapper,
                operationalTaskService,
                taskSubmitter,
                keywordRefreshRunner,
                productDetailRefreshService,
                clock,
                riskBackoffGuard,
                refreshTaskFactory,
                new CompetitorMonitoringRecoveryService(operationalTaskService)
        );
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            CompetitorProductDetailRefreshService productDetailRefreshService,
            Clock clock,
            NoonRiskBackoffGuard riskBackoffGuard,
            CompetitorRefreshTaskFactory refreshTaskFactory,
            CompetitorMonitoringRecoveryService monitoringRecoveryService
    ) {
        this(
                mapper,
                monitoringMapper,
                operationalTaskService,
                taskSubmitter,
                keywordRefreshRunner,
                productDetailRefreshService,
                clock,
                riskBackoffGuard,
                refreshTaskFactory,
                monitoringRecoveryService,
                taskSubmitter
        );
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            CompetitorMonitoringMapper monitoringMapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            CompetitorProductDetailRefreshService productDetailRefreshService,
            Clock clock,
            NoonRiskBackoffGuard riskBackoffGuard,
            CompetitorRefreshTaskFactory refreshTaskFactory,
            CompetitorMonitoringRecoveryService monitoringRecoveryService,
            CompetitorTaskSubmitter monitoringTaskSubmitter
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.taskSubmitter = taskSubmitter == null ? (accountKey, task) -> task.run() : taskSubmitter;
        this.keywordRefreshRunner = keywordRefreshRunner;
        this.productDetailRefreshService = productDetailRefreshService;
        this.riskBackoff = new CompetitorRiskBackoffSupport(riskBackoffGuard);
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.refreshTaskFactory = refreshTaskFactory;
        this.executionFinalizer =
                CompetitorRefreshExecutionFinalizer.unfenced(
                        mapper, operationalTaskService
                );
        this.detailRetryCoordinator = new CompetitorDetailRetryCoordinator(refreshTaskFactory, this.clock);
        this.refreshTaskDispatcher = new CompetitorRefreshTaskDispatcher(
                mapper,
                operationalTaskService,
                this.taskSubmitter
        );
        this.refreshRecoveryCoordinator = new CompetitorRefreshRecoveryCoordinator(
                mapper,
                operationalTaskService,
                refreshTaskFactory,
                refreshTaskDispatcher,
                (task, watchProduct) -> detailRetryCoordinator.isReady(task)
                        && riskBackoff.current(watchProduct).isEmpty(),
                this::runRefresh, this.clock
        );
        this.taskRecovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                this.clock,
                refreshRecoveryCoordinator::resubmitQueued,
                refreshRecoveryCoordinator::recoverInterrupted,
                () -> refreshTaskDispatcher.availableCapacity(1000)
        );
        this.monitoringBatchService = new CompetitorMonitoringBatchService(
                monitoringMapper,
                operationalTaskService,
                monitoringRecoveryService,
                monitoringTaskSubmitter == null ? this.taskSubmitter : monitoringTaskSubmitter,
                this::enqueueMonitoringRefresh,
                taskRecovery::resumeQueuedRefreshTasks,
                this.clock
        );
    }

    public CompetitorRefreshRunView requestRefresh(BusinessAccessContext context, Long watchProductId) {
        CompetitorWatchProductRow watchProduct = requireWatchProduct(context, watchProductId);
        return requestRefreshForWatchProduct(
                watchProduct,
                actorUserId(context),
                CompetitorRefreshExecutionMode.FULL_MANUAL
        );
    }

    public CompetitorTaskView requestStoreMonitoring(
            BusinessAccessContext context,
            String storeCode,
            String siteCode
    ) {
        String normalizedStoreCode = normalizeRequired(storeCode, "COMPETITOR_STORE_REQUIRED");
        String normalizedSiteCode = normalizeRequired(siteCode, "COMPETITOR_SITE_REQUIRED");
        requireStoreInContext(context, normalizedStoreCode);
        Long ownerUserId = context == null ? null : context.getBusinessOwnerUserId();
        if (ownerUserId == null) {
            throw badRequest("COMPETITOR_OWNER_REQUIRED");
        }
        riskBackoff.rejectActive(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        return monitoringBatchService.requestStore(
                ownerUserId,
                normalizedStoreCode,
                normalizedSiteCode,
                actorUserId(context),
                CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR
        );
    }

    public CompetitorTaskView requestScheduledStoreMonitoring(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        return requestScheduledRankMonitoring(ownerUserId, storeCode, siteCode);
    }

    public CompetitorTaskView requestScheduledRankMonitoring(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        Long normalizedOwnerUserId = ownerUserId;
        if (normalizedOwnerUserId == null) {
            throw badRequest("COMPETITOR_OWNER_REQUIRED");
        }
        String normalizedStoreCode = normalizeRequired(storeCode, "COMPETITOR_STORE_REQUIRED");
        String normalizedSiteCode = normalizeRequired(siteCode, "COMPETITOR_SITE_REQUIRED");
        riskBackoff.rejectActive(normalizedOwnerUserId, normalizedStoreCode, normalizedSiteCode);
        return monitoringBatchService.requestStore(
                normalizedOwnerUserId,
                normalizedStoreCode,
                normalizedSiteCode,
                null,
                CompetitorRefreshExecutionMode.SCHEDULED_RANK
        );
    }

    public CompetitorTaskView requestScheduledDetailMonitoring(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        Long normalizedOwnerUserId = ownerUserId;
        if (normalizedOwnerUserId == null) {
            throw badRequest("COMPETITOR_OWNER_REQUIRED");
        }
        String normalizedStoreCode = normalizeRequired(storeCode, "COMPETITOR_STORE_REQUIRED");
        String normalizedSiteCode = normalizeRequired(siteCode, "COMPETITOR_SITE_REQUIRED");
        riskBackoff.rejectActive(normalizedOwnerUserId, normalizedStoreCode, normalizedSiteCode);
        return monitoringBatchService.requestStore(
                normalizedOwnerUserId,
                normalizedStoreCode,
                normalizedSiteCode,
                null,
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
        );
    }

    public int runScheduledRankCycle() {
        return monitoringBatchService.runScheduledCycle(CompetitorRefreshExecutionMode.SCHEDULED_RANK);
    }

    public int runScheduledDetailCycle() {
        return monitoringBatchService.runScheduledCycle(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL);
    }

    public int recoverStaleRefreshTasks() {
        return monitoringBatchService.recoverStaleBatches() + taskRecovery.recoverStaleRefreshTasks();
    }

    public int resumeQueuedRefreshTasks() {
        return monitoringBatchService.resumeQueuedBatches() + taskRecovery.resumeQueuedRefreshTasks();
    }

    public int retryRecentTransientRankKeywordFailures(Duration lookback, int limit) {
        Duration safeLookback = lookback == null || lookback.isNegative() || lookback.isZero()
                ? Duration.ofHours(24)
                : lookback;
        int safeLimit = Math.max(1, limit);
        LocalDateTime sinceTime = LocalDateTime.now(clock).minus(safeLookback);
        List<CompetitorTransientKeywordFailureRow> failures = mapper.listRetryableTransientRankKeywordFailures(
                sinceTime,
                safeLimit
        );
        int recovered = 0;
        for (CompetitorTransientKeywordFailureRow failure : failures) {
            if (retryTransientRankKeywordFailure(failure)) {
                recovered++;
            }
        }
        return recovered;
    }

    private boolean retryTransientRankKeywordFailure(CompetitorTransientKeywordFailureRow failure) {
        if (failure == null || failure.getSearchRunId() == null || failure.getWatchProductId() == null || failure.getKeywordId() == null) {
            return false;
        }
        CompetitorSearchRunRow run = mapper.selectSearchRunById(failure.getSearchRunId());
        if (run == null || !"PARTIAL_FAILED".equals(run.getStatus())) {
            return false;
        }
        CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(failure.getWatchProductId());
        CompetitorKeywordRow keyword = mapper.selectKeywordById(failure.getKeywordId());
        if (watchProduct == null || keyword == null || !"ACTIVE".equals(keyword.getStatus())) {
            return false;
        }
        CompetitorKeywordRefreshResult retryResult = keywordRefreshRunner.runKeyword(
                run.getId(),
                watchProduct,
                keyword,
                run.getRequestedBy()
        );
        if (!retryResult.isSuccess()) {
            return false;
        }
        int success = nullToZero(run.getKeywordSuccess()) + 1;
        int failed = Math.max(0, nullToZero(run.getKeywordFailed()) - 1);
        int candidateUpsertedCount = nullToZero(run.getCandidateUpsertedCount())
                + retryResult.getCandidateUpsertedCount();
        int rankFactWrittenCount = nullToZero(run.getRankFactWrittenCount())
                + retryResult.getRankFactWrittenCount();
        String status = resolveRunStatus(success, failed);
        mapper.completeSearchRun(
                run.getId(),
                status,
                success,
                failed,
                candidateUpsertedCount,
                rankFactWrittenCount,
                failed > 0 ? run.getErrorCode() : null,
                failed > 0 ? truncateMessage(run.getErrorMessage()) : null,
                run.getRequestedBy()
        );
        mapper.updateWatchProductLatestRun(watchProduct.getId(), run.getId(), status, run.getRequestedBy());
        return true;
    }

    private CompetitorRefreshRunView requestRefreshForWatchProduct(
            CompetitorWatchProductRow watchProduct, Long actorUserId,
            CompetitorRefreshExecutionMode executionMode
    ) {
        return queueRefreshForWatchProduct(
                watchProduct, actorUserId, executionMode, null, true, null
        ).getView();
    }

    private CompetitorMonitoringEnqueueOutcome enqueueMonitoringRefresh(
            CompetitorWatchProductRow watchProduct, Long actorUserId,
            CompetitorRefreshExecutionMode executionMode,
            String batchKey
    ) {
        return queueRefreshForWatchProduct(
                watchProduct, actorUserId, executionMode, batchKey, false, null
        ).getOutcome();
    }

    private CompetitorQueuedRefresh queueRefreshForWatchProduct(
            CompetitorWatchProductRow watchProduct, Long actorUserId,
            CompetitorRefreshExecutionMode executionMode, String batchKey,
            boolean dispatchNow, String payloadJsonOverride
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        String naturalKey = CompetitorRefreshNaturalKey.product(
                watchProduct.getId(), safeMode, batchKey
        );
        OperationalTask activeTask = operationalTaskService.findActive(TASK_TYPE, naturalKey).orElse(null);
        if (activeTask != null && !isStale(activeTask)) {
            return existingRefresh(activeTask, batchKey);
        }
        if (activeTask != null) {
            if (StringUtils.hasText(batchKey)) {
                return existingRefresh(activeTask, batchKey);
            }
        }
        if (StringUtils.hasText(batchKey)) {
            OperationalTask latestTask = operationalTaskService
                    .findLatestByBatchKey(TASK_TYPE, naturalKey, batchKey).orElse(null);
            if (latestTask != null && payloadHasBatchKey(latestTask, batchKey)) {
                return new CompetitorQueuedRefresh(
                        CompetitorRefreshRunView.from(
                                latestTask,
                                mapper.selectSearchRunByTaskId(latestTask.getId())
                        ),
                        CompetitorMonitoringEnqueueOutcome.REUSED_SAME_BATCH
                );
            }
        }
        if (dispatchNow) {
            riskBackoff.rejectActive(
                    watchProduct.getOwnerUserId(),
                    watchProduct.getStoreCode(),
                    watchProduct.getSiteCode()
            );
        }

        List<CompetitorKeywordRow> activeKeywords = safeMode.runsRank()
                ? mapper.listActiveKeywordsByWatchProductId(watchProduct.getId())
                : List.of();
        if (safeMode.runsRank() && activeKeywords.isEmpty()) {
            throw badRequest("COMPETITOR_NO_ACTIVE_KEYWORD");
        }
        int keywordTotal = activeKeywords.size();
        if (activeTask != null) {
            CompetitorSearchRunRow staleRun = mapper.selectSearchRunByTaskId(activeTask.getId());
            CompetitorQueuedRefresh replacement = refreshRecoveryCoordinator.replaceManualStale(
                    activeTask,
                    staleRun,
                    watchProduct,
                    LocalDateTime.now(clock).minus(STALE_AFTER),
                    actorUserId,
                    safeMode,
                    batchKey,
                    keywordTotal
            );
            if (replacement != null && replacement.getOutcome()
                    != CompetitorMonitoringEnqueueOutcome.STALE_TERMINAL_RECONCILED) {
                return replacement;
            }
            OperationalTask currentTask =
                    operationalTaskService.findActive(TASK_TYPE, naturalKey).orElse(null);
            if (currentTask != null) {
                return existingRefresh(currentTask, batchKey);
            }
        }
        CompetitorQueuedRefresh queued = refreshTaskFactory.persistQueued(
                watchProduct,
                actorUserId,
                safeMode,
                naturalKey,
                batchKey,
                keywordTotal,
                payloadJsonOverride
        );
        if (dispatchNow && queued.getOutcome() != CompetitorMonitoringEnqueueOutcome.DEFERRED_ACTIVE) {
            refreshRecoveryCoordinator.dispatchQueued(queued, watchProduct, actorUserId, safeMode);
        }
        return queued;
    }

    private CompetitorQueuedRefresh existingRefresh(OperationalTask task, String batchKey) {
        CompetitorMonitoringEnqueueOutcome outcome = !StringUtils.hasText(batchKey)
                || payloadHasBatchKey(task, batchKey)
                ? CompetitorMonitoringEnqueueOutcome.REUSED_SAME_BATCH
                : CompetitorMonitoringEnqueueOutcome.DEFERRED_ACTIVE;
        return new CompetitorQueuedRefresh(
                CompetitorRefreshRunView.from(task, mapper.selectSearchRunByTaskId(task.getId())),
                outcome
        );
    }

    public CompetitorRefreshRunView getRefreshRun(BusinessAccessContext context, Long runId) {
        if (runId == null) {
            throw badRequest("COMPETITOR_RUN_REQUIRED");
        }
        CompetitorSearchRunRow run = mapper.selectSearchRunById(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_RUN_NOT_FOUND");
        }
        requireWatchProduct(context, run.getWatchProductId());
        OperationalTask task = run.getTaskId() == null
                ? null
                : operationalTaskService.find(run.getTaskId()).orElse(null);
        return CompetitorRefreshRunView.from(task, run);
    }

    public CompetitorTaskView getTask(BusinessAccessContext context, Long taskId) {
        if (taskId == null) {
            throw badRequest("COMPETITOR_TASK_REQUIRED");
        }
        OperationalTask task = operationalTaskService.find(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_TASK_NOT_FOUND"));
        if (!TASK_TYPE.equals(task.getTaskType()) && !MONITOR_TASK_TYPE.equals(task.getTaskType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_TASK_NOT_FOUND");
        }
        requireStoreInContext(context, task.getStoreCode());
        return CompetitorTaskView.from(task);
    }

    private void runRefresh(
            Long taskId,
            Long runId,
            Long watchProductId,
            Long actorUserId,
            CompetitorRefreshExecutionMode executionMode
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        int success = 0;
        int failed = 0;
        int candidateUpsertedCount = 0;
        int rankFactWrittenCount = 0;
        String firstErrorCode = null;
        String firstErrorMessage = null;
        try {
            executionFinalizer.progress(
                    taskId, runId, watchProductId, 5, RUNNING_MESSAGE
            );
            OperationalTask runningTask = operationalTaskService.find(taskId).orElse(null);
            CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(watchProductId);
            if (watchProduct == null) {
                throw new IllegalStateException("监控商品不存在或已删除。");
            }
            Optional<NoonRiskBackoffHold> activeRiskBackoff = riskBackoff.current(watchProduct);
            if (activeRiskBackoff.isPresent()) {
                String message = riskBackoff.message(activeRiskBackoff.get());
                if (safeMode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL && runningTask != null) {
                    detailRetryCoordinator.parkForRiskHold(
                            runningTask,
                            runId,
                            activeRiskBackoff.get(),
                            message
                    );
                    return;
                }
                executionFinalizer.fail(
                        taskId, runId, watchProductId,
                        COMPETITOR_RISK_BACKOFF, message, actorUserId
                );
                return;
            }
            CompetitorProductDetailRefreshResult detailResult = safeMode.runsDetail()
                    ? refreshConfirmedCompetitorDetails(
                            taskId,
                            runId,
                            watchProduct,
                            actorUserId,
                            runningTask,
                            safeMode
                    )
                    : CompetitorProductDetailRefreshResult.empty();
            NoonRiskBackoffHold riskBackoffHold = null;
            if (detailResult.getFailedCount() > 0) {
                firstErrorCode = detailResult.getFirstErrorCode();
                firstErrorMessage = detailResult.getFirstErrorMessage();
                if (detailResult.hasRiskBackoffFailure()) {
                    String riskErrorCode = detailResult.getRiskErrorCode();
                    String riskErrorMessage = detailResult.getRiskErrorMessage();
                    firstErrorCode = riskErrorCode;
                    firstErrorMessage = riskErrorMessage;
                    riskBackoffHold = executionFinalizer.withLease(
                            taskId, runId, watchProductId,
                            () -> riskBackoff.record(
                                    watchProduct, taskId,
                                    riskErrorCode, riskErrorMessage
                            )
                    );
                }
            }
            if (safeMode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                    && runningTask != null
                    && detailRetryCoordinator.scheduleFailure(
                            runningTask,
                            runId,
                            detailResult,
                            firstErrorCode,
                            firstErrorMessage,
                            riskBackoffHold
                    )) {
                return;
            }
            if (safeMode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL && runningTask != null) {
                detailRetryCoordinator.addPriorCounts(runningTask, detailResult);
                firstErrorCode = firstNonBlank(firstErrorCode, detailResult.getFirstErrorCode());
                firstErrorMessage = firstNonBlank(firstErrorMessage, detailResult.getFirstErrorMessage());
            }
            List<CompetitorKeywordRow> keywords = safeMode.runsRank() && riskBackoffHold == null
                    ? mapper.listActiveKeywordsByWatchProductId(watchProductId)
                    : List.of();
            int total = keywords.size();
            List<KeywordRetryCandidate> retryCandidates = new ArrayList<>();
            int keywordRetried = 0;
            int keywordRetryRecovered = 0;
            for (CompetitorKeywordRow keyword : keywords) {
                CompetitorKeywordRefreshResult result = keywordRefreshRunner.runKeyword(
                        taskId, runId, watchProduct, keyword, actorUserId
                );
                if (result.isSuccess()) {
                    success++;
                    candidateUpsertedCount += result.getCandidateUpsertedCount();
                    rankFactWrittenCount += result.getRankFactWrittenCount();
                } else {
                    failed++;
                    if (riskBackoff.isRiskFailure(result.getErrorCode())) {
                        riskBackoffHold = executionFinalizer.withLease(
                                taskId, runId, watchProductId,
                                () -> riskBackoff.record(
                                        watchProduct, taskId,
                                        result.getErrorCode(),
                                        result.getErrorMessage()
                                )
                        );
                        firstErrorCode = firstNonBlank(firstErrorCode, result.getErrorCode());
                        firstErrorMessage = firstNonBlank(firstErrorMessage, result.getErrorMessage());
                        updateProgress(
                                taskId, runId, watchProductId,
                                total, success + failed
                        );
                        break;
                    }
                    if (shouldRetryTransientKeywordFailure(safeMode, result)) {
                        retryCandidates.add(new KeywordRetryCandidate(keyword, result));
                    } else {
                        firstErrorCode = firstNonBlank(firstErrorCode, result.getErrorCode());
                        firstErrorMessage = firstNonBlank(firstErrorMessage, result.getErrorMessage());
                    }
                }
                updateProgress(
                        taskId, runId, watchProductId,
                        total, success + failed
                );
            }

            for (KeywordRetryCandidate retryCandidate : retryCandidates) {
                keywordRetried++;
                CompetitorKeywordRefreshResult retryResult = keywordRefreshRunner.runKeyword(
                        taskId,
                        runId,
                        watchProduct,
                        retryCandidate.keyword,
                        actorUserId
                );
                if (retryResult.isSuccess()) {
                    failed--;
                    success++;
                    keywordRetryRecovered++;
                    candidateUpsertedCount += retryResult.getCandidateUpsertedCount();
                    rankFactWrittenCount += retryResult.getRankFactWrittenCount();
                    continue;
                }
                firstErrorCode = firstNonBlank(
                        firstErrorCode,
                        firstNonBlank(retryResult.getErrorCode(), retryCandidate.firstFailure.getErrorCode())
                );
                firstErrorMessage = firstNonBlank(
                        firstErrorMessage,
                        firstNonBlank(retryResult.getErrorMessage(), retryCandidate.firstFailure.getErrorMessage())
                );
            }

            String status = CompetitorRefreshRunResultSupport.status(success, failed, detailResult);
            String message = resolveRunMessage(safeMode, status, success, failed, detailResult);
            String runResultJson = CompetitorRefreshRunResultSupport.resultJson(
                    safeMode,
                    status,
                    success,
                    failed,
                    detailResult,
                    keywordRetried,
                    keywordRetryRecovered
            );
            String taskErrorCode = riskBackoffHold != null
                    ? COMPETITOR_RISK_BACKOFF
                    : "FAILED".equals(status)
                    || (safeMode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                            && !"SUCCEEDED".equals(status))
                            ? firstNonBlank(firstErrorCode, "REFRESH_FAILED")
                            : null;
            String taskMessage = riskBackoffHold == null
                    ? message
                    : riskBackoff.message(riskBackoffHold);
            if ("SUCCEEDED".equals(status)) {
                executionFinalizer.withLease(taskId, runId, watchProductId,
                        () -> riskBackoff.recordSuccess(watchProduct));
            }
            executionFinalizer.complete(
                    taskId,
                    runId,
                    watchProductId,
                    CompetitorRefreshCompletion.create(
                            status, success, failed, candidateUpsertedCount,
                            rankFactWrittenCount, firstErrorCode,
                            truncateMessage(firstErrorMessage),
                            actorUserId, taskErrorCode,
                            runResultJson, taskMessage
                    )
            );
        } catch (CompetitorRefreshLeaseLostException exception) {
            log.info(
                    "competitor refresh stopped after lease loss taskId={} runId={}",
                    taskId,
                    runId
            );
        } catch (RuntimeException exception) {
            String message = truncateMessage(firstNonBlank(exception.getMessage(), FAILED_MESSAGE));
            if (!safeFinalizeFailure(
                    taskId, runId, watchProductId, actorUserId, message
            )) {
                return;
            }
            log.warn(
                    "competitor analysis refresh failed watchProductId={} runId={} taskId={} error={}",
                    watchProductId,
                    runId,
                    taskId,
                    exception.getMessage(),
                    exception
            );
        }
    }
    private CompetitorProductDetailRefreshResult refreshConfirmedCompetitorDetails(
            Long taskId,
            Long runId,
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            OperationalTask task,
            CompetitorRefreshExecutionMode executionMode
    ) {
        if (productDetailRefreshService == null) {
            return CompetitorProductDetailRefreshResult.empty();
        }
        CompetitorProductDetailRefreshResult result;
        if (executionMode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                && detailRetryCoordinator.isRetry(task)) {
            result = productDetailRefreshService.refreshTargets(
                    watchProduct,
                    detailRetryCoordinator.retryTargets(task),
                    runId,
                    taskId,
                    actorUserId
            );
        } else {
            result = productDetailRefreshService.refreshConfirmedCompetitors(
                    watchProduct,
                    runId,
                    taskId,
                    actorUserId
            );
        }
        return result == null ? CompetitorProductDetailRefreshResult.empty() : result;
    }

    private boolean isStale(OperationalTask task) {
        LocalDateTime updatedAt = task.getUpdatedAt();
        if (updatedAt == null) {
            updatedAt = task.getStartedAt();
        }
        if (updatedAt == null) {
            return false;
        }
        return Duration.between(updatedAt, LocalDateTime.now(clock)).compareTo(STALE_AFTER) > 0;
    }

    private void updateProgress(
            Long taskId,
            Long runId,
            Long watchProductId,
            int total,
            int finished
    ) {
        if (total <= 0) {
            return;
        }
        int progress = 5 + (int) Math.floor((finished * 90.0d) / total);
        executionFinalizer.progress(
                taskId, runId, watchProductId, progress, RUNNING_MESSAGE
        );
    }

    private CompetitorWatchProductRow requireWatchProduct(BusinessAccessContext context, Long watchProductId) {
        if (watchProductId == null) {
            throw badRequest("COMPETITOR_WATCH_PRODUCT_REQUIRED");
        }
        Long ownerUserId = context == null ? null : context.getBusinessOwnerUserId();
        CompetitorWatchProductRow watchProduct = mapper.selectWatchProductById(ownerUserId, watchProductId);
        if (watchProduct == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_WATCH_PRODUCT_NOT_FOUND");
        }
        requireStoreInContext(context, watchProduct.getStoreCode());
        return watchProduct;
    }

    private void requireStoreInContext(BusinessAccessContext context, String storeCode) {
        if (context == null || !context.canAccessStore(storeCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPETITOR_STORE_SCOPE_REQUIRED");
        }
    }

    private Long actorUserId(BusinessAccessContext context) {
        return context == null ? null : context.getSessionUserId();
    }

    private String resolveRunStatus(int success, int failed) {
        if (failed <= 0) {
            return "SUCCEEDED";
        }
        return success > 0 ? "PARTIAL_FAILED" : "FAILED";
    }

    private String resolveRunMessage(
            CompetitorRefreshExecutionMode executionMode,
            String status,
            int success,
            int failed,
            CompetitorProductDetailRefreshResult detailResult
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        if (safeMode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL) {
            if ("SUCCEEDED".equals(status)) {
                return "竞品详情快照刷新完成。";
            }
            return "竞品详情快照刷新失败。";
        }
        if (safeMode == CompetitorRefreshExecutionMode.SCHEDULED_RANK) {
            if ("SUCCEEDED".equals(status)) {
                return "竞品排名刷新完成。";
            }
            if ("PARTIAL_FAILED".equals(status)) {
                return "竞品排名部分关键词失败。";
            }
            return failed > 0 && success <= 0 ? "竞品排名刷新失败。" : FAILED_MESSAGE;
        }
        if ("PARTIAL_FAILED".equals(status) && detailResult != null && detailResult.getFailedCount() > 0) {
            return "竞品刷新部分详情失败。";
        }
        if ("SUCCEEDED".equals(status)) {
            return "竞品刷新完成。";
        }
        if ("PARTIAL_FAILED".equals(status)) {
            return "竞品刷新部分关键词失败。";
        }
        return failed > 0 && success <= 0 ? "竞品刷新失败。" : FAILED_MESSAGE;
    }

    private boolean payloadHasBatchKey(OperationalTask task, String batchKey) {
        return task != null
                && StringUtils.hasText(batchKey)
                && StringUtils.hasText(task.getPayloadJson())
                && task.getPayloadJson().contains("\"batchKey\":\"" + json(batchKey) + "\"");
    }

    private String resultJson(
            CompetitorRefreshExecutionMode executionMode,
            String status,
            int success,
            int failed,
            int keywordRetried,
            int keywordRetryRecovered
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        return "{"
                + "\"status\":\"" + status + "\""
                + ",\"triggerMode\":\"" + json(safeMode.triggerMode()) + "\""
                + ",\"executionMode\":\"" + json(safeMode.taskKey()) + "\""
                + ",\"rankRefresh\":" + safeMode.runsRank()
                + ",\"detailRefresh\":" + safeMode.runsDetail()
                + ",\"keywordSuccess\":" + success
                + ",\"keywordFailed\":" + failed
                + ",\"keywordRetried\":" + keywordRetried
                + ",\"keywordRetryRecovered\":" + keywordRetryRecovered
                + "}";
    }
    private String normalizeRequired(String value, String reason) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw badRequest(reason);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean safeFinalizeFailure(
            Long taskId,
            Long runId,
            Long watchProductId,
            Long actorUserId,
            String message
    ) {
        try {
            executionFinalizer.fail(
                    taskId,
                    runId,
                    watchProductId,
                    "REFRESH_FAILED",
                    message,
                    actorUserId
            );
            return true;
        } catch (CompetitorRefreshLeaseLostException exception) {
            log.info(
                    "competitor failure finalization skipped after lease loss taskId={} runId={}",
                    taskId,
                    runId
            );
            return false;
        } catch (RuntimeException finalizationFailure) {
            log.debug(
                    "competitor failure finalization failed taskId={} runId={}",
                    taskId,
                    runId,
                    finalizationFailure
            );
            return true;
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncateMessage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean shouldRetryTransientKeywordFailure(
            CompetitorRefreshExecutionMode executionMode,
            CompetitorKeywordRefreshResult result
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        if (!safeMode.runsRank() || result == null || result.isSuccess()) {
            return false;
        }
        return PROVIDER_UNAVAILABLE.equals(normalize(result.getErrorCode()));
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static CompetitorTaskSubmitter queueSubmitter(NoonAccountTaskQueue noonAccountTaskQueue) {
        return noonAccountTaskQueue == null ? (accountKey, task) -> task.run() : noonAccountTaskQueue::submit;
    }

    private static final class KeywordRetryCandidate {
        private final CompetitorKeywordRow keyword;
        private final CompetitorKeywordRefreshResult firstFailure;

        private KeywordRetryCandidate(
                CompetitorKeywordRow keyword,
                CompetitorKeywordRefreshResult firstFailure
        ) {
            this.keyword = keyword;
            this.firstFailure = firstFailure;
        }
    }

}
