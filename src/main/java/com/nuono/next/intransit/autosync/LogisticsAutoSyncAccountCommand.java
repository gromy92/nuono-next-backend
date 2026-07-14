package com.nuono.next.intransit.autosync;

import java.time.LocalTime;

public class LogisticsAutoSyncAccountCommand {
    private Long accountId;
    private Long ownerUserId;
    private Long operatorUserId;
    private String sourceSystem;
    private String forwarderName;
    private String loginAccount;
    private String password;
    private Boolean enabled;
    private Boolean scheduleEnabled;
    private Boolean commitEnabled;
    private LocalTime scheduleWindowStart;
    private LocalTime scheduleWindowEnd;
    private Integer minIntervalHours;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getForwarderName() { return forwarderName; }
    public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
    public String getLoginAccount() { return loginAccount; }
    public void setLoginAccount(String loginAccount) { this.loginAccount = loginAccount; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public Boolean getCommitEnabled() { return commitEnabled; }
    public void setCommitEnabled(Boolean commitEnabled) { this.commitEnabled = commitEnabled; }
    public LocalTime getScheduleWindowStart() { return scheduleWindowStart; }
    public void setScheduleWindowStart(LocalTime scheduleWindowStart) { this.scheduleWindowStart = scheduleWindowStart; }
    public LocalTime getScheduleWindowEnd() { return scheduleWindowEnd; }
    public void setScheduleWindowEnd(LocalTime scheduleWindowEnd) { this.scheduleWindowEnd = scheduleWindowEnd; }
    public Integer getMinIntervalHours() { return minIntervalHours; }
    public void setMinIntervalHours(Integer minIntervalHours) { this.minIntervalHours = minIntervalHours; }
}
