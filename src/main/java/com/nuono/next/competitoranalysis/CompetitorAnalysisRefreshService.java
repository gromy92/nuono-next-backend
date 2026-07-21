package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
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
    public static final String MONITOR_TASK_TYPE = "OPERATIONS_COMPETITOR_MONITORING";

    private static final Logger log = LoggerFactory.getLogger(CompetitorAnalysisRefreshService.class);
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final String NATURAL_KEY_PREFIX = "watchProduct:";
    private static final String MONITOR_NATURAL_KEY_PREFIX = "store:";
    private static final String TRIGGER_MODE_MANUAL = "MANUAL_REFRESH";
    private static final String RUNNING_MESSAGE = "竞品刷新正在后台执行。";
    private static final String MONITOR_RUNNING_MESSAGE = "竞品监控批次正在后台提交。";
    private static final String STALE_MESSAGE = "刷新任务超过 30 分钟未完成，已自动释放。";
    private static final String FAILED_MESSAGE = "竞品刷新失败，请稍后重试。";
    private static final int STORE_MONITOR_PRODUCT_LIMIT = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    private static final String RATE_LIMITED = "RATE_LIMITED";
    private static final String BLOCKED_BY_RISK_CONTROL = "BLOCKED_BY_RISK_CONTROL";
    private static final String CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED";
    private static final String COMPETITOR_RISK_BACKOFF = "COMPETITOR_RISK_BACKOFF";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorTaskSubmitter taskSubmitter;
    private final CompetitorRefreshTaskDispatcher refreshTaskDispatcher;
    private final CompetitorAnalysisTaskRecovery taskRecovery;
    private final CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner;
    private final CompetitorProductDetailRefreshService productDetailRefreshService;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final Clock clock;

    @Autowired
    public CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            ObjectProvider<NoonAccountTaskQueue> noonAccountTaskQueueProvider,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            ObjectProvider<CompetitorProductDetailRefreshService> productDetailRefreshServiceProvider,
            ObjectProvider<NoonRiskBackoffGuard> riskBackoffGuardProvider
    ) {
        this(
                mapper,
                operationalTaskService,
                queueSubmitter(noonAccountTaskQueueProvider == null ? null : noonAccountTaskQueueProvider.getIfAvailable()),
                keywordRefreshRunner,
                productDetailRefreshServiceProvider == null ? null : productDetailRefreshServiceProvider.getIfAvailable(),
                Clock.systemUTC(),
                riskBackoffGuardProvider == null
                        ? NoonRiskBackoffGuard.disabled()
                        : riskBackoffGuardProvider.getIfAvailable(NoonRiskBackoffGuard::disabled)
        );
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            Clock clock
    ) {
        this(
                mapper,
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
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            Clock clock
    ) {
        this(mapper, operationalTaskService, taskSubmitter, keywordRefreshRunner, null, clock, NoonRiskBackoffGuard.disabled());
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            CompetitorProductDetailRefreshService productDetailRefreshService,
            Clock clock
    ) {
        this(
                mapper,
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
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            CompetitorProductDetailRefreshService productDetailRefreshService,
            Clock clock,
            NoonRiskBackoffGuard riskBackoffGuard
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.taskSubmitter = taskSubmitter == null ? (accountKey, task) -> task.run() : taskSubmitter;
        this.keywordRefreshRunner = keywordRefreshRunner;
        this.productDetailRefreshService = productDetailRefreshService;
        this.riskBackoffGuard = riskBackoffGuard == null ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.refreshTaskDispatcher = new CompetitorRefreshTaskDispatcher(
                mapper,
                operationalTaskService,
                this.taskSubmitter
        );
        this.taskRecovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                this.clock,
                this::resubmitQueuedRefresh,
                this::retryInterruptedRefresh
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
        return requestStoreMonitoring(
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
        return requestStoreMonitoring(
                normalizedOwnerUserId,
                normalizeRequired(storeCode, "COMPETITOR_STORE_REQUIRED"),
                normalizeRequired(siteCode, "COMPETITOR_SITE_REQUIRED"),
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
        return requestStoreMonitoring(
                normalizedOwnerUserId,
                normalizeRequired(storeCode, "COMPETITOR_STORE_REQUIRED"),
                normalizeRequired(siteCode, "COMPETITOR_SITE_REQUIRED"),
                null,
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
        );
    }

    public int recoverStaleRefreshTasks() {
        return taskRecovery.recoverStaleRefreshTasks();
    }

    public int resumeQueuedRefreshTasks() {
        return taskRecovery.resumeQueuedRefreshTasks();
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

    private CompetitorTaskView requestStoreMonitoring(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long actorUserId,
            CompetitorRefreshExecutionMode executionMode
    ) {
        rejectIfNoonRiskBackoffActive(ownerUserId, storeCode, siteCode);
        List<CompetitorWatchProductRow> watchProducts = mapper.listRefreshableWatchProducts(
                ownerUserId,
                storeCode,
                siteCode,
                STORE_MONITOR_PRODUCT_LIMIT
        );
        if (watchProducts.isEmpty()) {
            throw badRequest("COMPETITOR_MONITOR_NO_REFRESHABLE_PRODUCT");
        }

        String naturalKey = monitorNaturalKey(ownerUserId, storeCode, siteCode, executionMode);
        OperationalTask activeTask = operationalTaskService.findActive(MONITOR_TASK_TYPE, naturalKey).orElse(null);
        if (activeTask != null && !isStale(activeTask)) {
            return CompetitorTaskView.from(activeTask);
        }
        if (activeTask != null) {
            operationalTaskService.fail(activeTask.getId(), "FAILED_STALE", STALE_MESSAGE);
        }

        OperationalTask task = operationalTaskService.start(
                MONITOR_TASK_TYPE,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(ownerUserId)
                        .storeCode(storeCode)
                        .siteCode(siteCode)
                        .payloadJson(monitorPayloadJson(executionMode, watchProducts.size()))
                        .message(MONITOR_RUNNING_MESSAGE)
                        .build()
        );
        taskSubmitter.submit(
                accountKey(ownerUserId, storeCode),
                () -> runStoreMonitoring(
                        task.getId(),
                        ownerUserId,
                        storeCode,
                        siteCode,
                        actorUserId,
                        executionMode
                )
        );
        return CompetitorTaskView.from(task);
    }

    private CompetitorRefreshRunView requestRefreshForWatchProduct(
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            CompetitorRefreshExecutionMode executionMode
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        String naturalKey = naturalKey(watchProduct.getId(), safeMode);
        OperationalTask activeTask = operationalTaskService.findActive(TASK_TYPE, naturalKey).orElse(null);
        if (activeTask != null && !isStale(activeTask)) {
            return CompetitorRefreshRunView.from(activeTask, mapper.selectSearchRunByTaskId(activeTask.getId()));
        }
        if (activeTask != null) {
            releaseStaleTask(activeTask);
        }
        rejectIfNoonRiskBackoffActive(
                watchProduct.getOwnerUserId(),
                watchProduct.getStoreCode(),
                watchProduct.getSiteCode()
        );

        List<CompetitorKeywordRow> activeKeywords = safeMode.runsRank()
                ? mapper.listActiveKeywordsByWatchProductId(watchProduct.getId())
                : List.of();
        if (safeMode.runsRank() && activeKeywords.isEmpty()) {
            throw badRequest("COMPETITOR_NO_ACTIVE_KEYWORD");
        }
        int keywordTotal = activeKeywords.size();

        OperationalTask task = operationalTaskService.queue(
                TASK_TYPE,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(watchProduct.getOwnerUserId())
                        .storeCode(watchProduct.getStoreCode())
                        .siteCode(watchProduct.getSiteCode())
                        .payloadJson(payloadJson(watchProduct.getId(), keywordTotal, safeMode))
                        .message(RUNNING_MESSAGE)
                        .build()
        );
        CompetitorSearchRunInsertCommand runCommand = buildSearchRunCommand(
                watchProduct,
                task,
                keywordTotal,
                actorUserId,
                safeMode.triggerMode()
        );
        mapper.insertSearchRun(runCommand);
        CompetitorSearchRunRow run = runRow(runCommand);
        submitQueuedRefresh(task, run, watchProduct, actorUserId, safeMode);
        return CompetitorRefreshRunView.from(task, run);
    }

    private boolean submitQueuedRefresh(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            CompetitorRefreshExecutionMode executionMode
    ) {
        return refreshTaskDispatcher.submit(
                accountKey(watchProduct),
                task,
                run,
                RUNNING_MESSAGE,
                () -> runRefresh(task.getId(), run.getId(), watchProduct.getId(), actorUserId, executionMode)
        );
    }

    private boolean resubmitQueuedRefresh(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct
    ) {
        return submitQueuedRefresh(
                task,
                run,
                watchProduct,
                run.getRequestedBy(),
                CompetitorRefreshExecutionMode.fromTriggerMode(run.getTriggerMode())
        );
    }

    private void retryInterruptedRefresh(
            CompetitorWatchProductRow watchProduct,
            CompetitorSearchRunRow run
    ) {
        requestRefreshForWatchProduct(
                watchProduct,
                run.getRequestedBy(),
                CompetitorRefreshExecutionMode.fromTriggerMode(run.getTriggerMode())
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

    private void runStoreMonitoring(
            Long taskId,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long actorUserId,
            CompetitorRefreshExecutionMode executionMode
    ) {
        int submitted = 0;
        int failed = 0;
        String firstErrorCode = null;
        String firstErrorMessage = null;
        try {
            operationalTaskService.progress(taskId, 5, MONITOR_RUNNING_MESSAGE);
            List<CompetitorWatchProductRow> watchProducts = mapper.listRefreshableWatchProducts(
                    ownerUserId,
                    storeCode,
                    siteCode,
                    STORE_MONITOR_PRODUCT_LIMIT
            );
            int total = watchProducts.size();
            if (total <= 0) {
                operationalTaskService.fail(taskId, "COMPETITOR_MONITOR_NO_REFRESHABLE_PRODUCT", "当前没有可监控商品。");
                return;
            }
            for (CompetitorWatchProductRow watchProduct : watchProducts) {
                try {
                    requestRefreshForWatchProduct(watchProduct, actorUserId, executionMode);
                    submitted++;
                } catch (RuntimeException exception) {
                    failed++;
                    firstErrorCode = firstNonBlank(firstErrorCode, errorCode(exception));
                    firstErrorMessage = firstNonBlank(firstErrorMessage, exception.getMessage());
                    log.warn(
                            "competitor monitoring submit failed watchProductId={} taskId={} error={}",
                            watchProduct.getId(),
                            taskId,
                            exception.getMessage(),
                            exception
                    );
                }
                updateMonitorProgress(taskId, total, submitted + failed);
            }

            String resultJson = monitorResultJson(executionMode, total, submitted, failed);
            if (submitted <= 0) {
                operationalTaskService.fail(
                        taskId,
                        firstNonBlank(firstErrorCode, "COMPETITOR_MONITOR_SUBMIT_FAILED"),
                        firstNonBlank(firstErrorMessage, "竞品监控批次提交失败。")
                );
                return;
            }
            String message = failed > 0
                    ? "竞品监控批次已提交，部分商品提交失败。"
                    : "竞品监控批次已提交。";
            operationalTaskService.complete(taskId, resultJson, message);
        } catch (RuntimeException exception) {
            safeFailTask(taskId, "COMPETITOR_MONITOR_FAILED", firstNonBlank(exception.getMessage(), "竞品监控批次失败。"));
            log.warn(
                    "competitor monitoring failed ownerUserId={} storeCode={} siteCode={} taskId={} error={}",
                    ownerUserId,
                    storeCode,
                    siteCode,
                    taskId,
                    exception.getMessage(),
                    exception
            );
        }
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
            operationalTaskService.progress(taskId, 5, RUNNING_MESSAGE);
            CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(watchProductId);
            if (watchProduct == null) {
                throw new IllegalStateException("监控商品不存在或已删除。");
            }
            Optional<NoonRiskBackoffHold> activeRiskBackoff = currentNoonRiskBackoff(watchProduct);
            if (activeRiskBackoff.isPresent()) {
                String message = riskBackoffMessage(activeRiskBackoff.get());
                safeMarkRunFailed(runId, COMPETITOR_RISK_BACKOFF, message);
                safeUpdateWatchLatestRun(watchProductId, runId, "FAILED", actorUserId);
                safeFailTask(taskId, COMPETITOR_RISK_BACKOFF, message);
                return;
            }
            if (safeMode.runsDetail()) {
                refreshConfirmedCompetitorDetails(taskId, runId, watchProduct, actorUserId);
            }
            List<CompetitorKeywordRow> keywords = safeMode.runsRank()
                    ? mapper.listActiveKeywordsByWatchProductId(watchProductId)
                    : List.of();
            int total = keywords.size();
            List<KeywordRetryCandidate> retryCandidates = new ArrayList<>();
            int keywordRetried = 0;
            int keywordRetryRecovered = 0;
            NoonRiskBackoffHold riskBackoffHold = null;
            for (CompetitorKeywordRow keyword : keywords) {
                CompetitorKeywordRefreshResult result = keywordRefreshRunner.runKeyword(runId, watchProduct, keyword, actorUserId);
                if (result.isSuccess()) {
                    success++;
                    candidateUpsertedCount += result.getCandidateUpsertedCount();
                    rankFactWrittenCount += result.getRankFactWrittenCount();
                } else {
                    failed++;
                    if (isRiskBackoffFailure(result.getErrorCode())) {
                        riskBackoffHold = recordRiskBackoff(
                                watchProduct,
                                taskId,
                                result.getErrorCode(),
                                result.getErrorMessage()
                        );
                        firstErrorCode = firstNonBlank(firstErrorCode, result.getErrorCode());
                        firstErrorMessage = firstNonBlank(firstErrorMessage, result.getErrorMessage());
                        updateProgress(taskId, total, success + failed);
                        break;
                    }
                    if (shouldRetryTransientKeywordFailure(safeMode, result)) {
                        retryCandidates.add(new KeywordRetryCandidate(keyword, result));
                    } else {
                        firstErrorCode = firstNonBlank(firstErrorCode, result.getErrorCode());
                        firstErrorMessage = firstNonBlank(firstErrorMessage, result.getErrorMessage());
                    }
                }
                updateProgress(taskId, total, success + failed);
            }

            for (KeywordRetryCandidate retryCandidate : retryCandidates) {
                keywordRetried++;
                CompetitorKeywordRefreshResult retryResult = keywordRefreshRunner.runKeyword(
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

            String status = resolveRunStatus(success, failed);
            String message = resolveRunMessage(safeMode, status, success, failed);
            mapper.completeSearchRun(
                    runId,
                    status,
                    success,
                    failed,
                    candidateUpsertedCount,
                    rankFactWrittenCount,
                    firstErrorCode,
                    truncateMessage(firstErrorMessage),
                    actorUserId
            );
            mapper.updateWatchProductLatestRun(watchProductId, runId, status, actorUserId);
            if (riskBackoffHold != null) {
                operationalTaskService.fail(taskId, COMPETITOR_RISK_BACKOFF, riskBackoffMessage(riskBackoffHold));
                return;
            }
            if ("FAILED".equals(status)) {
                operationalTaskService.fail(taskId, firstNonBlank(firstErrorCode, "REFRESH_FAILED"), message);
            } else {
                if ("SUCCEEDED".equals(status)) {
                    riskBackoffGuard.recordSuccess(competitorRiskScope(watchProduct.getOwnerUserId(), watchProduct.getStoreCode(), watchProduct.getSiteCode()), "PUBLIC_SEARCH");
                }
                operationalTaskService.complete(
                        taskId,
                        resultJson(safeMode, status, success, failed, keywordRetried, keywordRetryRecovered),
                        message
                );
            }
        } catch (RuntimeException exception) {
            String message = truncateMessage(firstNonBlank(exception.getMessage(), FAILED_MESSAGE));
            safeMarkRunFailed(runId, "REFRESH_FAILED", message);
            safeUpdateWatchLatestRun(watchProductId, runId, "FAILED", actorUserId);
            safeFailTask(taskId, "REFRESH_FAILED", message);
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
    private void rejectIfNoonRiskBackoffActive(Long ownerUserId, String storeCode, String siteCode) {
        Optional<NoonRiskBackoffHold> activeHold = riskBackoffGuard.currentHold(competitorRiskScope(
                ownerUserId,
                storeCode,
                siteCode
        ));
        if (activeHold.isPresent()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "NOON_RISK_BACKOFF");
        }
    }
    private Optional<NoonRiskBackoffHold> currentNoonRiskBackoff(CompetitorWatchProductRow watchProduct) {
        if (watchProduct == null) {
            return Optional.empty();
        }
        return riskBackoffGuard.currentHold(competitorRiskScope(
                watchProduct.getOwnerUserId(),
                watchProduct.getStoreCode(),
                watchProduct.getSiteCode()
        ));
    }
    private NoonRiskBackoffHold recordRiskBackoff(
            CompetitorWatchProductRow watchProduct,
            Long taskId,
            String errorCode,
            String errorMessage
    ) {
        return riskBackoffGuard.recordRiskSignal(
                competitorRiskScope(
                        watchProduct == null ? null : watchProduct.getOwnerUserId(),
                        watchProduct == null ? null : watchProduct.getStoreCode(),
                        watchProduct == null ? null : watchProduct.getSiteCode()
                ),
                riskType(errorCode),
                "PUBLIC_SEARCH",
                taskId,
                null,
                firstNonBlank(errorMessage, errorCode)
        );
    }

    private NoonRiskBackoffScope competitorRiskScope(Long ownerUserId, String storeCode, String siteCode) {
        return NoonRiskBackoffScope.publicSearch(ownerUserId, storeCode, siteCode);
    }

    private boolean isRiskBackoffFailure(String errorCode) {
        String normalized = normalize(errorCode);
        return RATE_LIMITED.equals(normalized)
                || BLOCKED_BY_RISK_CONTROL.equals(normalized)
                || CAPTCHA_REQUIRED.equals(normalized);
    }

    private String riskType(String errorCode) {
        String normalized = normalize(errorCode);
        if (RATE_LIMITED.equals(normalized)) {
            return "rate_limited";
        }
        if (BLOCKED_BY_RISK_CONTROL.equals(normalized)) {
            return "blocked_by_risk_control";
        }
        if (CAPTCHA_REQUIRED.equals(normalized)) {
            return "captcha_required";
        }
        return normalized == null ? "risk_control" : normalized.toLowerCase(Locale.ROOT);
    }

    private String riskBackoffMessage(NoonRiskBackoffHold hold) {
        return "竞品监控触发 Noon 风控退避："
                + (hold == null ? "unknown" : hold.getRiskType())
                + "，冷却至 "
                + (hold == null ? "unknown" : hold.getBlockedUntil())
                + "。";
    }

    private void refreshConfirmedCompetitorDetails(
            Long taskId,
            Long runId,
            CompetitorWatchProductRow watchProduct,
            Long actorUserId
    ) {
        if (productDetailRefreshService == null) {
            return;
        }
        try {
            productDetailRefreshService.refreshConfirmedCompetitors(watchProduct, runId, taskId, actorUserId);
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor confirmed product detail refresh skipped watchProductId={} runId={} taskId={} error={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    runId,
                    taskId,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void releaseStaleTask(OperationalTask task) {
        operationalTaskService.fail(task.getId(), "FAILED_STALE", STALE_MESSAGE);
        CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(task.getId());
        if (run != null) {
            mapper.markSearchRunFailed(run.getId(), "FAILED_STALE", STALE_MESSAGE);
        }
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

    private void updateProgress(Long taskId, int total, int finished) {
        if (total <= 0) {
            return;
        }
        int progress = 5 + (int) Math.floor((finished * 90.0d) / total);
        operationalTaskService.progress(taskId, progress, RUNNING_MESSAGE);
    }

    private void updateMonitorProgress(Long taskId, int total, int finished) {
        if (total <= 0) {
            return;
        }
        int progress = 5 + (int) Math.floor((finished * 90.0d) / total);
        operationalTaskService.progress(taskId, progress, MONITOR_RUNNING_MESSAGE);
    }

    private CompetitorSearchRunInsertCommand buildSearchRunCommand(
            CompetitorWatchProductRow watchProduct,
            OperationalTask task,
            int keywordTotal,
            Long actorUserId,
            String triggerMode
    ) {
        CompetitorSearchRunInsertCommand command = new CompetitorSearchRunInsertCommand();
        command.setId(mapper.nextSearchRunId());
        command.setWatchProductId(watchProduct.getId());
        command.setTaskId(task.getId());
        command.setTriggerMode(StringUtils.hasText(triggerMode) ? triggerMode : TRIGGER_MODE_MANUAL);
        command.setStatus("QUEUED");
        command.setRequestedBy(actorUserId);
        command.setKeywordTotal(keywordTotal);
        command.setActorUserId(actorUserId);
        return command;
    }

    private CompetitorSearchRunRow runRow(CompetitorSearchRunInsertCommand command) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(command.getId());
        row.setWatchProductId(command.getWatchProductId());
        row.setTaskId(command.getTaskId());
        row.setTriggerMode(command.getTriggerMode());
        row.setStatus(command.getStatus());
        row.setRequestedBy(command.getRequestedBy());
        row.setKeywordTotal(command.getKeywordTotal());
        row.setKeywordSuccess(0);
        row.setKeywordFailed(0);
        row.setCandidateUpsertedCount(0);
        row.setRankFactWrittenCount(0);
        return row;
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

    private String naturalKey(Long watchProductId, CompetitorRefreshExecutionMode executionMode) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        String key = NATURAL_KEY_PREFIX + watchProductId;
        return safeMode == CompetitorRefreshExecutionMode.FULL_MANUAL ? key : key + ":" + safeMode.taskKey();
    }

    private String accountKey(CompetitorWatchProductRow watchProduct) {
        return accountKey(watchProduct.getOwnerUserId(), watchProduct.getStoreCode());
    }

    private String accountKey(Long ownerUserId, String storeCode) {
        return ownerUserId + "::" + normalize(storeCode);
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

    private String resolveRunMessage(CompetitorRefreshExecutionMode executionMode, String status, int success, int failed) {
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
        if ("SUCCEEDED".equals(status)) {
            return "竞品刷新完成。";
        }
        if ("PARTIAL_FAILED".equals(status)) {
            return "竞品刷新部分关键词失败。";
        }
        return failed > 0 && success <= 0 ? "竞品刷新失败。" : FAILED_MESSAGE;
    }

    private String payloadJson(Long watchProductId, int keywordTotal, CompetitorRefreshExecutionMode executionMode) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL : executionMode;
        return "{"
                + "\"watchProductId\":" + watchProductId
                + ",\"keywordTotal\":" + keywordTotal
                + ",\"triggerMode\":\"" + json(safeMode.triggerMode()) + "\""
                + ",\"executionMode\":\"" + json(safeMode.taskKey()) + "\""
                + ",\"rankRefresh\":" + safeMode.runsRank()
                + ",\"detailRefresh\":" + safeMode.runsDetail()
                + "}";
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

    private String monitorPayloadJson(CompetitorRefreshExecutionMode executionMode, int watchProductTotal) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR : executionMode;
        return "{"
                + "\"triggerMode\":\"" + json(safeMode.triggerMode()) + "\""
                + ",\"executionMode\":\"" + json(safeMode.taskKey()) + "\""
                + ",\"rankRefresh\":" + safeMode.runsRank()
                + ",\"detailRefresh\":" + safeMode.runsDetail()
                + ",\"watchProductTotal\":" + watchProductTotal
                + "}";
    }

    private String monitorResultJson(CompetitorRefreshExecutionMode executionMode, int total, int submitted, int failed) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR : executionMode;
        return "{"
                + "\"triggerMode\":\"" + json(safeMode.triggerMode()) + "\""
                + ",\"executionMode\":\"" + json(safeMode.taskKey()) + "\""
                + ",\"rankRefresh\":" + safeMode.runsRank()
                + ",\"detailRefresh\":" + safeMode.runsDetail()
                + ",\"watchProductTotal\":" + total
                + ",\"submittedCount\":" + submitted
                + ",\"failedCount\":" + failed
                + "}";
    }

    private String monitorNaturalKey(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            CompetitorRefreshExecutionMode executionMode
    ) {
        CompetitorRefreshExecutionMode safeMode = executionMode == null ? CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR : executionMode;
        return MONITOR_NATURAL_KEY_PREFIX
                + ownerUserId
                + ":"
                + normalize(storeCode)
                + ":"
                + normalize(siteCode)
                + (safeMode == CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR ? "" : ":" + safeMode.taskKey());
    }

    private String normalizeRequired(String value, String reason) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw badRequest(reason);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof ResponseStatusException) {
            String reason = ((ResponseStatusException) exception).getReason();
            if (StringUtils.hasText(reason)) {
                return reason;
            }
        }
        return "COMPETITOR_MONITOR_SUBMIT_FAILED";
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void safeMarkRunFailed(Long runId, String errorCode, String message) {
        try {
            mapper.markSearchRunFailed(runId, errorCode, truncateMessage(message));
        } catch (RuntimeException ignored) {
            log.debug("competitor search run {} was already terminal while recording failure", runId);
        }
    }

    private void safeUpdateWatchLatestRun(Long watchProductId, Long runId, String runStatus, Long actorUserId) {
        try {
            mapper.updateWatchProductLatestRun(watchProductId, runId, runStatus, actorUserId);
        } catch (RuntimeException ignored) {
            log.debug("watch product {} latest run update failed for run {}", watchProductId, runId);
        }
    }

    private void safeFailTask(Long taskId, String errorCode, String message) {
        try {
            operationalTaskService.fail(taskId, errorCode, truncateMessage(message));
        } catch (RuntimeException ignored) {
            log.debug("operational task {} was already terminal while recording refresh failure", taskId);
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
