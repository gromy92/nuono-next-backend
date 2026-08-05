package com.nuono.next.datapull.report;

import java.util.function.Supplier;

/** Fenced, idempotent transaction Seam around one complete report fact import. */
public interface ReportFactApplyGuard {
    ReportImportResult apply(
            ExportReportIntent intent,
            Supplier<ReportImportResult> factImport
    );
}
