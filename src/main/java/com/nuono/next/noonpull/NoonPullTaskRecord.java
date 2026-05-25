package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoonPullTaskRecord {
    private Long id;
    private Long planId;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullType pullType;
    private NoonPullDataDomain dataDomain;
    private NoonPullTriggerMode triggerMode;
    private String targetIdentity;
    private LocalDate targetDateFrom;
    private LocalDate targetDateTo;
    private String activeLockKey;
    private NoonPullTaskStatus status;
    private String sourceBatchId;
    private String failureType;
    private String retryAction;
    private Boolean retryable;
    private Boolean requiresManualAction;
    private String diagnosticSummary;
    private String checkpointCursor;
    private Integer processedItemCount;
    private Integer requestCount;
    private String nextResumePosition;
    private String lastSafeResponseSummary;
    private String readinessState;
    private String lockedBy;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoonPullTaskRecord copy() {
        NoonPullTaskRecord copy = new NoonPullTaskRecord();
        copy.id = id;
        copy.planId = planId;
        copy.ownerUserId = ownerUserId;
        copy.storeCode = storeCode;
        copy.siteCode = siteCode;
        copy.pullType = pullType;
        copy.dataDomain = dataDomain;
        copy.triggerMode = triggerMode;
        copy.targetIdentity = targetIdentity;
        copy.targetDateFrom = targetDateFrom;
        copy.targetDateTo = targetDateTo;
        copy.activeLockKey = activeLockKey;
        copy.status = status;
        copy.sourceBatchId = sourceBatchId;
        copy.failureType = failureType;
        copy.retryAction = retryAction;
        copy.retryable = retryable;
        copy.requiresManualAction = requiresManualAction;
        copy.diagnosticSummary = diagnosticSummary;
        copy.checkpointCursor = checkpointCursor;
        copy.processedItemCount = processedItemCount;
        copy.requestCount = requestCount;
        copy.nextResumePosition = nextResumePosition;
        copy.lastSafeResponseSummary = lastSafeResponseSummary;
        copy.readinessState = readinessState;
        copy.lockedBy = lockedBy;
        copy.queuedAt = queuedAt;
        copy.startedAt = startedAt;
        copy.finishedAt = finishedAt;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public NoonPullType getPullType() {
        return pullType;
    }

    public void setPullType(NoonPullType pullType) {
        this.pullType = pullType;
    }

    public NoonPullDataDomain getDataDomain() {
        return dataDomain;
    }

    public void setDataDomain(NoonPullDataDomain dataDomain) {
        this.dataDomain = dataDomain;
    }

    public NoonPullTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(NoonPullTriggerMode triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public void setTargetIdentity(String targetIdentity) {
        this.targetIdentity = targetIdentity;
    }

    public LocalDate getTargetDateFrom() {
        return targetDateFrom;
    }

    public void setTargetDateFrom(LocalDate targetDateFrom) {
        this.targetDateFrom = targetDateFrom;
    }

    public LocalDate getTargetDateTo() {
        return targetDateTo;
    }

    public void setTargetDateTo(LocalDate targetDateTo) {
        this.targetDateTo = targetDateTo;
    }

    public String getActiveLockKey() {
        return activeLockKey;
    }

    public void setActiveLockKey(String activeLockKey) {
        this.activeLockKey = activeLockKey;
    }

    public NoonPullTaskStatus getStatus() {
        return status;
    }

    public void setStatus(NoonPullTaskStatus status) {
        this.status = status;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getRetryAction() {
        return retryAction;
    }

    public void setRetryAction(String retryAction) {
        this.retryAction = retryAction;
    }

    public Boolean getRetryable() {
        return retryable;
    }

    public void setRetryable(Boolean retryable) {
        this.retryable = retryable;
    }

    public Boolean getRequiresManualAction() {
        return requiresManualAction;
    }

    public void setRequiresManualAction(Boolean requiresManualAction) {
        this.requiresManualAction = requiresManualAction;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public void setDiagnosticSummary(String diagnosticSummary) {
        this.diagnosticSummary = diagnosticSummary;
    }

    public String getCheckpointCursor() {
        return checkpointCursor;
    }

    public void setCheckpointCursor(String checkpointCursor) {
        this.checkpointCursor = checkpointCursor;
    }

    public Integer getProcessedItemCount() {
        return processedItemCount;
    }

    public void setProcessedItemCount(Integer processedItemCount) {
        this.processedItemCount = processedItemCount;
    }

    public Integer getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(Integer requestCount) {
        this.requestCount = requestCount;
    }

    public String getNextResumePosition() {
        return nextResumePosition;
    }

    public void setNextResumePosition(String nextResumePosition) {
        this.nextResumePosition = nextResumePosition;
    }

    public String getLastSafeResponseSummary() {
        return lastSafeResponseSummary;
    }

    public void setLastSafeResponseSummary(String lastSafeResponseSummary) {
        this.lastSafeResponseSummary = lastSafeResponseSummary;
    }

    public String getReadinessState() {
        return readinessState;
    }

    public void setReadinessState(String readinessState) {
        this.readinessState = readinessState;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public LocalDateTime getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(LocalDateTime queuedAt) {
        this.queuedAt = queuedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
