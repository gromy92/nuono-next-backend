package com.nuono.next.replenishmentplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReplenishmentPlanRecords {

    private ReplenishmentPlanRecords() {
    }

    public static final class PlanInput {
        private final String partnerSku;
        private final String sku;
        private final String productTitle;
        private final String imageUrl;
        private final LocalDate listingAt;
        private final LocalDate latestFactDate;
        private final int observedDays;
        private final int historyUnits7;
        private final int historyUnits30;
        private final int historyUnits60;
        private final int historyUnits90;
        private final BigDecimal adjustedHistoryUnits7;
        private final BigDecimal adjustedHistoryUnits30;
        private final BigDecimal adjustedHistoryUnits60;
        private final BigDecimal adjustedHistoryUnits90;
        private final int forecastUnits30;
        private final int forecastUnits60;
        private final int forecastUnits90;
        private final String confidenceLabel;
        private final String shortReason;
        private final LocalDate anchorDate;
        private final LocalDate planDate;
        private final StockSnapshot stockSnapshot;
        private final Map<Integer, BigDecimal> dailyDemandByDay;
        private final List<InboundBatch> inboundBatches;
        private final List<MissingEtaBatch> missingEtaBatches;
        private final boolean inboundSiteUnresolved;

        public PlanInput(
                String partnerSku,
                String sku,
                String productTitle,
                LocalDate anchorDate,
                StockSnapshot stockSnapshot,
                Map<Integer, BigDecimal> dailyDemandByDay,
                List<InboundBatch> inboundBatches,
                List<MissingEtaBatch> missingEtaBatches
        ) {
            this(
                    partnerSku,
                    sku,
                    productTitle,
                    anchorDate,
                    null,
                    stockSnapshot,
                    dailyDemandByDay,
                    inboundBatches,
                    missingEtaBatches
            );
        }

        public PlanInput(
                String partnerSku,
                String sku,
                String productTitle,
                LocalDate anchorDate,
                LocalDate planDate,
                StockSnapshot stockSnapshot,
                Map<Integer, BigDecimal> dailyDemandByDay,
                List<InboundBatch> inboundBatches,
                List<MissingEtaBatch> missingEtaBatches
        ) {
            this(
                    partnerSku,
                    sku,
                    productTitle,
                    null,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    anchorDate,
                    planDate,
                    stockSnapshot,
                    dailyDemandByDay,
                    inboundBatches,
                    missingEtaBatches
            );
        }

        public PlanInput(
                String partnerSku,
                String sku,
                String productTitle,
                LocalDate latestFactDate,
                int observedDays,
                int historyUnits7,
                int historyUnits30,
                int historyUnits60,
                int historyUnits90,
                int forecastUnits30,
                int forecastUnits60,
                int forecastUnits90,
                String confidenceLabel,
                String shortReason,
                LocalDate anchorDate,
                StockSnapshot stockSnapshot,
                Map<Integer, BigDecimal> dailyDemandByDay,
                List<InboundBatch> inboundBatches,
                List<MissingEtaBatch> missingEtaBatches
        ) {
            this(
                    partnerSku,
                    sku,
                    productTitle,
                    null,
                    null,
                    latestFactDate,
                    observedDays,
                    historyUnits7,
                    historyUnits30,
                    historyUnits60,
                    historyUnits90,
                    null,
                    null,
                    null,
                    null,
                    forecastUnits30,
                    forecastUnits60,
                    forecastUnits90,
                    confidenceLabel,
                    shortReason,
                    anchorDate,
                    null,
                    stockSnapshot,
                    dailyDemandByDay,
                    inboundBatches,
                    missingEtaBatches
            );
        }

        public PlanInput(
                String partnerSku,
                String sku,
                String productTitle,
                String imageUrl,
                LocalDate latestFactDate,
                int observedDays,
                int historyUnits7,
                int historyUnits30,
                int historyUnits60,
                int historyUnits90,
                int forecastUnits30,
                int forecastUnits60,
                int forecastUnits90,
                String confidenceLabel,
                String shortReason,
                LocalDate anchorDate,
                StockSnapshot stockSnapshot,
                Map<Integer, BigDecimal> dailyDemandByDay,
                List<InboundBatch> inboundBatches,
                List<MissingEtaBatch> missingEtaBatches
        ) {
            this(
                    partnerSku,
                    sku,
                    productTitle,
                    imageUrl,
                    null,
                    latestFactDate,
                    observedDays,
                    historyUnits7,
                    historyUnits30,
                    historyUnits60,
                    historyUnits90,
                    null,
                    null,
                    null,
                    null,
                    forecastUnits30,
                    forecastUnits60,
                    forecastUnits90,
                    confidenceLabel,
                    shortReason,
                    anchorDate,
                    null,
                    stockSnapshot,
                    dailyDemandByDay,
                    inboundBatches,
                    missingEtaBatches
            );
        }

        public PlanInput(
                String partnerSku,
                String sku,
                String productTitle,
                String imageUrl,
                LocalDate listingAt,
                LocalDate latestFactDate,
                int observedDays,
                int historyUnits7,
                int historyUnits30,
                int historyUnits60,
                int historyUnits90,
                int forecastUnits30,
                int forecastUnits60,
                int forecastUnits90,
                String confidenceLabel,
                String shortReason,
                LocalDate anchorDate,
                LocalDate planDate,
                StockSnapshot stockSnapshot,
                Map<Integer, BigDecimal> dailyDemandByDay,
                List<InboundBatch> inboundBatches,
                List<MissingEtaBatch> missingEtaBatches
        ) {
            this(
                    partnerSku,
                    sku,
                    productTitle,
                    imageUrl,
                    listingAt,
                    latestFactDate,
                    observedDays,
                    historyUnits7,
                    historyUnits30,
                    historyUnits60,
                    historyUnits90,
                    null,
                    null,
                    null,
                    null,
                    forecastUnits30,
                    forecastUnits60,
                    forecastUnits90,
                    confidenceLabel,
                    shortReason,
                    anchorDate,
                    planDate,
                    stockSnapshot,
                    dailyDemandByDay,
                    inboundBatches,
                    missingEtaBatches
            );
        }

        public PlanInput(
                String partnerSku,
                String sku,
                String productTitle,
                String imageUrl,
                LocalDate listingAt,
                LocalDate latestFactDate,
                int observedDays,
                int historyUnits7,
                int historyUnits30,
                int historyUnits60,
                int historyUnits90,
                BigDecimal adjustedHistoryUnits7,
                BigDecimal adjustedHistoryUnits30,
                BigDecimal adjustedHistoryUnits60,
                BigDecimal adjustedHistoryUnits90,
                int forecastUnits30,
                int forecastUnits60,
                int forecastUnits90,
                String confidenceLabel,
                String shortReason,
                LocalDate anchorDate,
                LocalDate planDate,
                StockSnapshot stockSnapshot,
                Map<Integer, BigDecimal> dailyDemandByDay,
                List<InboundBatch> inboundBatches,
                List<MissingEtaBatch> missingEtaBatches
        ) {
            this(
                    partnerSku,
                    sku,
                    productTitle,
                    imageUrl,
                    listingAt,
                    latestFactDate,
                    observedDays,
                    historyUnits7,
                    historyUnits30,
                    historyUnits60,
                    historyUnits90,
                    adjustedHistoryUnits7,
                    adjustedHistoryUnits30,
                    adjustedHistoryUnits60,
                    adjustedHistoryUnits90,
                    forecastUnits30,
                    forecastUnits60,
                    forecastUnits90,
                    confidenceLabel,
                    shortReason,
                    anchorDate,
                    planDate,
                    stockSnapshot,
                    dailyDemandByDay,
                    inboundBatches,
                    missingEtaBatches,
                    false
            );
        }

        public PlanInput(
                String partnerSku,
                String sku,
                String productTitle,
                String imageUrl,
                LocalDate listingAt,
                LocalDate latestFactDate,
                int observedDays,
                int historyUnits7,
                int historyUnits30,
                int historyUnits60,
                int historyUnits90,
                BigDecimal adjustedHistoryUnits7,
                BigDecimal adjustedHistoryUnits30,
                BigDecimal adjustedHistoryUnits60,
                BigDecimal adjustedHistoryUnits90,
                int forecastUnits30,
                int forecastUnits60,
                int forecastUnits90,
                String confidenceLabel,
                String shortReason,
                LocalDate anchorDate,
                LocalDate planDate,
                StockSnapshot stockSnapshot,
                Map<Integer, BigDecimal> dailyDemandByDay,
                List<InboundBatch> inboundBatches,
                List<MissingEtaBatch> missingEtaBatches,
                boolean inboundSiteUnresolved
        ) {
            this.partnerSku = partnerSku;
            this.sku = sku;
            this.productTitle = productTitle;
            this.imageUrl = imageUrl;
            this.listingAt = listingAt;
            this.latestFactDate = latestFactDate;
            this.observedDays = Math.max(0, observedDays);
            this.historyUnits7 = Math.max(0, historyUnits7);
            this.historyUnits30 = Math.max(0, historyUnits30);
            this.historyUnits60 = Math.max(0, historyUnits60);
            this.historyUnits90 = Math.max(0, historyUnits90);
            this.adjustedHistoryUnits7 = adjustedHistoryUnits(adjustedHistoryUnits7, this.historyUnits7);
            this.adjustedHistoryUnits30 = adjustedHistoryUnits(adjustedHistoryUnits30, this.historyUnits30);
            this.adjustedHistoryUnits60 = adjustedHistoryUnits(adjustedHistoryUnits60, this.historyUnits60);
            this.adjustedHistoryUnits90 = adjustedHistoryUnits(adjustedHistoryUnits90, this.historyUnits90);
            this.forecastUnits30 = Math.max(0, forecastUnits30);
            this.forecastUnits60 = Math.max(0, forecastUnits60);
            this.forecastUnits90 = Math.max(0, forecastUnits90);
            this.confidenceLabel = confidenceLabel;
            this.shortReason = shortReason;
            this.anchorDate = anchorDate;
            this.planDate = planDate;
            this.stockSnapshot = stockSnapshot == null
                    ? new StockSnapshot(null, null, null)
                    : stockSnapshot;
            this.dailyDemandByDay = Collections.unmodifiableMap(
                    dailyDemandByDay == null ? Collections.emptyMap() : new HashMap<>(dailyDemandByDay)
            );
            this.inboundBatches = immutableList(inboundBatches);
            this.missingEtaBatches = immutableList(missingEtaBatches);
            this.inboundSiteUnresolved = inboundSiteUnresolved;
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

        public String getImageUrl() {
            return imageUrl;
        }

        public LocalDate getListingAt() {
            return listingAt;
        }

        public LocalDate getLatestFactDate() {
            return latestFactDate;
        }

        public int getObservedDays() {
            return observedDays;
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

        public int getForecastUnits30() {
            return forecastUnits30;
        }

        public int getForecastUnits60() {
            return forecastUnits60;
        }

        public int getForecastUnits90() {
            return forecastUnits90;
        }

        public String getConfidenceLabel() {
            return confidenceLabel;
        }

        public String getShortReason() {
            return shortReason;
        }

        public LocalDate getAnchorDate() {
            return anchorDate;
        }

        public LocalDate getPlanDate() {
            return planDate;
        }

        public StockSnapshot getStockSnapshot() {
            return stockSnapshot;
        }

        public Map<Integer, BigDecimal> getDailyDemandByDay() {
            return dailyDemandByDay;
        }

        public List<InboundBatch> getInboundBatches() {
            return inboundBatches;
        }

        public List<MissingEtaBatch> getMissingEtaBatches() {
            return missingEtaBatches;
        }

        public boolean isInboundSiteUnresolved() {
            return inboundSiteUnresolved;
        }
    }

    public static final class StockSnapshot {
        private final BigDecimal currentStockUnits;
        private final BigDecimal fbnStockUnits;
        private final BigDecimal supermallStockUnits;
        private final boolean currentStockFactMissing;

        public StockSnapshot(BigDecimal currentStockUnits, BigDecimal fbnStockUnits, BigDecimal supermallStockUnits) {
            this(currentStockUnits, fbnStockUnits, supermallStockUnits, fbnStockUnits == null);
        }

        StockSnapshot(
                BigDecimal currentStockUnits,
                BigDecimal fbnStockUnits,
                BigDecimal supermallStockUnits,
                boolean currentStockFactMissing
        ) {
            this.fbnStockUnits = nonNegativeOrNull(fbnStockUnits);
            this.currentStockUnits = this.fbnStockUnits;
            this.supermallStockUnits = nonNegativeOrNull(supermallStockUnits);
            this.currentStockFactMissing = currentStockFactMissing;
        }

        public BigDecimal getCurrentStockUnits() {
            return currentStockUnits;
        }

        public BigDecimal getFbnStockUnits() {
            return fbnStockUnits;
        }

        public BigDecimal getSupermallStockUnits() {
            return supermallStockUnits;
        }

        boolean currentStockFactMissing() {
            return currentStockFactMissing;
        }
    }

    public static final class InboundBatch {
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final LocalDate etaDate;
        private final BigDecimal remainingQuantity;
        private final String destinationCode;
        private final boolean coverageIncluded;
        private final boolean etaReviewRequired;

        public InboundBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity
        ) {
            this(
                    batchId,
                    batchReferenceNo,
                    transportMode,
                    batchStatus,
                    etaDate,
                    remainingQuantity,
                    null,
                    true,
                    false
            );
        }

        public InboundBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity,
                String destinationCode
        ) {
            this(
                    batchId,
                    batchReferenceNo,
                    transportMode,
                    batchStatus,
                    etaDate,
                    remainingQuantity,
                    destinationCode,
                    true,
                    false
            );
        }

        public InboundBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity,
                boolean coverageIncluded,
                boolean etaReviewRequired
        ) {
            this(
                    batchId,
                    batchReferenceNo,
                    transportMode,
                    batchStatus,
                    etaDate,
                    remainingQuantity,
                    null,
                    coverageIncluded,
                    etaReviewRequired
            );
        }

        public InboundBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity,
                String destinationCode,
                boolean coverageIncluded,
                boolean etaReviewRequired
        ) {
            this.batchId = batchId;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.batchStatus = batchStatus;
            this.etaDate = etaDate;
            this.remainingQuantity = nonNegative(remainingQuantity);
            this.destinationCode = destinationCode;
            this.coverageIncluded = coverageIncluded;
            this.etaReviewRequired = etaReviewRequired;
        }

        public Long getBatchId() {
            return batchId;
        }

        public String getBatchReferenceNo() {
            return batchReferenceNo;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public String getBatchStatus() {
            return batchStatus;
        }

        public LocalDate getEtaDate() {
            return etaDate;
        }

        public BigDecimal getRemainingQuantity() {
            return remainingQuantity;
        }

        public String getDestinationCode() {
            return destinationCode;
        }

        public boolean isCoverageIncluded() {
            return coverageIncluded;
        }

        public boolean isEtaReviewRequired() {
            return etaReviewRequired;
        }
    }

    public static final class MissingEtaBatch {
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final BigDecimal remainingQuantity;
        private final String destinationCode;

        public MissingEtaBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                BigDecimal remainingQuantity
        ) {
            this(batchId, batchReferenceNo, transportMode, batchStatus, remainingQuantity, null);
        }

        public MissingEtaBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                BigDecimal remainingQuantity,
                String destinationCode
        ) {
            this.batchId = batchId;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.batchStatus = batchStatus;
            this.remainingQuantity = nonNegative(remainingQuantity);
            this.destinationCode = destinationCode;
        }

        public Long getBatchId() {
            return batchId;
        }

        public String getBatchReferenceNo() {
            return batchReferenceNo;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public String getBatchStatus() {
            return batchStatus;
        }

        public BigDecimal getRemainingQuantity() {
            return remainingQuantity;
        }

        public String getDestinationCode() {
            return destinationCode;
        }
    }

    public static final class DailyProjectionView {
        private final int day;
        private final LocalDate date;
        private final BigDecimal forecastDemand;
        private final BigDecimal inboundUnits;
        private final BigDecimal projectedStock;

        public DailyProjectionView(
                int day,
                LocalDate date,
                BigDecimal forecastDemand,
                BigDecimal inboundUnits,
                BigDecimal projectedStock
        ) {
            this.day = day;
            this.date = date;
            this.forecastDemand = nonNegative(forecastDemand);
            this.inboundUnits = nonNegative(inboundUnits);
            this.projectedStock = projectedStock == null ? BigDecimal.ZERO : projectedStock;
        }

        public int getDay() {
            return day;
        }

        public LocalDate getDate() {
            return date;
        }

        public BigDecimal getForecastDemand() {
            return forecastDemand;
        }

        public BigDecimal getInboundUnits() {
            return inboundUnits;
        }

        public BigDecimal getProjectedStock() {
            return projectedStock;
        }
    }

    public static final class PlanItemView {
        private final String calculationVersion;
        private final ReplenishmentPlanConfig configSnapshot;
        private final String partnerSku;
        private final String sku;
        private final String productTitle;
        private final String imageUrl;
        private final LocalDate listingAt;
        private final LocalDate latestFactDate;
        private final int observedDays;
        private final int historyUnits7;
        private final int historyUnits30;
        private final int historyUnits60;
        private final int historyUnits90;
        private final BigDecimal adjustedHistoryUnits7;
        private final BigDecimal adjustedHistoryUnits30;
        private final BigDecimal adjustedHistoryUnits60;
        private final BigDecimal adjustedHistoryUnits90;
        private final int forecastUnits30;
        private final int forecastUnits60;
        private final int forecastUnits90;
        private final BigDecimal forecastUnits100;
        private final String confidenceLabel;
        private final String shortReason;
        private final BigDecimal currentStockUnits;
        private final BigDecimal fbnStockUnits;
        private final BigDecimal supermallStockUnits;
        private final BigDecimal knownInboundUnits;
        private final List<InboundBatch> inboundBatches;
        private final LocalDate nearestInboundEtaDate;
        private final BigDecimal missingEtaInboundQty;
        private final int missingEtaBatchCount;
        private final Integer firstStockoutDay;
        private final String stockoutWindowLabel;
        private final int airWindowStartDay;
        private final int airWindowEndDay;
        private final BigDecimal airWindowForecastUnits;
        private final BigDecimal airCalculatedUnits;
        private final BigDecimal airSuggestedUnits;
        private final int seaWindowStartDay;
        private final int seaWindowEndDay;
        private final BigDecimal seaWindowForecastUnits;
        private final BigDecimal seaCalculatedUnits;
        private final BigDecimal seaSuggestedUnits;
        private final List<DailyProjectionView> dailyProjection;
        private final List<MissingEtaBatch> missingEtaBatches;
        private final boolean calculationBlocked;
        private final List<String> warnings;
        private final String explanation;

        public PlanItemView(
                String calculationVersion,
                ReplenishmentPlanConfig configSnapshot,
                String partnerSku,
                String sku,
                String productTitle,
                String imageUrl,
                LocalDate listingAt,
                LocalDate latestFactDate,
                int observedDays,
                int historyUnits7,
                int historyUnits30,
                int historyUnits60,
                int historyUnits90,
                BigDecimal adjustedHistoryUnits7,
                BigDecimal adjustedHistoryUnits30,
                BigDecimal adjustedHistoryUnits60,
                BigDecimal adjustedHistoryUnits90,
                int forecastUnits30,
                int forecastUnits60,
                int forecastUnits90,
                BigDecimal forecastUnits100,
                String confidenceLabel,
                String shortReason,
                BigDecimal currentStockUnits,
                BigDecimal fbnStockUnits,
                BigDecimal supermallStockUnits,
                BigDecimal knownInboundUnits,
                List<InboundBatch> inboundBatches,
                LocalDate nearestInboundEtaDate,
                BigDecimal missingEtaInboundQty,
                int missingEtaBatchCount,
                Integer firstStockoutDay,
                String stockoutWindowLabel,
                int airWindowStartDay,
                int airWindowEndDay,
                BigDecimal airWindowForecastUnits,
                BigDecimal airCalculatedUnits,
                BigDecimal airSuggestedUnits,
                int seaWindowStartDay,
                int seaWindowEndDay,
                BigDecimal seaWindowForecastUnits,
                BigDecimal seaCalculatedUnits,
                BigDecimal seaSuggestedUnits,
                List<DailyProjectionView> dailyProjection,
                List<MissingEtaBatch> missingEtaBatches,
                boolean calculationBlocked,
                List<String> warnings,
                String explanation
        ) {
            this.calculationVersion = calculationVersion;
            this.configSnapshot = configSnapshot;
            this.partnerSku = partnerSku;
            this.sku = sku;
            this.productTitle = productTitle;
            this.imageUrl = imageUrl;
            this.listingAt = listingAt;
            this.latestFactDate = latestFactDate;
            this.observedDays = Math.max(0, observedDays);
            this.historyUnits7 = Math.max(0, historyUnits7);
            this.historyUnits30 = Math.max(0, historyUnits30);
            this.historyUnits60 = Math.max(0, historyUnits60);
            this.historyUnits90 = Math.max(0, historyUnits90);
            this.adjustedHistoryUnits7 = adjustedHistoryUnits(adjustedHistoryUnits7, this.historyUnits7);
            this.adjustedHistoryUnits30 = adjustedHistoryUnits(adjustedHistoryUnits30, this.historyUnits30);
            this.adjustedHistoryUnits60 = adjustedHistoryUnits(adjustedHistoryUnits60, this.historyUnits60);
            this.adjustedHistoryUnits90 = adjustedHistoryUnits(adjustedHistoryUnits90, this.historyUnits90);
            this.forecastUnits30 = Math.max(0, forecastUnits30);
            this.forecastUnits60 = Math.max(0, forecastUnits60);
            this.forecastUnits90 = Math.max(0, forecastUnits90);
            this.forecastUnits100 = nonNegative(forecastUnits100);
            this.confidenceLabel = confidenceLabel;
            this.shortReason = shortReason;
            this.currentStockUnits = nonNegativeOrNull(currentStockUnits);
            this.fbnStockUnits = nonNegativeOrNull(fbnStockUnits);
            this.supermallStockUnits = nonNegativeOrNull(supermallStockUnits);
            this.knownInboundUnits = nonNegative(knownInboundUnits);
            this.inboundBatches = immutableList(inboundBatches);
            this.nearestInboundEtaDate = nearestInboundEtaDate;
            this.missingEtaInboundQty = nonNegative(missingEtaInboundQty);
            this.missingEtaBatchCount = missingEtaBatchCount;
            this.firstStockoutDay = firstStockoutDay;
            this.stockoutWindowLabel = stockoutWindowLabel;
            this.airWindowStartDay = airWindowStartDay;
            this.airWindowEndDay = airWindowEndDay;
            this.airWindowForecastUnits = nonNegative(airWindowForecastUnits);
            this.airCalculatedUnits = nonNegative(airCalculatedUnits);
            this.airSuggestedUnits = nonNegative(airSuggestedUnits);
            this.seaWindowStartDay = seaWindowStartDay;
            this.seaWindowEndDay = seaWindowEndDay;
            this.seaWindowForecastUnits = nonNegative(seaWindowForecastUnits);
            this.seaCalculatedUnits = nonNegative(seaCalculatedUnits);
            this.seaSuggestedUnits = nonNegative(seaSuggestedUnits);
            this.dailyProjection = immutableList(dailyProjection);
            this.missingEtaBatches = immutableList(missingEtaBatches);
            this.calculationBlocked = calculationBlocked;
            this.warnings = immutableList(warnings);
            this.explanation = explanation;
        }

        public String getCalculationVersion() {
            return calculationVersion;
        }

        public ReplenishmentPlanConfig getConfigSnapshot() {
            return configSnapshot;
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

        public String getImageUrl() {
            return imageUrl;
        }

        public LocalDate getListingAt() {
            return listingAt;
        }

        public LocalDate getLatestFactDate() {
            return latestFactDate;
        }

        public int getObservedDays() {
            return observedDays;
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

        public int getForecastUnits30() {
            return forecastUnits30;
        }

        public int getForecastUnits60() {
            return forecastUnits60;
        }

        public int getForecastUnits90() {
            return forecastUnits90;
        }

        public BigDecimal getForecastUnits100() {
            return forecastUnits100;
        }

        public String getConfidenceLabel() {
            return confidenceLabel;
        }

        public String getShortReason() {
            return shortReason;
        }

        public BigDecimal getCurrentStockUnits() {
            return currentStockUnits;
        }

        public BigDecimal getFbnStockUnits() {
            return fbnStockUnits;
        }

        public BigDecimal getSupermallStockUnits() {
            return supermallStockUnits;
        }

        public BigDecimal getKnownInboundUnits() {
            return knownInboundUnits;
        }

        public List<InboundBatch> getInboundBatches() {
            return inboundBatches;
        }

        public LocalDate getNearestInboundEtaDate() {
            return nearestInboundEtaDate;
        }

        public BigDecimal getMissingEtaInboundQty() {
            return missingEtaInboundQty;
        }

        public int getMissingEtaBatchCount() {
            return missingEtaBatchCount;
        }

        public Integer getFirstStockoutDay() {
            return firstStockoutDay;
        }

        public String getStockoutWindowLabel() {
            return stockoutWindowLabel;
        }

        public int getAirWindowStartDay() {
            return airWindowStartDay;
        }

        public int getAirWindowEndDay() {
            return airWindowEndDay;
        }

        public BigDecimal getAirWindowForecastUnits() {
            return airWindowForecastUnits;
        }

        public BigDecimal getAirCalculatedUnits() {
            return airCalculatedUnits;
        }

        public BigDecimal getAirSuggestedUnits() {
            return airSuggestedUnits;
        }

        public int getSeaWindowStartDay() {
            return seaWindowStartDay;
        }

        public int getSeaWindowEndDay() {
            return seaWindowEndDay;
        }

        public BigDecimal getSeaWindowForecastUnits() {
            return seaWindowForecastUnits;
        }

        public BigDecimal getSeaCalculatedUnits() {
            return seaCalculatedUnits;
        }

        public BigDecimal getSeaSuggestedUnits() {
            return seaSuggestedUnits;
        }

        public List<DailyProjectionView> getDailyProjection() {
            return dailyProjection;
        }

        public List<MissingEtaBatch> getMissingEtaBatches() {
            return missingEtaBatches;
        }

        public boolean isCalculationBlocked() {
            return calculationBlocked;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getExplanation() {
            return explanation;
        }
    }

    public static final class PlanQuery {
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;

        public PlanQuery(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
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
    }

    public static final class PlanOverviewView {
        private final String state;
        private final String storeCode;
        private final String siteCode;
        private final String calculationVersion;
        private final ReplenishmentPlanConfig configSnapshot;
        private final LocalDate anchorDate;
        private final List<PlanItemView> rows;

        public PlanOverviewView(
                String state,
                String storeCode,
                String siteCode,
                String calculationVersion,
                ReplenishmentPlanConfig configSnapshot,
                LocalDate anchorDate,
                List<PlanItemView> rows
        ) {
            this.state = state;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.calculationVersion = calculationVersion;
            this.configSnapshot = configSnapshot;
            this.anchorDate = anchorDate;
            this.rows = immutableList(rows);
        }

        public String getState() {
            return state;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public String getCalculationVersion() {
            return calculationVersion;
        }

        public ReplenishmentPlanConfig getConfigSnapshot() {
            return configSnapshot;
        }

        public LocalDate getAnchorDate() {
            return anchorDate;
        }

        public List<PlanItemView> getRows() {
            return rows;
        }
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private static BigDecimal adjustedHistoryUnits(BigDecimal value, int fallback) {
        if (value == null) {
            return BigDecimal.valueOf(Math.max(0, fallback));
        }
        return nonNegative(value);
    }

    private static BigDecimal nonNegativeOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private static <T> List<T> immutableList(List<T> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(items));
    }
}
