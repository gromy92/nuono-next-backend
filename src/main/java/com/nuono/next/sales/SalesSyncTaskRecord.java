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
    private final String listingCoverageMode;
    private final String status;
    private final Long sourceBatchId;
    private final Integer totalRows;
    private final Integer successRows;
    private final Integer failureRows;
    private final LocalDate latestFactDate;
    private final String failureReason;
    private final Long authRecoveryId;

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
            String listingCoverageMode,
            String status,
            Long sourceBatchId,
            Integer totalRows,
            Integer successRows,
            Integer failureRows,
            LocalDate latestFactDate,
            String failureReason,
            Long authRecoveryId
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
        this.listingCoverageMode = listingCoverageMode;
        this.status = status;
        this.sourceBatchId = sourceBatchId;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failureRows = failureRows;
        this.latestFactDate = latestFactDate;
        this.failureReason = failureReason;
        this.authRecoveryId = authRecoveryId;
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
                command.getListingCoverageMode(),
                "queued",
                null,
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
                requestedBy, triggerType, listingCoverageMode, status, sourceBatchId, totalRows, successRows,
                failureRows, latestFactDate, failureReason, authRecoveryId
        );
    }

    public SalesSyncTaskRecord succeeded(NoonSalesCsvImportResult result) {
        return new SalesSyncTaskRecord(
                id, ownerUserId, logicalStoreId, storeCode, siteCode, dateFrom, dateTo,
                requestedBy, triggerType, listingCoverageMode, result.getTaskStatus(), result.getSourceBatchId(), result.getTotalRows(),
                result.getSuccessRows(), result.getFailureRows(), result.getReportDateTo(), result.getTaskFailureReason(), null
        );
    }

    public SalesSyncTaskRecord failed(String failureReason) {
        return new SalesSyncTaskRecord(
                id, ownerUserId, logicalStoreId, storeCode, siteCode, dateFrom, dateTo,
                requestedBy, triggerType, listingCoverageMode, "failed", sourceBatchId, totalRows, successRows,
                failureRows, latestFactDate, failureReason, authRecoveryId
        );
    }

    public SalesSyncTaskRecord waitingForAuthorization(Long recoveryId) {
        return new SalesSyncTaskRecord(
                id, ownerUserId, logicalStoreId, storeCode, siteCode, dateFrom, dateTo,
                requestedBy, triggerType, listingCoverageMode, "waiting_authorization", sourceBatchId,
                totalRows, successRows, failureRows, latestFactDate,
                "Noon Project 授权恢复中，恢复后将自动继续原销量同步任务。", recoveryId
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

    public Long getAuthRecoveryId() {
        return authRecoveryId;
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

    public String getListingCoverageMode() {
        return listingCoverageMode;
    }
}
