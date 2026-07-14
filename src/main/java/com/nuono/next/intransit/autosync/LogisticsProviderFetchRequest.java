package com.nuono.next.intransit.autosync;

public class LogisticsProviderFetchRequest {
    private Long accountId;
    private Long ownerUserId;
    private String sourceSystem;
    private String forwarderName;
    private String loginAccount;
    private String password;
    private int recentLimit;
    private boolean forceFullSync;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getForwarderName() { return forwarderName; }
    public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
    public String getLoginAccount() { return loginAccount; }
    public void setLoginAccount(String loginAccount) { this.loginAccount = loginAccount; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getRecentLimit() { return recentLimit; }
    public void setRecentLimit(int recentLimit) { this.recentLimit = recentLimit; }
    public boolean isForceFullSync() { return forceFullSync; }
    public void setForceFullSync(boolean forceFullSync) { this.forceFullSync = forceFullSync; }
}
