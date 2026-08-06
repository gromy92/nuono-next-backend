package com.nuono.next.datapull.report;

import com.nuono.next.noonpull.NoonReportDownloadedFile;
import java.util.List;

/** Adapter Seam from one report's row contract to normalized staging payloads. */
public interface ReportFactPlanAdapter {
    void requireHeader(String[] header);

    List<ReportPlannedRow> planRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows,
            long firstRowNumber
    );
}
