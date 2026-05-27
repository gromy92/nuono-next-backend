package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public class NoonProductViewsSalesReportRow {

    private final LocalDate visitDate;
    private final String partnerSku;
    private final String mpCode;
    private final String skuConfig;
    private final String sku;
    private final String family;
    private final String productType;
    private final String productSubtype;
    private final String brand;
    private final String currencyCode;
    private final String productTitle;
    private final String countryCode;
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

    public NoonProductViewsSalesReportRow(
            LocalDate visitDate,
            String partnerSku,
            String mpCode,
            String skuConfig,
            String sku,
            String family,
            String productType,
            String productSubtype,
            String brand,
            String currencyCode,
            String productTitle,
            String countryCode,
            Integer yourVisitors,
            Integer totalVisitors,
            Integer grossUnits,
            Integer shippedUnits,
            Integer cancelledUnits,
            BigDecimal revenueShipped,
            BigDecimal buyBoxVisitorPercentage,
            BigDecimal conversionVisitorsPercentage,
            BigDecimal aspShippedPercentage
    ) {
        this.visitDate = visitDate;
        this.partnerSku = partnerSku;
        this.mpCode = mpCode;
        this.skuConfig = skuConfig;
        this.sku = sku;
        this.family = family;
        this.productType = productType;
        this.productSubtype = productSubtype;
        this.brand = brand;
        this.currencyCode = currencyCode;
        this.productTitle = productTitle;
        this.countryCode = countryCode;
        this.yourVisitors = yourVisitors;
        this.totalVisitors = totalVisitors;
        this.grossUnits = grossUnits;
        this.shippedUnits = shippedUnits;
        this.cancelledUnits = cancelledUnits;
        int gross = valueOrZero(grossUnits);
        int shipped = valueOrZero(shippedUnits);
        int cancelled = valueOrZero(cancelledUnits);
        this.netUnits = Math.max(Math.max(gross - cancelled, shipped), 0);
        this.revenueShipped = revenueShipped;
        this.buyBoxVisitorPercentage = buyBoxVisitorPercentage;
        this.conversionVisitorsPercentage = conversionVisitorsPercentage;
        this.aspShippedPercentage = aspShippedPercentage;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getMpCode() {
        return mpCode;
    }

    public String getSkuConfig() {
        return skuConfig;
    }

    public String getSku() {
        return sku;
    }

    public String getFamily() {
        return family;
    }

    public String getProductType() {
        return productType;
    }

    public String getProductSubtype() {
        return productSubtype;
    }

    public String getBrand() {
        return brand;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public String getCountryCode() {
        return countryCode;
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

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
