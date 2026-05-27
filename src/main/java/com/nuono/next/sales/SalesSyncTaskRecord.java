package com.nuono.next.sales;

import java.time.LocalDate;

public class SalesSyncTaskRecord {

    private final Long id;
    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final Long requestedBy;
    private final String triggerType;
    private final String status;
    private final Long sourceBatchId;
    private final Integer totalRows;
    private final Integer successRows;
    private final Integer failureRows;
    private final LocalDate latestFactDate;
    private final String failureReason;

    public SalesSyncTaskRecord(
            Long id,
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long requestedBy,
            String triggerType,
            String status,
            Long sourceBatchId,
            Integer totalRows,
            Integer successRows,
            Integer failureRows,
            LocalDate latestFactDate,
            String failureReason
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.requestedBy = requestedBy;
        this.triggerType = triggerType;
        this.status = status;
        this.sourceBatchId = sourceBatchId;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failureRows = failureRows;
        this.latestFactDate = latestFactDate;
        this.failureReason = failureReason;
    }

    public static SalesSyncTaskRecord queued(Long id, SalesSyncTaskCommand command) {
        return new SalesSyncTaskRecord(
                id,
                command.getOwnerUserId(),
                command.getLogicalStoreId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getDateFrom(),
                command.getDateTo(),
                command.getRequestedBy(),
                command.getTriggerType(),
                "queued",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SalesSyncTaskRecord withStatus(String status) {
        return new SalesSyncTaskRecord(
                id, ownerUserId, logicalStoreId, storeCode, siteCode, dateFrom, dateTo,
                requestedBy, triggerType, status, sourceBatchId, totalRows, successRows,
                failureRows, latestFactDate, failureReason
        );
    }

    public SalesSyncTaskRecord succeeded(NoonSalesCsvImportResult result) {
        return new SalesSyncTaskRecord(
                id, ownerUserId, logicalStoreId, storeCode, siteCode, dateFrom, dateTo,
                requestedBy, triggerType, result.getTaskStatus(), result.getSourceBatchId(), result.getTotalRows(),
                result.getSuccessRows(), result.getFailureRows(), result.getReportDateTo(), result.getTaskFailureReason()
        );
    }

    public SalesSyncTaskRecord failed(String failureReason) {
        return new SalesSyncTaskRecord(
                id, ownerUserId, logicalStoreId, storeCode, siteCode, dateFrom, dateTo,
                requestedBy, triggerType, "failed", sourceBatchId, totalRows, successRows,
                failureRows, latestFactDate, failureReason
        );
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Long getSourceBatchId() {
        return sourceBatchId;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public Integer getSuccessRows() {
        return successRows;
    }

    public Integer getFailureRows() {
        return failureRows;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public String getFailureReason() {
        return failureReason;
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

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getTriggerType() {
        return triggerType;
    }
}
