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

    private static final Set<String> INACTIVE_INBOUND_STATUSES = new HashSet<>(Arrays.asList(
            "draft",
            "warehouse_received",
            "completed",
            "cancelled"
    ));

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
        List<MissingEtaBatch> missingEtaBatches = collectMissingEtaBatches(input.getInboundBatches(), input.getMissingEtaBatches());
        BigDecimal missingEtaInboundQty = sumMissingEta(missingEtaBatches);
        List<String> warnings = buildWarnings(dailyDemandByDay, missingEtaInboundQty, seaWindowEndDay);

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
        boolean shouldCalculateAirSuggestion = !resolvedConfig.isAirEmergencyOnly() || airEmergencyTriggered;
        BigDecimal airSuggestedUnits = shouldCalculateAirSuggestion
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
                buildExplanation(
                        seaWindowStartDay,
                        seaWindowEndDay,
                        resolvedConfig.isAirEmergencyOnly(),
                        airEmergencyTriggered,
                        firstStockoutDay
                )
        );
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

    private static Map<Integer, BigDecimal> groupInboundByDay(LocalDate anchorDate, List<InboundBatch> inboundBatches) {
        Map<Integer, BigDecimal> inboundByDay = new HashMap<>();
        if (anchorDate == null || inboundBatches == null || inboundBatches.isEmpty()) {
            return inboundByDay;
        }
        for (InboundBatch batch : inboundBatches) {
            if (batch == null || !isActiveStatus(batch.getBatchStatus()) || batch.getEtaDate() == null) {
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
            int projectionDay = day == 0 ? 1 : (int) day;
            inboundByDay.merge(projectionDay, remainingQuantity, BigDecimal::add);
        }
        return inboundByDay;
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
                        batch.getRemainingQuantity()
                ));
            }
        }
        return new ArrayList<>(batchesByKey.values());
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
            int seaWindowEndDay
    ) {
        List<String> warnings = new ArrayList<>();
        if (missingEtaInboundQty.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("missing_eta_inbound_excluded");
        }
        if (dailyDemandByDay == null || dailyDemandByDay.isEmpty()) {
            warnings.add("daily_forecast_missing");
        }
        if (hasForecastGap(dailyDemandByDay, seaWindowEndDay)) {
            warnings.add("daily_forecast_gap");
        }
        return warnings;
    }

    private static boolean hasForecastGap(Map<Integer, BigDecimal> dailyDemandByDay, int seaWindowEndDay) {
        if (dailyDemandByDay == null) {
            return true;
        }
        for (int day = 1; day < seaWindowEndDay; day++) {
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
            boolean airEmergencyOnly,
            boolean airEmergencyTriggered,
            Integer firstStockoutDay
    ) {
        String airExplanation;
        if (!airEmergencyOnly) {
            airExplanation = "air emergency mode disabled; air window calculated without stockout trigger.";
        } else if (airEmergencyTriggered) {
            airExplanation = "air emergency triggered because first stockout day "
                    + firstStockoutDay + " is before sea arrival.";
        } else {
            airExplanation = "air emergency not triggered because projected stock does not run out before sea arrival.";
        }
        return "Sea window " + seaWindowStartDay + "-" + seaWindowEndDay
                + " days uses forecast demand over the half-open window; " + airExplanation;
    }
}
