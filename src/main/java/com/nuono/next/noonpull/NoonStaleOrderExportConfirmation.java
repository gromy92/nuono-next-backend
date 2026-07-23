package com.nuono.next.noonpull;

import java.time.Duration;
import org.springframework.util.StringUtils;

final class NoonStaleOrderExportConfirmation {
    private static final String STALE_EXPORT_DIAGNOSTIC = "provider_reused_latest_export";

    private NoonStaleOrderExportConfirmation() {
    }

    static NoonPullTaskRecord resolve(
            NoonPullFoundationService foundationService,
            Long taskId,
            NoonPullTaskRecord previousTask,
            NoonReportPullRequest request,
            NoonReportProcessResult processResult,
            String sourceBatchId,
            String emptySummary,
            Duration nextPollDelay
    ) {
        if (isRepeatedEvidence(previousTask, request, processResult, sourceBatchId)) {
            return foundationService.markReportExportConfirmedEmpty(
                    taskId,
                    sourceBatchId,
                    emptySummary + "; confirmed_empty; repeated_stale_export=true"
            );
        }
        return foundationService.markReportExportPendingConfirmation(
                taskId,
                sourceBatchId,
                emptySummary,
                nextPollDelay
        );
    }

    private static boolean isRepeatedEvidence(
            NoonPullTaskRecord previousTask,
            NoonReportPullRequest request,
            NoonReportProcessResult processResult,
            String sourceBatchId
    ) {
        return previousTask != null
                && request != null
                && request.getDataDomain() == NoonPullDataDomain.ORDER
                && processResult != null
                && StringUtils.hasText(processResult.getDiagnosticMessage())
                && processResult.getDiagnosticMessage().contains(STALE_EXPORT_DIAGNOSTIC)
                && "pending_confirmation".equals(previousTask.getReadinessState())
                && StringUtils.hasText(sourceBatchId)
                && sourceBatchId.equals(previousTask.getSourceBatchId());
    }
}
