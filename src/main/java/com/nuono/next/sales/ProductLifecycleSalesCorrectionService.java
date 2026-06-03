package com.nuono.next.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleSalesCorrectionService {

    public ProductLifecycleCorrectedFeatureSnapshot correct(
            ProductLifecycleStateQuery query,
            LocalDate analysisDate,
            ProductLifecycleFeatureSnapshot rawSnapshot,
            List<DailySalesFact> facts,
            List<SalesActivityWindowRecord> activityWindows
    ) {
        List<SalesActivityWindowRecord> activeFactors = activeFactors(query, analysisDate, activityWindows);
        BigDecimal correctedRecent7 = correctedSales(facts, analysisDate.minusDays(6), analysisDate, activeFactors);
        BigDecimal correctedRecent15 = correctedSales(facts, analysisDate.minusDays(14), analysisDate, activeFactors);
        BigDecimal correctedPrevious15 = correctedSales(
                facts,
                analysisDate.minusDays(29),
                analysisDate.minusDays(15),
                activeFactors
        );
        BigDecimal correctedRecent30 = correctedSales(facts, analysisDate.minusDays(29), analysisDate, activeFactors);
        BigDecimal correctedPrevious30 = correctedSales(
                facts,
                analysisDate.minusDays(59),
                analysisDate.minusDays(30),
                activeFactors
        );
        BigDecimal correctedRecent60 = correctedSales(facts, analysisDate.minusDays(59), analysisDate, activeFactors);
        BigDecimal correctedHistoricalT38ToT8 = correctedSales(
                facts,
                analysisDate.minusDays(38),
                analysisDate.minusDays(8),
                activeFactors
        );
        BigDecimal correctedGrowth15 = growthRate(correctedRecent15, correctedPrevious15);
        BigDecimal correctedGrowth30 = growthRate(correctedRecent30, correctedPrevious30);
        List<String> factorNames = factorNames(activeFactors);
        return new ProductLifecycleCorrectedFeatureSnapshot(
                rawSnapshot,
                correctedRecent7,
                correctedRecent15,
                correctedPrevious15,
                correctedRecent30,
                correctedPrevious30,
                correctedRecent60,
                correctedHistoricalT38ToT8,
                correctedGrowth15,
                correctedGrowth30,
                factorNames,
                evidenceJson(
                        correctedRecent7,
                        correctedRecent15,
                        correctedPrevious15,
                        correctedRecent30,
                        correctedPrevious30,
                        correctedHistoricalT38ToT8,
                        correctedGrowth15,
                        correctedGrowth30,
                        activeFactors
                )
        );
    }

    private List<SalesActivityWindowRecord> activeFactors(
            ProductLifecycleStateQuery query,
            LocalDate analysisDate,
            List<SalesActivityWindowRecord> activityWindows
    ) {
        if (activityWindows == null || activityWindows.isEmpty()) {
            return List.of();
        }
        LocalDate from = analysisDate.minusDays(59);
        List<SalesActivityWindowRecord> active = new ArrayList<>();
        for (SalesActivityWindowRecord window : activityWindows) {
            if (window == null || !window.isEnabled() || window.getFactor() == null) {
                continue;
            }
            if (window.getFactor().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (!query.getOwnerUserId().equals(window.getOwnerUserId())
                    || !query.getStoreCode().equals(window.getStoreCode())
                    || !query.getSiteCode().equals(window.getSiteCode())) {
                continue;
            }
            if (window.getDateFrom() == null || window.getDateTo() == null) {
                continue;
            }
            if (window.getDateTo().isBefore(from) || window.getDateFrom().isAfter(analysisDate)) {
                continue;
            }
            active.add(window);
        }
        active.sort(Comparator
                .comparing(SalesActivityWindowRecord::getDateFrom)
                .thenComparing(SalesActivityWindowRecord::getName, Comparator.nullsLast(String::compareTo))
                .thenComparing(record -> record.getId() == null ? Long.MAX_VALUE : record.getId()));
        return active;
    }

    private BigDecimal correctedSales(
            List<DailySalesFact> facts,
            LocalDate from,
            LocalDate to,
            List<SalesActivityWindowRecord> activeFactors
    ) {
        BigDecimal total = BigDecimal.ZERO;
        if (facts == null || facts.isEmpty()) {
            return total.setScale(4, RoundingMode.HALF_UP);
        }
        for (DailySalesFact fact : facts) {
            if (fact == null || fact.getFactDate() == null) {
                continue;
            }
            LocalDate date = fact.getFactDate();
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            BigDecimal factor = factorForDate(activeFactors, date);
            BigDecimal corrected = BigDecimal.valueOf(fact.getNetUnits())
                    .divide(factor, 8, RoundingMode.HALF_UP);
            total = total.add(corrected);
        }
        return total.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal factorForDate(List<SalesActivityWindowRecord> activeFactors, LocalDate date) {
        BigDecimal factor = BigDecimal.ONE;
        for (SalesActivityWindowRecord window : activeFactors) {
            if (!date.isBefore(window.getDateFrom()) && !date.isAfter(window.getDateTo())) {
                factor = factor.multiply(window.getFactor());
            }
        }
        return factor;
    }

    private BigDecimal growthRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 6, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private List<String> factorNames(List<SalesActivityWindowRecord> activeFactors) {
        Set<String> names = new LinkedHashSet<>();
        for (SalesActivityWindowRecord window : activeFactors) {
            if (window.getName() != null && !window.getName().isBlank()) {
                names.add(window.getName());
            }
        }
        return new ArrayList<>(names);
    }

    private String evidenceJson(
            BigDecimal correctedRecent7,
            BigDecimal correctedRecent15,
            BigDecimal correctedPrevious15,
            BigDecimal correctedRecent30,
            BigDecimal correctedPrevious30,
            BigDecimal correctedHistoricalT38ToT8,
            BigDecimal correctedGrowth15,
            BigDecimal correctedGrowth30,
            List<SalesActivityWindowRecord> activeFactors
    ) {
        if (activeFactors.isEmpty()) {
            return "{"
                    + "\"activityCorrection\":\"no_activity_factor\","
                    + "\"correctedRecent7Sales\":" + correctedRecent7 + ","
                    + "\"correctedRecent15Sales\":" + correctedRecent15 + ","
                    + "\"correctedPrevious15Sales\":" + correctedPrevious15 + ","
                    + "\"correctedRecent30Sales\":" + correctedRecent30 + ","
                    + "\"correctedPrevious30Sales\":" + correctedPrevious30 + ","
                    + "\"correctedHistoricalT38ToT8Sales\":" + correctedHistoricalT38ToT8 + ","
                    + "\"correctedSalesGrowth15\":" + jsonDecimal(correctedGrowth15) + ","
                    + "\"correctedSalesGrowth30\":" + jsonDecimal(correctedGrowth30)
                    + "}";
        }
        return "{"
                + "\"activityCorrection\":\"activity_factor_applied\","
                + "\"correctedRecent7Sales\":" + correctedRecent7 + ","
                + "\"correctedRecent15Sales\":" + correctedRecent15 + ","
                + "\"correctedPrevious15Sales\":" + correctedPrevious15 + ","
                + "\"correctedRecent30Sales\":" + correctedRecent30 + ","
                + "\"correctedPrevious30Sales\":" + correctedPrevious30 + ","
                + "\"correctedHistoricalT38ToT8Sales\":" + correctedHistoricalT38ToT8 + ","
                + "\"correctedSalesGrowth15\":" + jsonDecimal(correctedGrowth15) + ","
                + "\"correctedSalesGrowth30\":" + jsonDecimal(correctedGrowth30) + ","
                + "\"appliedFactors\":" + appliedFactorsJson(activeFactors)
                + "}";
    }

    private String jsonDecimal(BigDecimal value) {
        return value == null ? "null" : value.toPlainString();
    }

    private String appliedFactorsJson(List<SalesActivityWindowRecord> activeFactors) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < activeFactors.size(); i++) {
            SalesActivityWindowRecord factor = activeFactors.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append("\"name\":\"").append(escape(factor.getName())).append("\",")
                    .append("\"type\":\"").append(escape(factor.getActivityType())).append("\",")
                    .append("\"factor\":").append(factor.getFactor().toPlainString()).append(',')
                    .append("\"dateFrom\":\"").append(factor.getDateFrom()).append("\",")
                    .append("\"dateTo\":\"").append(factor.getDateTo()).append("\"");
            if (factor.getOperationsConfigBundleVersionId() != null) {
                builder.append(',')
                        .append("\"bundleVersionId\":").append(factor.getOperationsConfigBundleVersionId()).append(',')
                        .append("\"bundleVersionNo\":\"").append(escape(factor.getOperationsConfigBundleVersionNo())).append("\",")
                        .append("\"bundleSourceRole\":\"").append(escape(factor.getOperationsConfigSourceRole())).append("\",")
                        .append("\"bundleSourceLabel\":\"").append(escape(factor.getOperationsConfigSourceLabel())).append("\"");
            }
            builder
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
