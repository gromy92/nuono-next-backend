package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private static final String TRIGGER_MODE_MANUAL_MONITOR = "MANUAL_MONITOR";
    private static final String TRIGGER_MODE_SCHEDULED_MONITOR = "SCHEDULED_MONITOR";
    private static final String RUNNING_MESSAGE = "竞品刷新正在后台执行。";
    private static final String MONITOR_RUNNING_MESSAGE = "竞品监控批次正在后台提交。";
    private static final String STALE_MESSAGE = "刷新任务超过 30 分钟未完成，已自动释放。";
    private static final String FAILED_MESSAGE = "竞品刷新失败，请稍后重试。";
    private static final int STORE_MONITOR_PRODUCT_LIMIT = 500;

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final TaskSubmitter taskSubmitter;
    private final CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner;
    private final Clock clock;

    @Autowired
    public CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            ObjectProvider<NoonAccountTaskQueue> noonAccountTaskQueueProvider,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner
    ) {
        this(
                mapper,
                operationalTaskService,
                queueSubmitter(noonAccountTaskQueueProvider == null ? null : noonAccountTaskQueueProvider.getIfAvailable()),
                keywordRefreshRunner,
                Clock.systemUTC()
        );
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            TaskSubmitter taskSubmitter,
            Clock clock
    ) {
        this(
                mapper,
                operationalTaskService,
                taskSubmitter,
                new CompetitorKeywordRefreshTransactionRunner(mapper, new NoopCompetitorKeywordRefreshRunner()),
                clock
        );
    }

    CompetitorAnalysisRefreshService(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            TaskSubmitter taskSubmitter,
            CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner,
            Clock clock
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.taskSubmitter = taskSubmitter == null ? (accountKey, task) -> task.run() : taskSubmitter;
        this.keywordRefreshRunner = keywordRefreshRunner;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public CompetitorRefreshRunView requestRefresh(BusinessAccessContext context, Long watchProductId) {
        CompetitorWatchProductRow watchProduct = requireWatchProduct(context, watchProductId);
        return requestRefreshForWatchProduct(watchProduct, actorUserId(context), TRIGGER_MODE_MANUAL);
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
                TRIGGER_MODE_MANUAL_MONITOR
        );
    }

    public CompetitorTaskView requestScheduledStoreMonitoring(
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
                TRIGGER_MODE_SCHEDULED_MONITOR
        );
    }

    private CompetitorTaskView requestStoreMonitoring(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long actorUserId,
            String triggerMode
    ) {
        List<CompetitorWatchProductRow> watchProducts = mapper.listRefreshableWatchProducts(
                ownerUserId,
                storeCode,
                siteCode,
                STORE_MONITOR_PRODUCT_LIMIT
        );
        if (watchProducts.isEmpty()) {
            throw badRequest("COMPETITOR_MONITOR_NO_REFRESHABLE_PRODUCT");
        }

        String naturalKey = monitorNaturalKey(ownerUserId, storeCode, siteCode);
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
                        .payloadJson(monitorPayloadJson(triggerMode, watchProducts.size()))
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
                        triggerMode
                )
        );
        return CompetitorTaskView.from(task);
    }

    private CompetitorRefreshRunView requestRefreshForWatchProduct(
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            String triggerMode
    ) {
        String naturalKey = naturalKey(watchProduct.getId());
        OperationalTask activeTask = operationalTaskService.findActive(TASK_TYPE, naturalKey).orElse(null);
        if (activeTask != null && !isStale(activeTask)) {
            return CompetitorRefreshRunView.from(activeTask, mapper.selectSearchRunByTaskId(activeTask.getId()));
        }
        if (activeTask != null) {
            releaseStaleTask(activeTask);
        }

        List<CompetitorKeywordRow> activeKeywords = mapper.listActiveKeywordsByWatchProductId(watchProduct.getId());
        if (activeKeywords.isEmpty()) {
            throw badRequest("COMPETITOR_NO_ACTIVE_KEYWORD");
        }

        OperationalTask task = operationalTaskService.start(
                TASK_TYPE,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(watchProduct.getOwnerUserId())
                        .storeCode(watchProduct.getStoreCode())
                        .siteCode(watchProduct.getSiteCode())
                        .payloadJson(payloadJson(watchProduct.getId(), activeKeywords.size(), triggerMode))
                        .message(RUNNING_MESSAGE)
                        .build()
        );
        CompetitorSearchRunInsertCommand runCommand = buildSearchRunCommand(
                watchProduct,
                task,
                activeKeywords.size(),
                actorUserId,
                triggerMode
        );
        mapper.insertSearchRun(runCommand);
        CompetitorSearchRunRow run = runRow(runCommand);
        taskSubmitter.submit(
                accountKey(watchProduct),
                () -> runRefresh(task.getId(), run.getId(), watchProduct.getId(), actorUserId)
        );
        return CompetitorRefreshRunView.from(task, run);
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
            String triggerMode
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
                    requestRefreshForWatchProduct(watchProduct, actorUserId, triggerMode);
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

            String resultJson = monitorResultJson(triggerMode, total, submitted, failed);
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

    private void runRefresh(Long taskId, Long runId, Long watchProductId, Long actorUserId) {
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
            List<CompetitorKeywordRow> keywords = mapper.listActiveKeywordsByWatchProductId(watchProductId);
            int total = keywords.size();
            for (CompetitorKeywordRow keyword : keywords) {
                CompetitorKeywordRefreshResult result = keywordRefreshRunner.runKeyword(runId, watchProduct, keyword, actorUserId);
                if (result.isSuccess()) {
                    success++;
                    candidateUpsertedCount += result.getCandidateUpsertedCount();
                    rankFactWrittenCount += result.getRankFactWrittenCount();
                } else {
                    failed++;
                    firstErrorCode = firstNonBlank(firstErrorCode, result.getErrorCode());
                    firstErrorMessage = firstNonBlank(firstErrorMessage, result.getErrorMessage());
                }
                updateProgress(taskId, total, success + failed);
            }

            String status = resolveRunStatus(success, failed);
            String message = resolveRunMessage(status, success, failed);
            mapper.completeSearchRun(
                    runId,
                    status,
                    success,
                    failed,
                    candidateUpsertedCount,
                    rankFactWrittenCount,
                    firstErrorCode,
                    firstErrorMessage,
                    actorUserId
            );
            mapper.updateWatchProductLatestRun(watchProductId, runId, status, actorUserId);
            if ("FAILED".equals(status)) {
                operationalTaskService.fail(taskId, firstNonBlank(firstErrorCode, "REFRESH_FAILED"), message);
            } else {
                operationalTaskService.complete(taskId, resultJson(status, success, failed), message);
            }
        } catch (RuntimeException exception) {
            String message = firstNonBlank(exception.getMessage(), FAILED_MESSAGE);
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
        command.setStatus("RUNNING");
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

    private String naturalKey(Long watchProductId) {
        return NATURAL_KEY_PREFIX + watchProductId;
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

    private String resolveRunMessage(String status, int success, int failed) {
        if ("SUCCEEDED".equals(status)) {
            return "竞品刷新完成。";
        }
        if ("PARTIAL_FAILED".equals(status)) {
            return "竞品刷新部分关键词失败。";
        }
        return failed > 0 && success <= 0 ? "竞品刷新失败。" : FAILED_MESSAGE;
    }

    private String payloadJson(Long watchProductId, int keywordTotal, String triggerMode) {
        return "{"
                + "\"watchProductId\":" + watchProductId
                + ",\"keywordTotal\":" + keywordTotal
                + ",\"triggerMode\":\"" + json(triggerMode) + "\""
                + "}";
    }

    private String resultJson(String status, int success, int failed) {
        return "{"
                + "\"status\":\"" + status + "\""
                + ",\"keywordSuccess\":" + success
                + ",\"keywordFailed\":" + failed
                + "}";
    }

    private String monitorPayloadJson(String triggerMode, int watchProductTotal) {
        return "{"
                + "\"triggerMode\":\"" + json(triggerMode) + "\""
                + ",\"watchProductTotal\":" + watchProductTotal
                + "}";
    }

    private String monitorResultJson(String triggerMode, int total, int submitted, int failed) {
        return "{"
                + "\"triggerMode\":\"" + json(triggerMode) + "\""
                + ",\"watchProductTotal\":" + total
                + ",\"submittedCount\":" + submitted
                + ",\"failedCount\":" + failed
                + "}";
    }

    private String monitorNaturalKey(Long ownerUserId, String storeCode, String siteCode) {
        return MONITOR_NATURAL_KEY_PREFIX
                + ownerUserId
                + ":"
                + normalize(storeCode)
                + ":"
                + normalize(siteCode);
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
            mapper.markSearchRunFailed(runId, errorCode, message);
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
            operationalTaskService.fail(taskId, errorCode, message);
        } catch (RuntimeException ignored) {
            log.debug("operational task {} was already terminal while recording refresh failure", taskId);
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static TaskSubmitter queueSubmitter(NoonAccountTaskQueue noonAccountTaskQueue) {
        return noonAccountTaskQueue == null ? (accountKey, task) -> task.run() : noonAccountTaskQueue::submit;
    }

    @FunctionalInterface
    interface TaskSubmitter {
        void submit(String accountKey, Runnable task);
    }
}
