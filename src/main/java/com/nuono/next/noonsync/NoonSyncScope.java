package com.nuono.next.noonsync;

import java.util.Objects;

public class NoonSyncScope {

    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;

    private NoonSyncScope(Long ownerUserId, Long logicalStoreId, String storeCode, String siteCode) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
    }

    public static NoonSyncScope of(Long ownerUserId, Long logicalStoreId, String storeCode, String siteCode) {
        return new NoonSyncScope(ownerUserId, logicalStoreId, storeCode, siteCode);
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NoonSyncScope)) {
            return false;
        }
        NoonSyncScope that = (NoonSyncScope) other;
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(logicalStoreId, that.logicalStoreId)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, logicalStoreId, storeCode, siteCode);
    }
}
