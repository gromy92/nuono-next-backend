package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LegacySalesBackfillRow {

    private final String sourceRowId;
    private final Long legacyOwnerUserId;
    private final String legacyStoreCode;
    private final String siteCode;
    private final LocalDate factDate;
    private final String partnerSku;
    private final String sku;
    private final String skuConfig;
    private final String productTitle;
    private final String currencyCode;
    private final Integer grossUnits;
    private final Integer shippedUnits;
    private final Integer cancelledUnits;
    private final BigDecimal revenueShipped;

    public LegacySalesBackfillRow(
            String sourceRowId,
            Long legacyOwnerUserId,
            String legacyStoreCode,
            String siteCode,
            LocalDate factDate,
            String partnerSku,
            String sku,
            String skuConfig,
            String productTitle,
            String currencyCode,
            Integer grossUnits,
            Integer shippedUnits,
            Integer cancelledUnits,
            BigDecimal revenueShipped
    ) {
        this.sourceRowId = sourceRowId;
        this.legacyOwnerUserId = legacyOwnerUserId;
        this.legacyStoreCode = legacyStoreCode;
        this.siteCode = siteCode;
        this.factDate = factDate;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.skuConfig = skuConfig;
        this.productTitle = productTitle;
        this.currencyCode = currencyCode;
        this.grossUnits = grossUnits;
        this.shippedUnits = shippedUnits;
        this.cancelledUnits = cancelledUnits;
        this.revenueShipped = revenueShipped;
    }

    public String getSourceRowId() {
        return sourceRowId;
    }

    public Long getLegacyOwnerUserId() {
        return legacyOwnerUserId;
    }

    public String getLegacyStoreCode() {
        return legacyStoreCode;
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

    public String getProductTitle() {
        return productTitle;
    }

    public String getCurrencyCode() {
        return currencyCode;
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

    public BigDecimal getRevenueShipped() {
        return revenueShipped;
    }
}
