package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.LocalDateTime;

public class NoonAuthTransientBackoffState {
    private Long logicalStoreId;
    private NoonTransientErrorType errorType;
    private Long ownerUserId;
    private String projectCode;
    private String lastStoreCode;
    private NoonAuthRecoveryFailureStage sourceStage;
    private Long sourceRecoveryId;
    private Integer attemptCount;
    private LocalDateTime blockedUntil;
    private LocalDateTime lastFailedAt;
    private LocalDateTime lastSuccessAt;
    private String diagnosticSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public void setLogicalStoreId(Long logicalStoreId) {
        this.logicalStoreId = logicalStoreId;
    }

    public NoonTransientErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(NoonTransientErrorType errorType) {
        this.errorType = errorType;
    }

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

    public String getLastStoreCode() {
        return lastStoreCode;
    }

    public void setLastStoreCode(String lastStoreCode) {
        this.lastStoreCode = lastStoreCode;
    }

    public NoonAuthRecoveryFailureStage getSourceStage() {
        return sourceStage;
    }

    public void setSourceStage(NoonAuthRecoveryFailureStage sourceStage) {
        this.sourceStage = sourceStage;
    }

    public Long getSourceRecoveryId() {
        return sourceRecoveryId;
    }

    public void setSourceRecoveryId(Long sourceRecoveryId) {
        this.sourceRecoveryId = sourceRecoveryId;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public LocalDateTime getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(LocalDateTime lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
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
