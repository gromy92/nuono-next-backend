package com.nuono.next.noonpull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class NoonSalesDailyFact {
    final Long ownerUserId;
    final String storeCode;
    final String siteCode;
    final LocalDate salesDate;
    final String skuParent;
    final String sku;
    final long unitsSold;
    final BigDecimal salesAmount;
    final String currency;
    final String sourceBatchId;

    public NoonSalesDailyFact(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate salesDate,
            String skuParent,
            String sku,
            long unitsSold,
            BigDecimal salesAmount,
            String currency,
            String sourceBatchId
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.salesDate = salesDate;
        this.skuParent = skuParent;
        this.sku = sku;
        this.unitsSold = unitsSold;
        this.salesAmount = salesAmount;
        this.currency = currency;
        this.sourceBatchId = sourceBatchId;
    }

    public String key() {
        return ownerUserId + "|" + storeCode + "|" + siteCode + "|" + salesDate + "|" + skuParent;
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

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public String getSku() {
        return sku;
    }

    public long getUnitsSold() {
        return unitsSold;
    }

    public BigDecimal getSalesAmount() {
        return salesAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }
}
