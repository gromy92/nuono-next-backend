package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductPublishTaskRecord {

    private Long id;
    private Long ownerUserId;
    private Long productMasterId;
    private Long baselineSnapshotId;
    private Long draftSnapshotId;
    private String storeCode;
    private String projectCode;
    private String skuParent;
    private String partnerSku;
    private String pskuCode;
    private String currentSiteCode;
    private String taskType;
    private String status;
    private String activeLockKey;
    private String idempotencyKey;
    private String draftHash;
    private String changedDomainsJson;
    private String baselineJson;
    private String draftJson;
    private String requestJson;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private Integer verifyAttemptCount;
    private Integer maxRetryCount;
    private Integer versionNo;
    private LocalDateTime nextRunAt;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime verifyStartedAt;
    private LocalDateTime verifyFinishedAt;
    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getProductMasterId() {
        return productMasterId;
    }

    public void setProductMasterId(Long productMasterId) {
        this.productMasterId = productMasterId;
    }

    public Long getBaselineSnapshotId() {
        return baselineSnapshotId;
    }

    public void setBaselineSnapshotId(Long baselineSnapshotId) {
        this.baselineSnapshotId = baselineSnapshotId;
    }

    public Long getDraftSnapshotId() {
        return draftSnapshotId;
    }

    public void setDraftSnapshotId(Long draftSnapshotId) {
        this.draftSnapshotId = draftSnapshotId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getPskuCode() {
        return pskuCode;
    }

    public void setPskuCode(String pskuCode) {
        this.pskuCode = pskuCode;
    }

    public String getCurrentSiteCode() {
        return currentSiteCode;
    }

    public void setCurrentSiteCode(String currentSiteCode) {
        this.currentSiteCode = currentSiteCode;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActiveLockKey() {
        return activeLockKey;
    }

    public void setActiveLockKey(String activeLockKey) {
        this.activeLockKey = activeLockKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDraftHash() {
        return draftHash;
    }

    public void setDraftHash(String draftHash) {
        this.draftHash = draftHash;
    }

    public String getChangedDomainsJson() {
        return changedDomainsJson;
    }

    public void setChangedDomainsJson(String changedDomainsJson) {
        this.changedDomainsJson = changedDomainsJson;
    }

    public String getBaselineJson() {
        return baselineJson;
    }

    public void setBaselineJson(String baselineJson) {
        this.baselineJson = baselineJson;
    }

    public String getDraftJson() {
        return draftJson;
    }

    public void setDraftJson(String draftJson) {
        this.draftJson = draftJson;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getVerifyAttemptCount() {
        return verifyAttemptCount;
    }

    public void setVerifyAttemptCount(Integer verifyAttemptCount) {
        this.verifyAttemptCount = verifyAttemptCount;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getVerifyStartedAt() {
        return verifyStartedAt;
    }

    public void setVerifyStartedAt(LocalDateTime verifyStartedAt) {
        this.verifyStartedAt = verifyStartedAt;
    }

    public LocalDateTime getVerifyFinishedAt() {
        return verifyFinishedAt;
    }

    public void setVerifyFinishedAt(LocalDateTime verifyFinishedAt) {
        this.verifyFinishedAt = verifyFinishedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
