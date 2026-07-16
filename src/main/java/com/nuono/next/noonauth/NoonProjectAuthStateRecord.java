package com.nuono.next.noonauth;

import java.time.LocalDateTime;

public class NoonProjectAuthStateRecord {
    private Long ownerUserId;
    private String projectCode;
    private String identityKey;
    private NoonProjectAuthStatus status;
    private Long activeRecoveryId;
    private Long authVersion;
    private String bindingFingerprint;
    private String configFingerprint;
    private String lastFailureCode;
    private Long lastFailureTaskId;
    private LocalDateTime lastFailureAt;
    private LocalDateTime lastSuccessAt;
    private String manualHoldReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public void setIdentityKey(String identityKey) {
        this.identityKey = identityKey;
    }

    public NoonProjectAuthStatus getStatus() {
        return status;
    }

    public void setStatus(NoonProjectAuthStatus status) {
        this.status = status;
    }

    public Long getActiveRecoveryId() {
        return activeRecoveryId;
    }

    public void setActiveRecoveryId(Long activeRecoveryId) {
        this.activeRecoveryId = activeRecoveryId;
    }

    public Long getAuthVersion() {
        return authVersion;
    }

    public void setAuthVersion(Long authVersion) {
        this.authVersion = authVersion;
    }

    public String getBindingFingerprint() {
        return bindingFingerprint;
    }

    public void setBindingFingerprint(String bindingFingerprint) {
        this.bindingFingerprint = bindingFingerprint;
    }

    public String getConfigFingerprint() {
        return configFingerprint;
    }

    public void setConfigFingerprint(String configFingerprint) {
        this.configFingerprint = configFingerprint;
    }

    public String getLastFailureCode() {
        return lastFailureCode;
    }

    public void setLastFailureCode(String lastFailureCode) {
        this.lastFailureCode = lastFailureCode;
    }

    public Long getLastFailureTaskId() {
        return lastFailureTaskId;
    }

    public void setLastFailureTaskId(Long lastFailureTaskId) {
        this.lastFailureTaskId = lastFailureTaskId;
    }

    public LocalDateTime getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(LocalDateTime lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public String getManualHoldReason() {
        return manualHoldReason;
    }

    public void setManualHoldReason(String manualHoldReason) {
        this.manualHoldReason = manualHoldReason;
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
