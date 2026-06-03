package com.nuono.next.operationsconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OperationConfigTypedVersionContentSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})\\s*(?:~|至|到)\\s*(\\d{4}-\\d{2}-\\d{2})"
    );
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("(?<![\\d.])-?\\d+\\.\\d+(?![\\d.])");

    private OperationConfigTypedVersionContentSupport() {
    }

    static Optional<OperationConfigTypedVersion> resolveEffectiveVersion(
            OperationConfigTypedVersionRepository repository,
            OperationConfigVersionType configType,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        if (repository == null || configType == null) {
            return Optional.empty();
        }
        String exactScope = scopeSummary(ownerUserId, storeCode, siteCode);
        OperationConfigTypedVersion exact = null;
        OperationConfigTypedVersion global = null;
        OperationConfigTypedVersion systemDefault = null;
        String defaultVersionNo = defaultVersionNo(configType);
        for (OperationConfigTypedVersion version : repository.listVersions()) {
            if (!configType.name().equals(version.getConfigType())) {
                continue;
            }
            if ("CURRENT".equals(version.getStatus())) {
                if (exactScope != null && exactScope.equals(version.getScopeSummary())) {
                    exact = later(exact, version);
                } else if ("全局当前".equals(version.getScopeSummary())) {
                    global = later(global, version);
                }
            } else if ("SYSTEM_DEFAULT".equals(version.getStatus())
                    && defaultVersionNo.equals(version.getVersionNo())) {
                systemDefault = later(systemDefault, version);
            }
        }
        if (exact != null) {
            return Optional.of(exact);
        }
        if (global != null) {
            return Optional.of(global);
        }
        return Optional.ofNullable(systemDefault);
    }

    static OperationConfigTypedVersion later(OperationConfigTypedVersion left, OperationConfigTypedVersion right) {
        if (left == null) {
            return right;
        }
        Comparator<OperationConfigTypedVersion> comparator = Comparator
                .comparing(OperationConfigTypedVersion::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(OperationConfigTypedVersion::getId, Comparator.nullsFirst(Long::compareTo));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    static String scopeSummary(Long ownerUserId, String storeCode, String siteCode) {
        if (ownerUserId == null || !hasText(storeCode) || !hasText(siteCode)) {
            return null;
        }
        return ownerUserId + "/" + storeCode.trim().toUpperCase(Locale.ROOT) + "/" + siteCode.trim().toUpperCase(Locale.ROOT);
    }

    static List<OperationConfigDefaultVersionItemView> parseItems(String contentJson) {
        if (!hasText(contentJson)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<OperationConfigDefaultVersionItemView> items = new ArrayList<>();
            for (JsonNode item : root) {
                items.add(new OperationConfigDefaultVersionItemView(
                        textValue(item, "groupName"),
                        textValue(item, "itemName"),
                        textValue(item, "cadence"),
                        textValue(item, "valueType"),
                        textValue(item, "defaultValue"),
                        textValue(item, "resultShape"),
                        textValue(item, "note")
                ));
            }
            return List.copyOf(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("operation config typed version content parsing failed", exception);
        }
    }

    static OperationLifecycleRuleThresholds lifecycleThresholds(OperationConfigTypedVersion version) {
        OperationLifecycleRuleThresholds defaults = OperationLifecycleRuleThresholds.defaultV1();
        Integer newMaxAgeDays = defaults.getNewMaxAgeDays();
        Integer newMinAgeDays = defaults.getNewMinAgeDays();
        BigDecimal highPriceThreshold = defaults.getHighPriceThreshold();
        BigDecimal growthMinSalesGrowthRate = defaults.getGrowthMinSalesGrowthRate();
        BigDecimal growthMinPvGrowthRate = defaults.getGrowthMinPvGrowthRate();
        BigDecimal growthMinMonthlySales = defaults.getGrowthMinMonthlySales();
        Integer growthMinActiveSalesDays = defaults.getGrowthMinActiveSalesDays();
        BigDecimal growthMaxVolatility = defaults.getGrowthMaxVolatility();
        BigDecimal stableMinPvGrowthRate = defaults.getStableMinPvGrowthRate();
        BigDecimal stableVolatilityMin = defaults.getStableVolatilityMin();
        BigDecimal stableVolatilityMax = defaults.getStableVolatilityMax();
        BigDecimal declineMaxVolatility = defaults.getDeclineMaxVolatility();
        BigDecimal declineMaxSalesGrowthRate = defaults.getDeclineMaxSalesGrowthRate();
        BigDecimal longTailMaxVolatility = defaults.getLongTailMaxVolatility();
        BigDecimal longTailMaxMonthlySales = defaults.getLongTailMaxMonthlySales();
        BigDecimal explosiveInertiaFactor = defaults.getExplosiveInertiaFactor();
        BigDecimal steadyTrendFactor = defaults.getSteadyTrendFactor();
        BigDecimal stepGrowthMultiplier = defaults.getStepGrowthMultiplier();
        BigDecimal volatileOutlierTrimRatio = defaults.getVolatileOutlierTrimRatio();
        BigDecimal volatileMomentumThreshold = defaults.getVolatileMomentumThreshold();
        BigDecimal declineDecayRatioThreshold = defaults.getDeclineDecayRatioThreshold();
        BigDecimal stableRisingShortWeight = defaults.getStableRisingShortWeight();
        BigDecimal stableFallingShortWeight = defaults.getStableFallingShortWeight();

        for (OperationConfigDefaultVersionItemView item : parseItems(version.getContentJson())) {
            String name = item.getItemName() == null ? "" : item.getItemName();
            if (name.contains("稳定期波动率范围")) {
                List<BigDecimal> range = decimals(item.getDefaultValue());
                if (range.size() >= 2) {
                    stableVolatilityMin = range.get(0);
                    stableVolatilityMax = range.get(1);
                }
                continue;
            }
            BigDecimal decimal = firstDecimal(item.getDefaultValue()).orElse(null);
            if (decimal == null) {
                continue;
            }
            if (name.contains("新品期最长周期")) {
                newMaxAgeDays = decimal.intValue();
            } else if (name.contains("新品期最小周期")) {
                newMinAgeDays = decimal.intValue();
            } else if (name.contains("高客单价阈值")) {
                highPriceThreshold = decimal;
            } else if (name.contains("成长期最小销量环比增长率")) {
                growthMinSalesGrowthRate = decimal;
            } else if (name.contains("成长期最小浏览环比增长率")) {
                growthMinPvGrowthRate = decimal;
            } else if (name.contains("成长期最小月销量")) {
                growthMinMonthlySales = decimal;
            } else if (name.contains("成长期最小月动销天数")) {
                growthMinActiveSalesDays = decimal.intValue();
            } else if (name.contains("成长期最大波动率")) {
                growthMaxVolatility = decimal;
            } else if (name.contains("稳定期最小浏览环比增长率")) {
                stableMinPvGrowthRate = decimal;
            } else if (name.contains("衰退期最大波动率")) {
                declineMaxVolatility = decimal;
            } else if (name.contains("衰退最小销量环比增长率")) {
                declineMaxSalesGrowthRate = decimal;
            } else if (name.contains("长尾期最大波动率")) {
                longTailMaxVolatility = decimal;
            } else if (name.contains("长尾期最大月销")) {
                longTailMaxMonthlySales = decimal;
            } else if (name.contains("爆发惯性系数")) {
                explosiveInertiaFactor = decimal;
            } else if (name.contains("稳健系数")) {
                steadyTrendFactor = decimal;
            } else if (name.contains("阶梯增长倍数")) {
                stepGrowthMultiplier = decimal;
            } else if (name.contains("波动去极值比例")) {
                volatileOutlierTrimRatio = decimal;
            } else if (name.contains("波动增长动量阈值")) {
                volatileMomentumThreshold = decimal;
            } else if (name.contains("衰退比例阈值")) {
                declineDecayRatioThreshold = decimal;
            } else if (name.contains("成熟期上升短期权重")) {
                stableRisingShortWeight = decimal;
            } else if (name.contains("成熟期下滑短期权重")) {
                stableFallingShortWeight = decimal;
            }
        }
        return new OperationLifecycleRuleThresholds(
                newMaxAgeDays,
                newMinAgeDays,
                highPriceThreshold,
                growthMinSalesGrowthRate,
                growthMinPvGrowthRate,
                growthMinMonthlySales,
                growthMinActiveSalesDays,
                growthMaxVolatility,
                stableMinPvGrowthRate,
                stableVolatilityMin,
                stableVolatilityMax,
                declineMaxVolatility,
                declineMaxSalesGrowthRate,
                longTailMaxVolatility,
                longTailMaxMonthlySales,
                explosiveInertiaFactor,
                steadyTrendFactor,
                stepGrowthMultiplier,
                volatileOutlierTrimRatio,
                volatileMomentumThreshold,
                declineDecayRatioThreshold,
                stableRisingShortWeight,
                stableFallingShortWeight
        );
    }

    static List<OperationCalendarRule> calendarRules(
            OperationConfigTypedVersion version,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate queryDate
    ) {
        List<OperationCalendarRule> rules = new ArrayList<>();
        List<OperationConfigDefaultVersionItemView> items = parseItems(version.getContentJson());
        for (int index = 0; index < items.size(); index++) {
            OperationConfigDefaultVersionItemView item = items.get(index);
            Optional<BigDecimal> factor = firstDecimal(item.getDefaultValue());
            if (factor.isEmpty()) {
                continue;
            }
            LocalDate[] range = dateRange(item.getDefaultValue(), queryDate);
            TargetScope target = targetScope(item.getResultShape());
            rules.add(new OperationCalendarRule(
                    -1L - index,
                    ownerUserId,
                    storeCode,
                    siteCode,
                    item.getItemName(),
                    item.getGroupName() == null ? "typed_version" : item.getGroupName(),
                    range[0],
                    range[1],
                    null,
                    target.type,
                    target.value,
                    factor.get(),
                    "typed_version",
                    true,
                    null,
                    OperationConfigPublishStatus.PUBLISHED,
                    "typed_version",
                    version.getDisplayName(),
                    version.getCreatedBy(),
                    version.getUpdatedBy(),
                    version.getCreatedAt() == null ? LocalDateTime.now() : version.getCreatedAt(),
                    version.getUpdatedAt() == null ? LocalDateTime.now() : version.getUpdatedAt()
            ));
        }
        return List.copyOf(rules);
    }

    private static Optional<BigDecimal> firstDecimal(String value) {
        List<BigDecimal> values = decimals(value);
        if (!values.isEmpty()) {
            return Optional.of(values.get(values.size() - 1));
        }
        if (value != null && value.trim().matches("-?\\d+")) {
            return Optional.of(new BigDecimal(value.trim()));
        }
        return Optional.empty();
    }

    private static List<BigDecimal> decimals(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(value);
        List<BigDecimal> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(new BigDecimal(matcher.group()));
        }
        return values;
    }

    private static LocalDate[] dateRange(String value, LocalDate queryDate) {
        if (hasText(value)) {
            Matcher matcher = DATE_RANGE_PATTERN.matcher(value);
            if (matcher.find()) {
                return new LocalDate[] {
                        LocalDate.parse(matcher.group(1)),
                        LocalDate.parse(matcher.group(2))
                };
            }
        }
        return new LocalDate[] { queryDate, queryDate };
    }

    private static TargetScope targetScope(String resultShape) {
        if (!hasText(resultShape)) {
            return new TargetScope("all_products", null);
        }
        String normalized = resultShape.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (separator > 0) {
            String type = normalized.substring(0, separator).trim();
            String value = normalized.substring(separator + 1).trim();
            return new TargetScope(type, value.isEmpty() ? null : value);
        }
        if (lower.contains("brand")) {
            return new TargetScope("brand", null);
        }
        if (lower.contains("fulltype")) {
            return new TargetScope("product_fulltype", null);
        }
        if (lower.contains("category")) {
            return new TargetScope("category", null);
        }
        if (lower.contains("psku")) {
            return new TargetScope("psku", null);
        }
        return new TargetScope("all_products", null);
    }

    private static String defaultVersionNo(OperationConfigVersionType configType) {
        return OperationConfigVersionType.PRODUCT_LIFECYCLE.equals(configType)
                ? OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO
                : OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO;
    }

    private static String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class TargetScope {
        private final String type;
        private final String value;

        private TargetScope(String type, String value) {
            this.type = type;
            this.value = value;
        }
    }
}
