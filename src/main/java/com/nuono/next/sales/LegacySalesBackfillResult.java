package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.List;

public class LegacySalesBackfillResult {

    private final String sourceSystem;
    private final long sourceBatchId;
    private final int totalRows;
    private final int successRows;
    private final int failureRows;
    private final LocalDate reportDateFrom;
    private final LocalDate reportDateTo;
    private final String status;
    private final String failureSummary;
    private final List<SalesImportExceptionRecord> exceptions;

    public LegacySalesBackfillResult(
            String sourceSystem,
            long sourceBatchId,
            int totalRows,
            int successRows,
            int failureRows,
            LocalDate reportDateFrom,
            LocalDate reportDateTo,
            String status,
            String failureSummary,
            List<SalesImportExceptionRecord> exceptions
    ) {
        this.sourceSystem = sourceSystem;
        this.sourceBatchId = sourceBatchId;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failureRows = failureRows;
        this.reportDateFrom = reportDateFrom;
        this.reportDateTo = reportDateTo;
        this.status = status;
        this.failureSummary = failureSummary;
        this.exceptions = List.copyOf(exceptions);
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public long getSourceBatchId() {
        return sourceBatchId;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getSuccessRows() {
        return successRows;
    }

    public int getFailureRows() {
        return failureRows;
    }

    public LocalDate getReportDateFrom() {
        return reportDateFrom;
    }

    public LocalDate getReportDateTo() {
        return reportDateTo;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public List<SalesImportExceptionRecord> getExceptions() {
        return exceptions;
    }
}
