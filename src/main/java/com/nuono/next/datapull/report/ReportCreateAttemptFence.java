package com.nuono.next.datapull.report;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.LocalDateTime;

/** Persists the read-back phase before an export create can leave this process. */
public interface ReportCreateAttemptFence {
    boolean prepareReadbackBeforeCreate(
            DataPullTask task,
            ExportReportCheckpoint reconcileCheckpoint,
            LocalDateTime nowUtc
    );
}
