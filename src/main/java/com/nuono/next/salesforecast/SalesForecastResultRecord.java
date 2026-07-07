package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesForecastResultRecord {

    private final Long id;
    private final Long runId;
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
    private final int forecastUnits30;
    private final int forecastUnits60;
    private final int forecastUnits90;
    private final String calculationVersion;
    private final String configVersion;
    private final BigDecimal baseDailySales;
    private final BigDecimal recentDailyTrendRate;
    private final BigDecimal trendFactor;
    private final BigDecimal futureFactor;
    private final String confidenceLevel;
    private final String confidenceLabel;
    private final String confidenceExplanation;
    private final String warningCodes;
    private final String riskCodes;
    private final String activityWindowSummary;
    private final String activityExplanation;
    private final String shortReason;
    private final String featureSnapshotJson;

    public SalesForecastResultRecord(
            Long id,
            Long runId,
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
            int forecastUnits30,
            int forecastUnits60,
            int forecastUnits90,
            String calculationVersion,
            String configVersion,
            BigDecimal baseDailySales,
            BigDecimal recentDailyTrendRate,
            BigDecimal trendFactor,
            BigDecimal futureFactor,
            String confidenceLevel,
            String confidenceLabel,
            String confidenceExplanation,
            String warningCodes,
            String riskCodes,
            String activityWindowSummary,
            String activityExplanation,
            String shortReason,
            String featureSnapshotJson
    ) {
        this.id = id;
        this.runId = runId;
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
        this.forecastUnits30 = forecastUnits30;
        this.forecastUnits60 = forecastUnits60;
        this.forecastUnits90 = forecastUnits90;
        this.calculationVersion = calculationVersion;
        this.configVersion = configVersion;
        this.baseDailySales = baseDailySales;
        this.recentDailyTrendRate = recentDailyTrendRate;
        this.trendFactor = trendFactor;
        this.futureFactor = futureFactor;
        this.confidenceLevel = confidenceLevel;
        this.confidenceLabel = confidenceLabel;
        this.confidenceExplanation = confidenceExplanation;
        this.warningCodes = warningCodes;
        this.riskCodes = riskCodes;
        this.activityWindowSummary = activityWindowSummary;
        this.activityExplanation = activityExplanation;
        this.shortReason = shortReason;
        this.featureSnapshotJson = featureSnapshotJson;
    }

    public SalesForecastResultRecord withIdAndRunId(Long id, Long runId) {
        return new SalesForecastResultRecord(
                id,
                runId,
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
                forecastUnits30,
                forecastUnits60,
                forecastUnits90,
                calculationVersion,
                configVersion,
                baseDailySales,
                recentDailyTrendRate,
                trendFactor,
                futureFactor,
                confidenceLevel,
                confidenceLabel,
                confidenceExplanation,
                warningCodes,
                riskCodes,
                activityWindowSummary,
                activityExplanation,
                shortReason,
                featureSnapshotJson
        );
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
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

    public int getForecastUnits30() {
        return forecastUnits30;
    }

    public int getForecastUnits60() {
        return forecastUnits60;
    }

    public int getForecastUnits90() {
        return forecastUnits90;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public BigDecimal getBaseDailySales() {
        return baseDailySales;
    }

    public BigDecimal getRecentDailyTrendRate() {
        return recentDailyTrendRate;
    }

    public BigDecimal getTrendFactor() {
        return trendFactor;
    }

    public BigDecimal getFutureFactor() {
        return futureFactor;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public String getConfidenceLabel() {
        return confidenceLabel;
    }

    public String getConfidenceExplanation() {
        return confidenceExplanation;
    }

    public String getWarningCodes() {
        return warningCodes;
    }

    public String getRiskCodes() {
        return riskCodes;
    }

    public String getActivityWindowSummary() {
        return activityWindowSummary;
    }

    public String getActivityExplanation() {
        return activityExplanation;
    }

    public String getShortReason() {
        return shortReason;
    }

    public String getFeatureSnapshotJson() {
        return featureSnapshotJson;
    }
}
