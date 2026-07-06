package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OperationBusinessCalendarFactorResolver {

    private final OperationConfigTypedVersionRepository typedVersionRepository;

    public OperationBusinessCalendarFactorResolver(OperationConfigTypedVersionRepository typedVersionRepository) {
        this.typedVersionRepository = typedVersionRepository;
    }

    public BigDecimal averageFactor(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate baseDate,
            int forecastDays,
            ProductScope productScope
    ) {
        return explainFactors(ownerUserId, storeCode, siteCode, baseDate, forecastDays, productScope)
                .averageFactor(forecastDays);
    }

    public CalendarFactorExplanation explainFactors(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate baseDate,
            int forecastDays,
            ProductScope productScope
    ) {
        if (forecastDays <= 0 || baseDate == null) {
            return CalendarFactorExplanation.empty();
        }
        LocalDate firstForecastDate = baseDate.plusDays(1);
        return explainFactorsForDateRange(
                ownerUserId,
                storeCode,
                siteCode,
                firstForecastDate,
                firstForecastDate.plusDays(forecastDays - 1L),
                productScope
        );
    }

    public CalendarFactorExplanation explainFactorsForDateRange(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            ProductScope productScope
    ) {
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            return CalendarFactorExplanation.empty();
        }
        Optional<OperationConfigTypedVersion> version = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                typedVersionRepository,
                OperationConfigVersionType.BUSINESS_CALENDAR,
                ownerUserId,
                storeCode,
                siteCode
        );
        if (version.isEmpty()) {
            return CalendarFactorExplanation.neutral(daysBetweenInclusive(dateFrom, dateTo));
        }
        List<OperationCalendarRule> rules = OperationConfigTypedVersionContentSupport.calendarRules(
                version.get(),
                ownerUserId,
                storeCode,
                siteCode,
                dateFrom
        );
        if (rules.isEmpty()) {
            return CalendarFactorExplanation.neutral(daysBetweenInclusive(dateFrom, dateTo));
        }
        List<BigDecimal> dailyFactors = new java.util.ArrayList<>();
        Map<String, MutableCalendarFactorImpact> impactsByRule = new LinkedHashMap<>();
        int dayOffset = 1;
        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            ScoredFactor factor = factorForDate(date, siteCode, productScope, rules);
            dailyFactors.add(factor.factor);
            if (factor.rule != null && BigDecimal.ONE.compareTo(factor.factor) != 0) {
                String key = calendarImpactKey(factor.rule);
                MutableCalendarFactorImpact impact = impactsByRule.computeIfAbsent(key, ignored ->
                        new MutableCalendarFactorImpact(
                                factor.rule,
                                factor.priority,
                                scopeLabel(factor.priority),
                                factor.factor
                        )
                );
                impact.addDate(date, dayOffset);
            }
            dayOffset++;
        }
        List<CalendarFactorImpact> impacts = impactsByRule.values().stream()
                .map(MutableCalendarFactorImpact::toView)
                .collect(Collectors.toList());
        return new CalendarFactorExplanation(dailyFactors, impacts);
    }

    private ScoredFactor factorForDate(
            LocalDate date,
            String siteCode,
            ProductScope productScope,
            List<OperationCalendarRule> rules
    ) {
        ScoredFactor best = ScoredFactor.neutral();
        for (OperationCalendarRule rule : rules) {
            if (!rule.isEnabled()
                    || rule.getFactorValue() == null
                    || date.isBefore(rule.getDateFrom())
                    || date.isAfter(rule.getDateTo())) {
                continue;
            }
            int priority = scopePriority(rule, siteCode, productScope);
            if (priority <= 0) {
                continue;
            }
            ScoredFactor scored = new ScoredFactor(priority, rule.getFactorValue(), rule);
            if (isBetterFactor(scored, best)) {
                best = scored;
            }
        }
        return best;
    }

    private boolean isBetterFactor(ScoredFactor candidate, ScoredFactor current) {
        int impactCompare = impactStrength(candidate.factor).compareTo(impactStrength(current.factor));
        if (impactCompare > 0) {
            return true;
        }
        if (impactCompare < 0) {
            return false;
        }
        return candidate.priority > current.priority;
    }

    private BigDecimal impactStrength(BigDecimal factor) {
        if (factor == null) {
            return BigDecimal.ZERO;
        }
        return factor.subtract(BigDecimal.ONE).abs();
    }

    private int daysBetweenInclusive(LocalDate dateFrom, LocalDate dateTo) {
        return (int) (java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1L);
    }

    private String calendarImpactKey(OperationCalendarRule rule) {
        return rule.getRuleName()
                + "|" + rule.getDateFrom()
                + "|" + rule.getDateTo()
                + "|" + rule.getTargetScopeType()
                + "|" + rule.getTargetScopeValue()
                + "|" + rule.getFactorValue();
    }

    private String scopeLabel(int priority) {
        if (priority >= 80) return "站点+大类目";
        if (priority >= 70) return "站点+后台类目";
        if (priority >= 65) return "站点+品牌";
        if (priority >= 60) return "大类目";
        if (priority >= 50) return "后台类目";
        if (priority >= 40) return "品牌";
        if (priority >= 30) return "站点全品";
        if (priority >= 10) return "全品";
        return "未命中";
    }

    private int scopePriority(OperationCalendarRule rule, String siteCode, ProductScope productScope) {
        String type = normalized(rule.getTargetScopeType());
        String value = normalized(rule.getTargetScopeValue());
        ProductScope safeScope = productScope == null ? ProductScope.empty(siteCode) : productScope;
        if (type.isEmpty() || "all_products".equals(type)) {
            return 10;
        }
        if ("site".equals(type)) {
            return siteScopePriority(value, siteCode, safeScope);
        }
        if ("family".equals(type) && equalsNormalized(value, safeScope.productFamily)) {
            return 60;
        }
        if (("category".equals(type) || "product_fulltype".equals(type))
                && equalsNormalized(value, safeScope.productFulltype)) {
            return 50;
        }
        if ("brand".equals(type) && equalsNormalized(value, safeScope.brand)) {
            return 40;
        }
        return 0;
    }

    private int siteScopePriority(String value, String siteCode, ProductScope productScope) {
        if (value.isEmpty()) {
            return 0;
        }
        String[] parts = value.split("\\|");
        if (parts.length == 0 || !equalsNormalized(parts[0], siteCode)) {
            return 0;
        }
        if (parts.length == 1) {
            return 30;
        }
        int best = 0;
        for (int index = 1; index < parts.length; index++) {
            String segment = parts[index];
            int separator = segment.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String dimensionType = normalized(segment.substring(0, separator));
            String dimensionValue = normalized(segment.substring(separator + 1));
            if ("family".equals(dimensionType) && equalsNormalized(dimensionValue, productScope.productFamily)) {
                best = Math.max(best, 80);
            } else if (("category".equals(dimensionType) || "product_fulltype".equals(dimensionType))
                    && equalsNormalized(dimensionValue, productScope.productFulltype)) {
                best = Math.max(best, 70);
            } else if ("brand".equals(dimensionType) && equalsNormalized(dimensionValue, productScope.brand)) {
                best = Math.max(best, 65);
            }
        }
        return best;
    }

    private boolean equalsNormalized(String left, String right) {
        return !normalized(left).isEmpty() && normalized(left).equals(normalized(right));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static class ScoredFactor {
        private final int priority;
        private final BigDecimal factor;
        private final OperationCalendarRule rule;

        private ScoredFactor(int priority, BigDecimal factor, OperationCalendarRule rule) {
            this.priority = priority;
            this.factor = factor;
            this.rule = rule;
        }

        private static ScoredFactor neutral() {
            return new ScoredFactor(0, BigDecimal.ONE, null);
        }
    }

    public static class CalendarFactorExplanation {
        private final List<BigDecimal> dailyFactors;
        private final List<CalendarFactorImpact> impacts;

        private CalendarFactorExplanation(List<BigDecimal> dailyFactors, List<CalendarFactorImpact> impacts) {
            this.dailyFactors = dailyFactors == null ? List.of() : List.copyOf(dailyFactors);
            this.impacts = impacts == null ? List.of() : List.copyOf(impacts);
        }

        private static CalendarFactorExplanation empty() {
            return new CalendarFactorExplanation(List.of(), List.of());
        }

        public static CalendarFactorExplanation neutral(int forecastDays) {
            return new CalendarFactorExplanation(
                    java.util.Collections.nCopies(Math.max(0, forecastDays), BigDecimal.ONE),
                    List.of()
            );
        }

        public BigDecimal averageFactor(int forecastDays) {
            if (forecastDays <= 0 || dailyFactors.isEmpty()) {
                return BigDecimal.ONE;
            }
            int days = Math.min(forecastDays, dailyFactors.size());
            BigDecimal total = BigDecimal.ZERO;
            for (int index = 0; index < days; index++) {
                total = total.add(dailyFactors.get(index));
            }
            return total.divide(BigDecimal.valueOf(days), 8, RoundingMode.HALF_UP);
        }

        public List<CalendarFactorImpact> getImpacts() {
            return impacts;
        }

        public List<BigDecimal> getDailyFactors() {
            return dailyFactors;
        }
    }

    public static class CalendarFactorImpact {
        private final String ruleName;
        private final String activityType;
        private final LocalDate dateFrom;
        private final LocalDate dateTo;
        private final String targetScopeType;
        private final String targetScopeValue;
        private final BigDecimal factorValue;
        private final String matchedScopeLabel;
        private final int affectedDays30;
        private final int affectedDays60;
        private final int affectedDays90;

        private CalendarFactorImpact(
                String ruleName,
                String activityType,
                LocalDate dateFrom,
                LocalDate dateTo,
                String targetScopeType,
                String targetScopeValue,
                BigDecimal factorValue,
                String matchedScopeLabel,
                int affectedDays30,
                int affectedDays60,
                int affectedDays90
        ) {
            this.ruleName = ruleName;
            this.activityType = activityType;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.targetScopeType = targetScopeType;
            this.targetScopeValue = targetScopeValue;
            this.factorValue = factorValue;
            this.matchedScopeLabel = matchedScopeLabel;
            this.affectedDays30 = affectedDays30;
            this.affectedDays60 = affectedDays60;
            this.affectedDays90 = affectedDays90;
        }

        public String getRuleName() {
            return ruleName;
        }

        public String getActivityType() {
            return activityType;
        }

        public LocalDate getDateFrom() {
            return dateFrom;
        }

        public LocalDate getDateTo() {
            return dateTo;
        }

        public String getTargetScopeType() {
            return targetScopeType;
        }

        public String getTargetScopeValue() {
            return targetScopeValue;
        }

        public BigDecimal getFactorValue() {
            return factorValue;
        }

        public String getMatchedScopeLabel() {
            return matchedScopeLabel;
        }

        public int getAffectedDays30() {
            return affectedDays30;
        }

        public int getAffectedDays60() {
            return affectedDays60;
        }

        public int getAffectedDays90() {
            return affectedDays90;
        }
    }

    private static class MutableCalendarFactorImpact {
        private final OperationCalendarRule rule;
        private final int priority;
        private final String matchedScopeLabel;
        private final BigDecimal factor;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private int affectedDays30;
        private int affectedDays60;
        private int affectedDays90;

        private MutableCalendarFactorImpact(
                OperationCalendarRule rule,
                int priority,
                String matchedScopeLabel,
                BigDecimal factor
        ) {
            this.rule = rule;
            this.priority = priority;
            this.matchedScopeLabel = matchedScopeLabel;
            this.factor = factor;
        }

        private void addDate(LocalDate date, int dayOffset) {
            if (dateFrom == null || date.isBefore(dateFrom)) {
                dateFrom = date;
            }
            if (dateTo == null || date.isAfter(dateTo)) {
                dateTo = date;
            }
            if (dayOffset <= 30) {
                affectedDays30++;
            }
            if (dayOffset <= 60) {
                affectedDays60++;
            }
            if (dayOffset <= 90) {
                affectedDays90++;
            }
        }

        private CalendarFactorImpact toView() {
            return new CalendarFactorImpact(
                    rule.getRuleName(),
                    rule.getActivityType(),
                    dateFrom,
                    dateTo,
                    rule.getTargetScopeType(),
                    rule.getTargetScopeValue(),
                    factor,
                    matchedScopeLabel,
                    affectedDays30,
                    affectedDays60,
                    affectedDays90
            );
        }
    }

    public static class ProductScope {
        private final String siteCode;
        private final String brand;
        private final String productFulltype;
        private final String productFamily;

        public ProductScope(String siteCode, String brand, String productFulltype, String productFamily) {
            this.siteCode = siteCode;
            this.brand = brand;
            this.productFulltype = productFulltype;
            this.productFamily = productFamily;
        }

        private static ProductScope empty(String siteCode) {
            return new ProductScope(siteCode, null, null, null);
        }

        public String getSiteCode() {
            return siteCode;
        }

        public String getBrand() {
            return brand;
        }

        public String getProductFulltype() {
            return productFulltype;
        }

        public String getProductFamily() {
            return productFamily;
        }
    }
}
