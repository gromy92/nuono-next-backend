package com.nuono.next.aiopsdashboard;

import com.nuono.next.noonsync.NoonSalesSyncReadModel;
import com.nuono.next.noonsync.NoonSalesSyncSurfaceState;
import com.nuono.next.noonsync.NoonSyncFailureReason;
import com.nuono.next.noonsync.NoonSyncTaskStatus;
import com.nuono.next.sales.ProductLifecycleClassifier;
import com.nuono.next.sales.SalesAnalyticsService;
import com.nuono.next.sales.SalesAnalyticsSummary;
import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesProductRow;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiOperationsDashboardSalesLifecycleSignalProvider implements AiOperationsDashboardSignalProvider {

    private static final List<LifecycleCategory> CATEGORIES = List.of(
            new LifecycleCategory("new", "sales_lifecycle_new", "新品"),
            new LifecycleCategory("growth", "sales_lifecycle_growth", "增长"),
            new LifecycleCategory("stable", "sales_lifecycle_stable", "稳定"),
            new LifecycleCategory("decline", "sales_lifecycle_decline", "衰退"),
            new LifecycleCategory("longTail", "sales_lifecycle_long_tail", "长尾期"),
            new LifecycleCategory("data_insufficient", "sales_lifecycle_data_insufficient", "数据不足")
    );

    private final SalesAnalyticsService salesAnalyticsService;

    @Autowired
    public AiOperationsDashboardSalesLifecycleSignalProvider(SalesAnalyticsService salesAnalyticsService) {
        this.salesAnalyticsService = salesAnalyticsService;
    }

    @Override
    public List<AiOperationsDashboardOverview.Signal> collect(AiOperationsDashboardQuery query) {
        return contribute(query, null).getSignals();
    }

    @Override
    public AiOperationsDashboardContribution contribute(
            AiOperationsDashboardQuery query,
            AiOperationsDashboardScope scope
    ) {
        AiOperationsDashboardScope resolvedScope = resolveScope(query, scope);
        SalesFactQuery scopedQuery = salesQuery(
                resolvedScope,
                resolvedScope.getDateFrom(),
                resolvedScope.getDateTo()
        );
        SalesAnalyticsSummary summary = salesAnalyticsService.getSummary(scopedQuery);
        NoonSalesSyncReadModel syncStatus = summary.getSyncStatus();
        String dashboardState = dashboardState(syncStatus);
        String qualityState = qualityState(syncStatus, dashboardState);
        boolean businessMetricsAllowed = syncStatus != null && summary.isBusinessMetricsAvailable();

        Map<String, Integer> distribution = emptyDistribution();
        List<SalesProductRow> rows = List.of();
        int pendingLifecycleCount = 0;
        if (businessMetricsAllowed) {
            rows = salesAnalyticsService.listProductRows(salesQuery(
                    resolvedScope,
                    lifecycleLookbackDateFrom(resolvedScope),
                    resolvedScope.getDateTo()
            ));
            for (SalesProductRow row : rows) {
                if (distribution.containsKey(row.getLifecycleCode())) {
                    distribution.put(row.getLifecycleCode(), distribution.get(row.getLifecycleCode()) + 1);
                }
                if ("pending".equals(row.getLifecycleCode()) || "pending".equals(row.getLifecycleQualityState())) {
                    pendingLifecycleCount++;
                }
            }
        }

        boolean lifecycleDistributionAvailable = businessMetricsAllowed && pendingLifecycleCount == 0;
        String state = businessMetricsAllowed
                ? (lifecycleDistributionAvailable ? "ready" : "lifecycle_pending")
                : dashboardState;
        String lifecycleQualityState = businessMetricsAllowed
                ? (lifecycleDistributionAvailable ? "ready" : "lifecycle_pending")
                : qualityState;
        AiOperationsDashboardOverview.EvidenceItem evidence = new AiOperationsDashboardOverview.EvidenceItem(
                "sales-lifecycle-default-v1-distribution",
                "生命周期规则版本",
                "sales",
                lifecycleQualityState,
                evidenceDescription(
                        resolvedScope,
                        distribution,
                        syncStatus,
                        businessMetricsAllowed,
                        lifecycleDistributionAvailable,
                        pendingLifecycleCount
                )
        );
        List<AiOperationsDashboardOverview.Signal> signals = anomalySignals(
                resolvedScope,
                rows,
                syncStatus,
                dashboardState,
                lifecycleDistributionAvailable
        );
        signals.add(new AiOperationsDashboardOverview.Signal(
                "sales_lifecycle_distribution",
                "商品生命周期分布",
                state,
                severity(state, lifecycleQualityState),
                signalDescription(distribution, lifecycleQualityState, lifecycleDistributionAvailable),
                "sales",
                salesAnalysisPath(resolvedScope, null),
                List.of()
        ));
        signals.sort(Comparator.comparingInt(signal -> severityRank(signal.getSeverity())));
        return new AiOperationsDashboardContribution(
                metricCards(distribution, state, lifecycleQualityState, lifecycleDistributionAvailable),
                signals,
                List.of(evidence)
        );
    }

    private AiOperationsDashboardScope resolveScope(AiOperationsDashboardQuery query, AiOperationsDashboardScope scope) {
        if (scope != null) {
            return scope;
        }
        AiOperationsDashboardQuery safeQuery = query == null
                ? new AiOperationsDashboardQuery(null, null, null, null, "last7Days")
                : query;
        return new AiOperationsDashboardScope(
                safeQuery.getOwnerUserId(),
                safeQuery.getOperatorUserId(),
                safeQuery.getStoreCode(),
                safeQuery.getSiteCode(),
                safeQuery.getDatePreset(),
                null,
                null
        );
    }

    private SalesFactQuery salesQuery(AiOperationsDashboardScope scope, LocalDate dateFrom, LocalDate dateTo) {
        return new SalesFactQuery(
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode(),
                dateFrom,
                dateTo,
                null,
                null
        );
    }

    private LocalDate lifecycleLookbackDateFrom(AiOperationsDashboardScope scope) {
        LocalDate dateTo = scope.getDateTo();
        if (dateTo == null) {
            return scope.getDateFrom();
        }
        return dateTo.minusDays(89);
    }

    private Map<String, Integer> emptyDistribution() {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (LifecycleCategory category : CATEGORIES) {
            distribution.put(category.code, 0);
        }
        return distribution;
    }

    private List<AiOperationsDashboardOverview.MetricCard> metricCards(
            Map<String, Integer> distribution,
            String state,
            String qualityState,
            boolean distributionAvailable
    ) {
        List<AiOperationsDashboardOverview.MetricCard> metricCards = new ArrayList<>();
        for (LifecycleCategory category : CATEGORIES) {
            metricCards.add(new AiOperationsDashboardOverview.MetricCard(
                    category.metricKey,
                    "生命周期：" + category.label,
                    state,
                    qualityState,
                    distributionAvailable ? BigDecimal.valueOf(distribution.get(category.code)) : null,
                    "个",
                    distributionAvailable
                            ? ProductLifecycleClassifier.DEFAULT_RULE_VERSION + " 分类：" + category.label
                                    + "；来源于 product_lifecycle_current_state，经销量分析 productRows.lifecycleCode 透出。"
                            : "质量状态 " + qualityState + "，暂不展示生命周期分布数值。"
            ));
        }
        return metricCards;
    }

    private String signalDescription(
            Map<String, Integer> distribution,
            String qualityState,
            boolean distributionAvailable
    ) {
        if ("lifecycle_pending".equals(qualityState)) {
            return "生命周期状态待计算，暂不展示分布数值。";
        }
        if (!distributionAvailable) {
            return "质量状态 " + qualityState + "，暂不生成生命周期分布判断。";
        }
        return ProductLifecycleClassifier.DEFAULT_RULE_VERSION + "："
                + CATEGORIES.stream()
                .map(category -> category.label + " " + distribution.get(category.code))
                .collect(Collectors.joining("，"));
    }

    private String evidenceDescription(
            AiOperationsDashboardScope scope,
            Map<String, Integer> distribution,
            NoonSalesSyncReadModel syncStatus,
            boolean businessMetricsAllowed,
            boolean distributionAvailable,
            int pendingLifecycleCount
    ) {
        String counts = CATEGORIES.stream()
                .map(category -> category.code + "=" + distribution.get(category.code))
                .collect(Collectors.joining(";"));
        return "owner=" + scope.getOwnerUserId()
                + ";operator=" + scope.getOperatorUserId()
                + ";store=" + scope.getStoreCode()
                + ";site=" + scope.getSiteCode()
                + ";datePreset=" + scope.getDatePreset()
                + ";dateFrom=" + scope.getDateFrom()
                + ";dateTo=" + scope.getDateTo()
                + ";lookbackDateFrom=" + lifecycleLookbackDateFrom(scope)
                + ";ruleVersion=" + ProductLifecycleClassifier.DEFAULT_RULE_VERSION
                + ";source=product_lifecycle_current_state via sales_analysis.product_rows"
                + ";businessMetricsAllowed=" + businessMetricsAllowed
                + ";distributionAvailable=" + distributionAvailable
                + ";pendingLifecycleCount=" + pendingLifecycleCount
                + ";syncState=" + (syncStatus == null ? "not_connected" : syncStatus.getState())
                + ";" + counts;
    }

    private List<AiOperationsDashboardOverview.Signal> anomalySignals(
            AiOperationsDashboardScope scope,
            List<SalesProductRow> rows,
            NoonSalesSyncReadModel syncStatus,
            String dashboardState,
            boolean distributionAvailable
    ) {
        List<AiOperationsDashboardOverview.Signal> signals = new ArrayList<>();
        if ("stale".equals(dashboardState)) {
            signals.add(staleSalesSignal(scope, syncStatus));
        }
        if (!distributionAvailable) {
            return signals;
        }

        List<SalesProductRow> stockoutRows = rows.stream()
                .filter(row -> row.getLifecycleWarningCodes().contains("possible_stockout_distortion")
                        || row.getLifecycleWarningCodes().contains("lifecycle_stockout_hold")
                        || "stockout_hold".equals(row.getLifecycleQualityState()))
                .collect(Collectors.toList());
        if (!stockoutRows.isEmpty()) {
            signals.add(productAnomalySignal(
                    "sales_possible_stockout_distortion",
                    "疑似断货导致销量失真",
                    "warning",
                    "warning=possible_stockout_distortion;qualityState=stockout_hold",
                    "发现 " + stockoutRows.size() + " 个商品存在疑似断货销量失真，优先查看 "
                            + productLabel(stockoutRows.get(0)) + "。",
                    stockoutRows,
                    scope
            ));
        }

        List<SalesProductRow> declineRows = rowsByLifecycle(rows, "decline");
        if (!declineRows.isEmpty()) {
            signals.add(productAnomalySignal(
                    "sales_declining_products",
                    "衰退商品",
                    "warning",
                    "lifecycle=decline",
                    "发现 " + declineRows.size() + " 个衰退商品，优先查看 " + productLabel(declineRows.get(0)) + "。",
                    declineRows,
                    scope
            ));
        }

        List<SalesProductRow> dataInsufficientRows = rowsByLifecycle(rows, "data_insufficient");
        if (!dataInsufficientRows.isEmpty()) {
            signals.add(productAnomalySignal(
                    "sales_data_insufficient_products",
                    "数据不足商品",
                    "warning",
                    "lifecycle=data_insufficient",
                    "发现 " + dataInsufficientRows.size() + " 个商品销量样本不足，暂不让 AI 下结论。",
                    dataInsufficientRows,
                    scope
            ));
        }

        List<SalesProductRow> longTailRows = rowsByLifecycle(rows, "longTail");
        if (!longTailRows.isEmpty()) {
            signals.add(productAnomalySignal(
                    "sales_long_tail_products",
                    "长尾期商品",
                    "info",
                    "lifecycle=longTail",
                    "发现 " + longTailRows.size() + " 个长尾期商品，可进入低频运营关注。",
                    longTailRows,
                    scope
            ));
        }
        return signals;
    }

    private AiOperationsDashboardOverview.Signal staleSalesSignal(
            AiOperationsDashboardScope scope,
            NoonSalesSyncReadModel syncStatus
    ) {
        AiOperationsDashboardOverview.EvidenceItem evidence = new AiOperationsDashboardOverview.EvidenceItem(
                "sales-sync-stale-latest-sales",
                "销量同步新鲜度",
                "noon_sync",
                "stale",
                "syncState=" + (syncStatus == null ? "not_connected" : syncStatus.getState())
                        + ";latestAvailableSalesDate="
                        + (syncStatus == null ? null : syncStatus.getLatestAvailableSalesDate())
                        + ";taskStatus=" + (syncStatus == null ? null : syncStatus.getTaskStatus())
                        + ";dateTo=" + scope.getDateTo()
        );
        return new AiOperationsDashboardOverview.Signal(
                "sales_stale_data",
                "销量数据已过期",
                "stale",
                "warning",
                "最新可用销量日期早于当前分析窗口，当前看板不生成产品级异常判断。",
                "noon_sync",
                salesAnalysisPath(scope, null),
                List.of(evidence)
        );
    }

    private AiOperationsDashboardOverview.Signal productAnomalySignal(
            String key,
            String title,
            String severity,
            String evidenceClassifier,
            String description,
            List<SalesProductRow> rows,
            AiOperationsDashboardScope scope
    ) {
        SalesProductRow topRow = rows.get(0);
        AiOperationsDashboardOverview.EvidenceItem evidence = new AiOperationsDashboardOverview.EvidenceItem(
                key + "-evidence",
                title + "证据",
                "product_lifecycle_current_state",
                "ready",
                "ruleVersion=" + ProductLifecycleClassifier.DEFAULT_RULE_VERSION
                        + ";source=product_lifecycle_current_state via sales_analysis.product_rows"
                        + ";" + evidenceClassifier
                        + ";count=" + rows.size()
                        + ";topPartnerSku=" + topRow.getPartnerSku()
                        + ";topSku=" + topRow.getSku()
                        + ";dateFrom=" + lifecycleLookbackDateFrom(scope)
                        + ";dateTo=" + scope.getDateTo()
        );
        return new AiOperationsDashboardOverview.Signal(
                key,
                title,
                "ready",
                severity,
                description,
                "sales",
                salesAnalysisPath(scope, topRow),
                List.of(evidence)
        );
    }

    private List<SalesProductRow> rowsByLifecycle(List<SalesProductRow> rows, String lifecycleCode) {
        return rows.stream()
                .filter(row -> lifecycleCode.equals(row.getLifecycleCode()))
                .collect(Collectors.toList());
    }

    private String productLabel(SalesProductRow row) {
        if (row.getPartnerSku() != null && !row.getPartnerSku().isBlank()) {
            return row.getPartnerSku();
        }
        if (row.getSku() != null && !row.getSku().isBlank()) {
            return row.getSku();
        }
        return "商品";
    }

    private String salesAnalysisPath(AiOperationsDashboardScope scope, SalesProductRow row) {
        List<String> params = new ArrayList<>();
        addParam(params, "storeCode", scope.getStoreCode());
        addParam(params, "siteCode", scope.getSiteCode());
        addParam(params, "dateFrom", stringValue(scope.getDateFrom()));
        addParam(params, "dateTo", stringValue(scope.getDateTo()));
        if (row != null) {
            addParam(params, "search", productLabel(row));
        }
        if (params.isEmpty()) {
            return "/data/sales-analysis";
        }
        return "/data/sales-analysis?" + String.join("&", params);
    }

    private void addParam(List<String> params, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        params.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String stringValue(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private String dashboardState(NoonSalesSyncReadModel syncStatus) {
        if (syncStatus == null) {
            return "not_connected";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.READY
                && syncStatus.getTaskStatus() == NoonSyncTaskStatus.PARTIAL) {
            return "partial_success";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.READY) {
            return "ready";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.SYNC_IN_PROGRESS) {
            return "sync_in_progress";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.STALE_LATEST_SALES) {
            return "stale";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.NO_DATA_BACKFILL_REQUIRED) {
            return "backfill_required";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.BACKFILL_RUNNING) {
            return "backfill_running";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.BACKFILL_FAILED) {
            return "backfill_failed";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.EMPTY_REPORT) {
            return "empty_report";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.MISSING_MAPPING) {
            return "missing_mapping";
        }
        return "empty";
    }

    private String qualityState(NoonSalesSyncReadModel syncStatus, String dashboardState) {
        if (syncStatus == null) {
            return dashboardState;
        }
        if (syncStatus.getFailureReason() == NoonSyncFailureReason.PROVIDER_UNAVAILABLE) {
            return "provider_unavailable";
        }
        if (syncStatus.getTaskStatus() == NoonSyncTaskStatus.PARTIAL) {
            return "partial_success";
        }
        return dashboardState;
    }

    private String severity(String dashboardState, String qualityState) {
        if ("backfill_failed".equals(dashboardState)
                || "missing_mapping".equals(dashboardState)
                || "provider_unavailable".equals(qualityState)) {
            return "error";
        }
        if ("ready".equals(dashboardState)
                || "sync_in_progress".equals(dashboardState)
                || "not_connected".equals(dashboardState)) {
            return "info";
        }
        return "warning";
    }

    private int severityRank(String severity) {
        if ("error".equals(severity)) {
            return 0;
        }
        if ("warning".equals(severity)) {
            return 1;
        }
        if ("info".equals(severity)) {
            return 2;
        }
        return 3;
    }

    private static class LifecycleCategory {
        private final String code;
        private final String metricKey;
        private final String label;

        private LifecycleCategory(String code, String metricKey, String label) {
            this.code = code;
            this.metricKey = metricKey;
            this.label = label;
        }
    }
}
