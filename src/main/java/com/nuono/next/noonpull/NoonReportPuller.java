package com.nuono.next.noonpull;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonReportPuller {
    private static final Duration EXPORT_PENDING_POLL_DELAY = Duration.ofMinutes(20);

    private final NoonPullFoundationService foundationService;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final NoonPullFailurePolicy failurePolicy;

    @Autowired
    public NoonReportPuller(
            NoonPullFoundationService foundationService,
            ObjectProvider<NoonRiskBackoffGuard> riskBackoffGuard,
            ObjectProvider<NoonPullFailurePolicy> failurePolicy
    ) {
        this(
                foundationService,
                riskBackoffGuard == null
                        ? NoonRiskBackoffGuard.disabled()
                        : riskBackoffGuard.getIfAvailable(NoonRiskBackoffGuard::disabled),
                failurePolicy == null ? new NoonPullFailurePolicy() : failurePolicy.getIfAvailable(NoonPullFailurePolicy::new)
        );
    }

    public NoonReportPuller(NoonPullFoundationService foundationService) {
        this(foundationService, NoonRiskBackoffGuard.disabled(), new NoonPullFailurePolicy());
    }

    NoonReportPuller(
            NoonPullFoundationService foundationService,
            NoonRiskBackoffGuard riskBackoffGuard,
            NoonPullFailurePolicy failurePolicy
    ) {
        this.foundationService = foundationService;
        this.riskBackoffGuard = riskBackoffGuard == null ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failurePolicy = failurePolicy == null ? new NoonPullFailurePolicy() : failurePolicy;
    }

    public NoonReportPullResult execute(
            Long taskId,
            NoonReportPullRequest request,
            NoonReportProvider provider,
            NoonReportDownloadedFileHandler handler
    ) {
        NoonPullTaskRecord task = foundationService.markRunning(taskId, "noon-report-puller");
        NoonReportPullResult result = new NoonReportPullResult();
        if (task.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            result.setStatus(task.getStatus());
            return result;
        }
        int pollAttempts = task.getReportPollAttempts() == null ? 0 : task.getReportPollAttempts();
        Optional<NoonRiskBackoffHold> activeHold = riskBackoffGuard.currentHold(NoonRiskBackoffScope.report(request));
        if (activeHold.isPresent()) {
            NoonPullTaskRecord delayed = foundationService.recordReportRiskBackoffDelay(
                    taskId,
                    activeHold.get(),
                    request.descriptor()
            );
            result.setStatus(delayed.getStatus());
            return result;
        }
        String exportId = task.getReportExportId();
        try {
            if (!StringUtils.hasText(exportId)) {
                NoonReportCreateCoordinator.Attempt create = NoonReportCreateCoordinator.ensureHandle(
                        taskId,
                        task,
                        request.descriptor(),
                        () -> provider.createExport(request),
                        foundationService,
                        failurePolicy
                );
                task = create.task();
                if (create.isWaiting()) {
                    result.setStatus(task.getStatus());
                    return result;
                }
                exportId = create.exportId();
                pollAttempts = task.getReportPollAttempts() == null ? 0 : task.getReportPollAttempts();
            }

            pollAttempts++;
            NoonReportExportStatus status;
            try {
                status = provider.pollExport(request, exportId);
            } catch (RuntimeException exception) {
                Optional<NoonRiskBackoffHold> hold = recordRiskBackoffIfNeeded(request, taskId, safeMessage(exception));
                if (hold.isPresent()) {
                    NoonPullTaskRecord delayed = foundationService.recordReportRiskBackoffDelay(
                            taskId,
                            hold.get(),
                            request.descriptor(),
                            pollAttempts
                    );
                    result.setStatus(delayed.getStatus());
                    return result;
                }
                NoonPullTaskRecord retrying = foundationService.recordReportExportTransientFailure(
                        taskId,
                        exportId,
                        task.getReportExportStatus(),
                        pollAttempts,
                        safeMessage(exception)
                );
                result.setStatus(retrying.getStatus());
                return result;
            }
            status = status == null ? NoonReportExportStatus.pending() : status;
            foundationService.recordReportExportPollResult(
                    taskId,
                    exportId,
                    status,
                    pollAttempts,
                    status.isReady() || status.isFailed() ? null : jitteredDelay(taskId, exportId, EXPORT_PENDING_POLL_DELAY),
                    exportPollSummary(request, status, pollAttempts)
            );

            if (status.isFailed()) {
                NoonPullTaskRecord failed = markFailedOrRiskBackoff(
                        taskId,
                        request,
                        "provider unavailable: report export failed " + status.getMessage(),
                        pollAttempts
                );
                result.setStatus(failed.getStatus());
                return result;
            }
            if (!status.isReady()) {
                NoonPullTaskRecord pending = foundationService.recordReportExportPollResult(
                        taskId,
                        exportId,
                        status,
                        pollAttempts,
                        jitteredDelay(taskId, exportId, EXPORT_PENDING_POLL_DELAY),
                        exportPollSummary(request, status, pollAttempts)
                );
                result.setStatus(pending.getStatus());
                return result;
            }
            if (!StringUtils.hasText(status.getDownloadUrl())) {
                String reason = status.getTotalRows() != null && status.getTotalRows() == 0
                        ? "report not ready: authoritative_empty_proof_unavailable; "
                        : "provider unavailable: report_ready_locator_missing; ";
                NoonPullTaskRecord retrying = retrySameExport(
                        taskId, exportId, status, pollAttempts, reason,
                        exportPollSummary(request, status, pollAttempts)
                );
                result.setStatus(retrying.getStatus());
                return result;
            }
            byte[] content;
            try {
                content = provider.download(request, status.getDownloadUrl());
            } catch (RuntimeException exception) {
                Optional<NoonRiskBackoffHold> hold = recordRiskBackoffIfNeeded(request, taskId, safeMessage(exception));
                if (hold.isPresent()) {
                    NoonPullTaskRecord delayed = foundationService.recordReportRiskBackoffDelay(
                            taskId,
                            hold.get(),
                            request.descriptor(),
                            pollAttempts
                    );
                    result.setStatus(delayed.getStatus());
                    return result;
                }
                NoonPullTaskRecord retrying = foundationService.recordReportExportTransientFailure(
                        taskId,
                        exportId,
                        "READY",
                        pollAttempts,
                        safeMessage(exception)
                );
                result.setStatus(retrying.getStatus());
                return result;
            }
            String digest = NoonReportDigest.sha256(content);
            String sourceBatchId = sourceBatchId(request, taskId, digest);
            NoonReportDownloadedFile file = new NoonReportDownloadedFile(request, exportId, sourceBatchId, digest, content);
            NoonReportProcessResult processResult = handler.handle(file);
            foundationService.recordReportExportPollResult(
                    taskId,
                    exportId,
                    status,
                    pollAttempts,
                    null,
                    exportDownloadSummary(request, status, digest, processResult)
            );
            result.setSourceBatchId(sourceBatchId);
            result.setFileDigestSha256(digest);
            result.setImportedCount(processResult.getImportedCount());
            result.setExceptionCount(processResult.getExceptionCount());
            if (processResult.getCode() == NoonReportProcessResult.Code.SUCCEEDED
                    || processResult.getCode()
                    == NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS) {
                result.setStatus(foundationService.markSucceeded(
                        taskId,
                        sourceBatchId,
                        summary(request, digest, processResult)
                ).getStatus());
                riskBackoffGuard.recordSuccess(NoonRiskBackoffScope.report(request), sourceDomain(request));
                return result;
            }
            if (processResult.getCode() == NoonReportProcessResult.Code.EMPTY_REPORT
                    || processResult.getCode()
                    == NoonReportProcessResult.Code.EMPTY_REPORT_PENDING_CONFIRMATION) {
                NoonPullTaskRecord awaitingProof = retrySameExport(
                        taskId,
                        exportId,
                        status,
                        pollAttempts,
                        "report not ready: authoritative_empty_proof_unavailable; ",
                        exportDownloadSummary(request, status, digest, processResult)
                );
                result.setStatus(awaitingProof.getStatus());
                return result;
            }
            NoonPullTaskRecord rejected = retrySameExport(
                    taskId,
                    exportId,
                    status,
                    pollAttempts,
                    "provider unavailable: report_payload_contract_rejected; ",
                    reportContentDiagnostic(processResult)
            );
            result.setStatus(rejected.getStatus());
            return result;
        } catch (RuntimeException exception) {
            String failure = safeMessage(exception);
            Optional<NoonRiskBackoffHold> hold = recordRiskBackoffIfNeeded(request, taskId, failure);
            if (hold.isPresent()) {
                NoonPullTaskRecord delayed = foundationService.recordReportRiskBackoffDelay(
                        taskId,
                        hold.get(),
                        request.descriptor(),
                        Math.max(1, pollAttempts)
                );
                result.setStatus(delayed.getStatus());
                return result;
            }
            if (StringUtils.hasText(exportId)) {
                NoonPullTaskRecord retrying = foundationService.recordReportExportTransientFailure(
                        taskId,
                        exportId,
                        null,
                        Math.max(1, pollAttempts),
                        safeMessage(exception)
                );
                result.setStatus(retrying.getStatus());
            } else {
                NoonPullTaskRecord failed = markFailedOrRiskBackoff(taskId, request, safeMessage(exception), 1);
                result.setStatus(failed.getStatus());
            }
            return result;
        }
    }

    private NoonPullTaskRecord markFailedOrRiskBackoff(
            Long taskId,
            NoonReportPullRequest request,
            String rawFailure,
            int attempt
    ) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        if (!isRiskBackoffFailure(failureType)) {
            return foundationService.markFailedWithPolicy(taskId, rawFailure, attempt);
        }
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.report(request),
                failureType.code(),
                sourceDomain(request),
                taskId,
                null,
                rawFailure
        );
        return foundationService.recordReportRiskBackoffDelay(taskId, hold, request.descriptor(), attempt);
    }

    private Optional<NoonRiskBackoffHold> recordRiskBackoffIfNeeded(
            NoonReportPullRequest request,
            Long taskId,
            String rawFailure
    ) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        if (!isRiskBackoffFailure(failureType)) {
            return Optional.empty();
        }
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.report(request),
                failureType.code(),
                sourceDomain(request),
                taskId,
                null,
                rawFailure
        );
        return Optional.of(hold);
    }

    private NoonPullTaskRecord retrySameExport(
            Long taskId,
            String exportId,
            NoonReportExportStatus status,
            int pollAttempts,
            String reason,
            String diagnostic
    ) {
        return foundationService.recordReportExportTransientFailure(
                taskId,
                exportId,
                status == null ? null : status.getStatus(),
                pollAttempts,
                reason + diagnostic
        );
    }

    private boolean isRiskBackoffFailure(NoonPullFailureType failureType) {
        return failureType == NoonPullFailureType.RATE_LIMITED
                || failureType == NoonPullFailureType.CAPTCHA_REQUIRED
                || failureType == NoonPullFailureType.BLOCKED_BY_RISK_CONTROL;
    }

    private String sourceDomain(NoonReportPullRequest request) {
        return request == null || request.getDataDomain() == null ? null : request.getDataDomain().name();
    }

    private String sourceBatchId(NoonReportPullRequest request, Long taskId, String digest) {
        String domain = request.getDataDomain() == null
                ? "unknown"
                : request.getDataDomain().name().toLowerCase(Locale.ROOT);
        return "noon-report-" + domain + "-" + taskId + "-" + digest.substring(0, 8);
    }

    private String summary(NoonReportPullRequest request, String digest, NoonReportProcessResult result) {
        return request.descriptor()
                + "; digest=" + digest
                + "; imported=" + result.getImportedCount()
                + "; exceptions=" + result.getExceptionCount();
    }

    private String exportPollSummary(NoonReportPullRequest request, NoonReportExportStatus status, int pollAttempts) {
        return request.descriptor()
                + "; exportStatus=" + status.getStatus()
                + "; download=" + StringUtils.hasText(status.getDownloadUrl())
                + "; totalRows=" + (status.getTotalRows() == null ? "unknown" : status.getTotalRows())
                + "; pollAttempts=" + pollAttempts;
    }

    private String exportDownloadSummary(
            NoonReportPullRequest request,
            NoonReportExportStatus status,
            String digest,
            NoonReportProcessResult result
    ) {
        return request.descriptor()
                + "; exportStatus=" + status.getStatus()
                + "; download=true"
                + "; totalRows="
                + (status.getTotalRows() == null ? "unknown" : status.getTotalRows())
                + "; digest=" + digest
                + "; importedRows=" + result.getImportedCount()
                + "; exceptions=" + result.getExceptionCount()
                + diagnosticSuffix(result);
    }

    private String diagnosticSuffix(NoonReportProcessResult result) {
        return result != null && StringUtils.hasText(result.getDiagnosticMessage())
                ? "; " + result.getDiagnosticMessage()
                : "";
    }

    private String reportContentDiagnostic(NoonReportProcessResult result) {
        if (result == null) {
            return "container_state=missing_result";
        }
        return "container_state=rejected"
                + "; importedRows=" + result.getImportedCount()
                + "; exceptions=" + result.getExceptionCount()
                + diagnosticSuffix(result);
    }

    private Duration jitteredDelay(Long taskId, String exportId, Duration baseDelay) {
        Duration safeDelay = baseDelay == null ? Duration.ofMinutes(15) : baseDelay;
        int seed = String.valueOf(taskId).hashCode();
        if (StringUtils.hasText(exportId)) {
            seed = 31 * seed + exportId.hashCode();
        }
        int jitterSeconds = Math.floorMod(seed, 240);
        return safeDelay.plusSeconds(jitterSeconds);
    }

    private String safeMessage(RuntimeException exception) {
        if (exception == null) {
            return "unknown failure";
        }
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

}
