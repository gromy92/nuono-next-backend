package com.nuono.next.orderfinance;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class OrderFinanceDataStatus {
    private String latestSourceBatchId;
    private LocalDate latestTransactionDate;
    private long missingPartnerSkuRowCount;
    private String latestSyncStatus;
    private String latestSyncMessage;
    private LocalDateTime latestSyncFinishedAt;

    public String getLatestSourceBatchId() { return latestSourceBatchId; }
    public void setLatestSourceBatchId(String latestSourceBatchId) { this.latestSourceBatchId = latestSourceBatchId; }
    public LocalDate getLatestTransactionDate() { return latestTransactionDate; }
    public void setLatestTransactionDate(LocalDate latestTransactionDate) { this.latestTransactionDate = latestTransactionDate; }
    public long getMissingPartnerSkuRowCount() { return missingPartnerSkuRowCount; }
    public void setMissingPartnerSkuRowCount(long missingPartnerSkuRowCount) { this.missingPartnerSkuRowCount = missingPartnerSkuRowCount; }
    public String getLatestSyncStatus() { return latestSyncStatus; }
    public void setLatestSyncStatus(String latestSyncStatus) { this.latestSyncStatus = latestSyncStatus; }
    public String getLatestSyncMessage() { return latestSyncMessage; }
    public void setLatestSyncMessage(String latestSyncMessage) { this.latestSyncMessage = latestSyncMessage; }
    public LocalDateTime getLatestSyncFinishedAt() { return latestSyncFinishedAt; }
    public void setLatestSyncFinishedAt(LocalDateTime latestSyncFinishedAt) { this.latestSyncFinishedAt = latestSyncFinishedAt; }
}
