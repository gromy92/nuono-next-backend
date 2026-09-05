package com.nuono.next.intransit.autosync;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class LogisticsAutoSyncAccount {
    private Long id;
    private Long ownerUserId;
    private Long operatorUserId;
    private String sourceSystem;
    private String forwarderName;
    private String loginAccount;
    private String loginAccountHash;
    private String passwordCipher;
    private Boolean enabled;
    private Boolean scheduleEnabled;
    private Boolean commitEnabled;
    private Boolean freightBillScheduleEnabled;
    private Boolean freightBillCommitEnabled;
    private LocalTime scheduleWindowStart;
    private LocalTime scheduleWindowEnd;
    private Integer minIntervalHours;
    private String verificationStatus;
    private String lastLoginStatus;
    private String lastPreviewStatus;
    private String lastSyncStatus;
    private Long lastTaskId;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime nextEligibleAt;
    private LocalDateTime cooldownUntil;
    private String lastFailureCode;
    private String lastFailureMessage;
    private String freightBillLastPreviewStatus;
    private String freightBillLastSyncStatus;
    private Long freightBillLastTaskId;
    private LocalDateTime freightBillLastSyncedAt;
    private LocalDateTime freightBillNextEligibleAt;
    private LocalDateTime freightBillCooldownUntil;
    private String freightBillLastFailureCode;
    private String freightBillLastFailureMessage;
    private Boolean deleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getForwarderName() {
        return forwarderName;
    }

    public void setForwarderName(String forwarderName) {
        this.forwarderName = forwarderName;
    }

    public String getLoginAccount() {
        return loginAccount;
    }

    public void setLoginAccount(String loginAccount) {
        this.loginAccount = loginAccount;
    }

    public String getLoginAccountHash() {
        return loginAccountHash;
    }

    public void setLoginAccountHash(String loginAccountHash) {
        this.loginAccountHash = loginAccountHash;
    }

    public String getPasswordCipher() {
        return passwordCipher;
    }

    public void setPasswordCipher(String passwordCipher) {
        this.passwordCipher = passwordCipher;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getScheduleEnabled() {
        return scheduleEnabled;
    }

    public void setScheduleEnabled(Boolean scheduleEnabled) {
        this.scheduleEnabled = scheduleEnabled;
    }

    public Boolean getCommitEnabled() {
        return commitEnabled;
    }

    public void setCommitEnabled(Boolean commitEnabled) {
        this.commitEnabled = commitEnabled;
    }

    public Boolean getFreightBillScheduleEnabled() { return freightBillScheduleEnabled; }
    public void setFreightBillScheduleEnabled(Boolean freightBillScheduleEnabled) { this.freightBillScheduleEnabled = freightBillScheduleEnabled; }
    public Boolean getFreightBillCommitEnabled() { return freightBillCommitEnabled; }
    public void setFreightBillCommitEnabled(Boolean freightBillCommitEnabled) { this.freightBillCommitEnabled = freightBillCommitEnabled; }

    public LocalTime getScheduleWindowStart() {
        return scheduleWindowStart;
    }

    public void setScheduleWindowStart(LocalTime scheduleWindowStart) {
        this.scheduleWindowStart = scheduleWindowStart;
    }

    public LocalTime getScheduleWindowEnd() {
        return scheduleWindowEnd;
    }

    public void setScheduleWindowEnd(LocalTime scheduleWindowEnd) {
        this.scheduleWindowEnd = scheduleWindowEnd;
    }

    public Integer getMinIntervalHours() {
        return minIntervalHours;
    }

    public void setMinIntervalHours(Integer minIntervalHours) {
        this.minIntervalHours = minIntervalHours;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getLastLoginStatus() {
        return lastLoginStatus;
    }

    public void setLastLoginStatus(String lastLoginStatus) {
        this.lastLoginStatus = lastLoginStatus;
    }

    public String getLastPreviewStatus() {
        return lastPreviewStatus;
    }

    public void setLastPreviewStatus(String lastPreviewStatus) {
        this.lastPreviewStatus = lastPreviewStatus;
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public void setLastSyncStatus(String lastSyncStatus) {
        this.lastSyncStatus = lastSyncStatus;
    }

    public Long getLastTaskId() {
        return lastTaskId;
    }

    public void setLastTaskId(Long lastTaskId) {
        this.lastTaskId = lastTaskId;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public LocalDateTime getNextEligibleAt() {
        return nextEligibleAt;
    }

    public void setNextEligibleAt(LocalDateTime nextEligibleAt) {
        this.nextEligibleAt = nextEligibleAt;
    }

    public LocalDateTime getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(LocalDateTime cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public String getLastFailureCode() {
        return lastFailureCode;
    }

    public void setLastFailureCode(String lastFailureCode) {
        this.lastFailureCode = lastFailureCode;
    }

    public String getLastFailureMessage() {
        return lastFailureMessage;
    }

    public void setLastFailureMessage(String lastFailureMessage) {
        this.lastFailureMessage = lastFailureMessage;
    }

    public String getFreightBillLastPreviewStatus() { return freightBillLastPreviewStatus; }
    public void setFreightBillLastPreviewStatus(String value) { this.freightBillLastPreviewStatus = value; }
    public String getFreightBillLastSyncStatus() { return freightBillLastSyncStatus; }
    public void setFreightBillLastSyncStatus(String value) { this.freightBillLastSyncStatus = value; }
    public Long getFreightBillLastTaskId() { return freightBillLastTaskId; }
    public void setFreightBillLastTaskId(Long value) { this.freightBillLastTaskId = value; }
    public LocalDateTime getFreightBillLastSyncedAt() { return freightBillLastSyncedAt; }
    public void setFreightBillLastSyncedAt(LocalDateTime value) { this.freightBillLastSyncedAt = value; }
    public LocalDateTime getFreightBillNextEligibleAt() { return freightBillNextEligibleAt; }
    public void setFreightBillNextEligibleAt(LocalDateTime value) { this.freightBillNextEligibleAt = value; }
    public LocalDateTime getFreightBillCooldownUntil() { return freightBillCooldownUntil; }
    public void setFreightBillCooldownUntil(LocalDateTime value) { this.freightBillCooldownUntil = value; }
    public String getFreightBillLastFailureCode() { return freightBillLastFailureCode; }
    public void setFreightBillLastFailureCode(String value) { this.freightBillLastFailureCode = value; }
    public String getFreightBillLastFailureMessage() { return freightBillLastFailureMessage; }
    public void setFreightBillLastFailureMessage(String value) { this.freightBillLastFailureMessage = value; }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
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
