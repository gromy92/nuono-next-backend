package com.nuono.next.intransit.autosync;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class LogisticsAutoSyncAccountView {
    private Long accountId;
    private Long ownerUserId;
    private Long operatorUserId;
    private String sourceSystem;
    private String forwarderName;
    private String loginAccountMasked;
    private Boolean enabled;
    private Boolean scheduleEnabled;
    private Boolean commitEnabled;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LogisticsAutoSyncAccountView from(LogisticsAutoSyncAccount row) {
        if (row == null) {
            return null;
        }
        LogisticsAutoSyncAccountView view = new LogisticsAutoSyncAccountView();
        view.setAccountId(row.getId());
        view.setOwnerUserId(row.getOwnerUserId());
        view.setOperatorUserId(row.getOperatorUserId());
        view.setSourceSystem(row.getSourceSystem());
        view.setForwarderName(row.getForwarderName());
        view.setLoginAccountMasked(maskLoginAccount(row.getLoginAccount()));
        view.setEnabled(row.getEnabled());
        view.setScheduleEnabled(row.getScheduleEnabled());
        view.setCommitEnabled(row.getCommitEnabled());
        view.setScheduleWindowStart(row.getScheduleWindowStart());
        view.setScheduleWindowEnd(row.getScheduleWindowEnd());
        view.setMinIntervalHours(row.getMinIntervalHours());
        view.setVerificationStatus(row.getVerificationStatus());
        view.setLastLoginStatus(row.getLastLoginStatus());
        view.setLastPreviewStatus(row.getLastPreviewStatus());
        view.setLastSyncStatus(row.getLastSyncStatus());
        view.setLastTaskId(row.getLastTaskId());
        view.setLastSyncedAt(row.getLastSyncedAt());
        view.setNextEligibleAt(row.getNextEligibleAt());
        view.setCooldownUntil(row.getCooldownUntil());
        view.setLastFailureCode(row.getLastFailureCode());
        view.setLastFailureMessage(row.getLastFailureMessage());
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
        return view;
    }

    private static String maskLoginAccount(String loginAccount) {
        if (loginAccount == null || loginAccount.isBlank()) {
            return null;
        }
        String value = loginAccount.trim();
        if (value.length() <= 4) {
            return value.substring(0, 1) + "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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

    public String getLoginAccountMasked() {
        return loginAccountMasked;
    }

    public void setLoginAccountMasked(String loginAccountMasked) {
        this.loginAccountMasked = loginAccountMasked;
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
