package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.runtime.AdvanceResult;
import java.util.Objects;

final class ExportReportApplyStep {

    private final ExportReportImporter importer;
    private final ExportReportTransitions transitions;

    ExportReportApplyStep(
            ExportReportImporter importer,
            ExportReportTransitions transitions
    ) {
        this.importer = importer;
        this.transitions = transitions;
    }

    AdvanceResult apply(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint,
            String remoteHandle
    ) {
        ReportImportResult result;
        try {
            result = Objects.requireNonNull(
                    importer.importComplete(intent, checkpoint.getArtifact()),
                    "import result"
            );
        } catch (RuntimeException unknownLocalResult) {
            return transitions.retryUntyped(
                    context,
                    checkpoint,
                    remoteHandle,
                    "REPORT_IMPORT_UNKNOWN_RESULT"
            );
        }
        if (result.getStatus() == ReportImportResult.Status.APPLIED) {
            return AdvanceResult.succeeded();
        }
        if (result.getStatus() == ReportImportResult.Status.IN_PROGRESS) {
            return transitions.queued(
                    checkpoint.preserveAttemptAt(ExportReportCheckpoint.Phase.APPLY),
                    remoteHandle
            );
        }
        if (result.getStatus()
                == ReportImportResult.Status.AWAITING_AUTHORITATIVE_EMPTY_PROOF) {
            return transitions.waitingPoll(
                    checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL),
                    remoteHandle,
                    result.getSanitizedCode()
            );
        }
        if (result.getStatus() == ReportImportResult.Status.STALE_FENCE) {
            // The runtime CAS rejects the old owner. Retain the durable artifact so the
            // current owner can reconcile the apply marker without downloading again.
            return transitions.queued(checkpoint, remoteHandle);
        }
        if (result.getStatus() == ReportImportResult.Status.CONTRACT_ERROR) {
            // The guarded fact transaction has rolled back. The task remains bound to the
            // same immutable export; this runtime never creates a second export to hide a
            // malformed or truncated first result.
            return transitions.retry(
                    context,
                    com.nuono.next.datapull.runtime.ProviderOutcome.contractError(
                            result.getSanitizedCode()
                    ),
                    checkpoint.retryAt(ExportReportCheckpoint.Phase.APPLY),
                    remoteHandle
            );
        }
        return transitions.retryUntyped(
                context,
                checkpoint.preserveAttemptAt(ExportReportCheckpoint.Phase.APPLY),
                remoteHandle,
                result.getSanitizedCode()
        );
    }
}
