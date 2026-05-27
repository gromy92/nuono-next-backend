package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.List;

public class NoonSalesCsvImportResult {

    private final String sourceSystem;
    private final Long sourceBatchId;
    private final String sourceFilename;
    private final int totalRows;
    private final int successRows;
    private final int failureRows;
    private final LocalDate reportDateFrom;
    private final LocalDate reportDateTo;
    private final String status;
    private final String failureSummary;
    private final List<SalesImportExceptionRecord> exceptions;

    public NoonSalesCsvImportResult(
            String sourceSystem,
            Long sourceBatchId,
            String sourceFilename,
            int totalRows,
            int successRows,
            int failureRows,
            LocalDate reportDateFrom,
            LocalDate reportDateTo
    ) {
        this(
                sourceSystem,
                sourceBatchId,
                sourceFilename,
                totalRows,
                successRows,
                failureRows,
                reportDateFrom,
                reportDateTo,
                "imported",
                null,
                List.of()
        );
    }

    public NoonSalesCsvImportResult(
            String sourceSystem,
            Long sourceBatchId,
            String sourceFilename,
            int totalRows,
            int successRows,
            int failureRows,
            LocalDate reportDateFrom,
            LocalDate reportDateTo,
            String status
    ) {
        this(
                sourceSystem,
                sourceBatchId,
                sourceFilename,
                totalRows,
                successRows,
                failureRows,
                reportDateFrom,
                reportDateTo,
                status,
                null,
                List.of()
        );
    }

    public NoonSalesCsvImportResult(
            String sourceSystem,
            Long sourceBatchId,
            String sourceFilename,
            int totalRows,
            int successRows,
            int failureRows,
            LocalDate reportDateFrom,
            LocalDate reportDateTo,
            String status,
            String failureSummary
    ) {
        this(
                sourceSystem,
                sourceBatchId,
                sourceFilename,
                totalRows,
                successRows,
                failureRows,
                reportDateFrom,
                reportDateTo,
                status,
                failureSummary,
                List.of()
        );
    }

    public NoonSalesCsvImportResult(
            String sourceSystem,
            Long sourceBatchId,
            String sourceFilename,
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
        this.sourceFilename = sourceFilename;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failureRows = failureRows;
        this.reportDateFrom = reportDateFrom;
        this.reportDateTo = reportDateTo;
        this.status = status;
        this.failureSummary = failureSummary;
        this.exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public Long getSourceBatchId() {
        return sourceBatchId;
    }

    public String getSourceFilename() {
        return sourceFilename;
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

    public String getTaskStatus() {
        if ("imported".equals(status)) {
            return "succeeded";
        }
        return status;
    }

    public String getTaskFailureReason() {
        if ("imported".equals(status)) {
            return null;
        }
        if ("failed".equals(status) && failureSummary != null && !failureSummary.isBlank()) {
            return failureSummary;
        }
        return status;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public List<SalesImportExceptionRecord> getExceptions() {
        return exceptions;
    }
}
