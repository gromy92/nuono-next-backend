package com.nuono.next.nooncompleteness;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoonDataGapWindowRecord {
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
    private Long linkedPullPlanId;
    private Long linkedPullTaskId;
    private String linkedSourceBatchId;
    private Integer rowOrItemCount;
    private String failureType;
    private Boolean retryable;
    private Boolean requiresManualAction;
    private String diagnosticSummary;
    private String completedEmptyEvidenceSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoonDataGapWindowRecord copy() {
        NoonDataGapWindowRecord copy = new NoonDataGapWindowRecord();
        copy.setId(id);
        copy.setCompletenessId(completenessId);
        copy.setOwnerUserId(ownerUserId);
        copy.setStoreCode(storeCode);
        copy.setSiteCode(siteCode);
        copy.setCategory(category);
        copy.setWindowType(windowType);
        copy.setDateFrom(dateFrom);
        copy.setDateTo(dateTo);
        copy.setStatus(status);
        copy.setAttempts(attempts);
        copy.setNextRetryAt(nextRetryAt);
        copy.setLinkedPullPlanId(linkedPullPlanId);
        copy.setLinkedPullTaskId(linkedPullTaskId);
        copy.setLinkedSourceBatchId(linkedSourceBatchId);
        copy.setRowOrItemCount(rowOrItemCount);
        copy.setFailureType(failureType);
        copy.setRetryable(retryable);
        copy.setRequiresManualAction(requiresManualAction);
        copy.setDiagnosticSummary(diagnosticSummary);
        copy.setCompletedEmptyEvidenceSummary(completedEmptyEvidenceSummary);
        copy.setCreatedAt(createdAt);
        copy.setUpdatedAt(updatedAt);
        return copy;
    }

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
    public Long getLinkedPullPlanId() { return linkedPullPlanId; }
    public void setLinkedPullPlanId(Long linkedPullPlanId) { this.linkedPullPlanId = linkedPullPlanId; }
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
    public String getCompletedEmptyEvidenceSummary() { return completedEmptyEvidenceSummary; }
    public void setCompletedEmptyEvidenceSummary(String completedEmptyEvidenceSummary) { this.completedEmptyEvidenceSummary = completedEmptyEvidenceSummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

