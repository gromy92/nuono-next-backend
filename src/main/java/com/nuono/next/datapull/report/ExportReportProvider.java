package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.ProviderOutcome;

/**
 * Provider Adapter Seam for one export-report operation.
 *
 * <p>Every method performs at most one remote Interface call. A successful download must return
 * a durable artifact reference whose content survives a process restart; the report Module never
 * stores report bytes in the task checkpoint. READY polls must persist any sensitive download URL
 * behind a durable, secret-free locator reference before returning. Ambiguous create transport
 * failures must be returned as UNKNOWN_OUTCOME, while
 * {@link #findByRequestKey(ExportReportIntent)} is strictly read-only.</p>
 */
public interface ExportReportProvider {

    /** True only when create generates a read-only artifact and replay cannot write business data. */
    default boolean retryUnknownCreateAfterReadbackFailure() {
        return false;
    }

    ProviderOutcome<RemoteExportHandle> create(ExportReportIntent intent);

    ProviderOutcome<ExportCreateReadback> findByRequestKey(ExportReportIntent intent);

    ProviderOutcome<ExportPollResult> poll(ExportReportIntent intent, RemoteExportHandle handle);

    ProviderOutcome<DownloadedReportArtifact> download(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String downloadLocatorReference
    );
}
