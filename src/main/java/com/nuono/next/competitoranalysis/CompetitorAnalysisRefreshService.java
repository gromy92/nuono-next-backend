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
    private final CompetitorRefreshTaskLocator refreshTaskLocator;
    private final CompetitorRefreshRecoveryCoordinator refreshRecoveryCoordinator;
    private final CompetitorAnalysisTaskRecovery taskRecovery;
    private final CompetitorMonitoringBatchService monitoringBatchService;
    private final CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner;
    private final CompetitorProductDetailRefreshService productDetailRefreshService;
    private final CompetitorDetailRetryCoordinator detailRetryCoordinator;
    private final CompetitorRiskBackoffSupport riskBackoff;
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
            CompetitorMonitoringTaskExecutor monitoringTaskExecutor
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
        this.refreshTaskLocator = new CompetitorRefreshTaskLocator(
                operationalTaskService
        );
        this.detailRetryCoordinator = new CompetitorDetailRetryCoordinator(
                refreshTaskFactory, this.clock
        );
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
                watchProduct -> riskBackoff.current(watchProduct).isEmpty(),
                detailRetryCoordinator::isReady,
                this::runRefresh, this.clock
        );
        this.taskRecovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                this.clock,
                refreshRecoveryCoordinator::resubmitQueued,
                refreshRecoveryCoordinator::recoverInterrupted,
                refreshTaskFactory.executionFinalizer(),
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
        return 0;
    }

    private CompetitorRefreshRunView requestRefreshForWatchProduct(
            CompetitorWatchProductRow watchProduct, Long actorUserId,
            CompetitorRefreshExecutionMode executionMode
    ) {
        return queueRefreshForWatchProduct(
                watchProduct, actorUserId, executionMode, null, true
        ).getView();
    }

    private CompetitorMonitoringEnqueueOutcome enqueueMonitoringRefresh(
            CompetitorWatchProductRow watchProduct, Long actorUserId,
            CompetitorRefreshExecutionMode executionMode,
            String batchKey
    ) {
        return queueRefreshForWatchProduct(
                watchProduct, actorUserId, executionMode, batchKey, false
        ).getOutcome();
    }

    private CompetitorQueuedRefresh queueRefreshForWatchProduct(
            CompetitorWatchProductRow watchProduct, Long actorUserId,
            CompetitorRefreshExecutionMode executionMode, String batchKey,
            boolean dispatchNow
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        CompetitorRefreshTaskLocator.Keys keys =
                refreshTaskLocator.keys(watchProduct.getId(), safeMode, batchKey);
        String naturalKey = keys.current;
        OperationalTask activeTask = refreshTaskLocator.active(keys, batchKey);
        if (activeTask != null && !isStale(activeTask)) {
            return existingRefresh(activeTask, batchKey);
        }
        if (activeTask != null) {
            if (StringUtils.hasText(batchKey)) {
                return existingRefresh(activeTask, batchKey);
            }
        }
        if (StringUtils.hasText(batchKey)) {
            OperationalTask latestTask = refreshTaskLocator.latest(keys, batchKey);
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
            OperationalTask currentTask = refreshTaskLocator.active(keys, batchKey);
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
                keywordTotal
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
            refreshTaskFactory.executionFinalizer().progress(
                    taskId, runId, watchProductId, 5, RUNNING_MESSAGE
            );
            OperationalTask runningTask = operationalTaskService.find(taskId)
                    .orElseThrow(() -> new CompetitorRefreshLeaseLostException(
                            taskId, runId
                    ));
            CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(watchProductId);
            if (watchProduct == null) {
                throw new IllegalStateException("监控商品不存在或已删除。");
            }
            CompetitorDetailRetrySession detailRetrySession =
                    openDetailRetrySession(
                            runningTask, runId, watchProduct, safeMode
                    );
            Optional<NoonRiskBackoffHold> activeRiskBackoff =
                    riskBackoff.current(watchProduct);
            if (activeRiskBackoff.isPresent()) {
                String message = riskBackoff.message(activeRiskBackoff.get());
                if (detailRetrySession != null
                        && detailRetrySession.requeue(
                                activeRiskBackoff.get(),
                                COMPETITOR_RISK_BACKOFF,
                                message
                        )) {
                    return;
                }
                if (detailRetrySession != null && detailRetrySession.isComplete()) {
                    log.info(
                            "competitor detail task finalized under existing risk hold taskId={}",
                            taskId
                    );
                } else {
                    refreshTaskFactory.executionFinalizer().fail(
                            taskId,
                            runId,
                            watchProductId,
                            COMPETITOR_RISK_BACKOFF,
                            message,
                            actorUserId
                    );
                    return;
                }
            }
            CompetitorProductDetailRefreshResult detailResult =
                    refreshConfirmedCompetitorDetails(
                            taskId,
                            runId,
                            watchProduct,
                            actorUserId,
                            safeMode,
                            detailRetrySession
                    );
            NoonRiskBackoffHold riskBackoffHold = null;
            if (detailResult.getFailedCount() > 0) {
                firstErrorCode = detailResult.getFirstErrorCode();
                firstErrorMessage = detailResult.getFirstErrorMessage();
                if (detailResult.hasRiskBackoffFailure()) {
                    firstErrorCode = detailResult.getRiskErrorCode();
                    firstErrorMessage = detailResult.getRiskErrorMessage();
                    String riskErrorCode = firstErrorCode;
                    String riskErrorMessage = firstErrorMessage;
                    riskBackoffHold = detailRetrySession == null
                            ? refreshTaskFactory.executionFinalizer().withLease(
                                    taskId, runId, watchProductId,
                                    () -> riskBackoff.record(
                                            watchProduct, taskId,
                                            riskErrorCode, riskErrorMessage
                                    )
                            )
                            : detailRetrySession.ensureRiskHold(
                                    riskErrorCode, riskErrorMessage
                            );
                }
            }
            if (detailRetrySession != null) {
                if (detailRetrySession.requeue(
                        riskBackoffHold, firstErrorCode, firstErrorMessage
                )) {
                    return;
                }
                detailRetrySession.applyCumulative(detailResult);
                firstErrorCode = firstNonBlank(
                        firstErrorCode, detailResult.getFirstErrorCode()
                );
                firstErrorMessage = firstNonBlank(
                        firstErrorMessage, detailResult.getFirstErrorMessage()
                );
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
                        riskBackoffHold = refreshTaskFactory
                                .executionFinalizer()
                                .withLease(
                                        taskId,
                                        runId,
                                        watchProductId,
                                        () -> riskBackoff.record(
                                                watchProduct,
                                                taskId,
                                                result.getErrorCode(),
                                                result.getErrorMessage()
                                        )
                        );
                        firstErrorCode = firstNonBlank(firstErrorCode, result.getErrorCode());
                        firstErrorMessage = firstNonBlank(firstErrorMessage, result.getErrorMessage());
                        updateProgress(taskId, runId, watchProductId, total, success + failed);
                        break;
                    }
                    if (shouldRetryTransientKeywordFailure(safeMode, result)) {
                        retryCandidates.add(new KeywordRetryCandidate(keyword, result));
                    } else {
                        firstErrorCode = firstNonBlank(firstErrorCode, result.getErrorCode());
                        firstErrorMessage = firstNonBlank(firstErrorMessage, result.getErrorMessage());
                    }
                }
                updateProgress(taskId, runId, watchProductId, total, success + failed);
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

            String status = CompetitorRefreshRunResultSupport.status(
                    success, failed, detailResult
            );
            String message = resolveRunMessage(
                    safeMode, status, success, failed, detailResult
            );
            String taskErrorCode = riskBackoffHold != null
                    ? COMPETITOR_RISK_BACKOFF
                    : "FAILED".equals(status)
                            ? firstNonBlank(firstErrorCode, "REFRESH_FAILED")
                            : null;
            String taskMessage = riskBackoffHold == null
                    ? message
                    : riskBackoff.message(riskBackoffHold);
            String resultJson = CompetitorRefreshRunResultSupport.resultJson(
                    safeMode,
                    status,
                    success,
                    failed,
                    detailResult,
                    keywordRetried,
                    keywordRetryRecovered
            );
            refreshTaskFactory.executionFinalizer().complete(
                    taskId,
                    runId,
                    watchProductId,
                    status,
                    success,
                    failed,
                    candidateUpsertedCount,
                    rankFactWrittenCount,
                    firstErrorCode,
                    truncateMessage(firstErrorMessage),
                    actorUserId,
                    taskErrorCode,
                    resultJson,
                    taskMessage
            );
            if (riskBackoffHold != null) {
                return;
            }
            if ("SUCCEEDED".equals(status) && activeRiskBackoff.isEmpty()) {
                riskBackoff.recordSuccess(watchProduct);
            }
        } catch (CompetitorRefreshLeaseLostException exception) {
            log.info(
                    "competitor refresh stopped after lease loss watchProductId={} runId={} taskId={}",
                    watchProductId,
                    runId,
                    taskId
            );
        } catch (RuntimeException exception) {
            String message = truncateMessage(firstNonBlank(exception.getMessage(), FAILED_MESSAGE));
            try {
                refreshTaskFactory.executionFinalizer().fail(
                        taskId,
                        runId,
                        watchProductId,
                        "REFRESH_FAILED",
                        message,
                        actorUserId
                );
            } catch (CompetitorRefreshLeaseLostException ignored) {
                log.info(
                        "competitor refresh failure ignored after lease loss runId={} taskId={}",
                        runId,
                        taskId
                );
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
            CompetitorRefreshExecutionMode mode,
            CompetitorDetailRetrySession retrySession
    ) {
        if (!mode.runsDetail() || productDetailRefreshService == null) {
            return CompetitorProductDetailRefreshResult.empty();
        }
        CompetitorProductDetailRefreshResult result = retrySession == null
                ? productDetailRefreshService.refreshConfirmedCompetitors(
                        watchProduct, runId, taskId, actorUserId
                )
                : productDetailRefreshService.refreshTargets(
                        watchProduct,
                        retrySession.readyTargets(),
                        runId,
                        taskId,
                        actorUserId,
                        retrySession
                );
        return result == null ? CompetitorProductDetailRefreshResult.empty() : result;
    }

    private CompetitorDetailRetrySession openDetailRetrySession(
            OperationalTask task,
            Long runId,
            CompetitorWatchProductRow watchProduct,
            CompetitorRefreshExecutionMode mode
    ) {
        if (mode != CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                || productDetailRefreshService == null) {
            return null;
        }
        return detailRetryCoordinator.openSession(
                task, runId, watchProduct.getId(),
                productDetailRefreshService.currentTargets(watchProduct),
                (errorCode, errorMessage) -> riskBackoff.record(
                        watchProduct, task.getId(), errorCode, errorMessage
                )
        );
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
        refreshTaskFactory.executionFinalizer().progress(
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
        return refreshTaskLocator.hasBatchKey(task, batchKey);
    }

    private String normalizeRequired(String value, String reason) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw badRequest(reason);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
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
