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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReplenishmentPlanCalculator {

    public PlanItemView calculate(PlanInput input, ReplenishmentPlanConfig config) {
        Objects.requireNonNull(input, "input must not be null");
        ReplenishmentPlanConfig resolvedConfig = config == null ? ReplenishmentPlanConfig.defaultBasicV1() : config;

        int airWindowStartDay = resolvedConfig.getAirLeadDays();
        int airWindowEndDay = resolvedConfig.getAirLeadDays() + resolvedConfig.getAirCoverDays();
        int seaWindowStartDay = resolvedConfig.getSeaLeadDays();
        int seaWindowEndDay = resolvedConfig.getSeaLeadDays() + resolvedConfig.getSeaCoverDays();
        int horizon = Math.max(
                Math.max(resolvedConfig.getForecastHorizonDays(), seaWindowEndDay),
                Math.max(30, airWindowEndDay)
        );

        Map<Integer, BigDecimal> dailyDemandByDay = normalizeDemand(input.getDailyDemandByDay());
        Map<Integer, BigDecimal> inboundByDay = groupInboundByDay(input.getAnchorDate(), input.getInboundBatches());
        BigDecimal knownInboundUnits = sumValues(inboundByDay);
        List<MissingEtaBatch> missingEtaBatches = positiveMissingEtaBatches(input.getMissingEtaBatches());
        BigDecimal missingEtaInboundQty = sumMissingEta(missingEtaBatches);
        List<String> warnings = buildWarnings(input.getDailyDemandByDay(), missingEtaInboundQty);

        StockSnapshot stockSnapshot = input.getStockSnapshot();
        BigDecimal fbnStockUnits = stockSnapshot.getFbnStockUnits();
        BigDecimal supermallStockUnits = stockSnapshot.getSupermallStockUnits();
        BigDecimal currentStockUnits = fbnStockUnits.add(supermallStockUnits);

        BigDecimal projectedStock = currentStockUnits;
        Integer firstStockoutDay = null;
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
                    dateFor(input.getAnchorDate(), day),
                    forecastDemand,
                    inboundUnits,
                    projectedStock
            ));
        }

        BigDecimal seaSuggestedUnits = ceilDemand(sumDemand(dailyDemandByDay, seaWindowStartDay, seaWindowEndDay));
        boolean airEmergencyTriggered = firstStockoutDay != null && firstStockoutDay < resolvedConfig.getSeaLeadDays();
        BigDecimal airSuggestedUnits = airEmergencyTriggered
                ? ceilDemand(sumDemand(dailyDemandByDay, airWindowStartDay, airWindowEndDay))
                : BigDecimal.ZERO;
        String stockoutWindowLabel = firstStockoutDay != null && firstStockoutDay < resolvedConfig.getAirLeadDays()
                ? firstStockoutDay + "-" + resolvedConfig.getAirLeadDays() + " 天"
                : null;

        return new PlanItemView(
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                resolvedConfig,
                input.getPartnerSku(),
                input.getSku(),
                input.getProductTitle(),
                currentStockUnits,
                fbnStockUnits,
                supermallStockUnits,
                knownInboundUnits,
                missingEtaInboundQty,
                missingEtaBatches.size(),
                firstStockoutDay,
                stockoutWindowLabel,
                airWindowStartDay,
                airWindowEndDay,
                airSuggestedUnits,
                seaWindowStartDay,
                seaWindowEndDay,
                seaSuggestedUnits,
                dailyProjection,
                missingEtaBatches,
                warnings,
                buildExplanation(seaWindowStartDay, seaWindowEndDay, airEmergencyTriggered, firstStockoutDay)
        );
    }

    private static Map<Integer, BigDecimal> normalizeDemand(Map<Integer, BigDecimal> dailyDemandByDay) {
        Map<Integer, BigDecimal> normalized = new HashMap<>();
        if (dailyDemandByDay == null || dailyDemandByDay.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<Integer, BigDecimal> entry : dailyDemandByDay.entrySet()) {
            Integer day = entry.getKey();
            BigDecimal demand = nonNegative(entry.getValue());
            if (day == null || day < 1 || demand.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            normalized.merge(day, demand, BigDecimal::add);
        }
        return normalized;
    }

    private static Map<Integer, BigDecimal> groupInboundByDay(LocalDate anchorDate, List<InboundBatch> inboundBatches) {
        Map<Integer, BigDecimal> inboundByDay = new HashMap<>();
        if (anchorDate == null || inboundBatches == null || inboundBatches.isEmpty()) {
            return inboundByDay;
        }
        for (InboundBatch batch : inboundBatches) {
            if (batch == null || batch.getEtaDate() == null) {
                continue;
            }
            BigDecimal remainingQuantity = nonNegative(batch.getRemainingQuantity());
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            long day = ChronoUnit.DAYS.between(anchorDate, batch.getEtaDate());
            if (day < 0 || day > Integer.MAX_VALUE) {
                continue;
            }
            inboundByDay.merge((int) day, remainingQuantity, BigDecimal::add);
        }
        return inboundByDay;
    }

    private static List<MissingEtaBatch> positiveMissingEtaBatches(List<MissingEtaBatch> batches) {
        List<MissingEtaBatch> positiveBatches = new ArrayList<>();
        if (batches == null || batches.isEmpty()) {
            return positiveBatches;
        }
        for (MissingEtaBatch batch : batches) {
            if (batch != null && batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                positiveBatches.add(batch);
            }
        }
        return positiveBatches;
    }

    private static List<String> buildWarnings(Map<Integer, BigDecimal> dailyDemandByDay, BigDecimal missingEtaInboundQty) {
        List<String> warnings = new ArrayList<>();
        if (missingEtaInboundQty.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("missing_eta_inbound_excluded");
        }
        if (dailyDemandByDay == null || dailyDemandByDay.isEmpty()) {
            warnings.add("daily_forecast_missing");
        }
        return warnings;
    }

    private static BigDecimal sumDemand(Map<Integer, BigDecimal> dailyDemandByDay, int startInclusive, int endExclusive) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int day = startInclusive; day < endExclusive; day++) {
            sum = sum.add(dailyDemandByDay.getOrDefault(day, BigDecimal.ZERO));
        }
        return sum;
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
            boolean airEmergencyTriggered,
            Integer firstStockoutDay
    ) {
        String airExplanation = airEmergencyTriggered
                ? "air emergency triggered because first stockout day "
                + firstStockoutDay + " is before sea arrival."
                : "air emergency not triggered because projected stock does not run out before sea arrival.";
        return "Sea window " + seaWindowStartDay + "-" + seaWindowEndDay
                + " days uses forecast demand over the half-open window; " + airExplanation;
    }
}
