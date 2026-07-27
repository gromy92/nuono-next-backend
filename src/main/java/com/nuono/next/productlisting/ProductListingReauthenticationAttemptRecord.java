package com.nuono.next.productlisting;

import java.time.LocalDateTime;

public class ProductListingReauthenticationAttemptRecord {
    private Long realRunTaskId;
    private Long ownerUserId;
    private Long draftId;
    private Long projectId;
    private String projectCode;
    private String storeCode;
    private Long recoveryId;
    private Long recoveryItemId;
    private Long requestedAuthVersion;
    private String resumeAction;
    private String status;
    private Long versionNo;
    private String failureCode;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private String recoveryItemStatus;
    private String recoveryItemFailureCode;
    private String recoveryStatus;
    private String recoveryFailureCode;
    private String projectAuthStatus;
    private Long currentAuthVersion;
    private Long activeRecoveryId;
    private LocalDateTime recoveryItemRecoveredAt;

    public Long getRealRunTaskId() {
        return realRunTaskId;
    }

    public void setRealRunTaskId(Long realRunTaskId) {
        this.realRunTaskId = realRunTaskId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public Long getRecoveryId() {
        return recoveryId;
    }

    public void setRecoveryId(Long recoveryId) {
        this.recoveryId = recoveryId;
    }

    public Long getRecoveryItemId() {
        return recoveryItemId;
    }

    public void setRecoveryItemId(Long recoveryItemId) {
        this.recoveryItemId = recoveryItemId;
    }

    public Long getRequestedAuthVersion() {
        return requestedAuthVersion;
    }

    public void setRequestedAuthVersion(Long requestedAuthVersion) {
        this.requestedAuthVersion = requestedAuthVersion;
    }

    public String getResumeAction() {
        return resumeAction;
    }

    public void setResumeAction(String resumeAction) {
        this.resumeAction = resumeAction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Long versionNo) {
        this.versionNo = versionNo;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getRecoveryItemStatus() {
        return recoveryItemStatus;
    }

    public void setRecoveryItemStatus(String recoveryItemStatus) {
        this.recoveryItemStatus = recoveryItemStatus;
    }

    public String getRecoveryItemFailureCode() {
        return recoveryItemFailureCode;
    }

    public void setRecoveryItemFailureCode(String recoveryItemFailureCode) {
        this.recoveryItemFailureCode = recoveryItemFailureCode;
    }

    public String getRecoveryStatus() {
        return recoveryStatus;
    }

    public void setRecoveryStatus(String recoveryStatus) {
        this.recoveryStatus = recoveryStatus;
    }

    public String getRecoveryFailureCode() {
        return recoveryFailureCode;
    }

    public void setRecoveryFailureCode(String recoveryFailureCode) {
        this.recoveryFailureCode = recoveryFailureCode;
    }

    public String getProjectAuthStatus() {
        return projectAuthStatus;
    }

    public void setProjectAuthStatus(String projectAuthStatus) {
        this.projectAuthStatus = projectAuthStatus;
    }

    public Long getCurrentAuthVersion() {
        return currentAuthVersion;
    }

    public void setCurrentAuthVersion(Long currentAuthVersion) {
        this.currentAuthVersion = currentAuthVersion;
    }

    public Long getActiveRecoveryId() {
        return activeRecoveryId;
    }

    public void setActiveRecoveryId(Long activeRecoveryId) {
        this.activeRecoveryId = activeRecoveryId;
    }

    public LocalDateTime getRecoveryItemRecoveredAt() {
        return recoveryItemRecoveredAt;
    }

    public void setRecoveryItemRecoveredAt(
            LocalDateTime recoveryItemRecoveredAt
    ) {
        this.recoveryItemRecoveredAt = recoveryItemRecoveredAt;
    }
}
