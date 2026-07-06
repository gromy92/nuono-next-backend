package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SalesForecastFeatureSnapshot {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final String sku;
    private final String productTitle;
    private final String brand;
    private final String productFulltype;
    private final String productFamily;
    private final LocalDate latestFactDate;
    private final int historyUnits7;
    private final int historyUnits30;
    private final int historyUnits60;
    private final int historyUnits90;
    private final BigDecimal adjustedHistoryUnits7;
    private final BigDecimal adjustedHistoryUnits30;
    private final BigDecimal adjustedHistoryUnits60;
    private final BigDecimal adjustedHistoryUnits90;
    private final int observedDays;
    private final Integer currentStock;
    private final BigDecimal stockCoverDays;
    private final List<String> warningCodes;

    public SalesForecastFeatureSnapshot(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int observedDays,
            Integer currentStock,
            BigDecimal stockCoverDays,
            List<String> warningCodes
    ) {
        this(
                ownerUserId,
                storeCode,
                siteCode,
                partnerSku,
                sku,
                productTitle,
                null,
                null,
                null,
                latestFactDate,
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                BigDecimal.valueOf(historyUnits7),
                BigDecimal.valueOf(historyUnits30),
                BigDecimal.valueOf(historyUnits60),
                BigDecimal.valueOf(historyUnits90),
                observedDays,
                currentStock,
                stockCoverDays,
                warningCodes
        );
    }

    public SalesForecastFeatureSnapshot(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            String productTitle,
            String brand,
            String productFulltype,
            String productFamily,
            LocalDate latestFactDate,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int observedDays,
            Integer currentStock,
            BigDecimal stockCoverDays,
            List<String> warningCodes
    ) {
        this(
                ownerUserId,
                storeCode,
                siteCode,
                partnerSku,
                sku,
                productTitle,
                brand,
                productFulltype,
                productFamily,
                latestFactDate,
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                BigDecimal.valueOf(historyUnits7),
                BigDecimal.valueOf(historyUnits30),
                BigDecimal.valueOf(historyUnits60),
                BigDecimal.valueOf(historyUnits90),
                observedDays,
                currentStock,
                stockCoverDays,
                warningCodes
        );
    }

    public SalesForecastFeatureSnapshot(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            String productTitle,
            String brand,
            String productFulltype,
            String productFamily,
            LocalDate latestFactDate,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            BigDecimal adjustedHistoryUnits7,
            BigDecimal adjustedHistoryUnits30,
            BigDecimal adjustedHistoryUnits60,
            BigDecimal adjustedHistoryUnits90,
            int observedDays,
            Integer currentStock,
            BigDecimal stockCoverDays,
            List<String> warningCodes
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.productTitle = productTitle;
        this.brand = brand;
        this.productFulltype = productFulltype;
        this.productFamily = productFamily;
        this.latestFactDate = latestFactDate;
        this.historyUnits7 = historyUnits7;
        this.historyUnits30 = historyUnits30;
        this.historyUnits60 = historyUnits60;
        this.historyUnits90 = historyUnits90;
        this.adjustedHistoryUnits7 = safeAdjusted(adjustedHistoryUnits7, historyUnits7);
        this.adjustedHistoryUnits30 = safeAdjusted(adjustedHistoryUnits30, historyUnits30);
        this.adjustedHistoryUnits60 = safeAdjusted(adjustedHistoryUnits60, historyUnits60);
        this.adjustedHistoryUnits90 = safeAdjusted(adjustedHistoryUnits90, historyUnits90);
        this.observedDays = observedDays;
        this.currentStock = currentStock;
        this.stockCoverDays = stockCoverDays;
        this.warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
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

    public String getProductTitle() {
        return productTitle;
    }

    public String getBrand() {
        return brand;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public String getProductFamily() {
        return productFamily;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public int getHistoryUnits7() {
        return historyUnits7;
    }

    public int getHistoryUnits30() {
        return historyUnits30;
    }

    public int getHistoryUnits60() {
        return historyUnits60;
    }

    public int getHistoryUnits90() {
        return historyUnits90;
    }

    public BigDecimal getAdjustedHistoryUnits7() {
        return adjustedHistoryUnits7;
    }

    public BigDecimal getAdjustedHistoryUnits30() {
        return adjustedHistoryUnits30;
    }

    public BigDecimal getAdjustedHistoryUnits60() {
        return adjustedHistoryUnits60;
    }

    public BigDecimal getAdjustedHistoryUnits90() {
        return adjustedHistoryUnits90;
    }

    public int getObservedDays() {
        return observedDays;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public BigDecimal getStockCoverDays() {
        return stockCoverDays;
    }

    public List<String> getWarningCodes() {
        return warningCodes;
    }

    private BigDecimal safeAdjusted(BigDecimal value, int fallbackUnits) {
        return value == null ? BigDecimal.valueOf(fallbackUnits) : value;
    }
}
