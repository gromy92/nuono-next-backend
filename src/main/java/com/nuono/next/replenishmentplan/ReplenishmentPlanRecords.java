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
        private final LocalDate anchorDate;
        private final StockSnapshot stockSnapshot;
        private final Map<Integer, BigDecimal> dailyDemandByDay;
        private final List<InboundBatch> inboundBatches;
        private final List<MissingEtaBatch> missingEtaBatches;

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
            this.partnerSku = partnerSku;
            this.sku = sku;
            this.productTitle = productTitle;
            this.anchorDate = anchorDate;
            this.stockSnapshot = stockSnapshot == null
                    ? new StockSnapshot(null, null, null)
                    : stockSnapshot;
            this.dailyDemandByDay = Collections.unmodifiableMap(
                    dailyDemandByDay == null ? Collections.emptyMap() : new HashMap<>(dailyDemandByDay)
            );
            this.inboundBatches = immutableList(inboundBatches);
            this.missingEtaBatches = immutableList(missingEtaBatches);
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

        public LocalDate getAnchorDate() {
            return anchorDate;
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
    }

    public static final class StockSnapshot {
        private final BigDecimal currentStockUnits;
        private final BigDecimal fbnStockUnits;
        private final BigDecimal supermallStockUnits;
        private final boolean currentStockFactMissing;

        public StockSnapshot(BigDecimal currentStockUnits, BigDecimal fbnStockUnits, BigDecimal supermallStockUnits) {
            this(currentStockUnits, fbnStockUnits, supermallStockUnits, currentStockUnits == null);
        }

        StockSnapshot(
                BigDecimal currentStockUnits,
                BigDecimal fbnStockUnits,
                BigDecimal supermallStockUnits,
                boolean currentStockFactMissing
        ) {
            this.currentStockUnits = nonNegativeOrNull(currentStockUnits);
            this.fbnStockUnits = nonNegativeOrNull(fbnStockUnits);
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

        public InboundBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity
        ) {
            this.batchId = batchId;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.batchStatus = batchStatus;
            this.etaDate = etaDate;
            this.remainingQuantity = nonNegative(remainingQuantity);
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
    }

    public static final class MissingEtaBatch {
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final BigDecimal remainingQuantity;

        public MissingEtaBatch(
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                BigDecimal remainingQuantity
        ) {
            this.batchId = batchId;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.batchStatus = batchStatus;
            this.remainingQuantity = nonNegative(remainingQuantity);
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
        private final BigDecimal currentStockUnits;
        private final BigDecimal fbnStockUnits;
        private final BigDecimal supermallStockUnits;
        private final BigDecimal knownInboundUnits;
        private final BigDecimal missingEtaInboundQty;
        private final int missingEtaBatchCount;
        private final Integer firstStockoutDay;
        private final String stockoutWindowLabel;
        private final int airWindowStartDay;
        private final int airWindowEndDay;
        private final BigDecimal airSuggestedUnits;
        private final int seaWindowStartDay;
        private final int seaWindowEndDay;
        private final BigDecimal seaSuggestedUnits;
        private final List<DailyProjectionView> dailyProjection;
        private final List<MissingEtaBatch> missingEtaBatches;
        private final List<String> warnings;
        private final String explanation;

        public PlanItemView(
                String calculationVersion,
                ReplenishmentPlanConfig configSnapshot,
                String partnerSku,
                String sku,
                String productTitle,
                BigDecimal currentStockUnits,
                BigDecimal fbnStockUnits,
                BigDecimal supermallStockUnits,
                BigDecimal knownInboundUnits,
                BigDecimal missingEtaInboundQty,
                int missingEtaBatchCount,
                Integer firstStockoutDay,
                String stockoutWindowLabel,
                int airWindowStartDay,
                int airWindowEndDay,
                BigDecimal airSuggestedUnits,
                int seaWindowStartDay,
                int seaWindowEndDay,
                BigDecimal seaSuggestedUnits,
                List<DailyProjectionView> dailyProjection,
                List<MissingEtaBatch> missingEtaBatches,
                List<String> warnings,
                String explanation
        ) {
            this.calculationVersion = calculationVersion;
            this.configSnapshot = configSnapshot;
            this.partnerSku = partnerSku;
            this.sku = sku;
            this.productTitle = productTitle;
            this.currentStockUnits = nonNegativeOrNull(currentStockUnits);
            this.fbnStockUnits = nonNegativeOrNull(fbnStockUnits);
            this.supermallStockUnits = nonNegativeOrNull(supermallStockUnits);
            this.knownInboundUnits = nonNegative(knownInboundUnits);
            this.missingEtaInboundQty = nonNegative(missingEtaInboundQty);
            this.missingEtaBatchCount = missingEtaBatchCount;
            this.firstStockoutDay = firstStockoutDay;
            this.stockoutWindowLabel = stockoutWindowLabel;
            this.airWindowStartDay = airWindowStartDay;
            this.airWindowEndDay = airWindowEndDay;
            this.airSuggestedUnits = nonNegative(airSuggestedUnits);
            this.seaWindowStartDay = seaWindowStartDay;
            this.seaWindowEndDay = seaWindowEndDay;
            this.seaSuggestedUnits = nonNegative(seaSuggestedUnits);
            this.dailyProjection = immutableList(dailyProjection);
            this.missingEtaBatches = immutableList(missingEtaBatches);
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

        public BigDecimal getAirSuggestedUnits() {
            return airSuggestedUnits;
        }

        public int getSeaWindowStartDay() {
            return seaWindowStartDay;
        }

        public int getSeaWindowEndDay() {
            return seaWindowEndDay;
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
