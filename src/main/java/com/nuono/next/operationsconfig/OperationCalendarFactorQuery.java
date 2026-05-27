package com.nuono.next.operationsconfig;

import java.time.LocalDate;

public class OperationCalendarFactorQuery {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate date;
    private final String partnerSku;
    private final String sku;

    public OperationCalendarFactorQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate date,
            String partnerSku,
            String sku
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.date = date;
        this.partnerSku = partnerSku;
        this.sku = sku;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getDate() { return date; }
    public String getPartnerSku() { return partnerSku; }
    public String getSku() { return sku; }
}
