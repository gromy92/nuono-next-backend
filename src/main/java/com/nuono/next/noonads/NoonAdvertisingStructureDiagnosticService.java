package com.nuono.next.noonads;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class NoonAdvertisingStructureDiagnosticService {
    private static final BigDecimal SIGNIFICANT_SPEND = new BigDecimal("20");
    private static final BigDecimal HIGH_ZERO_ORDER_SHARE = new BigDecimal("0.60");
    private static final BigDecimal HIGH_ROAS = new BigDecimal("10");
    private static final BigDecimal LOW_ROAS = new BigDecimal("3");
    private static final long CORE_MIN_ORDERS = 3;
    private static final long EXPLORATION_QUERY_COUNT = 50;
    private static final String RANK_LABEL = "搜索排名未接入";
    private static final String RANK_ACTION = "当前没有搜索排名数据，若要判断自然排名，需要接入关键词排名事实。";

    public NoonAdvertisingStructureDiagnosticResult diagnose(
            List<NoonAdvertisingProductRow> productRows,
            List<NoonAdvertisingCampaignRow> campaignRows,
            List<NoonAdvertisingQueryRow> zeroOrderQueries,
            List<NoonAdvertisingQueryRow> winningQueries
    ) {
        List<NoonAdvertisingProductRow> products = productRows == null ? List.of() : productRows;
        List<NoonAdvertisingCampaignRow> campaigns = campaignRows == null ? List.of() : campaignRows;
        List<NoonAdvertisingQueryRow> winning = winningQueries == null ? List.of() : winningQueries;

        Map<String, List<NoonAdvertisingCampaignRow>> campaignsByProductKey = campaigns.stream()
                .filter(row -> hasText(row.getAdvertisingIdentityKey()))
                .collect(Collectors.groupingBy(NoonAdvertisingCampaignRow::getAdvertisingIdentityKey, LinkedHashMap::new, Collectors.toList()));

        List<NoonAdvertisingCampaignDiagnostic> campaignDiagnostics = campaigns.stream()
                .map(this::diagnoseCampaign)
                .collect(Collectors.toList());

        List<NoonAdvertisingProductDiagnostic> productDiagnostics = products.stream()
                .map(product -> diagnoseProduct(product, campaignsByProductKey.getOrDefault(product.getAdvertisingIdentityKey(), List.of()), winning))
                .collect(Collectors.toList());

        return new NoonAdvertisingStructureDiagnosticResult(productDiagnostics, campaignDiagnostics);
    }

    public NoonAdvertisingPlanType classifyPlanType(NoonAdvertisingCampaignRow row) {
        return classifyPlan(row).planType;
    }

    private NoonAdvertisingProductDiagnostic diagnoseProduct(
            NoonAdvertisingProductRow product,
            List<NoonAdvertisingCampaignRow> campaigns,
            List<NoonAdvertisingQueryRow> winningQueries
    ) {
        List<String> labels = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        Map<NoonAdvertisingPlanType, Integer> planTypeCounts = planTypeCounts(campaigns);
        int coreCampaignCount = planTypeCounts.getOrDefault(NoonAdvertisingPlanType.CORE, 0);
        int explorationCampaignCount = planTypeCounts.getOrDefault(NoonAdvertisingPlanType.EXPLORATION, 0);
        int unclassifiedCampaignCount = planTypeCounts.getOrDefault(NoonAdvertisingPlanType.UNCLASSIFIED, 0);

        if (amount(product.getSpendAmount()).compareTo(BigDecimal.ZERO) <= 0 || product.getCampaignCount() <= 0) {
            labels.add("样本不足");
            labels.add(RANK_LABEL);
            actions.add("样本不足，暂不判断广告结构。");
            actions.add(RANK_ACTION);
            return buildProductDiagnostic(
                    product,
                    NoonAdvertisingProductDiagnosisType.INSUFFICIENT_DATA,
                    0,
                    coreCampaignCount,
                    explorationCampaignCount,
                    unclassifiedCampaignCount,
                    NoonAdvertisingStructureStatus.INSUFFICIENT_DATA,
                    labels,
                    actions,
                    planTypeCounts
            );
        }

        boolean stableCore = campaigns.stream().anyMatch(this::isStableCoreCampaign);
        boolean coreWeak = campaigns.stream().anyMatch(this::isCoreWeakCampaign);
        boolean coreZeroHigh = campaigns.stream().anyMatch(this::isCoreZeroHighCampaign);
        boolean explorationHighZero = campaigns.stream().anyMatch(this::isExplorationHighZeroCampaign);
        boolean explorationWinner = hasHighRoasWinningQuery(product.getAdvertisingIdentityKey(), campaigns, winningQueries, true);
        boolean productHighZeroWithoutStableCore = amount(product.getSpendAmount()).compareTo(SIGNIFICANT_SPEND) >= 0
                && amount(product.getZeroOrderSpendShare()).compareTo(HIGH_ZERO_ORDER_SHARE) >= 0
                && !stableCore;
        boolean unclearStructure = unclassifiedCampaignCount > 0
                || (coreCampaignCount == 0 && explorationCampaignCount == 0)
                || (coreCampaignCount == 0 && !explorationWinner)
                || (explorationCampaignCount == 0 && !stableCore);

        if (explorationWinner) {
            labels.add("探索计划有高转化词");
            actions.add("把探索计划里的高转化关键词/搜索词沉淀到核心计划。");
        }
        if (explorationHighZero) {
            labels.add("探索计划零订单消耗偏高");
            actions.add("探索计划零订单消耗偏高，建议人工确认后收缩范围、降 bid 或加否定。");
        } else if (productHighZeroWithoutStableCore) {
            labels.add("商品零订单消耗偏高");
            actions.add("商品整体零订单花费占比偏高，建议先人工确认无效流量来源。");
        }
        if (stableCore) {
            labels.add("核心表现稳定");
            actions.add("核心计划表现稳定，建议保护预算和稳定性，避免频繁大幅调整。");
        }
        if (coreWeak) {
            labels.add("核心计划走弱");
            actions.add("核心计划 ROAS 偏低，优先检查商品承接和关键词质量，不建议直接加预算。");
        }
        if (coreZeroHigh) {
            labels.add("核心零订单偏高");
            actions.add("核心计划零订单偏高，建议先复盘关键词质量和商品承接，谨慎收缩。");
        }
        if (coreCampaignCount == 0 && (explorationWinner || product.getQueryCount() >= EXPLORATION_QUERY_COUNT)) {
            labels.add("缺核心计划");
            actions.add("当前缺少核心计划，建议先整理已验证关键词/搜索词。");
        }
        if (unclassifiedCampaignCount > 0) {
            labels.add("计划用途待确认");
            actions.add("当前计划用途不清，建议先归类为核心或探索后再判断动作。");
        }
        labels.add(RANK_LABEL);
        actions.add(RANK_ACTION);

        NoonAdvertisingProductDiagnosisType diagnosisType = chooseDiagnosisType(
                explorationHighZero || productHighZeroWithoutStableCore,
                explorationWinner && !stableCore,
                stableCore && amount(product.getRoas()).compareTo(HIGH_ROAS) >= 0,
                unclearStructure
        );
        NoonAdvertisingStructureStatus status = structureStatusFor(diagnosisType);
        int priorityScore = priorityScore(diagnosisType, product);

        return buildProductDiagnostic(
                product,
                diagnosisType,
                priorityScore,
                coreCampaignCount,
                explorationCampaignCount,
                unclassifiedCampaignCount,
                status,
                labels,
                actions,
                planTypeCounts
        );
    }

    private NoonAdvertisingCampaignDiagnostic diagnoseCampaign(NoonAdvertisingCampaignRow campaign) {
        PlanClassification classification = classifyPlan(campaign);
        List<String> labels = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        if (classification.planType == NoonAdvertisingPlanType.CORE) {
            if (isStableCoreCampaign(campaign)) {
                labels.add("核心表现稳定");
                actions.add("核心计划表现稳定，建议保护预算和稳定性，避免频繁大幅调整。");
            }
            if (isCoreWeakCampaign(campaign)) {
                labels.add("核心计划走弱");
                actions.add("核心计划 ROAS 偏低，优先检查商品承接和关键词质量，不建议直接加预算。");
            }
            if (isCoreZeroHighCampaign(campaign)) {
                labels.add("核心零订单偏高");
                actions.add("核心计划零订单偏高，建议先复盘关键词质量和商品承接，谨慎收缩。");
            }
            if (labels.isEmpty()) {
                labels.add("核心计划待观察");
                actions.add("核心计划样本尚未稳定，建议先观察订单和 ROAS 趋势。");
            }
        } else if (classification.planType == NoonAdvertisingPlanType.EXPLORATION) {
            if (amount(campaign.getRoas()).compareTo(HIGH_ROAS) >= 0 && campaign.getOrdersCount() >= CORE_MIN_ORDERS) {
                labels.add("探索有收获");
                actions.add("把探索计划里的高转化关键词/搜索词沉淀到核心计划。");
            }
            if (isExplorationHighZeroCampaign(campaign)) {
                labels.add("探索消耗偏高");
                actions.add("探索计划零订单消耗偏高，建议人工确认后收缩范围、降 bid 或加否定。");
            }
            if (labels.isEmpty()) {
                labels.add("探索样本不足");
                actions.add("探索计划样本不足，建议继续小预算观察。");
            }
        } else {
            labels.add("计划用途待确认");
            labels.add("命名无法归类");
            actions.add("当前计划用途不清，建议先归类为核心或探索后再判断动作。");
        }

        return new NoonAdvertisingCampaignDiagnostic(
                campaign.getCampaignCode(),
                campaign.getStoreCode(),
                campaign.getSiteCode(),
                campaign.getPrimaryAdSkuCode(),
                campaign.getPrimaryPartnerSku(),
                classification.planType,
                classification.confidence,
                dedupe(labels),
                dedupe(actions)
        );
    }

    private PlanClassification classifyPlan(NoonAdvertisingCampaignRow row) {
        String name = normalize(row == null ? null : row.getCampaignName());
        if (containsAny(name, "exact", "core", "hero", "main", "winner", "核心", "主推", "精准", "稳定")) {
            return new PlanClassification(NoonAdvertisingPlanType.CORE, NoonAdvertisingPlanTypeConfidence.RULE);
        }
        if (containsAny(name, "auto", "explore", "discovery", "test", "broad", "探索", "测试", "自动", "泛流量")) {
            return new PlanClassification(NoonAdvertisingPlanType.EXPLORATION, NoonAdvertisingPlanTypeConfidence.RULE);
        }
        if (isStableCoreFacts(row)) {
            return new PlanClassification(NoonAdvertisingPlanType.CORE, NoonAdvertisingPlanTypeConfidence.INFERRED);
        }
        if (isExplorationFacts(row)) {
            return new PlanClassification(NoonAdvertisingPlanType.EXPLORATION, NoonAdvertisingPlanTypeConfidence.INFERRED);
        }
        return new PlanClassification(NoonAdvertisingPlanType.UNCLASSIFIED, NoonAdvertisingPlanTypeConfidence.UNKNOWN);
    }

    private NoonAdvertisingProductDiagnostic buildProductDiagnostic(
            NoonAdvertisingProductRow product,
            NoonAdvertisingProductDiagnosisType diagnosisType,
            int priorityScore,
            int coreCampaignCount,
            int explorationCampaignCount,
            int unclassifiedCampaignCount,
            NoonAdvertisingStructureStatus structureStatus,
            List<String> labels,
            List<String> actions,
            Map<NoonAdvertisingPlanType, Integer> planTypeCounts
    ) {
        return new NoonAdvertisingProductDiagnostic(
                product.getStoreCode(),
                product.getSiteCode(),
                product.getAdSkuCode(),
                product.getPartnerSku(),
                product.getCampaignCount(),
                product.getQueryCount(),
                diagnosisType,
                priorityScore,
                coreCampaignCount,
                explorationCampaignCount,
                unclassifiedCampaignCount,
                structureStatus,
                dedupe(labels),
                dedupe(actions),
                planTypeCounts,
                false
        );
    }

    private Map<NoonAdvertisingPlanType, Integer> planTypeCounts(List<NoonAdvertisingCampaignRow> campaigns) {
        Map<NoonAdvertisingPlanType, Integer> counts = new EnumMap<>(NoonAdvertisingPlanType.class);
        for (NoonAdvertisingCampaignRow campaign : campaigns) {
            NoonAdvertisingPlanType type = classifyPlanType(campaign);
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Enum::ordinal)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
    }

    private NoonAdvertisingProductDiagnosisType chooseDiagnosisType(
            boolean shouldStopLoss,
            boolean shouldPromoteToCore,
            boolean coreObservable,
            boolean structureReview
    ) {
        if (shouldStopLoss) return NoonAdvertisingProductDiagnosisType.STOP_LOSS;
        if (shouldPromoteToCore) return NoonAdvertisingProductDiagnosisType.PROMOTE_TO_CORE;
        if (coreObservable) return NoonAdvertisingProductDiagnosisType.CORE_OBSERVE;
        if (structureReview) return NoonAdvertisingProductDiagnosisType.STRUCTURE_REVIEW;
        return NoonAdvertisingProductDiagnosisType.INSUFFICIENT_DATA;
    }

    private NoonAdvertisingStructureStatus structureStatusFor(NoonAdvertisingProductDiagnosisType diagnosisType) {
        if (diagnosisType == NoonAdvertisingProductDiagnosisType.STOP_LOSS) return NoonAdvertisingStructureStatus.RISK;
        if (diagnosisType == NoonAdvertisingProductDiagnosisType.CORE_OBSERVE) return NoonAdvertisingStructureStatus.HEALTHY;
        if (diagnosisType == NoonAdvertisingProductDiagnosisType.INSUFFICIENT_DATA) return NoonAdvertisingStructureStatus.INSUFFICIENT_DATA;
        return NoonAdvertisingStructureStatus.NEEDS_ATTENTION;
    }

    private int priorityScore(NoonAdvertisingProductDiagnosisType diagnosisType, NoonAdvertisingProductRow product) {
        int base;
        switch (diagnosisType) {
            case STOP_LOSS:
                base = 100;
                break;
            case PROMOTE_TO_CORE:
                base = 80;
                break;
            case STRUCTURE_REVIEW:
                base = 55;
                break;
            case CORE_OBSERVE:
                base = 35;
                break;
            case INSUFFICIENT_DATA:
            default:
                base = 0;
                break;
        }
        int spendWeight = amount(product.getSpendAmount()).min(new BigDecimal("100")).divide(new BigDecimal("10"), 0, java.math.RoundingMode.DOWN).intValue();
        return base + spendWeight;
    }

    private boolean hasHighRoasWinningQuery(
            String advertisingIdentityKey,
            List<NoonAdvertisingCampaignRow> campaigns,
            List<NoonAdvertisingQueryRow> winningQueries,
            boolean requireExplorationCampaign
    ) {
        Map<String, NoonAdvertisingPlanType> campaignTypes = campaigns.stream()
                .filter(row -> hasText(row.getCampaignCode()))
                .collect(Collectors.toMap(NoonAdvertisingCampaignRow::getCampaignCode, this::classifyPlanType, (left, right) -> left));
        return winningQueries.stream()
                .filter(row -> Objects.equals(advertisingIdentityKey, row.getAdvertisingIdentityKey()))
                .filter(row -> row.getOrdersCount() > 0 && amount(row.getRoas()).compareTo(HIGH_ROAS) >= 0)
                .anyMatch(row -> !requireExplorationCampaign || campaignTypes.get(row.getCampaignCode()) == NoonAdvertisingPlanType.EXPLORATION);
    }

    private boolean isStableCoreCampaign(NoonAdvertisingCampaignRow row) {
        return classifyPlan(row).planType == NoonAdvertisingPlanType.CORE && isStableCoreFacts(row);
    }

    private boolean isCoreWeakCampaign(NoonAdvertisingCampaignRow row) {
        return classifyPlan(row).planType == NoonAdvertisingPlanType.CORE
                && amount(row.getSpendAmount()).compareTo(SIGNIFICANT_SPEND) >= 0
                && amount(row.getRoas()).compareTo(LOW_ROAS) < 0;
    }

    private boolean isCoreZeroHighCampaign(NoonAdvertisingCampaignRow row) {
        return classifyPlan(row).planType == NoonAdvertisingPlanType.CORE
                && amount(row.getSpendAmount()).compareTo(SIGNIFICANT_SPEND) >= 0
                && amount(row.getZeroOrderSpendShare()).compareTo(HIGH_ZERO_ORDER_SHARE) >= 0;
    }

    private boolean isExplorationHighZeroCampaign(NoonAdvertisingCampaignRow row) {
        return classifyPlan(row).planType == NoonAdvertisingPlanType.EXPLORATION && isExplorationFacts(row);
    }

    private boolean isStableCoreFacts(NoonAdvertisingCampaignRow row) {
        if (row == null) return false;
        return row.getOrdersCount() >= CORE_MIN_ORDERS
                && amount(row.getRoas()).compareTo(HIGH_ROAS) >= 0
                && amount(row.getZeroOrderSpendShare()).compareTo(HIGH_ZERO_ORDER_SHARE) < 0;
    }

    private boolean isExplorationFacts(NoonAdvertisingCampaignRow row) {
        if (row == null) return false;
        return amount(row.getSpendAmount()).compareTo(SIGNIFICANT_SPEND) >= 0
                && row.getOrdersCount() < CORE_MIN_ORDERS
                && amount(row.getZeroOrderSpendShare()).compareTo(HIGH_ZERO_ORDER_SHARE) >= 0;
    }

    private List<String> dedupe(List<String> values) {
        return values.stream().filter(this::hasText).distinct().collect(Collectors.toList());
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private static class PlanClassification {
        private final NoonAdvertisingPlanType planType;
        private final NoonAdvertisingPlanTypeConfidence confidence;

        private PlanClassification(NoonAdvertisingPlanType planType, NoonAdvertisingPlanTypeConfidence confidence) {
            this.planType = planType == null ? NoonAdvertisingPlanType.UNCLASSIFIED : planType;
            this.confidence = confidence == null ? NoonAdvertisingPlanTypeConfidence.UNKNOWN : confidence;
        }
    }
}
