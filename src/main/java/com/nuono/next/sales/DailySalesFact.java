package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailySalesFact {

    private final String sourceSystem;
    private final Long sourceBatchId;
    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate factDate;
    private final String partnerSku;
    private final String sku;
    private final String skuConfig;
    private final String countryCode;
    private final String currencyCode;
    private final String productTitle;
    private final Integer yourVisitors;
    private final Integer totalVisitors;
    private final Integer grossUnits;
    private final Integer shippedUnits;
    private final Integer cancelledUnits;
    private final int netUnits;
    private final BigDecimal revenueShipped;
    private final BigDecimal buyBoxVisitorPercentage;
    private final BigDecimal conversionVisitorsPercentage;
    private final BigDecimal aspShippedPercentage;

    public DailySalesFact(
            String sourceSystem,
            Long sourceBatchId,
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            LocalDate factDate,
            String partnerSku,
            String sku,
            String skuConfig,
            String countryCode,
            String currencyCode,
            String productTitle,
            Integer yourVisitors,
            Integer totalVisitors,
            Integer grossUnits,
            Integer shippedUnits,
            Integer cancelledUnits,
            int netUnits,
            BigDecimal revenueShipped,
            BigDecimal buyBoxVisitorPercentage,
            BigDecimal conversionVisitorsPercentage,
            BigDecimal aspShippedPercentage
    ) {
        this.sourceSystem = sourceSystem;
        this.sourceBatchId = sourceBatchId;
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.factDate = factDate;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.skuConfig = skuConfig;
        this.countryCode = countryCode;
        this.currencyCode = currencyCode;
        this.productTitle = productTitle;
        this.yourVisitors = yourVisitors;
        this.totalVisitors = totalVisitors;
        this.grossUnits = grossUnits;
        this.shippedUnits = shippedUnits;
        this.cancelledUnits = cancelledUnits;
        this.netUnits = netUnits;
        this.revenueShipped = revenueShipped;
        this.buyBoxVisitorPercentage = buyBoxVisitorPercentage;
        this.conversionVisitorsPercentage = conversionVisitorsPercentage;
        this.aspShippedPercentage = aspShippedPercentage;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public Long getSourceBatchId() {
        return sourceBatchId;
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

    public LocalDate getFactDate() {
        return factDate;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public String getSkuConfig() {
        return skuConfig;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public Integer getYourVisitors() {
        return yourVisitors;
    }

    public Integer getTotalVisitors() {
        return totalVisitors;
    }

    public Integer getGrossUnits() {
        return grossUnits;
    }

    public Integer getShippedUnits() {
        return shippedUnits;
    }

    public Integer getCancelledUnits() {
        return cancelledUnits;
    }

    public int getNetUnits() {
        return netUnits;
    }

    public BigDecimal getRevenueShipped() {
        return revenueShipped;
    }

    public BigDecimal getBuyBoxVisitorPercentage() {
        return buyBoxVisitorPercentage;
    }

    public BigDecimal getConversionVisitorsPercentage() {
        return conversionVisitorsPercentage;
    }

    public BigDecimal getAspShippedPercentage() {
        return aspShippedPercentage;
    }
}
