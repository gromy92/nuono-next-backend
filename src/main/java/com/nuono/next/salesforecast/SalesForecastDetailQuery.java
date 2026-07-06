package com.nuono.next.salesforecast;

public class SalesForecastDetailQuery extends SalesForecastQuery {

    private final String partnerSku;

    public SalesForecastDetailQuery(Long ownerUserId, String storeCode, String siteCode, String partnerSku) {
        super(ownerUserId, storeCode, siteCode);
        this.partnerSku = partnerSku;
    }

    public String getPartnerSku() {
        return partnerSku;
    }
}
