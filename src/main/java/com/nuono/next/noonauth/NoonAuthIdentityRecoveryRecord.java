package com.nuono.next.noonauth;

import java.time.LocalDateTime;

public class NoonAuthIdentityRecoveryRecord {
    private Long id;
    private Long predecessorRecoveryId;
    private String identityKey;
    private NoonAuthRecoveryStatus status;
    private Integer generationNo;
    private Integer sendAttemptCount;
    private LocalDateTime firstSendAt;
    private LocalDateTime secondSendAt;
    private LocalDateTime coalesceUntil;
    private LocalDateTime nextAttemptAt;
    private String leaseOwner;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private Long versionNo;
    private String configFingerprint;
    private String lastMailUidHash;
    private String lastMessageIdHash;
    private String failureCode;
    private String diagnosticSummary;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPredecessorRecoveryId() {
        return predecessorRecoveryId;
    }

    public void setPredecessorRecoveryId(Long predecessorRecoveryId) {
        this.predecessorRecoveryId = predecessorRecoveryId;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public void setIdentityKey(String identityKey) {
        this.identityKey = identityKey;
    }

    public NoonAuthRecoveryStatus getStatus() {
        return status;
    }

    public void setStatus(NoonAuthRecoveryStatus status) {
        this.status = status;
    }

    public Integer getGenerationNo() {
        return generationNo;
    }

    public void setGenerationNo(Integer generationNo) {
        this.generationNo = generationNo;
    }

    public Integer getSendAttemptCount() {
        return sendAttemptCount;
    }

    public void setSendAttemptCount(Integer sendAttemptCount) {
        this.sendAttemptCount = sendAttemptCount;
    }

    public LocalDateTime getFirstSendAt() {
        return firstSendAt;
    }

    public void setFirstSendAt(LocalDateTime firstSendAt) {
        this.firstSendAt = firstSendAt;
    }

    public LocalDateTime getSecondSendAt() {
        return secondSendAt;
    }

    public void setSecondSendAt(LocalDateTime secondSendAt) {
        this.secondSendAt = secondSendAt;
    }

    public LocalDateTime getCoalesceUntil() {
        return coalesceUntil;
    }

    public void setCoalesceUntil(LocalDateTime coalesceUntil) {
        this.coalesceUntil = coalesceUntil;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public void setLeaseToken(String leaseToken) {
        this.leaseToken = leaseToken;
    }

    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(LocalDateTime leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public Long getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Long versionNo) {
        this.versionNo = versionNo;
    }

    public String getConfigFingerprint() {
        return configFingerprint;
    }

    public void setConfigFingerprint(String configFingerprint) {
        this.configFingerprint = configFingerprint;
    }

    public String getLastMailUidHash() {
        return lastMailUidHash;
    }

    public void setLastMailUidHash(String lastMailUidHash) {
        this.lastMailUidHash = lastMailUidHash;
    }

    public String getLastMessageIdHash() {
        return lastMessageIdHash;
    }

    public void setLastMessageIdHash(String lastMessageIdHash) {
        this.lastMessageIdHash = lastMessageIdHash;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public void setDiagnosticSummary(String diagnosticSummary) {
        this.diagnosticSummary = diagnosticSummary;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
