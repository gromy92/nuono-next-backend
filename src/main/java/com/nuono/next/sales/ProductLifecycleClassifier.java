package com.nuono.next.sales;

import com.nuono.next.operationsconfig.OperationLifecycleRuleThresholds;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleClassifier {

    public static final String DEFAULT_RULE_VERSION = ProductLifecycleResult.DEFAULT_RULE_VERSION;

    private static final BigDecimal GROWTH_SALES_RATE = new BigDecimal("0.3000");
    private static final BigDecimal LOW_VOLUME_MONTHLY_SALES = new BigDecimal("3.0000");
    private static final BigDecimal MIN_SIGNAL_MONTHLY_SALES = new BigDecimal("10.0000");
    private static final BigDecimal MIN_PV_RECOVERY_SALES = new BigDecimal("20.0000");
    private static final BigDecimal STEP_GROWTH_MULTIPLIER = new BigDecimal("2.0000");
    private static final BigDecimal VOLATILE_MOMENTUM_THRESHOLD = new BigDecimal("0.1000");
    private static final BigDecimal DECLINE_DECAY_RATIO_THRESHOLD = new BigDecimal("0.8000");
    private static final BigDecimal STABLE_RISING_SHORT_WEIGHT = new BigDecimal("0.7000");
    private static final BigDecimal STABLE_FALLING_SHORT_WEIGHT = new BigDecimal("0.6000");
    private static final BigDecimal STABLE_RISING_RATIO_THRESHOLD = new BigDecimal("0.9500");
    private static final BigDecimal VOLATILE_OUTLIER_TRIM_RATIO = new BigDecimal("0.1000");
    private static final int MIN_ACTIVE_SALES_DAYS = 7;
    private static final int MIN_OBSERVED_DAYS = 15;
    private static final int MIN_VOLATILE_VALID_POINTS = 10;

    public ProductLifecycleResult classify(ProductLifecycleSignal signal) {
        LocalDate analysisDate = signal.getAnalysisDate();
        int recent30Units = unitsBetween(signal.getFacts(), analysisDate.minusDays(29), analysisDate);
        boolean stockoutDistortion = Integer.valueOf(0).equals(signal.getAvailableStock()) && recent30Units > 0;
        List<String> warnings = stockoutDistortion
                ? List.of("possible_stockout_distortion")
                : List.of();

        if (signal.getFirstListedDate() != null
                && ChronoUnit.DAYS.between(signal.getFirstListedDate(), analysisDate) <= 30) {
            return result("new", "新品", "首次上架或首次可售在 30 天内。", warnings);
        }

        if (usableFactDateCount(signal.getFacts()) < 7) {
            return result("data_insufficient", "数据不足", "DEFAULT_V1 至少需要 7 个可用销量事实日期。", warnings);
        }

        int recent14Units = unitsBetween(signal.getFacts(), analysisDate.minusDays(13), analysisDate);
        int previous14Units = unitsBetween(signal.getFacts(), analysisDate.minusDays(27), analysisDate.minusDays(14));

        if (recent30Units <= 2) {
            return result("longTail", "长尾期", "近 30 天销量很低，按长尾期商品处理。", warnings);
        }
        if (previous14Units >= 5 && recent14Units <= previous14Units * 0.7) {
            if (stockoutDistortion) {
                return result("stable", "稳定", "销量下降同时出现零库存，先标记为库存失真而非自然衰退。", warnings);
            }
            return result("decline", "衰退", "近 14 天销量明显低于前 14 天。", warnings);
        }
        if (recent14Units >= 5 && recent14Units >= previous14Units * 1.3) {
            return result("growth", "增长", "近 14 天销量明显高于前 14 天。", warnings);
        }
        return result("stable", "稳定", "近 28 天销量波动处于稳定区间。", warnings);
    }

    public ProductLifecycleResult classify(ProductLifecycleClassificationInput input) {
        return classify(input, ProductLifecycleRuleSet.defaultV1());
    }

    public ProductLifecycleResult classify(ProductLifecycleClassificationInput input, ProductLifecycleRuleSet ruleSet) {
        ProductLifecycleRuleSet resolvedRuleSet = ruleSet == null ? ProductLifecycleRuleSet.defaultV1() : ruleSet;
        OperationLifecycleRuleThresholds thresholds = resolvedRuleSet.getThresholds();
        ProductLifecycleListingDateResolution listing = input.getListingDateResolution();
        ProductLifecycleFeatureSnapshot features = input.getFeatureSnapshot();
        ProductLifecycleCorrectedFeatureSnapshot corrected = input.getCorrectedFeatureSnapshot();
        if (listing == null || listing.getListingDate() == null || "missing".equals(listing.getSource())) {
            return result(
                    "data_insufficient",
                    "数据不足",
                    "缺少可用上架时间来源，无法安全计算生命周期。",
                    resolvedRuleSet,
                    "data_insufficient",
                    "missing_listing_date",
                    null,
                    features,
                    corrected,
                    thresholds
            );
        }
        if (isNewByConfiguredWindow(input.getAnalysisDate(), listing, thresholds)) {
            return result(
                    "new",
                    "新品",
                    "上架时间处于运营配置的新品窗口内，且没有 60 天历史信号证明为老品。",
                    resolvedRuleSet,
                    "ready",
                    "new_listing_window",
                    null,
                    features,
                    corrected,
                    thresholds
            );
        }
        if (features.getRecent30().getObservedDays() < MIN_OBSERVED_DAYS) {
            return result(
                    "data_insufficient",
                    "数据不足",
                    "非新品判断至少需要 15 天可用观察窗口。",
                    resolvedRuleSet,
                    "data_insufficient",
                    "insufficient_observation_window",
                    null,
                    features,
                    corrected,
                    thresholds
            );
        }
        if (features.getQualityReasons().contains("pv_unresolvable")
                && corrected.getCorrectedRecent30Sales().compareTo(MIN_PV_RECOVERY_SALES) < 0) {
            return result(
                    "data_insufficient",
                    "数据不足",
                    "PV 缺失且无法用相邻窗口补算，当前销量证据不足以安全分类。",
                    resolvedRuleSet,
                    "data_insufficient",
                    "pv_unresolvable",
                    null,
                    features,
                    corrected,
                    thresholds
            );
        }
        if (isLongTail(corrected, features, thresholds)) {
            return result(
                    "longTail",
                    "长尾期",
                    "订正后近 30 天销量低且动销天数弱，低销量波动不单独驱动增长或衰退。",
                    resolvedRuleSet,
                    "ready",
                    "long_tail_formula",
                    null,
                    features,
                    corrected,
                    thresholds
            );
        }
        String growthShape = growthShape(corrected, features, thresholds);
        if (growthShape != null) {
            return result(
                    "growth",
                    "增长",
                    "订正后销量窗口命中成长期形态公式。",
                    resolvedRuleSet,
                    "ready",
                    "growth_shape_" + growthShape,
                    growthShape,
                    features,
                    corrected,
                    thresholds
            );
        }
        if (declinesByDecayRatio(corrected, features, thresholds)) {
            return result(
                    "decline",
                    "衰退",
                    "近 7 天日均销量相对历史窗口明显衰减。",
                    resolvedRuleSet,
                    "ready",
                    "decline_decay_ratio",
                    null,
                    features,
                    corrected,
                    thresholds
            );
        }
        return result(
                "stable",
                "稳定",
                "销量窗口充足，短期与长期均值融合后未达到增长、衰退或长尾阈值。",
                resolvedRuleSet,
                "ready",
                "stable_dynamic_weight",
                null,
                features,
                corrected,
                thresholds
        );
    }

    private ProductLifecycleResult result(String code, String label, String explanation, List<String> warnings) {
        return new ProductLifecycleResult(code, label, explanation, DEFAULT_RULE_VERSION, warnings);
    }

    private ProductLifecycleResult result(
            String code,
            String label,
            String explanation,
            ProductLifecycleRuleSet ruleSet,
            String qualityState,
            String reason,
            String growthShape,
            ProductLifecycleFeatureSnapshot features,
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            OperationLifecycleRuleThresholds thresholds
    ) {
        return new ProductLifecycleResult(
                code,
                label,
                explanation,
                ruleVersion(ruleSet),
                List.of(),
                qualityState,
                evidenceJson(reason, growthShape, features, corrected, ruleSet, thresholds)
        );
    }

    private String ruleVersion(ProductLifecycleRuleSet ruleSet) {
        if (ruleSet == null || ruleSet.getRuleVersion() == null || ruleSet.getRuleVersion().isBlank()) {
            return DEFAULT_RULE_VERSION;
        }
        return ruleSet.getRuleVersion();
    }

    private boolean isLongTail(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        return corrected.getCorrectedRecent30Sales().compareTo(decimalOrDefault(
                        thresholds.getLongTailMaxMonthlySales(),
                        LOW_VOLUME_MONTHLY_SALES
                )) <= 0
                || features.getActiveSalesDays30() <= 2;
    }

    private boolean hasEnoughGrowthGuard(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        return corrected.getCorrectedRecent30Sales().compareTo(decimalOrDefault(
                        thresholds.getGrowthMinMonthlySales(),
                        MIN_SIGNAL_MONTHLY_SALES
                )) >= 0
                || features.getActiveSalesDays30() >= intOrDefault(
                        thresholds.getGrowthMinActiveSalesDays(),
                        MIN_ACTIVE_SALES_DAYS
                );
    }

    private String growthShape(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        if (!hasEnoughGrowthGuard(corrected, features, thresholds)) {
            return null;
        }
        if (isStepGrowth(corrected, thresholds)) {
            return "step";
        }
        if (isExplosiveGrowth(corrected, thresholds)) {
            return "explosive";
        }
        if (isVolatileGrowth(features, thresholds)) {
            return "volatile";
        }
        if (isSteadyGrowth(corrected, features, thresholds)) {
            return "steady";
        }
        return null;
    }

    private boolean isStepGrowth(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal previous30Avg = dailyAverage(corrected.getCorrectedPrevious30Sales(), 30);
        if (previous30Avg.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal recent7Avg = dailyAverage(corrected.getCorrectedRecent7Sales(), 7);
        BigDecimal multiplier = decimalOrDefault(thresholds.getStepGrowthMultiplier(), STEP_GROWTH_MULTIPLIER);
        return recent7Avg.compareTo(previous30Avg.multiply(multiplier)) > 0;
    }

    private boolean isExplosiveGrowth(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal growth = corrected.getCorrectedSalesGrowth30();
        return growth != null && growth.compareTo(decimalOrDefault(
                thresholds.getGrowthMinSalesGrowthRate(),
                GROWTH_SALES_RATE
        )) >= 0;
    }

    private boolean isVolatileGrowth(
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        ProductLifecycleGrowthShapeMetrics metrics = features.getGrowthShapeMetrics();
        BigDecimal momentumRate = metrics == null ? null : trimmedMomentumRate(
                metrics.getDailySales30(),
                decimalOrDefault(thresholds.getVolatileOutlierTrimRatio(), VOLATILE_OUTLIER_TRIM_RATIO)
        );
        return metrics != null
                && momentumRate != null
                && metrics.getValidPointCount() >= MIN_VOLATILE_VALID_POINTS
                && momentumRate.compareTo(decimalOrDefault(
                        thresholds.getVolatileMomentumThreshold(),
                        VOLATILE_MOMENTUM_THRESHOLD
                )) >= 0;
    }

    private boolean isNewByConfiguredWindow(
            LocalDate analysisDate,
            ProductLifecycleListingDateResolution listing,
            OperationLifecycleRuleThresholds thresholds
    ) {
        if (analysisDate == null || listing == null || listing.getListingDate() == null) {
            return false;
        }
        long ageDays = ChronoUnit.DAYS.between(listing.getListingDate(), analysisDate) + 1;
        if (ageDays < 1) {
            return false;
        }
        int maxAgeDays = intOrDefault(thresholds.getNewMaxAgeDays(), 60);
        return ageDays <= maxAgeDays
                && !listing.isHistoricalOldProduct()
                && !listing.isLeftTruncatedHistoricalWindow();
    }

    private boolean isSteadyGrowth(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal growth = corrected.getCorrectedSalesGrowth30();
        BigDecimal volatility = features.getSalesVolatility30();
        return growth != null
                && growth.compareTo(BigDecimal.ZERO) > 0
                && growth.compareTo(decimalOrDefault(thresholds.getGrowthMinSalesGrowthRate(), GROWTH_SALES_RATE)) < 0
                && volatility != null
                && volatility.compareTo(decimalOrDefault(thresholds.getGrowthMaxVolatility(), BigDecimal.ONE)) <= 0;
    }

    private boolean declinesByDecayRatio(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal decayRatio = decayRatio(corrected);
        return decayRatio != null
                && decayRatio.compareTo(decimalOrDefault(
                        thresholds.getDeclineDecayRatioThreshold(),
                        DECLINE_DECAY_RATIO_THRESHOLD
                )) <= 0
                && corrected.getCorrectedRecent30Sales().compareTo(decimalOrDefault(
                        thresholds.getGrowthMinMonthlySales(),
                        MIN_SIGNAL_MONTHLY_SALES
                )) >= 0
                && features.getActiveSalesDays30() >= intOrDefault(
                        thresholds.getGrowthMinActiveSalesDays(),
                        MIN_ACTIVE_SALES_DAYS
                );
    }

    private BigDecimal decayRatio(ProductLifecycleCorrectedFeatureSnapshot corrected) {
        BigDecimal historicalAvg = dailyAverage(corrected.getCorrectedHistoricalT38ToT8Sales(), 31);
        if (historicalAvg.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal recentAvg = dailyAverage(corrected.getCorrectedRecent7Sales(), 7);
        return recentAvg.divide(historicalAvg, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal stableShortWeight(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal recentAvg = dailyAverage(corrected.getCorrectedRecent7Sales(), 7);
        BigDecimal longAvg = dailyAverage(corrected.getCorrectedRecent60Sales(), 60);
        BigDecimal ratio = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (longAvg.compareTo(BigDecimal.ZERO) > 0) {
            ratio = recentAvg.divide(longAvg, 4, RoundingMode.HALF_UP);
        }
        if (ratio.compareTo(STABLE_RISING_RATIO_THRESHOLD) > 0) {
            return decimalOrDefault(thresholds.getStableRisingShortWeight(), STABLE_RISING_SHORT_WEIGHT);
        }
        return decimalOrDefault(thresholds.getStableFallingShortWeight(), STABLE_FALLING_SHORT_WEIGHT);
    }

    private BigDecimal stableCombinedDailyForecast(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal recentAvg = dailyAverage(corrected.getCorrectedRecent7Sales(), 7);
        BigDecimal longAvg = dailyAverage(corrected.getCorrectedRecent60Sales(), 60);
        BigDecimal shortWeight = stableShortWeight(corrected, thresholds);
        return recentAvg.multiply(shortWeight)
                .add(longAvg.multiply(BigDecimal.ONE.subtract(shortWeight)))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal dailyAverage(BigDecimal total, int days) {
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO : total;
        return safeTotal.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal trimmedMomentumRate(List<Integer> dailySales, BigDecimal trimRatio) {
        if (dailySales == null || dailySales.size() != 30) {
            return null;
        }
        List<Integer> firstHalf = trimOutliers(dailySales.subList(0, 15), trimRatio);
        List<Integer> secondHalf = trimOutliers(dailySales.subList(15, 30), trimRatio);
        BigDecimal firstAvg = average(firstHalf);
        BigDecimal secondAvg = average(secondHalf);
        if (firstAvg.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return secondAvg.subtract(firstAvg)
                .divide(firstAvg, 6, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private List<Integer> trimOutliers(List<Integer> values, BigDecimal trimRatio) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        BigDecimal safeRatio = trimRatio == null || trimRatio.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO
                : trimRatio;
        int trimCount = safeRatio.multiply(BigDecimal.valueOf(sorted.size())).intValue();
        if (trimCount <= 0 || sorted.size() - (trimCount * 2) <= 0) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(trimCount, sorted.size() - trimCount));
    }

    private BigDecimal average(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        int total = 0;
        for (Integer value : values) {
            total += value == null ? 0 : value;
        }
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalOrDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private int intOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String evidenceJson(
            String reason,
            String growthShape,
            ProductLifecycleFeatureSnapshot features,
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleRuleSet ruleSet,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal recentDailyAvg = dailyAverage(corrected.getCorrectedRecent7Sales(), 7);
        BigDecimal oldDailyAvg = dailyAverage(corrected.getCorrectedPrevious30Sales(), 30);
        BigDecimal shortWeight = stableShortWeight(corrected, thresholds);
        BigDecimal combinedDaily = stableCombinedDailyForecast(corrected, thresholds);
        return "{"
                + "\"reason\":\"" + reason + "\","
                + "\"growthShape\":" + jsonStringOrNull(growthShape) + ","
                + "\"lifecycleVersionNo\":\"" + jsonText(ruleSet.getLifecycleVersionNo()) + "\","
                + "\"lifecycleVersionName\":\"" + jsonText(ruleSet.getLifecycleVersionName()) + "\","
                + "\"lifecycleVersionSourceLabel\":\"" + jsonText(ruleSet.getLifecycleVersionSourceLabel()) + "\","
                + "\"last7Sales\":\"" + jsonDecimal(corrected.getCorrectedRecent7Sales()) + "\","
                + "\"last30Sales\":\"" + jsonDecimal(corrected.getCorrectedRecent30Sales()) + "\","
                + "\"prev30Sales\":\"" + jsonDecimal(corrected.getCorrectedPrevious30Sales()) + "\","
                + "\"growthRate30\":" + jsonDecimalStringOrNull(corrected.getCorrectedSalesGrowth30()) + ","
                + "\"recentDailyAvg\":\"" + jsonDecimal(recentDailyAvg) + "\","
                + "\"oldDailyAvg\":\"" + jsonDecimal(oldDailyAvg) + "\","
                + "\"salesVolatility30\":\"" + jsonDecimal(features.getSalesVolatility30()) + "\","
                + "\"activeSalesDays30\":" + features.getActiveSalesDays30() + ","
                + "\"decayRatio\":" + jsonDecimalStringOrNull(decayRatio(corrected)) + ","
                + "\"stableShortWeight\":\"" + jsonDecimal(shortWeight) + "\","
                + "\"stableCombinedDailyForecast\":\"" + jsonDecimal(combinedDaily) + "\","
                + "\"recent15Sales\":" + features.getRecent15().getSalesUnits() + ","
                + "\"previous15Sales\":" + features.getPrevious15().getSalesUnits() + ","
                + "\"salesGrowth15\":" + jsonDecimal(features.getSalesGrowth15()) + ","
                + "\"pvGrowth15\":" + jsonDecimal(features.getPvGrowth15()) + ","
                + "\"correctedRecent15Sales\":" + corrected.getCorrectedRecent15Sales().toPlainString() + ","
                + "\"correctedPrevious15Sales\":" + corrected.getCorrectedPrevious15Sales().toPlainString() + ","
                + "\"correctedRecent30Sales\":" + corrected.getCorrectedRecent30Sales().toPlainString() + ","
                + "\"correctedSalesGrowth15\":" + jsonDecimal(corrected.getCorrectedSalesGrowth15()) + ","
                + "\"qualityReasons\":" + jsonArray(features.getQualityReasons()) + ","
                + "\"salesCorrectionEvidence\":" + jsonObjectOrNull(corrected.getEvidenceJson())
                + "}";
    }

    private String jsonText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String jsonObjectOrNull(String value) {
        if (value == null || value.isBlank()) {
            return "null";
        }
        return value;
    }

    private String jsonStringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + jsonText(value) + "\"";
    }

    private String jsonDecimalStringOrNull(BigDecimal value) {
        if (value == null) {
            return "null";
        }
        return "\"" + jsonDecimal(value) + "\"";
    }

    private String jsonDecimal(BigDecimal value) {
        if (value == null) {
            return "null";
        }
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
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

    private int unitsBetween(List<DailySalesFact> facts, LocalDate from, LocalDate to) {
        return facts.stream()
                .filter(fact -> !fact.getFactDate().isBefore(from) && !fact.getFactDate().isAfter(to))
                .mapToInt(DailySalesFact::getNetUnits)
                .sum();
    }

    private long usableFactDateCount(List<DailySalesFact> facts) {
        return facts.stream()
                .map(DailySalesFact::getFactDate)
                .filter(date -> date != null)
                .distinct()
                .count();
    }
}
