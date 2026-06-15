package com.nuono.next.noonpull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonReportPuller {
    private final NoonPullFoundationService foundationService;

    public NoonReportPuller(NoonPullFoundationService foundationService) {
        this.foundationService = foundationService;
    }

    public NoonReportPullResult execute(
            Long taskId,
            NoonReportPullRequest request,
            NoonReportProvider provider,
            NoonReportDownloadedFileHandler handler
    ) {
        foundationService.markRunning(taskId, "noon-report-puller");
        NoonReportPullResult result = new NoonReportPullResult();
        try {
            String exportId = provider.createExport(request);
            NoonReportExportStatus status = pollUntilReady(request, provider, exportId);
            if (!StringUtils.hasText(status.getDownloadUrl())) {
                throw new NoonInterfacePullException("mapping failed: missing download url");
            }
            byte[] content = provider.download(request, status.getDownloadUrl());
            String digest = sha256(content);
            String sourceBatchId = sourceBatchId(request, taskId, digest);
            NoonReportDownloadedFile file = new NoonReportDownloadedFile(request, exportId, sourceBatchId, digest, content);
            NoonReportProcessResult processResult = handler.handle(file);
            result.setSourceBatchId(sourceBatchId);
            result.setFileDigestSha256(digest);
            result.setImportedCount(processResult.getImportedCount());
            result.setExceptionCount(processResult.getExceptionCount());
            if (processResult.getCode() == NoonReportProcessResult.Code.SUCCEEDED) {
                NoonPullTaskRecord task = foundationService.markSucceeded(
                        taskId,
                        sourceBatchId,
                        summary(request, digest, processResult)
                );
                result.setStatus(task.getStatus());
                return result;
            }
            NoonPullTaskRecord failed = foundationService.markFailedWithPolicy(
                    taskId,
                    failureMessage(processResult),
                    1
            );
            result.setStatus(failed.getStatus());
            return result;
        } catch (RuntimeException exception) {
            NoonPullTaskRecord failed = foundationService.markFailedWithPolicy(
                    taskId,
                    StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName(),
                    1
            );
            result.setStatus(failed.getStatus());
            return result;
        }
    }

    private NoonReportExportStatus pollUntilReady(
            NoonReportPullRequest request,
            NoonReportProvider provider,
            String exportId
    ) {
        for (int attempt = 1; attempt <= request.getMaxPollAttempts(); attempt++) {
            NoonReportExportStatus status = provider.pollExport(request, exportId);
            if (status != null && status.isReady()) {
                return status;
            }
            if (status != null && status.isFailed()) {
                throw new NoonInterfacePullException("provider unavailable: report export failed " + status.getMessage());
            }
        }
        throw new NoonInterfacePullException("timeout: export polling deadline exceeded");
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
