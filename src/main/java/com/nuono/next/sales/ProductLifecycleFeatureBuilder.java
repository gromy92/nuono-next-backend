package com.nuono.next.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleFeatureBuilder {

    public ProductLifecycleFeatureSnapshot build(
            ProductLifecycleStateQuery query,
            LocalDate analysisDate,
            List<DailySalesFact> facts
    ) {
        Map<LocalDate, DailyAggregate> aggregates = aggregate(facts);
        ProductLifecycleFeatureWindow recent7 = window(aggregates, analysisDate.minusDays(6), analysisDate, 7);
        ProductLifecycleFeatureWindow recent15 = window(aggregates, analysisDate.minusDays(14), analysisDate, 15);
        ProductLifecycleFeatureWindow previous15 = window(
                aggregates,
                analysisDate.minusDays(29),
                analysisDate.minusDays(15),
                15
        );
        ProductLifecycleFeatureWindow recent30 = window(aggregates, analysisDate.minusDays(29), analysisDate, 30);
        ProductLifecycleFeatureWindow previous30 = window(
                aggregates,
                analysisDate.minusDays(59),
                analysisDate.minusDays(30),
                30
        );
        ProductLifecycleFeatureWindow recent60 = window(aggregates, analysisDate.minusDays(59), analysisDate, 60);

        List<String> qualityReasons = new ArrayList<>();
        recent15 = estimatePvIfMissing(recent15, previous15, qualityReasons);
        previous15 = estimatePvIfMissing(previous15, recent15, qualityReasons);
        if ((recent15.getPv() == null || previous15.getPv() == null)
                && (recent15.getSalesUnits() > 0 || previous15.getSalesUnits() > 0)) {
            addReason(qualityReasons, "pv_unresolvable");
        }
        if (recent60.getObservedDays() < 60) {
            addReason(qualityReasons, "partial_60_day_window");
        }

        BigDecimal salesGrowth15 = growthRate(recent15.getSalesUnits(), previous15.getSalesUnits());
        BigDecimal pvGrowth15 = recent15.getPv() == null || previous15.getPv() == null
                ? null
                : growthRate(recent15.getPv(), previous15.getPv());
        BigDecimal volatility30 = volatility(aggregates, analysisDate.minusDays(29), analysisDate);
        return new ProductLifecycleFeatureSnapshot(
                query,
                analysisDate,
                recent7,
                recent15,
                previous15,
                recent30,
                previous30,
                recent60,
                salesGrowth15,
                pvGrowth15,
                volatility30,
                recent30.getActiveSalesDays(),
                qualityReasons,
                evidenceJson(recent15, previous15, recent30, recent60, qualityReasons)
        );
    }

    private Map<LocalDate, DailyAggregate> aggregate(List<DailySalesFact> facts) {
        Map<LocalDate, DailyAggregate> aggregates = new HashMap<>();
        if (facts == null) {
            return aggregates;
        }
        for (DailySalesFact fact : facts) {
            if (fact == null || fact.getFactDate() == null) {
                continue;
            }
            DailyAggregate aggregate = aggregates.computeIfAbsent(fact.getFactDate(), ignored -> new DailyAggregate());
            aggregate.salesUnits += fact.getNetUnits();
            Integer pv = observedPv(fact);
            if (pv != null) {
                aggregate.pv += pv;
                aggregate.hasPv = true;
            }
        }
        return aggregates;
    }

    private ProductLifecycleFeatureWindow window(
            Map<LocalDate, DailyAggregate> aggregates,
            LocalDate start,
            LocalDate end,
            int dayCount
    ) {
        int salesUnits = 0;
        long pv = 0L;
        int activeSalesDays = 0;
        int observedDays = 0;
        int pvObservedDays = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            DailyAggregate aggregate = aggregates.get(cursor);
            if (aggregate != null) {
                observedDays++;
                salesUnits += aggregate.salesUnits;
                if (aggregate.salesUnits > 0) {
                    activeSalesDays++;
                }
                if (aggregate.hasPv) {
                    pv += aggregate.pv;
                    pvObservedDays++;
                }
            }
            cursor = cursor.plusDays(1);
        }
        return new ProductLifecycleFeatureWindow(
                dayCount,
                observedDays,
                salesUnits,
                pvObservedDays == 0 ? null : pv,
                activeSalesDays,
                pvObservedDays,
                false
        );
    }

    private ProductLifecycleFeatureWindow estimatePvIfMissing(
            ProductLifecycleFeatureWindow target,
            ProductLifecycleFeatureWindow adjacent,
            List<String> qualityReasons
    ) {
        if (target.getPv() != null || target.getSalesUnits() <= 0) {
            return target;
        }
        if (adjacent.getPv() == null || adjacent.getSalesUnits() <= 0) {
            return target;
        }
        BigDecimal pvPerSale = BigDecimal.valueOf(adjacent.getPv())
                .divide(BigDecimal.valueOf(adjacent.getSalesUnits()), 8, RoundingMode.HALF_UP);
        long estimated = pvPerSale.multiply(BigDecimal.valueOf(target.getSalesUnits()))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        addReason(qualityReasons, "pv_estimated");
        return target.withEstimatedPv(estimated);
    }

    private Integer observedPv(DailySalesFact fact) {
        if (fact.getYourVisitors() != null) {
            return fact.getYourVisitors();
        }
        return fact.getTotalVisitors();
    }

    private BigDecimal growthRate(long current, long previous) {
        if (previous <= 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(previous), 6, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal volatility(Map<LocalDate, DailyAggregate> aggregates, LocalDate start, LocalDate end) {
        int dayCount = 0;
        int total = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            total += salesUnits(aggregates, cursor);
            dayCount++;
            cursor = cursor.plusDays(1);
        }
        if (dayCount == 0 || total == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        double mean = total / (double) dayCount;
        double squaredDistance = 0.0d;
        cursor = start;
        while (!cursor.isAfter(end)) {
            double delta = salesUnits(aggregates, cursor) - mean;
            squaredDistance += delta * delta;
            cursor = cursor.plusDays(1);
        }
        double standardDeviation = Math.sqrt(squaredDistance / dayCount);
        return BigDecimal.valueOf(standardDeviation / mean).setScale(4, RoundingMode.HALF_UP);
    }

    private int salesUnits(Map<LocalDate, DailyAggregate> aggregates, LocalDate date) {
        DailyAggregate aggregate = aggregates.get(date);
        return aggregate == null ? 0 : aggregate.salesUnits;
    }

    private void addReason(List<String> qualityReasons, String reason) {
        if (!qualityReasons.contains(reason)) {
            qualityReasons.add(reason);
        }
    }

    private String evidenceJson(
            ProductLifecycleFeatureWindow recent15,
            ProductLifecycleFeatureWindow previous15,
            ProductLifecycleFeatureWindow recent30,
            ProductLifecycleFeatureWindow recent60,
            List<String> qualityReasons
    ) {
        return "{"
                + "\"recent15Sales\":" + recent15.getSalesUnits() + ","
                + "\"previous15Sales\":" + previous15.getSalesUnits() + ","
                + "\"recent15Pv\":" + jsonNumber(recent15.getPv()) + ","
                + "\"previous15Pv\":" + jsonNumber(previous15.getPv()) + ","
                + "\"recent15PvEstimated\":" + recent15.isPvEstimated() + ","
                + "\"previous15PvEstimated\":" + previous15.isPvEstimated() + ","
                + "\"recent30ActiveSalesDays\":" + recent30.getActiveSalesDays() + ","
                + "\"recent60ObservedDays\":" + recent60.getObservedDays() + ","
                + "\"qualityReasons\":" + jsonArray(qualityReasons)
                + "}";
    }

    private String jsonNumber(Long value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String jsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(values.get(i)).append('"');
        }
        builder.append(']');
        return builder.toString();
    }

    private static class DailyAggregate {
        private int salesUnits;
        private long pv;
        private boolean hasPv;
    }
}
