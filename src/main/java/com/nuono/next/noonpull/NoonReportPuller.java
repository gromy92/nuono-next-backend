package com.nuono.next.noonpull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final Duration EMPTY_CONFIRMATION_POLL_DELAY = Duration.ofHours(6);

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
        int pollAttempts = task.getReportPollAttempts() == null ? 0 : task.getReportPollAttempts();
        try {
            if (!StringUtils.hasText(exportId)) {
                exportId = provider.createExport(request);
                task = foundationService.recordReportExportCreated(
                        taskId,
                        exportId,
                        request.descriptor() + "; exportCreated=true; exportId=" + exportId
                );
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
                            request.descriptor()
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
                NoonPullTaskRecord failed = foundationService.markFailedWithPolicy(
                        taskId,
                        "mapping failed: missing download url",
                        pollAttempts
                );
                result.setStatus(failed.getStatus());
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
                            request.descriptor()
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
            String digest = sha256(content);
            String sourceBatchId = sourceBatchId(request, taskId, digest);
            NoonReportDownloadedFile file = new NoonReportDownloadedFile(request, exportId, sourceBatchId, digest, content);
            NoonReportProcessResult processResult = handler.handle(file);
            int totalRows = totalRows(status, processResult);
            foundationService.recordReportExportPollResult(
                    taskId,
                    exportId,
                    NoonReportExportStatus.ready(status.getDownloadUrl(), totalRows),
                    pollAttempts,
                    null,
                    exportDownloadSummary(request, status, digest, processResult, totalRows)
            );
            result.setSourceBatchId(sourceBatchId);
            result.setFileDigestSha256(digest);
            result.setImportedCount(processResult.getImportedCount());
            result.setExceptionCount(processResult.getExceptionCount());
            if (processResult.getCode() == NoonReportProcessResult.Code.SUCCEEDED) {
                NoonPullTaskRecord succeeded = foundationService.markSucceeded(
                        taskId,
                        sourceBatchId,
                        summary(request, digest, processResult)
                );
                result.setStatus(succeeded.getStatus());
                return result;
            }
            if (processResult.getCode() == NoonReportProcessResult.Code.EMPTY_REPORT_PENDING_CONFIRMATION) {
                NoonPullTaskRecord pending = foundationService.markReportExportPendingConfirmation(
                        taskId,
                        sourceBatchId,
                        emptyReportSummary(request, status, digest, processResult, totalRows),
                        jitteredDelay(taskId, exportId, EMPTY_CONFIRMATION_POLL_DELAY)
                );
                result.setStatus(pending.getStatus());
                return result;
            }
            if (processResult.getCode() == NoonReportProcessResult.Code.EMPTY_REPORT) {
                NoonPullTaskRecord confirmedEmpty = foundationService.markReportExportConfirmedEmpty(
                        taskId,
                        sourceBatchId,
                        emptyReportSummary(request, status, digest, processResult, totalRows) + "; confirmed_empty"
                );
                result.setStatus(confirmedEmpty.getStatus());
                return result;
            }
            NoonPullTaskRecord failed = foundationService.markFailedWithPolicy(
                    taskId,
                    failureMessage(processResult),
                    pollAttempts
            );
            result.setStatus(failed.getStatus());
            return result;
        } catch (RuntimeException exception) {
            Optional<NoonRiskBackoffHold> hold = recordRiskBackoffIfNeeded(request, taskId, safeMessage(exception));
            if (hold.isPresent()) {
                NoonPullTaskRecord delayed = foundationService.recordReportRiskBackoffDelay(
                        taskId,
                        hold.get(),
                        request.descriptor()
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
        return foundationService.recordReportRiskBackoffDelay(taskId, hold, request.descriptor());
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
            NoonReportProcessResult result,
            int totalRows
    ) {
        return request.descriptor()
                + "; exportStatus=" + status.getStatus()
                + "; download=true"
                + "; totalRows=" + totalRows
                + "; digest=" + digest
                + "; importedRows=" + result.getImportedCount()
                + "; exceptions=" + result.getExceptionCount()
                + diagnosticSuffix(result);
    }

    private String emptyReportSummary(
            NoonReportPullRequest request,
            NoonReportExportStatus status,
            String digest,
            NoonReportProcessResult result,
            int totalRows
    ) {
        return exportDownloadSummary(request, status, digest, result, totalRows);
    }

    private String diagnosticSuffix(NoonReportProcessResult result) {
        return result != null && StringUtils.hasText(result.getDiagnosticMessage())
                ? "; " + result.getDiagnosticMessage()
                : "";
    }

    private int totalRows(NoonReportExportStatus status, NoonReportProcessResult result) {
        if (status != null && status.getTotalRows() != null) {
            return status.getTotalRows();
        }
        if (result == null) {
            return 0;
        }
        return Math.max(0, result.getImportedCount() + result.getExceptionCount());
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

    private String failureMessage(NoonReportProcessResult result) {
        switch (result.getCode()) {
            case EMPTY_REPORT:
                return "empty report";
            case EMPTY_REPORT_PENDING_CONFIRMATION:
                return "empty report pending confirmation";
            case REPORT_NOT_READY:
                return "report not ready";
            case MISSING_COLUMNS:
                return StringUtils.hasText(result.getDiagnosticMessage())
                        ? "missing columns: " + result.getDiagnosticMessage()
                        : "missing columns";
            case PARTIAL_SUCCESS:
                return "partial success";
            case MAPPING_FAILED:
            default:
                return StringUtils.hasText(result.getDiagnosticMessage())
                        ? "mapping failed: " + result.getDiagnosticMessage()
                        : "mapping failed";
        }
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content == null ? new byte[0] : content);
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
