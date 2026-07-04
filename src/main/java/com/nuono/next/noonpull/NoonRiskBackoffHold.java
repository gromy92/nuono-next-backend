package com.nuono.next.noonpull;

import java.time.LocalDateTime;

public class NoonRiskBackoffHold {
    private String scopeKey;
    private String scopeType;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String operationGroup;
    private String riskType;
    private String sourceDomain;
    private Long sourceTaskId;
    private LocalDateTime blockedUntil;
    private Integer attemptCount;
    private String diagnosticSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoonRiskBackoffHold copy() {
        NoonRiskBackoffHold copy = new NoonRiskBackoffHold();
        copy.scopeKey = scopeKey;
        copy.scopeType = scopeType;
        copy.ownerUserId = ownerUserId;
        copy.storeCode = storeCode;
        copy.siteCode = siteCode;
        copy.operationGroup = operationGroup;
        copy.riskType = riskType;
        copy.sourceDomain = sourceDomain;
        copy.sourceTaskId = sourceTaskId;
        copy.blockedUntil = blockedUntil;
        copy.attemptCount = attemptCount;
        copy.diagnosticSummary = diagnosticSummary;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
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

    public String getOperationGroup() {
        return operationGroup;
    }

    public void setOperationGroup(String operationGroup) {
        this.operationGroup = operationGroup;
    }

    public String getRiskType() {
        return riskType;
    }

    public void setRiskType(String riskType) {
        this.riskType = riskType;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public void setSourceDomain(String sourceDomain) {
        this.sourceDomain = sourceDomain;
    }

    public Long getSourceTaskId() {
        return sourceTaskId;
    }

    public void setSourceTaskId(Long sourceTaskId) {
        this.sourceTaskId = sourceTaskId;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public void setDiagnosticSummary(String diagnosticSummary) {
        this.diagnosticSummary = diagnosticSummary;
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
