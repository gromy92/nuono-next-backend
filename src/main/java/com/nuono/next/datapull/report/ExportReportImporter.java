package com.nuono.next.datapull.report;

/**
 * DP-owned fact-writer Adapter Seam.
 *
 * <p>The implementation must validate the immutable artifact through bounded durable staging.
 * No owned fact write may begin before encoding, container closure, required columns, requested
 * date range, provider row count and every row decision are sealed. APPLIED is returned only after
 * the constant-statement fact transaction and its marker commit together.</p>
 */
public interface ExportReportImporter {

    ReportImportResult importComplete(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    );
}
