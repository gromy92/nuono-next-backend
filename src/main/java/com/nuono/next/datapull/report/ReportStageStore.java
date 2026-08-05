package com.nuono.next.datapull.report;

/** Transactional staging and set-based seal Seam. */
public interface ReportStageStore {
    ReportStageState load(long taskId);

    ReportImportResult stage(ExportReportIntent intent, ReportStageChunk chunk);

    ReportImportResult applySealed(ExportReportIntent intent);
}
