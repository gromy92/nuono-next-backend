package com.nuono.next.salesforecast;

public class SalesForecastFollowUpRequest {

    private String storeCode;
    private String siteCode;
    private String partnerSku;
    private String sku;
    private boolean marked;

    public SalesForecastFollowUpRequest() {
    }

    public SalesForecastFollowUpRequest(
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            boolean marked
    ) {
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.marked = marked;
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

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public boolean isMarked() {
        return marked;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }
}
