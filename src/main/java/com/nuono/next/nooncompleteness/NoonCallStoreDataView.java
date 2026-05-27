package com.nuono.next.nooncompleteness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NoonCallStoreDataView {
    private String title = "店铺数据";
    private LocalDateTime generatedAt = LocalDateTime.now();
    private List<NoonDataCompletenessOverviewView.Metric> metrics = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<NoonDataCompletenessOverviewView.Metric> getMetrics() { return metrics; }
    public void setMetrics(List<NoonDataCompletenessOverviewView.Metric> metrics) { this.metrics = metrics == null ? new ArrayList<>() : metrics; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows == null ? new ArrayList<>() : rows; }

    public static class Row {
        private Long ownerUserId;
        private String storeName;
        private String storeCode;
        private String siteCode;
        private String overallMarker;
        private LocalDateTime lastSyncAt;
        private List<CategoryCell> categories = new ArrayList<>();

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getStoreName() { return storeName; }
        public void setStoreName(String storeName) { this.storeName = storeName; }
        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public String getOverallMarker() { return overallMarker; }
        public void setOverallMarker(String overallMarker) { this.overallMarker = overallMarker; }
        public LocalDateTime getLastSyncAt() { return lastSyncAt; }
        public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
        public List<CategoryCell> getCategories() { return categories; }
        public void setCategories(List<CategoryCell> categories) { this.categories = categories == null ? new ArrayList<>() : categories; }
    }

    public static class CategoryCell {
        private NoonDataCategory category;
        private String label;
        private Long completenessId;
        private String marker;
        private NoonDataLatestStatus latestStatus;
        private NoonDataHistoryStatus historyStatus;
        private LocalDate latestDataDate;
        private LocalDate historyCoveredFrom;
        private LocalDate historyCoveredTo;
        private Integer activeGapCount;
        private Long latestTaskId;
        private String latestTaskStatus;
        private String readinessState;
        private String failureType;
        private Integer processedItemCount;
        private Integer requestCount;
        private LocalDateTime lastSyncAt;
        private Boolean syncable;
        private Boolean requiresManualAction;
        private String diagnosticSummary;

        public NoonDataCategory getCategory() { return category; }
        public void setCategory(NoonDataCategory category) { this.category = category; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Long getCompletenessId() { return completenessId; }
        public void setCompletenessId(Long completenessId) { this.completenessId = completenessId; }
        public String getMarker() { return marker; }
        public void setMarker(String marker) { this.marker = marker; }
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
        public Integer getActiveGapCount() { return activeGapCount; }
        public void setActiveGapCount(Integer activeGapCount) { this.activeGapCount = activeGapCount; }
        public Long getLatestTaskId() { return latestTaskId; }
        public void setLatestTaskId(Long latestTaskId) { this.latestTaskId = latestTaskId; }
        public String getLatestTaskStatus() { return latestTaskStatus; }
        public void setLatestTaskStatus(String latestTaskStatus) { this.latestTaskStatus = latestTaskStatus; }
        public String getReadinessState() { return readinessState; }
        public void setReadinessState(String readinessState) { this.readinessState = readinessState; }
        public String getFailureType() { return failureType; }
        public void setFailureType(String failureType) { this.failureType = failureType; }
        public Integer getProcessedItemCount() { return processedItemCount; }
        public void setProcessedItemCount(Integer processedItemCount) { this.processedItemCount = processedItemCount; }
        public Integer getRequestCount() { return requestCount; }
        public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }
        public LocalDateTime getLastSyncAt() { return lastSyncAt; }
        public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
        public Boolean getSyncable() { return syncable; }
        public void setSyncable(Boolean syncable) { this.syncable = syncable; }
        public Boolean getRequiresManualAction() { return requiresManualAction; }
        public void setRequiresManualAction(Boolean requiresManualAction) { this.requiresManualAction = requiresManualAction; }
        public String getDiagnosticSummary() { return diagnosticSummary; }
        public void setDiagnosticSummary(String diagnosticSummary) { this.diagnosticSummary = diagnosticSummary; }
    }
}
