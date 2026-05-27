package com.nuono.next.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SalesImportBatchRecord {

    private final Long id;
    private final String sourceSystem;
    private final String sourceFilename;
    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate reportDateFrom;
    private final LocalDate reportDateTo;
    private final int totalRows;
    private final int successRows;
    private final int failureRows;
    private final String status;
    private final String failureSummary;
    private final LocalDateTime importedAt;

    public SalesImportBatchRecord(
            Long id,
            String sourceSystem,
            String sourceFilename,
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            LocalDate reportDateFrom,
            LocalDate reportDateTo,
            int totalRows,
            int successRows,
            int failureRows,
            String status,
            String failureSummary,
            LocalDateTime importedAt
    ) {
        this.id = id;
        this.sourceSystem = sourceSystem;
        this.sourceFilename = sourceFilename;
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.reportDateFrom = reportDateFrom;
        this.reportDateTo = reportDateTo;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failureRows = failureRows;
        this.status = status;
        this.failureSummary = failureSummary;
        this.importedAt = importedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getReportDateFrom() {
        return reportDateFrom;
    }

    public LocalDate getReportDateTo() {
        return reportDateTo;
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

    public String getStatus() {
        return status;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }
}
