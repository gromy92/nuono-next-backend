package com.nuono.next.salesforecast;

import java.time.LocalDate;

public class SalesForecastHistoricalStockSnapshot {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final LocalDate stockDate;
    private final Integer sellableStock;

    public SalesForecastHistoricalStockSnapshot(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            LocalDate stockDate,
            Integer sellableStock
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.stockDate = stockDate;
        this.sellableStock = sellableStock;
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

    public LocalDate getStockDate() {
        return stockDate;
    }

    public Integer getSellableStock() {
        return sellableStock;
    }
}
