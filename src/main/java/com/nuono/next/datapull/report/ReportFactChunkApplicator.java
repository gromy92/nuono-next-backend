package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** DP-specific deep Fact Writer behind the common sealed-stage cursor. */
interface ReportFactChunkApplicator {
    boolean supports(OperationCode operationCode);

    long applyChunk(
            ExportReportIntent intent,
            ReportStageState stage,
            long afterRowNumber,
            long throughRowNumber,
            long rowCount,
            LocalDateTime nowUtc
    );

    default void finalizeContainer(
            ExportReportIntent intent,
            ReportStageState stage,
            LocalDateTime nowUtc
    ) {
        // Most report facts have no separate container header.
    }
}
