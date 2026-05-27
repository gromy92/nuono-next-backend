package com.nuono.next.operationsconfig;

public class OperationConfigBundleScopeStoreCommand {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;

    public OperationConfigBundleScopeStoreCommand(Long ownerUserId, String storeCode, String siteCode) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }
}
