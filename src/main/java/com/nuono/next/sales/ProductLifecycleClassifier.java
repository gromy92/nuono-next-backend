package com.nuono.next.sales;

import com.nuono.next.operationsconfig.OperationLifecycleRuleThresholds;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleClassifier {

    public static final String DEFAULT_RULE_VERSION = ProductLifecycleResult.DEFAULT_RULE_VERSION;

    private static final BigDecimal GROWTH_SALES_RATE = new BigDecimal("0.3000");
    private static final BigDecimal GROWTH_PV_RATE = new BigDecimal("0.2000");
    private static final BigDecimal DECLINE_SALES_RATE = new BigDecimal("-0.3000");
    private static final BigDecimal LOW_VOLUME_MONTHLY_SALES = new BigDecimal("3.0000");
    private static final BigDecimal MIN_SIGNAL_MONTHLY_SALES = new BigDecimal("10.0000");
    private static final BigDecimal MIN_PV_RECOVERY_SALES = new BigDecimal("20.0000");
    private static final int MIN_ACTIVE_SALES_DAYS = 7;
    private static final int MIN_OBSERVED_DAYS = 15;

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
                    features,
                    corrected
            );
        }
        if (listing.isEligibleForNewInitialization()) {
            return result(
                    "new",
                    "新品",
                    "上架时间处于 30 天新品窗口内，且没有 60 天历史信号证明为老品。",
                    resolvedRuleSet,
                    "ready",
                    "new_listing_window",
                    features,
                    corrected
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
                    features,
                    corrected
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
                    features,
                    corrected
            );
        }
        if (isLongTail(corrected, features, thresholds)) {
            return result(
                    "longTail",
                    "长尾期",
                    "订正后近 30 天销量低且动销天数弱，低销量波动不单独驱动增长或衰退。",
                    resolvedRuleSet,
                    "ready",
                    "low_volume_volatility_guard",
                    features,
                    corrected
            );
        }
        if (hasEnoughGrowthGuard(corrected, features, thresholds) && growthByCorrectedSales(corrected, thresholds)) {
            return result(
                    "growth",
                    "增长",
                    "订正后近 15 天销量较前 15 天明显增长。",
                    resolvedRuleSet,
                    "ready",
                    "growth_path_corrected_sales",
                    features,
                    corrected
            );
        }
        if (hasEnoughGrowthGuard(corrected, features, thresholds) && growthBySalesPlusPv(features, thresholds)) {
            return result(
                    "growth",
                    "增长",
                    "销量增长同时 PV 增长，判定为增长期。",
                    resolvedRuleSet,
                    "ready",
                    "growth_path_sales_plus_pv",
                    features,
                    corrected
            );
        }
        if (declinesByCorrectedSales(corrected, features, thresholds)) {
            return result(
                    "decline",
                    "衰退",
                    "订正后近 15 天销量较前 15 天明显下降。",
                    resolvedRuleSet,
                    "ready",
                    "decline_corrected_sales",
                    features,
                    corrected
            );
        }
        return result(
                "stable",
                "稳定",
                "销量/PV 窗口充足，订正后趋势未达到增长、衰退或长尾阈值。",
                resolvedRuleSet,
                "ready",
                "stable_default_v1",
                features,
                corrected
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
            ProductLifecycleFeatureSnapshot features,
            ProductLifecycleCorrectedFeatureSnapshot corrected
    ) {
        return new ProductLifecycleResult(
                code,
                label,
                explanation,
                ruleVersion(ruleSet),
                List.of(),
                qualityState,
                evidenceJson(reason, features, corrected, ruleSet)
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

    private boolean growthByCorrectedSales(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal growth = corrected.getCorrectedSalesGrowth15();
        return growth != null && growth.compareTo(decimalOrDefault(
                thresholds.getGrowthMinSalesGrowthRate(),
                GROWTH_SALES_RATE
        )) >= 0;
    }

    private boolean growthBySalesPlusPv(
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal salesGrowth = features.getSalesGrowth15();
        BigDecimal pvGrowth = features.getPvGrowth15();
        return salesGrowth != null
                && pvGrowth != null
                && salesGrowth.compareTo(decimalOrDefault(thresholds.getGrowthMinPvGrowthRate(), GROWTH_PV_RATE)) >= 0
                && pvGrowth.compareTo(decimalOrDefault(thresholds.getGrowthMinPvGrowthRate(), GROWTH_PV_RATE)) >= 0;
    }

    private boolean declinesByCorrectedSales(
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleFeatureSnapshot features,
            OperationLifecycleRuleThresholds thresholds
    ) {
        BigDecimal growth = corrected.getCorrectedSalesGrowth15();
        return growth != null
                && growth.compareTo(decimalOrDefault(thresholds.getDeclineMaxSalesGrowthRate(), DECLINE_SALES_RATE)) <= 0
                && corrected.getCorrectedRecent30Sales().compareTo(decimalOrDefault(
                        thresholds.getGrowthMinMonthlySales(),
                        MIN_SIGNAL_MONTHLY_SALES
                )) >= 0
                && features.getActiveSalesDays30() >= intOrDefault(
                        thresholds.getGrowthMinActiveSalesDays(),
                        MIN_ACTIVE_SALES_DAYS
                );
    }

    private BigDecimal decimalOrDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private int intOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String evidenceJson(
            String reason,
            ProductLifecycleFeatureSnapshot features,
            ProductLifecycleCorrectedFeatureSnapshot corrected,
            ProductLifecycleRuleSet ruleSet
    ) {
        return "{"
                + "\"reason\":\"" + reason + "\","
                + "\"lifecycleVersionNo\":\"" + jsonText(ruleSet.getLifecycleVersionNo()) + "\","
                + "\"lifecycleVersionName\":\"" + jsonText(ruleSet.getLifecycleVersionName()) + "\","
                + "\"lifecycleVersionSourceLabel\":\"" + jsonText(ruleSet.getLifecycleVersionSourceLabel()) + "\","
                + "\"recent15Sales\":" + features.getRecent15().getSalesUnits() + ","
                + "\"previous15Sales\":" + features.getPrevious15().getSalesUnits() + ","
                + "\"salesGrowth15\":" + jsonDecimal(features.getSalesGrowth15()) + ","
                + "\"pvGrowth15\":" + jsonDecimal(features.getPvGrowth15()) + ","
                + "\"salesVolatility30\":" + jsonDecimal(features.getSalesVolatility30()) + ","
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
