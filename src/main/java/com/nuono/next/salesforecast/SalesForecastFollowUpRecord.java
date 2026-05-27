package com.nuono.next.salesforecast;

public class SalesForecastFollowUpRecord {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final String sku;
    private final boolean marked;
    private final Long markedBy;

    public SalesForecastFollowUpRecord(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            boolean marked,
            Long markedBy
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.marked = marked;
        this.markedBy = markedBy;
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

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public boolean isMarked() {
        return marked;
    }

    public Long getMarkedBy() {
        return markedBy;
    }
}
