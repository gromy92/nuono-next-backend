package com.nuono.next.datapull.report;

import com.nuono.next.noonpull.NoonReportCsvRecords;
import java.util.List;

/** Validates UTF-8, CSV closure, headers and row width before a fact writer is invoked. */
final class ReportCsvPreflight {
    private ReportCsvPreflight() {
    }

    static long validate(byte[] content) {
        List<String[]> rows = NoonReportCsvRecords.parseRectangular(content);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("downloaded report has no header");
        }
        return rows.size() - 1L;
    }
}
