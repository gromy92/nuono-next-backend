package com.nuono.next.nooncompleteness;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoonDataCompletenessRecord {
    private Long id;
    private Long ownerUserId;
    private String storeName;
    private String storeCode;
    private String siteCode;
    private NoonDataCategory category;
    private NoonDataLatestStatus latestStatus;
    private NoonDataHistoryStatus historyStatus;
    private LocalDate latestDataDate;
    private LocalDate historyCoveredFrom;
    private LocalDate historyCoveredTo;
    private boolean patrolEnabled;
    private LocalDateTime nextPatrolAt;
    private Integer activeGapCount;
    private Long lastTaskId;
    private String lastSourceBatchId;
    private String lastFailureType;
    private String diagnosticSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoonDataCompletenessRecord copy() {
        NoonDataCompletenessRecord copy = new NoonDataCompletenessRecord();
        copy.setId(id);
        copy.setOwnerUserId(ownerUserId);
        copy.setStoreName(storeName);
        copy.setStoreCode(storeCode);
        copy.setSiteCode(siteCode);
        copy.setCategory(category);
        copy.setLatestStatus(latestStatus);
        copy.setHistoryStatus(historyStatus);
        copy.setLatestDataDate(latestDataDate);
        copy.setHistoryCoveredFrom(historyCoveredFrom);
        copy.setHistoryCoveredTo(historyCoveredTo);
        copy.setPatrolEnabled(patrolEnabled);
        copy.setNextPatrolAt(nextPatrolAt);
        copy.setActiveGapCount(activeGapCount);
        copy.setLastTaskId(lastTaskId);
        copy.setLastSourceBatchId(lastSourceBatchId);
        copy.setLastFailureType(lastFailureType);
        copy.setDiagnosticSummary(diagnosticSummary);
        copy.setCreatedAt(createdAt);
        copy.setUpdatedAt(updatedAt);
        return copy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public NoonDataCategory getCategory() { return category; }
    public void setCategory(NoonDataCategory category) { this.category = category; }
    public NoonDataLatestStatus getLatestStatus() { return latestStatus; }
    public void setLatestStatus(NoonDataLatestStatus latestStatus) { this.latestStatus = latestStatus; }
    public NoonDataHistoryStatus getHistoryStatus() { return historyStatus; }
    public void setHistoryStatus(NoonDataHistoryStatus historyStatus) { this.historyStatus = historyStatus; }
    public LocalDate getLatestDataDate() { return latestDataDate; }
    public void setLatestDataDate(LocalDate latestDataDate) { this.latestDataDate = latestDataDate; }
    public LocalDate getHistoryCoveredFrom() { return historyCoveredFrom; }
    public void setHistoryCoveredFrom(LocalDate historyCoveredFrom) { this.historyCoveredFrom = historyCoveredFrom; }
    public LocalDate getHistoryCoveredTo() { return historyCoveredTo; }
    public void setHistoryCoveredTo(LocalDate historyCoveredTo) { this.historyCoveredTo = historyCoveredTo; }
    public boolean isPatrolEnabled() { return patrolEnabled; }
    public void setPatrolEnabled(boolean patrolEnabled) { this.patrolEnabled = patrolEnabled; }
    public LocalDateTime getNextPatrolAt() { return nextPatrolAt; }
    public void setNextPatrolAt(LocalDateTime nextPatrolAt) { this.nextPatrolAt = nextPatrolAt; }
    public Integer getActiveGapCount() { return activeGapCount; }
    public void setActiveGapCount(Integer activeGapCount) { this.activeGapCount = activeGapCount; }
    public Long getLastTaskId() { return lastTaskId; }
    public void setLastTaskId(Long lastTaskId) { this.lastTaskId = lastTaskId; }
    public String getLastSourceBatchId() { return lastSourceBatchId; }
    public void setLastSourceBatchId(String lastSourceBatchId) { this.lastSourceBatchId = lastSourceBatchId; }
    public String getLastFailureType() { return lastFailureType; }
    public void setLastFailureType(String lastFailureType) { this.lastFailureType = lastFailureType; }
    public String getDiagnosticSummary() { return diagnosticSummary; }
    public void setDiagnosticSummary(String diagnosticSummary) { this.diagnosticSummary = diagnosticSummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
