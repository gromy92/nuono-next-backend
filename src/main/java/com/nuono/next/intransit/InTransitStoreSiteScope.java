package com.nuono.next.intransit;

public class InTransitStoreSiteScope {

    private String storeCode;
    private String siteCode;

    public InTransitStoreSiteScope() {
    }

    public InTransitStoreSiteScope(String storeCode, String siteCode) {
        this.storeCode = storeCode;
        this.siteCode = siteCode;
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
