package com.nuono.next.replenishmentplan;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.DailyProjectionView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.InboundBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.MissingEtaBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanInput;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.StockSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReplenishmentPlanCalculator {

    private static final String SEA_ETA_UNCERTAIN_AIR_WINDOW_WARNING = "sea_eta_uncertain_air_window_coverage";

    private static final Set<String> INACTIVE_INBOUND_STATUSES = new HashSet<>(Arrays.asList(
            "draft",
            "warehouse_received",
            "completed",
            "cancelled"
    ));

    public PlanItemView calculate(PlanInput input, ReplenishmentPlanConfig config) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.getAnchorDate() == null) {
            throw new IllegalArgumentException("anchorDate is required for replenishment plan calculation");
        }
        ReplenishmentPlanConfig resolvedConfig = config == null ? ReplenishmentPlanConfig.defaultBasicV1() : config;

        int airWindowStartDay = resolvedConfig.getAirLeadDays();
        int airWindowEndDay = resolvedConfig.getAirLeadDays() + resolvedConfig.getAirCoverDays();
        int seaWindowStartDay = resolvedConfig.getSeaLeadDays();
        int seaWindowEndDay = resolvedConfig.getSeaLeadDays() + resolvedConfig.getSeaCoverDays();
        int horizon = Math.max(resolvedConfig.getForecastHorizonDays(), seaWindowEndDay);

        Map<Integer, BigDecimal> dailyDemandByDay = normalizeDemand(input.getDailyDemandByDay());
        LocalDate planDate = input.getPlanDate() == null ? input.getAnchorDate() : input.getPlanDate();
        Map<Integer, BigDecimal> inboundByDay = groupInboundByDay(planDate, input.getInboundBatches());
        Map<Integer, BigDecimal> nonSeaInboundByDay = groupNonSeaInboundByDay(planDate, input.getInboundBatches());
        BigDecimal knownInboundUnits = sumValues(inboundByDay);
        List<InboundBatch> knownInboundBatches = collectKnownInboundBatches(input.getInboundBatches(), planDate);
        List<MissingEtaBatch> missingEtaBatches = collectMissingEtaBatches(input.getInboundBatches(), input.getMissingEtaBatches());
        BigDecimal missingEtaInboundQty = sumMissingEta(missingEtaBatches);
        StockSnapshot stockSnapshot = input.getStockSnapshot();
        List<String> warnings = buildWarnings(
                dailyDemandByDay,
                missingEtaInboundQty,
                horizon,
                stockSnapshot,
                hasExcludedPastEtaInbound(input.getInboundBatches(), planDate),
                hasRecentPastEtaReview(knownInboundBatches),
                input.getLatestFactDate(),
                planDate,
                resolvedConfig,
                input.isInboundSiteUnresolved()
        );
        boolean calculationBlocked = isCalculationBlocked(warnings);

        BigDecimal fbnStockUnits = stockSnapshot.getFbnStockUnits();
        BigDecimal supermallStockUnits = stockSnapshot.getSupermallStockUnits();
        BigDecimal currentStockUnits = stockSnapshot.getCurrentStockUnits();
        BigDecimal projectionStockUnits = projectionStockUnits(stockSnapshot);
        BigDecimal startingProjectionStockUnits = projectionStockUnits.add(inboundByDay.getOrDefault(0, BigDecimal.ZERO));
        BigDecimal nonSeaStartingProjectionStockUnits = projectionStockUnits.add(nonSeaInboundByDay.getOrDefault(0, BigDecimal.ZERO));

        BigDecimal projectedStock = startingProjectionStockUnits;
        Integer firstStockoutDay = projectedStock.compareTo(BigDecimal.ZERO) <= 0 ? 0 : null;
        List<DailyProjectionView> dailyProjection = new ArrayList<>();
        for (int day = 1; day <= horizon; day++) {
            BigDecimal forecastDemand = dailyDemandByDay.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal inboundUnits = inboundByDay.getOrDefault(day, BigDecimal.ZERO);
            projectedStock = projectedStock.subtract(forecastDemand).add(inboundUnits);
            if (firstStockoutDay == null && projectedStock.compareTo(BigDecimal.ZERO) <= 0) {
                firstStockoutDay = day;
            }
            dailyProjection.add(new DailyProjectionView(
                    day,
                    dateFor(planDate, day),
                    forecastDemand,
                    inboundUnits,
                    projectedStock
            ));
        }

        BigDecimal airWindowForecastUnits = sumDemand(dailyDemandByDay, airWindowStartDay, airWindowEndDay);
        BigDecimal seaWindowForecastUnits = sumDemand(dailyDemandByDay, seaWindowStartDay, seaWindowEndDay);
        BigDecimal forecastUnits100 = sumDemand(dailyDemandByDay, 1, 101);
        BigDecimal airWindowNetShortage = ceilDemand(windowShortage(
                dailyDemandByDay,
                inboundByDay,
                airWindowStartDay,
                airWindowEndDay,
                stockBeforeDay(startingProjectionStockUnits, dailyDemandByDay, inboundByDay, airWindowStartDay)
        ));
        BigDecimal airWindowNetShortageWithoutSea = ceilDemand(windowShortage(
                dailyDemandByDay,
                nonSeaInboundByDay,
                airWindowStartDay,
                airWindowEndDay,
                stockBeforeDay(nonSeaStartingProjectionStockUnits, dailyDemandByDay, nonSeaInboundByDay, airWindowStartDay)
        ));
        if (airWindowNetShortageWithoutSea.compareTo(airWindowNetShortage) > 0) {
            warnings.add(SEA_ETA_UNCERTAIN_AIR_WINDOW_WARNING);
        }
        BigDecimal airCalculatedUnits = calculationBlocked ? BigDecimal.ZERO : airWindowNetShortage;
        BigDecimal airSuggestedUnits = airCalculatedUnits;
        Map<Integer, BigDecimal> inboundWithPlannedAirByDay = new HashMap<>(inboundByDay);
        if (airSuggestedUnits.compareTo(BigDecimal.ZERO) > 0) {
            inboundWithPlannedAirByDay.merge(airWindowStartDay, airSuggestedUnits, BigDecimal::add);
        }
        BigDecimal seaCalculatedUnits = calculationBlocked ? BigDecimal.ZERO : ceilDemand(windowShortage(
                dailyDemandByDay,
                inboundWithPlannedAirByDay,
                seaWindowStartDay,
                seaWindowEndDay,
                stockBeforeDay(
                        startingProjectionStockUnits,
                        dailyDemandByDay,
                        inboundWithPlannedAirByDay,
                        seaWindowStartDay
                )
        ));
        BigDecimal seaSuggestedUnits = calculationBlocked
                ? BigDecimal.ZERO
                : applySeaSuggestionTier(input.getHistoryUnits30(), seaCalculatedUnits);
        String stockoutWindowLabel = firstStockoutDay != null && firstStockoutDay < resolvedConfig.getAirLeadDays()
                ? firstStockoutDay + "-" + resolvedConfig.getAirLeadDays() + " 天"
                : null;

        return new PlanItemView(
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                resolvedConfig,
                input.getPartnerSku(),
                input.getSku(),
                input.getProductTitle(),
                input.getImageUrl(),
                input.getListingAt(),
                input.getLatestFactDate(),
                input.getObservedDays(),
                input.getHistoryUnits7(),
                input.getHistoryUnits30(),
                input.getHistoryUnits60(),
                input.getHistoryUnits90(),
                input.getAdjustedHistoryUnits7(),
                input.getAdjustedHistoryUnits30(),
                input.getAdjustedHistoryUnits60(),
                input.getAdjustedHistoryUnits90(),
                input.getForecastUnits30(),
                input.getForecastUnits60(),
                input.getForecastUnits90(),
                forecastUnits100,
                input.getConfidenceLabel(),
                input.getShortReason(),
                currentStockUnits,
                fbnStockUnits,
                supermallStockUnits,
                knownInboundUnits,
                knownInboundBatches,
                nearestInboundEtaDate(input.getInboundBatches(), planDate),
                missingEtaInboundQty,
                missingEtaBatches.size(),
                firstStockoutDay,
                stockoutWindowLabel,
                airWindowStartDay,
                airWindowEndDay,
                airWindowForecastUnits,
                airCalculatedUnits,
                airSuggestedUnits,
                seaWindowStartDay,
                seaWindowEndDay,
                seaWindowForecastUnits,
                seaCalculatedUnits,
                seaSuggestedUnits,
                dailyProjection,
                missingEtaBatches,
                calculationBlocked,
                warnings,
                buildExplanation(
                        seaWindowStartDay,
                        seaWindowEndDay,
                        firstStockoutDay,
                        airWindowStartDay,
                        airWindowEndDay,
                        airWindowNetShortage,
                        calculationBlocked
                )
        );
    }

    private static LocalDate nearestInboundEtaDate(List<InboundBatch> inboundBatches, LocalDate planDate) {
        LocalDate nearest = null;
        if (inboundBatches == null) {
            return null;
        }
        for (InboundBatch batch : inboundBatches) {
            if (!isCountableEtaInbound(batch, planDate)) {
                continue;
            }
            if (nearest == null || batch.getEtaDate().isBefore(nearest)) {
                nearest = batch.getEtaDate();
            }
        }
        return nearest;
    }

    private static Map<Integer, BigDecimal> normalizeDemand(Map<Integer, BigDecimal> dailyDemandByDay) {
        Map<Integer, BigDecimal> normalized = new HashMap<>();
        if (dailyDemandByDay == null || dailyDemandByDay.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<Integer, BigDecimal> entry : dailyDemandByDay.entrySet()) {
            Integer day = entry.getKey();
            if (day == null || day < 1) {
                continue;
            }
            BigDecimal demand = nonNegative(entry.getValue());
            normalized.merge(day, demand, BigDecimal::add);
        }
        return normalized;
    }

    private static Map<Integer, BigDecimal> groupInboundByDay(LocalDate planDate, List<InboundBatch> inboundBatches) {
        return groupInboundByDay(planDate, inboundBatches, false);
    }

    private static Map<Integer, BigDecimal> groupNonSeaInboundByDay(LocalDate planDate, List<InboundBatch> inboundBatches) {
        return groupInboundByDay(planDate, inboundBatches, true);
    }

    private static Map<Integer, BigDecimal> groupInboundByDay(
            LocalDate planDate,
            List<InboundBatch> inboundBatches,
            boolean excludeSea
    ) {
        Map<Integer, BigDecimal> inboundByDay = new HashMap<>();
        if (planDate == null || inboundBatches == null || inboundBatches.isEmpty()) {
            return inboundByDay;
        }
        for (InboundBatch batch : inboundBatches) {
            if (excludeSea && isSeaTransportMode(batch.getTransportMode())) {
                continue;
            }
            if (!isCountableEtaInbound(batch, planDate)) {
                continue;
            }
            BigDecimal remainingQuantity = nonNegative(batch.getRemainingQuantity());
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            long day = inboundCoverageDay(batch, planDate);
            if (day < 0 || day > Integer.MAX_VALUE) {
                continue;
            }
            inboundByDay.merge((int) day, remainingQuantity, BigDecimal::add);
        }
        return inboundByDay;
    }

    private static boolean isSeaTransportMode(String transportMode) {
        if (transportMode == null) {
            return false;
        }
        String normalized = transportMode.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("SEA")
                || normalized.contains("OCEAN")
                || normalized.contains("海运");
    }

    private static List<MissingEtaBatch> collectMissingEtaBatches(
            List<InboundBatch> inboundBatches,
            List<MissingEtaBatch> explicitMissingEtaBatches
    ) {
        Map<String, MissingEtaBatch> batchesByKey = new LinkedHashMap<>();
        if (explicitMissingEtaBatches != null) {
            for (MissingEtaBatch batch : explicitMissingEtaBatches) {
                addMissingEtaBatch(batchesByKey, batch);
            }
        }
        if (inboundBatches != null) {
            for (InboundBatch batch : inboundBatches) {
                if (batch == null
                        || !isActiveStatus(batch.getBatchStatus())
                        || batch.getEtaDate() != null
                        || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                addMissingEtaBatch(batchesByKey, new MissingEtaBatch(
                        batch.getBatchId(),
                        batch.getBatchReferenceNo(),
                        batch.getTransportMode(),
                        batch.getBatchStatus(),
                        batch.getRemainingQuantity(),
                        batch.getDestinationCode()
                ));
            }
        }
        return new ArrayList<>(batchesByKey.values());
    }

    private static List<InboundBatch> collectKnownInboundBatches(List<InboundBatch> inboundBatches, LocalDate planDate) {
        if (inboundBatches == null || inboundBatches.isEmpty()) {
            return List.of();
        }
        List<InboundBatch> knownBatches = new ArrayList<>();
        for (InboundBatch batch : inboundBatches) {
            if (batch == null
                    || !isActiveStatus(batch.getBatchStatus())
                    || batch.getEtaDate() == null
                    || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            boolean pastEta = isPastEta(batch, planDate);
            knownBatches.add(new InboundBatch(
                    batch.getBatchId(),
                    batch.getBatchReferenceNo(),
                    batch.getTransportMode(),
                    batch.getBatchStatus(),
                    batch.getEtaDate(),
                    batch.getRemainingQuantity(),
                    batch.getDestinationCode(),
                    !pastEta,
                    pastEta
            ));
        }
        return knownBatches;
    }

    private static void addMissingEtaBatch(Map<String, MissingEtaBatch> batchesByKey, MissingEtaBatch batch) {
        if (batch == null
                || !isActiveStatus(batch.getBatchStatus())
                || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        batchesByKey.putIfAbsent(missingEtaKey(batch), batch);
    }

    private static boolean isActiveStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return true;
        }
        return !INACTIVE_INBOUND_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    private static String missingEtaKey(MissingEtaBatch batch) {
        if (batch.getBatchId() != null) {
            return "id:" + batch.getBatchId();
        }
        if (batch.getBatchReferenceNo() != null && !batch.getBatchReferenceNo().trim().isEmpty()) {
            return "ref:" + batch.getBatchReferenceNo().trim();
        }
        return "batch:"
                + batch.getTransportMode() + "|"
                + batch.getBatchStatus() + "|"
                + batch.getRemainingQuantity().toPlainString();
    }

    private static List<String> buildWarnings(
            Map<Integer, BigDecimal> dailyDemandByDay,
            BigDecimal missingEtaInboundQty,
            int requiredForecastDays,
            StockSnapshot stockSnapshot,
            boolean hasPastEtaInbound,
            boolean hasRecentPastEtaReview,
            LocalDate latestFactDate,
            LocalDate planDate,
            ReplenishmentPlanConfig config,
            boolean inboundSiteUnresolved
    ) {
        List<String> warnings = new ArrayList<>();
        if (inboundSiteUnresolved) {
            warnings.add("inbound_site_unresolved");
        }
        if (missingEtaInboundQty.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("missing_eta_inbound_excluded");
        }
        if (hasPastEtaInbound) {
            warnings.add("past_eta_inbound_excluded");
        }
        if (hasRecentPastEtaReview) {
            warnings.add("recent_eta_review_required");
        }
        if (dailyDemandByDay == null || dailyDemandByDay.isEmpty()) {
            warnings.add("daily_forecast_missing");
        }
        if (hasForecastGap(dailyDemandByDay, requiredForecastDays)) {
            warnings.add("daily_forecast_gap");
        }
        if (stockSnapshot == null || stockSnapshot.getCurrentStockUnits() == null || stockSnapshot.currentStockFactMissing()) {
            warnings.add("stock_fact_missing");
        }
        if (stockSnapshot == null || stockSnapshot.getFbnStockUnits() == null) {
            warnings.add("fbn_stock_fact_missing");
        }
        if (latestFactDate != null && planDate != null) {
            long forecastAgeDays = ChronoUnit.DAYS.between(latestFactDate, planDate);
            if (forecastAgeDays > config.getForecastStaleWarningDays()) {
                warnings.add("forecast_fact_stale");
            }
            if (forecastAgeDays > config.getForecastStaleBlockingDays()) {
                warnings.add("forecast_fact_expired");
            }
        }
        return warnings;
    }

    private static boolean isCalculationBlocked(List<String> warnings) {
        return warnings.contains("daily_forecast_missing")
                || warnings.contains("daily_forecast_gap")
                || warnings.contains("stock_fact_missing")
                || warnings.contains("fbn_stock_fact_missing")
                || warnings.contains("forecast_fact_expired")
                || warnings.contains("inbound_site_unresolved");
    }

    private static boolean hasExcludedPastEtaInbound(List<InboundBatch> inboundBatches, LocalDate planDate) {
        if (inboundBatches == null || inboundBatches.isEmpty()) {
            return false;
        }
        for (InboundBatch batch : inboundBatches) {
            if (isValidEtaInbound(batch) && isEffectiveInboundPast(batch, planDate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRecentPastEtaReview(List<InboundBatch> inboundBatches) {
        if (inboundBatches == null || inboundBatches.isEmpty()) {
            return false;
        }
        for (InboundBatch batch : inboundBatches) {
            if (batch != null && batch.isEtaReviewRequired()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCountableEtaInbound(InboundBatch batch, LocalDate planDate) {
        return isValidEtaInbound(batch)
                && !isEffectiveInboundPast(batch, planDate);
    }

    private static long inboundCoverageDay(InboundBatch batch, LocalDate planDate) {
        return ChronoUnit.DAYS.between(planDate, effectiveInboundDate(batch.getEtaDate()));
    }

    private static boolean isValidEtaInbound(InboundBatch batch) {
        return batch != null
                && isActiveStatus(batch.getBatchStatus())
                && batch.getEtaDate() != null
                && batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean isPastEta(InboundBatch batch, LocalDate planDate) {
        return batch != null && isPastEta(batch.getEtaDate(), planDate);
    }

    private static boolean isPastEta(LocalDate etaDate, LocalDate planDate) {
        return etaDate != null && planDate != null && etaDate.isBefore(planDate);
    }

    private static boolean isEffectiveInboundPast(InboundBatch batch, LocalDate planDate) {
        return batch != null
                && planDate != null
                && effectiveInboundDate(batch.getEtaDate()).isBefore(planDate);
    }

    private static LocalDate effectiveInboundDate(LocalDate etaDate) {
        return etaDate;
    }

    private static BigDecimal projectionStockUnits(StockSnapshot stockSnapshot) {
        if (stockSnapshot == null) {
            return BigDecimal.ZERO;
        }
        return nonNegative(stockSnapshot.getFbnStockUnits());
    }

    private static boolean hasForecastGap(Map<Integer, BigDecimal> dailyDemandByDay, int requiredForecastDays) {
        if (dailyDemandByDay == null) {
            return true;
        }
        for (int day = 1; day <= requiredForecastDays; day++) {
            if (!dailyDemandByDay.containsKey(day)) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal sumDemand(Map<Integer, BigDecimal> dailyDemandByDay, int startInclusive, int endExclusive) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int day = startInclusive; day < endExclusive; day++) {
            sum = sum.add(dailyDemandByDay.getOrDefault(day, BigDecimal.ZERO));
        }
        return sum;
    }

    private static BigDecimal stockBeforeDay(
            BigDecimal initialStock,
            Map<Integer, BigDecimal> dailyDemandByDay,
            Map<Integer, BigDecimal> inboundByDay,
            int day
    ) {
        BigDecimal stock = nonNegative(initialStock);
        for (int cursor = 1; cursor < day; cursor++) {
            stock = stock
                    .subtract(dailyDemandByDay.getOrDefault(cursor, BigDecimal.ZERO))
                    .add(inboundByDay.getOrDefault(cursor, BigDecimal.ZERO));
        }
        return nonNegative(stock);
    }

    private static BigDecimal windowShortage(
            Map<Integer, BigDecimal> dailyDemandByDay,
            Map<Integer, BigDecimal> inboundByDay,
            int startInclusive,
            int endExclusive,
            BigDecimal startingStock
    ) {
        BigDecimal stock = nonNegative(startingStock);
        BigDecimal maxShortage = BigDecimal.ZERO;
        for (int day = startInclusive; day < endExclusive; day++) {
            stock = stock
                    .subtract(dailyDemandByDay.getOrDefault(day, BigDecimal.ZERO))
                    .add(inboundByDay.getOrDefault(day, BigDecimal.ZERO));
            if (stock.compareTo(BigDecimal.ZERO) < 0 && stock.abs().compareTo(maxShortage) > 0) {
                maxShortage = stock.abs();
            }
        }
        return maxShortage;
    }

    private static BigDecimal sumValues(Map<Integer, BigDecimal> valuesByDay) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : valuesByDay.values()) {
            sum = sum.add(nonNegative(value));
        }
        return sum;
    }

    private static BigDecimal sumMissingEta(List<MissingEtaBatch> batches) {
        BigDecimal sum = BigDecimal.ZERO;
        for (MissingEtaBatch batch : batches) {
            sum = sum.add(batch.getRemainingQuantity());
        }
        return sum;
    }

    private static BigDecimal ceilDemand(BigDecimal demand) {
        if (demand == null || demand.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return demand.setScale(0, RoundingMode.CEILING);
    }

    private static BigDecimal applySeaSuggestionTier(int historyUnits30, BigDecimal calculatedUnits) {
        if (calculatedUnits == null || calculatedUnits.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (historyUnits30 < 3) {
            if (calculatedUnits.compareTo(new BigDecimal("5")) < 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(ceilToTen(calculatedUnits));
        }

        int monthlySalesFloor = Math.max(0, historyUnits30);
        int minimumSuggestion = Math.max(10, (monthlySalesFloor / 10) * 10);
        int roundedCalculation = ceilToTen(calculatedUnits);
        if (historyUnits30 > 40) {
            if (calculatedUnits.compareTo(new BigDecimal("5")) < 0) {
                return BigDecimal.ZERO;
            }
            int rampedSuggestion = ceilToTen(calculatedUnits.multiply(new BigDecimal("1.5")));
            int cappedRamp = Math.min(minimumSuggestion, rampedSuggestion);
            return BigDecimal.valueOf(Math.max(roundedCalculation, cappedRamp));
        }
        return BigDecimal.valueOf(Math.max(minimumSuggestion, roundedCalculation));
    }

    private static int ceilToTen(BigDecimal units) {
        if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return units
                .divide(BigDecimal.TEN, 0, RoundingMode.CEILING)
                .multiply(BigDecimal.TEN)
                .intValue();
    }

    private static LocalDate dateFor(LocalDate anchorDate, int day) {
        if (anchorDate == null) {
            return null;
        }
        return anchorDate.plusDays(day);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private static String buildExplanation(
            int seaWindowStartDay,
            int seaWindowEndDay,
            Integer firstStockoutDay,
            int airWindowStartDay,
            int airWindowEndDay,
            BigDecimal airWindowNetShortage,
            boolean calculationBlocked
    ) {
        String airExplanation;
        if (calculationBlocked) {
            airExplanation = "suggestions unavailable because required forecast or FBN stock facts are incomplete or expired.";
        } else if (airWindowNetShortage != null && airWindowNetShortage.compareTo(BigDecimal.ZERO) > 0) {
            airExplanation = "air suggestion uses net shortage "
                    + airWindowNetShortage.toPlainString()
                    + " in the air window after known inbound coverage.";
        } else if (firstStockoutDay != null && firstStockoutDay < airWindowStartDay) {
            airExplanation = "projected stock runs out before air arrival, but known inbound covers the air window net shortage.";
        } else {
            airExplanation = "air suggestion not needed because projected stock has no net shortage during air window "
                    + airWindowStartDay + "-" + airWindowEndDay + " days.";
        }
        return "Sea window " + seaWindowStartDay + "-" + seaWindowEndDay
                + " days uses forecast demand over the half-open window; " + airExplanation;
    }
}
