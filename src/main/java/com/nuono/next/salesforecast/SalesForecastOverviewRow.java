package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SalesForecastOverviewRow {

    private final String partnerSku;
    private final String sku;
    private final String productTitle;
    private final LocalDate latestFactDate;
    private final int historyUnits7;
    private final int historyUnits30;
    private final int historyUnits60;
    private final int historyUnits90;
    private final int forecastUnits30;
    private final int forecastUnits60;
    private final int forecastUnits90;
    private final Integer currentStock;
    private final BigDecimal stockCoverDays;
    private final String calculationVersion;
    private final String configVersion;
    private final String confidenceLevel;
    private final String confidenceLabel;
    private final String confidenceExplanation;
    private final List<String> dataQualityWarnings;
    private final List<SalesForecastRiskLabelView> riskLabels;
    private final String activityWindowSummary;
    private final String activityExplanation;
    private final String shortReason;
    private final boolean followUpMarked;
    private final SalesForecastDetailView detail;

    public SalesForecastOverviewRow(
            String partnerSku,
            String sku,
            String productTitle,
            LocalDate latestFactDate,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int forecastUnits30,
            int forecastUnits60,
            int forecastUnits90,
            Integer currentStock,
            BigDecimal stockCoverDays,
            String calculationVersion,
            String configVersion,
            String confidenceLevel,
            String confidenceLabel,
            String confidenceExplanation,
            List<String> dataQualityWarnings,
            List<SalesForecastRiskLabelView> riskLabels,
            String activityWindowSummary,
            String activityExplanation,
            String shortReason,
            boolean followUpMarked,
            SalesForecastDetailView detail
    ) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.productTitle = productTitle;
        this.latestFactDate = latestFactDate;
        this.historyUnits7 = historyUnits7;
        this.historyUnits30 = historyUnits30;
        this.historyUnits60 = historyUnits60;
        this.historyUnits90 = historyUnits90;
        this.forecastUnits30 = forecastUnits30;
        this.forecastUnits60 = forecastUnits60;
        this.forecastUnits90 = forecastUnits90;
        this.currentStock = currentStock;
        this.stockCoverDays = stockCoverDays;
        this.calculationVersion = calculationVersion;
        this.configVersion = configVersion;
        this.confidenceLevel = confidenceLevel;
        this.confidenceLabel = confidenceLabel;
        this.confidenceExplanation = confidenceExplanation;
        this.dataQualityWarnings = dataQualityWarnings == null ? List.of() : List.copyOf(dataQualityWarnings);
        this.riskLabels = riskLabels == null ? List.of() : List.copyOf(riskLabels);
        this.activityWindowSummary = activityWindowSummary;
        this.activityExplanation = activityExplanation;
        this.shortReason = shortReason;
        this.followUpMarked = followUpMarked;
        this.detail = detail;
    }

    public static SalesForecastOverviewRow fromResult(SalesForecastResultRecord record) {
        return fromResult(record, false);
    }

    public static SalesForecastOverviewRow fromResult(SalesForecastResultRecord record, boolean followUpMarked) {
        return new SalesForecastOverviewRow(
                record.getPartnerSku(),
                record.getSku(),
                record.getProductTitle(),
                record.getLatestFactDate(),
                record.getHistoryUnits7(),
                record.getHistoryUnits30(),
                record.getHistoryUnits60(),
                record.getHistoryUnits90(),
                record.getForecastUnits30(),
                record.getForecastUnits60(),
                record.getForecastUnits90(),
                record.getCurrentStock(),
                record.getStockCoverDays(),
                record.getCalculationVersion(),
                record.getConfigVersion(),
                record.getConfidenceLevel(),
                record.getConfidenceLabel(),
                record.getConfidenceExplanation(),
                splitCodes(record.getWarningCodes()),
                splitCodes(record.getRiskCodes()).stream()
                        .map(SalesForecastRiskLabelView::fromCode)
                        .collect(Collectors.toList()),
                record.getActivityWindowSummary(),
                record.getActivityExplanation(),
                record.getShortReason(),
                followUpMarked,
                SalesForecastDetailView.fromResult(record, false)
        );
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

    public int getForecastUnits30() {
        return forecastUnits30;
    }

    public int getForecastUnits60() {
        return forecastUnits60;
    }

    public int getForecastUnits90() {
        return forecastUnits90;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public BigDecimal getStockCoverDays() {
        return stockCoverDays;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public String getConfigVersion() {
        return configVersion;
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

    public List<String> getDataQualityWarnings() {
        return dataQualityWarnings;
    }

    public List<SalesForecastRiskLabelView> getRiskLabels() {
        return riskLabels;
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

    public boolean isFollowUpMarked() {
        return followUpMarked;
    }

    public SalesForecastDetailView getDetail() {
        return detail;
    }

    private static List<String> splitCodes(String codes) {
        if (codes == null || codes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toList());
    }
}
