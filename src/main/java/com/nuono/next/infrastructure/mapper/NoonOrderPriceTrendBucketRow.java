package com.nuono.next.infrastructure.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public class NoonOrderPriceTrendBucketRow {

    private LocalDate bucketStart;
    private BigDecimal avgOfferPrice;
    private BigDecimal minOfferPrice;
    private BigDecimal maxOfferPrice;
    private int orderLineCount;
    private String currencyCode;

    public NoonOrderPriceTrendBucketRow() {
    }

    public NoonOrderPriceTrendBucketRow(
            LocalDate bucketStart,
            BigDecimal avgOfferPrice,
            BigDecimal minOfferPrice,
            BigDecimal maxOfferPrice,
            int orderLineCount,
            String currencyCode
    ) {
        this.bucketStart = bucketStart;
        this.avgOfferPrice = avgOfferPrice;
        this.minOfferPrice = minOfferPrice;
        this.maxOfferPrice = maxOfferPrice;
        this.orderLineCount = orderLineCount;
        this.currencyCode = currencyCode;
    }

    public LocalDate getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(LocalDate bucketStart) {
        this.bucketStart = bucketStart;
    }

    public BigDecimal getAvgOfferPrice() {
        return avgOfferPrice;
    }

    public void setAvgOfferPrice(BigDecimal avgOfferPrice) {
        this.avgOfferPrice = avgOfferPrice;
    }

    public BigDecimal getMinOfferPrice() {
        return minOfferPrice;
    }

    public void setMinOfferPrice(BigDecimal minOfferPrice) {
        this.minOfferPrice = minOfferPrice;
    }

    public BigDecimal getMaxOfferPrice() {
        return maxOfferPrice;
    }

    public void setMaxOfferPrice(BigDecimal maxOfferPrice) {
        this.maxOfferPrice = maxOfferPrice;
    }

    public int getOrderLineCount() {
        return orderLineCount;
    }

    public void setOrderLineCount(int orderLineCount) {
        this.orderLineCount = orderLineCount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }
}
