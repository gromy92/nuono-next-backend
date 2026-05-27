package com.nuono.next.salesforecast;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public class SalesForecastFeatureSnapshot {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final String sku;
    private final String productTitle;
    private final LocalDate latestFactDate;
    private final int historyUnits7;
    private final int historyUnits30;
    private final int historyUnits60;
    private final int historyUnits90;
    private final int observedDays;
    private final Integer currentStock;
    private final BigDecimal stockCoverDays;
    private final String lifecycleCode;
    private final String lifecycleLabel;
    private final String lifecycleRuleVersion;
    private final String lifecycleQualityState;
    private final String lifecycleEvidenceJson;
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
            String lifecycleCode,
            String lifecycleLabel,
            List<String> warningCodes
    ) {
        this(
                ownerUserId,
                storeCode,
                siteCode,
                partnerSku,
                sku,
                productTitle,
                latestFactDate,
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                observedDays,
                currentStock,
                stockCoverDays,
                lifecycleCode,
                lifecycleLabel,
                "DEFAULT_V1",
                "ready",
                null,
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
            LocalDate latestFactDate,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int observedDays,
            Integer currentStock,
            BigDecimal stockCoverDays,
            String lifecycleCode,
            String lifecycleLabel,
            String lifecycleRuleVersion,
            String lifecycleQualityState,
            String lifecycleEvidenceJson,
            List<String> warningCodes
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.productTitle = productTitle;
        this.latestFactDate = latestFactDate;
        this.historyUnits7 = historyUnits7;
        this.historyUnits30 = historyUnits30;
        this.historyUnits60 = historyUnits60;
        this.historyUnits90 = historyUnits90;
        this.observedDays = observedDays;
        this.currentStock = currentStock;
        this.stockCoverDays = stockCoverDays;
        this.lifecycleCode = lifecycleCode;
        this.lifecycleLabel = lifecycleLabel;
        this.lifecycleRuleVersion = lifecycleRuleVersion;
        this.lifecycleQualityState = lifecycleQualityState;
        this.lifecycleEvidenceJson = lifecycleEvidenceJson;
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

    public int getObservedDays() {
        return observedDays;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public BigDecimal getStockCoverDays() {
        return stockCoverDays;
    }

    public String getLifecycleCode() {
        return lifecycleCode;
    }

    public String getLifecycleLabel() {
        return lifecycleLabel;
    }

    public String getLifecycleRuleVersion() {
        return lifecycleRuleVersion;
    }

    public String getLifecycleQualityState() {
        return lifecycleQualityState;
    }

    public String getLifecycleEvidenceJson() {
        return lifecycleEvidenceJson;
    }

    public List<String> getWarningCodes() {
        return warningCodes;
    }
}
