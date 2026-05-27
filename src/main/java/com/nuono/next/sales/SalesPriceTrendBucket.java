package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesPriceTrendBucket {

    private final LocalDate bucketStart;
    private final String bucketLabel;
    private final BigDecimal avgOfferPrice;
    private final BigDecimal minOfferPrice;
    private final BigDecimal maxOfferPrice;
    private final int orderLineCount;
    private final String currencyCode;

    public SalesPriceTrendBucket(
            LocalDate bucketStart,
            String bucketLabel,
            BigDecimal avgOfferPrice,
            BigDecimal minOfferPrice,
            BigDecimal maxOfferPrice,
            int orderLineCount,
            String currencyCode
    ) {
        this.bucketStart = bucketStart;
        this.bucketLabel = bucketLabel;
        this.avgOfferPrice = avgOfferPrice;
        this.minOfferPrice = minOfferPrice;
        this.maxOfferPrice = maxOfferPrice;
        this.orderLineCount = Math.max(0, orderLineCount);
        this.currencyCode = currencyCode;
    }

    public LocalDate getBucketStart() {
        return bucketStart;
    }

    public String getBucketLabel() {
        return bucketLabel;
    }

    public BigDecimal getAvgOfferPrice() {
        return avgOfferPrice;
    }

    public BigDecimal getMinOfferPrice() {
        return minOfferPrice;
    }

    public BigDecimal getMaxOfferPrice() {
        return maxOfferPrice;
    }

    public int getOrderLineCount() {
        return orderLineCount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
