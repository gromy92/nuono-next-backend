package com.nuono.next.nooncompleteness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NoonDataGapPatrolView {
    private String title = "数据缺口巡检";
    private LocalDateTime generatedAt = LocalDateTime.now();
    private List<NoonDataCompletenessOverviewView.Metric> metrics = new ArrayList<>();
    private List<NoonDataCompletenessOverviewView.DistributionItem> statusDistribution = new ArrayList<>();
    private List<NoonDataCompletenessOverviewView.DistributionItem> failureDistribution = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<NoonDataCompletenessOverviewView.Metric> getMetrics() { return metrics; }
    public void setMetrics(List<NoonDataCompletenessOverviewView.Metric> metrics) { this.metrics = metrics == null ? new ArrayList<>() : metrics; }
    public List<NoonDataCompletenessOverviewView.DistributionItem> getStatusDistribution() { return statusDistribution; }
    public void setStatusDistribution(List<NoonDataCompletenessOverviewView.DistributionItem> statusDistribution) { this.statusDistribution = statusDistribution == null ? new ArrayList<>() : statusDistribution; }
    public List<NoonDataCompletenessOverviewView.DistributionItem> getFailureDistribution() { return failureDistribution; }
    public void setFailureDistribution(List<NoonDataCompletenessOverviewView.DistributionItem> failureDistribution) { this.failureDistribution = failureDistribution == null ? new ArrayList<>() : failureDistribution; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows == null ? new ArrayList<>() : rows; }

    public static class Row {
        private Long id;
        private Long completenessId;
        private Long ownerUserId;
        private String storeCode;
        private String siteCode;
        private NoonDataCategory category;
        private NoonDataGapWindowType windowType;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private NoonDataGapStatus status;
        private Integer attempts;
        private LocalDateTime nextRetryAt;
        private Long linkedPullTaskId;
        private String linkedSourceBatchId;
        private Integer rowOrItemCount;
        private String failureType;
        private Boolean retryable;
        private Boolean requiresManualAction;
        private String diagnosticSummary;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCompletenessId() { return completenessId; }
        public void setCompletenessId(Long completenessId) { this.completenessId = completenessId; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public NoonDataCategory getCategory() { return category; }
        public void setCategory(NoonDataCategory category) { this.category = category; }
        public NoonDataGapWindowType getWindowType() { return windowType; }
        public void setWindowType(NoonDataGapWindowType windowType) { this.windowType = windowType; }
        public LocalDate getDateFrom() { return dateFrom; }
        public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
        public LocalDate getDateTo() { return dateTo; }
        public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
        public NoonDataGapStatus getStatus() { return status; }
        public void setStatus(NoonDataGapStatus status) { this.status = status; }
        public Integer getAttempts() { return attempts; }
        public void setAttempts(Integer attempts) { this.attempts = attempts; }
        public LocalDateTime getNextRetryAt() { return nextRetryAt; }
        public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
        public Long getLinkedPullTaskId() { return linkedPullTaskId; }
        public void setLinkedPullTaskId(Long linkedPullTaskId) { this.linkedPullTaskId = linkedPullTaskId; }
        public String getLinkedSourceBatchId() { return linkedSourceBatchId; }
        public void setLinkedSourceBatchId(String linkedSourceBatchId) { this.linkedSourceBatchId = linkedSourceBatchId; }
        public Integer getRowOrItemCount() { return rowOrItemCount; }
        public void setRowOrItemCount(Integer rowOrItemCount) { this.rowOrItemCount = rowOrItemCount; }
        public String getFailureType() { return failureType; }
        public void setFailureType(String failureType) { this.failureType = failureType; }
        public Boolean getRetryable() { return retryable; }
        public void setRetryable(Boolean retryable) { this.retryable = retryable; }
        public Boolean getRequiresManualAction() { return requiresManualAction; }
        public void setRequiresManualAction(Boolean requiresManualAction) { this.requiresManualAction = requiresManualAction; }
        public String getDiagnosticSummary() { return diagnosticSummary; }
        public void setDiagnosticSummary(String diagnosticSummary) { this.diagnosticSummary = diagnosticSummary; }
    }
}
