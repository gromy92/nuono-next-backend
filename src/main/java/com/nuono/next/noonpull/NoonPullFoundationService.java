package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonPullFoundationService {
    private static final Long PLAN_ID_INITIAL_VALUE = 120000L;
    private static final Long TASK_ID_INITIAL_VALUE = 130000L;
    private static final Pattern COOKIE_PATTERN = Pattern.compile("(?i)(cookie\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd)(\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s;]+");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s;]+");

    private final NoonPullRepository repository;
    private final Clock clock;
    private final NoonPullFailurePolicy failurePolicy;

    @Autowired
    public NoonPullFoundationService(NoonPullRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public NoonPullFoundationService(NoonPullRepository repository, Clock clock) {
        this(repository, clock, new NoonPullFailurePolicy(clock));
    }

    public NoonPullFoundationService(NoonPullRepository repository, Clock clock, NoonPullFailurePolicy failurePolicy) {
        this.repository = repository;
        this.clock = clock;
        this.failurePolicy = failurePolicy;
    }

    public NoonPullPlanRecord createPlan(NoonPullPlanDraft draft) {
        requirePlanDraft(draft);
        LocalDateTime now = now();
        NoonPullPlanRecord plan = new NoonPullPlanRecord();
        plan.setId(repository.nextId("noon_pull_plan", PLAN_ID_INITIAL_VALUE));
        plan.setOwnerUserId(draft.getOwnerUserId());
        plan.setStoreCode(normalize(draft.getStoreCode()));
        plan.setSiteCode(normalize(draft.getSiteCode()));
        plan.setPullType(draft.getPullType());
        plan.setDataDomain(draft.getDataDomain());
        plan.setTriggerMode(draft.getTriggerMode());
        plan.setScheduleExpression(draft.getScheduleExpression());
        plan.setMaxPagesPerRun(draft.getMaxPagesPerRun());
        plan.setMaxProductsPerRun(draft.getMaxProductsPerRun());
        plan.setMaxDetailFetchesPerRun(draft.getMaxDetailFetchesPerRun());
        plan.setMaxRequestsPerRun(draft.getMaxRequestsPerRun());
        plan.setCooldownSeconds(draft.getCooldownSeconds());
        plan.setConcurrencyLimit(draft.getConcurrencyLimit());
        plan.setEnabled(true);
        plan.setPaused(false);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        repository.insertPlan(plan);
        return plan.copy();
    }

    public Optional<NoonPullTaskRecord> createTaskForPlan(Long planId, NoonPullTaskDraft draft) {
        NoonPullPlanRecord plan = requirePlan(planId);
        if (!plan.isEnabled() || plan.isPaused()) {
            return Optional.empty();
        }
        requireTaskDraft(draft);
        String activeLockKey = activeLockKey(draft);
        NoonPullTaskRecord activeTask = repository.selectActiveTaskByLockKey(activeLockKey);
        if (activeTask != null) {
            return Optional.of(activeTask);
        }
        NoonPullTaskRecord latestTask = repository.selectLatestTaskByLockKey(activeLockKey);
        if (isTerminalDuplicate(latestTask, draft.getTriggerMode())) {
            return Optional.empty();
        }

        LocalDateTime now = now();
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(repository.nextId("noon_pull_task", TASK_ID_INITIAL_VALUE));
        task.setPlanId(planId);
        task.setOwnerUserId(draft.getOwnerUserId());
        task.setStoreCode(normalize(draft.getStoreCode()));
        task.setSiteCode(normalize(draft.getSiteCode()));
        task.setPullType(draft.getPullType());
        task.setDataDomain(draft.getDataDomain());
        task.setTriggerMode(draft.getTriggerMode());
        task.setTargetIdentity(normalize(draft.getTargetIdentity()));
        task.setTargetDateFrom(draft.getTargetDateFrom());
        task.setTargetDateTo(draft.getTargetDateTo());
        task.setActiveLockKey(activeLockKey);
        task.setStatus(NoonPullTaskStatus.QUEUED);
        task.setQueuedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        repository.insertTask(task);
        return Optional.of(task.copy());
    }

    private boolean isTerminalDuplicate(NoonPullTaskRecord task, NoonPullTriggerMode triggerMode) {
        if (task == null || task.getStatus() == null || !suppressesTerminalDuplicates(triggerMode)) {
            return false;
        }
        if (task.getStatus() == NoonPullTaskStatus.SUCCEEDED || task.getStatus() == NoonPullTaskStatus.PARTIAL) {
            return true;
        }
        if (task.getStatus() == NoonPullTaskStatus.FAILED) {
            return !Boolean.TRUE.equals(task.getRetryable());
        }
        return false;
    }

    private boolean suppressesTerminalDuplicates(NoonPullTriggerMode triggerMode) {
        return triggerMode == NoonPullTriggerMode.SCHEDULED_DAILY
                || triggerMode == NoonPullTriggerMode.GAP_BACKFILL
                || triggerMode == NoonPullTriggerMode.LOW_FREQUENCY_CORRECTION;
    }

    public NoonPullTaskRecord markRunning(Long taskId, String workerId) {
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(NoonPullTaskStatus.RUNNING);
        task.setLockedBy(workerId);
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);
        return task.copy();
    }

    public NoonPullTaskRecord markSucceeded(Long taskId, String sourceBatchId, String diagnosticSummary) {
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(NoonPullTaskStatus.SUCCEEDED);
        task.setSourceBatchId(sourceBatchId);
        task.setFailureType(null);
        task.setRetryAction(null);
        task.setRetryable(null);
        task.setRequiresManualAction(null);
        task.setDiagnosticSummary(redact(diagnosticSummary));
        task.setNextResumePosition(null);
        task.setReadinessState("ready");
        task.setReportNextPollAt(null);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestSuccessAt(now);
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
        return task.copy();
    }

    public NoonPullTaskRecord markPartial(
            Long taskId,
            String sourceBatchId,
            String diagnosticSummary,
            String checkpointCursor,
            Integer processedItemCount,
            Integer requestCount,
            String nextResumePosition,
            String lastSafeResponseSummary,
            String readinessState
    ) {
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(NoonPullTaskStatus.PARTIAL);
        task.setSourceBatchId(sourceBatchId);
        task.setDiagnosticSummary(redact(diagnosticSummary));
        task.setCheckpointCursor(checkpointCursor);
        task.setProcessedItemCount(processedItemCount);
        task.setRequestCount(requestCount);
        task.setNextResumePosition(nextResumePosition);
        task.setLastSafeResponseSummary(redact(lastSafeResponseSummary));
        task.setReadinessState(readinessState);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);
        return task.copy();
    }

    public NoonPullTaskRecord recordProgress(
            Long taskId,
            String checkpointCursor,
            Integer processedItemCount,
            Integer requestCount,
            String nextResumePosition,
            String lastSafeResponseSummary,
            String readinessState
    ) {
        NoonPullTaskRecord task = requireTask(taskId);
        task.setCheckpointCursor(checkpointCursor);
        task.setProcessedItemCount(processedItemCount);
        task.setRequestCount(requestCount);
        task.setNextResumePosition(nextResumePosition);
        task.setLastSafeResponseSummary(redact(lastSafeResponseSummary));
        task.setReadinessState(readinessState);
        task.setUpdatedAt(now());
        repository.updateTask(task);
        return task.copy();
    }

    public NoonPullTaskRecord recordReportExportCreated(Long taskId, String exportId, String diagnosticSummary) {
        return updateReportExportTask(
                taskId,
                NoonPullTaskStatus.RUNNING,
                exportId,
                "CREATED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                diagnosticSummary,
                "export_created",
                false
        );
    }

    public NoonPullTaskRecord recordReportExportPollResult(
            Long taskId,
            String exportId,
            NoonReportExportStatus exportStatus,
            int pollAttempts,
            Duration nextPollDelay,
            String diagnosticSummary
    ) {
        NoonReportExportStatus safeStatus = exportStatus == null ? NoonReportExportStatus.pending() : exportStatus;
        return updateReportExportTask(
                taskId,
                NoonPullTaskStatus.RUNNING,
                exportId,
                safeStatus.getStatus(),
                safeStatus.getDownloadUrl(),
                safeStatus.getTotalRows(),
                now(),
                nextPollAt(nextPollDelay),
                pollAttempts,
                null,
                null,
                null,
                null,
                diagnosticSummary,
                safeStatus.isReady() ? "export_ready" : "export_pending",
                false
        );
    }

    public NoonPullTaskRecord recordReportExportTransientFailure(
            Long taskId,
            String exportId,
            String exportStatus,
            int pollAttempts,
            String rawFailure
    ) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        NoonPullFailureDecision decision = failurePolicy.decide(failureType, Math.max(1, pollAttempts));
        LocalDateTime nextPollAt = decision.getNextRetryAt() == null ? now().plusMinutes(30) : decision.getNextRetryAt();
        NoonPullTaskRecord task = updateReportExportTask(
                taskId,
                NoonPullTaskStatus.RUNNING,
                exportId,
                StringUtils.hasText(exportStatus) ? exportStatus : "POLL_ERROR",
                null,
                null,
                now(),
                nextPollAt,
                pollAttempts,
                failureType.code(),
                decision.getAction().name(),
                decision.isRetryable(),
                decision.requiresManualAction(),
                rawFailure + " | " + decision.getSummary(),
                "export_retry_wait",
                false
        );

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestFailureAt(now());
        plan.setLatestFailureType(failureType.code());
        plan.setNextRetryAt(nextPollAt);
        plan.setUpdatedAt(now());
        repository.updatePlan(plan);
        return task;
    }

    public NoonPullTaskRecord markReportExportAcceptedEmpty(
            Long taskId,
            String sourceBatchId,
            String diagnosticSummary
    ) {
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(NoonPullTaskStatus.SUCCEEDED);
        task.setSourceBatchId(sourceBatchId);
        task.setFailureType(null);
        task.setRetryAction(null);
        task.setRetryable(null);
        task.setRequiresManualAction(null);
        task.setDiagnosticSummary(redact(diagnosticSummary));
        task.setNextResumePosition(null);
        task.setLastSafeResponseSummary(null);
        task.setReadinessState("confirmed_empty");
        task.setReportNextPollAt(null);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestSuccessAt(now);
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
        return task.copy();
    }

    public NoonPullTaskRecord recordReportRiskBackoffDelay(
            Long taskId,
            NoonRiskBackoffHold hold,
            String diagnosticPrefix
    ) {
        if (hold == null) {
            throw new IllegalArgumentException("Noon risk backoff hold is required.");
        }
        String riskType = StringUtils.hasText(hold.getRiskType())
                ? hold.getRiskType()
                : NoonPullFailureType.BLOCKED_BY_RISK_CONTROL.code();
        NoonPullTaskRecord task = updateReportExportTask(
                taskId,
                NoonPullTaskStatus.RUNNING,
                null,
                null,
                null,
                null,
                null,
                hold.getBlockedUntil(),
                null,
                riskType,
                NoonPullRetryAction.DELAY.name(),
                Boolean.TRUE,
                Boolean.FALSE,
                riskBackoffSummary(hold, diagnosticPrefix),
                "risk_backoff",
                false
        );

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        LocalDateTime now = now();
        plan.setLatestFailureAt(now);
        plan.setLatestFailureType(riskType);
        plan.setNextRetryAt(hold.getBlockedUntil());
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
        return task;
    }

    public NoonPullTaskRecord recordInterfaceRiskBackoffDelay(
            Long taskId,
            NoonRiskBackoffHold hold,
            String diagnosticPrefix
    ) {
        if (hold == null) {
            throw new IllegalArgumentException("Noon risk backoff hold is required.");
        }
        String riskType = StringUtils.hasText(hold.getRiskType())
                ? hold.getRiskType()
                : NoonPullFailureType.BLOCKED_BY_RISK_CONTROL.code();
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(NoonPullTaskStatus.FAILED);
        task.setFailureType(riskType);
        task.setRetryAction(NoonPullRetryAction.DELAY.name());
        task.setRetryable(Boolean.TRUE);
        task.setRequiresManualAction(Boolean.FALSE);
        task.setDiagnosticSummary(redact(riskBackoffSummary(hold, diagnosticPrefix)));
        task.setReadinessState("risk_backoff");
        task.setReportNextPollAt(hold.getBlockedUntil());
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestFailureAt(now);
        plan.setLatestFailureType(riskType);
        plan.setNextRetryAt(hold.getBlockedUntil());
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
        return task.copy();
    }

    public NoonPullTaskRecord markReportExportPendingConfirmation(
            Long taskId,
            String sourceBatchId,
            String diagnosticSummary,
            Duration nextPollDelay
    ) {
        return updateReportExportTask(
                taskId,
                NoonPullTaskStatus.RUNNING,
                null,
                null,
                null,
                null,
                null,
                nextPollAt(nextPollDelay == null ? Duration.ofHours(6) : nextPollDelay),
                null,
                NoonPullFailureType.EMPTY_REPORT_PENDING_CONFIRMATION.code(),
                NoonPullRetryAction.DELAY.name(),
                Boolean.TRUE,
                Boolean.FALSE,
                diagnosticSummary,
                "pending_confirmation",
                false,
                sourceBatchId
        );
    }

    public NoonPullTaskRecord markReportExportConfirmedEmpty(
            Long taskId,
            String sourceBatchId,
            String diagnosticSummary
    ) {
        NoonPullTaskRecord task = updateReportExportTask(
                taskId,
                NoonPullTaskStatus.FAILED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                NoonPullFailureType.CONFIRMED_EMPTY.code(),
                NoonPullRetryAction.NONE.name(),
                Boolean.FALSE,
                Boolean.FALSE,
                diagnosticSummary,
                "confirmed_empty",
                true,
                sourceBatchId
        );

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestFailureAt(now());
        plan.setLatestFailureType(NoonPullFailureType.CONFIRMED_EMPTY.code());
        plan.setUpdatedAt(now());
        repository.updatePlan(plan);
        return task;
    }

    public NoonPullTaskRecord markFailed(Long taskId, String failureType, String diagnosticSummary) {
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(NoonPullTaskStatus.FAILED);
        task.setFailureType(normalize(failureType));
        task.setDiagnosticSummary(redact(diagnosticSummary));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestFailureAt(now);
        plan.setLatestFailureType(task.getFailureType());
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
        return task.copy();
    }

    public NoonPullTaskRecord markFailedWithPolicy(Long taskId, String rawFailure, int attempt) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        NoonPullFailureDecision decision = failurePolicy.decide(failureType, attempt);
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(failureType == NoonPullFailureType.PARTIAL_SUCCESS
                ? NoonPullTaskStatus.PARTIAL
                : NoonPullTaskStatus.FAILED);
        task.setFailureType(failureType.code());
        task.setRetryAction(decision.getAction().name());
        task.setRetryable(decision.isRetryable());
        task.setRequiresManualAction(decision.requiresManualAction());
        task.setDiagnosticSummary(redact(rawFailure + " | " + decision.getSummary()));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestFailureAt(now);
        plan.setLatestFailureType(failureType.code());
        plan.setNextRetryAt(decision.getNextRetryAt());
        if (decision.shouldPausePlan()) {
            plan.setPaused(true);
            plan.setPauseReason(decision.getSummary());
        }
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
        return task.copy();
    }

    public NoonPullPlanRecord pausePlan(Long planId, String reason) {
        NoonPullPlanRecord plan = requirePlan(planId);
        plan.setPaused(true);
        plan.setPauseReason(reason);
        plan.setUpdatedAt(now());
        repository.updatePlan(plan);
        return plan.copy();
    }

    public NoonPullPlanRecord resumePlan(Long planId) {
        NoonPullPlanRecord plan = requirePlan(planId);
        plan.setPaused(false);
        plan.setPauseReason(null);
        plan.setUpdatedAt(now());
        repository.updatePlan(plan);
        return plan.copy();
    }

    public NoonPullTaskRecord retryTask(Long taskId) {
        NoonPullTaskRecord task = requireTask(taskId);
        if (!Boolean.TRUE.equals(task.getRetryable()) || Boolean.TRUE.equals(task.getRequiresManualAction())) {
            throw new IllegalStateException("Noon pull task is not retryable without manual action: " + taskId);
        }
        return createTaskForPlan(task.getPlanId(), NoonPullTaskDraft.builder()
                .ownerUserId(task.getOwnerUserId())
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .pullType(task.getPullType())
                .dataDomain(task.getDataDomain())
                .triggerMode(task.getTriggerMode())
                .targetIdentity(task.getTargetIdentity())
                .targetDateFrom(task.getTargetDateFrom())
                .targetDateTo(task.getTargetDateTo())
                .build()).orElseThrow();
    }

    public int recoverStaleRunningTasks(Duration maxRunningAge) {
        Duration safeMaxAge = maxRunningAge == null || maxRunningAge.isNegative() || maxRunningAge.isZero()
                ? Duration.ofHours(2)
                : maxRunningAge;
        LocalDateTime threshold = now().minus(safeMaxAge);
        int recovered = 0;
        for (NoonPullTaskRecord task : repository.listActiveTasks()) {
            if (task.getStatus() != NoonPullTaskStatus.RUNNING || task.getStartedAt() == null) {
                continue;
            }
            if (StringUtils.hasText(task.getReportExportId())) {
                continue;
            }
            if (task.getStartedAt().isAfter(threshold)) {
                continue;
            }
            markFailedWithPolicy(
                    task.getId(),
                    "timeout: stale running task exceeded " + safeMaxAge.toMinutes() + " minutes",
                    1
            );
            recovered++;
        }
        return recovered;
    }

    public int recoverStaleQueuedTasks(Duration maxQueuedAge) {
        Duration safeMaxAge = maxQueuedAge == null || maxQueuedAge.isNegative() || maxQueuedAge.isZero()
                ? Duration.ofMinutes(30)
                : maxQueuedAge;
        LocalDateTime threshold = now().minus(safeMaxAge);
        int recovered = 0;
        for (NoonPullTaskRecord task : repository.listActiveTasks()) {
            if (task.getStatus() != NoonPullTaskStatus.QUEUED || task.getQueuedAt() == null) {
                continue;
            }
            if (task.getQueuedAt().isAfter(threshold)) {
                continue;
            }
            markStaleQueuedFailed(task, safeMaxAge);
            recovered++;
        }
        return recovered;
    }

    private void markStaleQueuedFailed(NoonPullTaskRecord task, Duration safeMaxAge) {
        LocalDateTime now = now();
        task.setStatus(NoonPullTaskStatus.FAILED);
        task.setFailureType("timeout");
        task.setRetryAction(NoonPullRetryAction.RETRY.name());
        task.setRetryable(Boolean.TRUE);
        task.setRequiresManualAction(Boolean.FALSE);
        task.setDiagnosticSummary(redact("timeout: stale queued task exceeded " + safeMaxAge.toMinutes() + " minutes"));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.updateTask(task);

        NoonPullPlanRecord plan = requirePlan(task.getPlanId());
        plan.setLatestFailureAt(now);
        plan.setLatestFailureType(task.getFailureType());
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
    }

    public boolean isTaskPlanActive(NoonPullTaskRecord task) {
        if (task == null || task.getPlanId() == null) {
            return false;
        }
        NoonPullPlanRecord plan = repository.selectPlan(task.getPlanId());
        return plan != null && plan.isEnabled() && !plan.isPaused();
    }

    public List<NoonPullPlanRecord> listPlans() {
        return repository.listPlans();
    }

    public List<NoonPullTaskRecord> listTasks() {
        return repository.listTasks();
    }

    public List<NoonPullTaskRecord> listActiveTasks() {
        return repository.listActiveTasks();
    }

    private NoonPullPlanRecord requirePlan(Long planId) {
        NoonPullPlanRecord plan = repository.selectPlan(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Noon pull plan not found: " + planId);
        }
        return plan;
    }

    private NoonPullTaskRecord requireTask(Long taskId) {
        NoonPullTaskRecord task = repository.selectTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Noon pull task not found: " + taskId);
        }
        return task;
    }

    private NoonPullTaskRecord updateReportExportTask(
            Long taskId,
            NoonPullTaskStatus status,
            String exportId,
            String exportStatus,
            String downloadUrl,
            Integer totalRows,
            LocalDateTime lastPollAt,
            LocalDateTime nextPollAt,
            Integer pollAttempts,
            String failureType,
            String retryAction,
            Boolean retryable,
            Boolean requiresManualAction,
            String diagnosticSummary,
            String readinessState,
            boolean finished
    ) {
        return updateReportExportTask(
                taskId,
                status,
                exportId,
                exportStatus,
                downloadUrl,
                totalRows,
                lastPollAt,
                nextPollAt,
                pollAttempts,
                failureType,
                retryAction,
                retryable,
                requiresManualAction,
                diagnosticSummary,
                readinessState,
                finished,
                null
        );
    }

    private NoonPullTaskRecord updateReportExportTask(
            Long taskId,
            NoonPullTaskStatus status,
            String exportId,
            String exportStatus,
            String downloadUrl,
            Integer totalRows,
            LocalDateTime lastPollAt,
            LocalDateTime nextPollAt,
            Integer pollAttempts,
            String failureType,
            String retryAction,
            Boolean retryable,
            Boolean requiresManualAction,
            String diagnosticSummary,
            String readinessState,
            boolean finished,
            String sourceBatchId
    ) {
        NoonPullTaskRecord task = requireTask(taskId);
        LocalDateTime now = now();
        task.setStatus(status == null ? task.getStatus() : status);
        if (StringUtils.hasText(sourceBatchId)) {
            task.setSourceBatchId(sourceBatchId);
        }
        if (StringUtils.hasText(exportId)) {
            task.setReportExportId(exportId.trim());
        }
        if (StringUtils.hasText(exportStatus)) {
            task.setReportExportStatus(exportStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(downloadUrl)) {
            task.setReportDownloadUrl(downloadUrl.trim());
        }
        if (totalRows != null) {
            task.setReportTotalRows(Math.max(0, totalRows));
        }
        if (lastPollAt != null) {
            task.setReportLastPollAt(lastPollAt);
        }
        task.setReportNextPollAt(nextPollAt);
        if (pollAttempts != null) {
            task.setReportPollAttempts(Math.max(0, pollAttempts));
        }
        task.setFailureType(normalize(failureType));
        task.setRetryAction(retryAction);
        task.setRetryable(retryable);
        task.setRequiresManualAction(requiresManualAction);
        task.setDiagnosticSummary(redact(diagnosticSummary));
        task.setReadinessState(readinessState);
        task.setFinishedAt(finished ? now : null);
        task.setUpdatedAt(now);
        repository.updateTask(task);
        return task.copy();
    }

    private LocalDateTime nextPollAt(Duration delay) {
        Duration safeDelay = delay == null || delay.isNegative() || delay.isZero()
                ? Duration.ofMinutes(15)
                : delay;
        return now().plus(safeDelay);
    }

    private String riskBackoffSummary(NoonRiskBackoffHold hold, String diagnosticPrefix) {
        StringBuilder summary = new StringBuilder();
        if (StringUtils.hasText(diagnosticPrefix)) {
            summary.append(diagnosticPrefix).append("; ");
        }
        summary.append("noon risk backoff active")
                .append("; riskType=").append(hold.getRiskType())
                .append("; attemptCount=").append(hold.getAttemptCount())
                .append("; blockedUntil=").append(hold.getBlockedUntil())
                .append("; sourceDomain=").append(hold.getSourceDomain())
                .append("; sourceTaskId=").append(hold.getSourceTaskId());
        if (StringUtils.hasText(hold.getDiagnosticSummary())) {
            summary.append("; ").append(hold.getDiagnosticSummary());
        }
        return summary.toString();
    }

    private void requirePlanDraft(NoonPullPlanDraft draft) {
        if (draft == null || draft.getOwnerUserId() == null || draft.getPullType() == null
                || draft.getDataDomain() == null || draft.getTriggerMode() == null) {
            throw new IllegalArgumentException("Noon pull plan scope, type, domain and trigger are required.");
        }
    }

    private void requireTaskDraft(NoonPullTaskDraft draft) {
        if (draft == null || draft.getOwnerUserId() == null || draft.getPullType() == null
                || draft.getDataDomain() == null || draft.getTriggerMode() == null) {
            throw new IllegalArgumentException("Noon pull task scope, type, domain and trigger are required.");
        }
    }

    private String activeLockKey(NoonPullTaskDraft draft) {
        String target = StringUtils.hasText(draft.getTargetIdentity())
                ? normalize(draft.getTargetIdentity())
                : String.valueOf(draft.getTargetDateFrom()) + ".." + String.valueOf(draft.getTargetDateTo());
        return "owner:" + draft.getOwnerUserId()
                + "|store:" + normalize(draft.getStoreCode())
                + "|site:" + normalize(draft.getSiteCode())
                + "|type:" + draft.getPullType().name()
                + "|domain:" + draft.getDataDomain().name()
                + "|target:" + target;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String redact(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = COOKIE_PATTERN.matcher(value).replaceAll("$1[redacted]");
        redacted = PASSWORD_PATTERN.matcher(redacted).replaceAll("$1$2[redacted]");
        redacted = BEARER_PATTERN.matcher(redacted).replaceAll("$1[redacted]");
        redacted = API_KEY_PATTERN.matcher(redacted).replaceAll("$1[redacted]");
        return redacted.toLowerCase(Locale.ROOT).contains("token-") ? redacted.replaceAll("token-[^\\s;]+", "[redacted]") : redacted;
    }
}
