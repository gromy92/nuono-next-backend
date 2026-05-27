package com.nuono.next.operationsconfig;

public class OperationConfigBossOption {

    private final Long ownerUserId;
    private final String displayName;
    private final String accountNo;

    public OperationConfigBossOption(Long ownerUserId, String displayName, String accountNo) {
        this.ownerUserId = ownerUserId;
        this.displayName = displayName;
        this.accountNo = accountNo;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccountNo() {
        return accountNo;
    }
}
