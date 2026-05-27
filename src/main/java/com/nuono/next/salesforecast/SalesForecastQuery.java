package com.nuono.next.salesforecast;

public class SalesForecastQuery {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;

    public SalesForecastQuery(Long ownerUserId, String storeCode, String siteCode) {
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
