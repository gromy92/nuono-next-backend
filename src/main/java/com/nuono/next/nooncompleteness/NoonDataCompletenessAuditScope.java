package com.nuono.next.nooncompleteness;

public class NoonDataCompletenessAuditScope {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;

    public NoonDataCompletenessAuditScope() {
    }

    public NoonDataCompletenessAuditScope(Long ownerUserId, String storeCode, String siteCode) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
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
}
