package com.nuono.next.aiopsdashboard;

import com.nuono.next.noonsync.NoonSalesSyncReadModel;
import com.nuono.next.noonsync.NoonSalesSyncSurfaceState;
import com.nuono.next.noonsync.NoonSyncFailureReason;
import com.nuono.next.noonsync.NoonSyncRequiredWork;
import com.nuono.next.noonsync.NoonSyncTaskStatus;
import com.nuono.next.sales.SalesAnalyticsService;
import com.nuono.next.sales.SalesAnalyticsSummary;
import com.nuono.next.sales.SalesFactQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiOperationsDashboardSalesFreshnessSignalProvider implements AiOperationsDashboardSignalProvider {

    private final SalesAnalyticsService salesAnalyticsService;

    @Autowired
    public AiOperationsDashboardSalesFreshnessSignalProvider(SalesAnalyticsService salesAnalyticsService) {
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
        SalesAnalyticsSummary summary = salesAnalyticsService.getSummary(new SalesFactQuery(
                resolvedScope.getOwnerUserId(),
                resolvedScope.getStoreCode(),
                resolvedScope.getSiteCode(),
                resolvedScope.getDateFrom(),
                resolvedScope.getDateTo(),
                null,
                null
        ));
        NoonSalesSyncReadModel syncStatus = summary.getSyncStatus();
        String dashboardState = dashboardState(syncStatus);
        String qualityState = qualityState(syncStatus, dashboardState);
        String severity = severity(dashboardState, qualityState);
        LocalDate latestSalesDate = syncStatus == null ? null : syncStatus.getLatestAvailableSalesDate();
        boolean businessMetricsAllowed = syncStatus != null && summary.isBusinessMetricsAvailable();
        AiOperationsDashboardOverview.EvidenceItem evidence = new AiOperationsDashboardOverview.EvidenceItem(
                "sales-freshness-latest-date",
                "最新可用销量日期",
                "sales",
                qualityState,
                evidenceDescription(resolvedScope, syncStatus, latestSalesDate)
        );
        List<AiOperationsDashboardOverview.MetricCard> metricCards = new ArrayList<>();
        metricCards.add(new AiOperationsDashboardOverview.MetricCard(
                "sales_freshness",
                "销量数据新鲜度",
                dashboardState,
                qualityState,
                null,
                null,
                cardDescription(syncStatus, latestSalesDate)
        ));
        metricCards.addAll(coreMetricCards(summary, dashboardState, qualityState, businessMetricsAllowed));
        return new AiOperationsDashboardContribution(
                metricCards,
                List.of(new AiOperationsDashboardOverview.Signal(
                        "sales_freshness",
                        "销量数据新鲜度",
                        dashboardState,
                        severity,
                        signalDescription(syncStatus, latestSalesDate),
                        "sales",
                        List.of()
                )),
                List.of(evidence)
        );
    }

    private List<AiOperationsDashboardOverview.MetricCard> coreMetricCards(
            SalesAnalyticsSummary summary,
            String dashboardState,
            String qualityState,
            boolean businessMetricsAllowed
    ) {
        return List.of(
                coreMetric(
                        "sales_revenue_shipped",
                        "销售额",
                        businessMetricsAllowed ? summary.getRevenueShipped() : null,
                        null,
                        dashboardState,
                        qualityState,
                        businessMetricsAllowed,
                        "来源于销量分析 summary.revenueShipped。"
                ),
                coreMetric(
                        "sales_net_units",
                        "净销量",
                        businessMetricsAllowed ? BigDecimal.valueOf(summary.getNetUnits()) : null,
                        "件",
                        dashboardState,
                        qualityState,
                        businessMetricsAllowed,
                        "来源于销量分析 summary.netUnits。"
                ),
                coreMetric(
                        "sales_visitors",
                        "访客",
                        businessMetricsAllowed ? BigDecimal.valueOf(summary.getYourVisitors()) : null,
                        "人",
                        dashboardState,
                        qualityState,
                        businessMetricsAllowed,
                        "来源于销量分析 summary.yourVisitors。"
                ),
                coreMetric(
                        "sales_conversion",
                        "转化率",
                        businessMetricsAllowed ? summary.getConversionVisitorsPercentage() : null,
                        "%",
                        dashboardState,
                        qualityState,
                        businessMetricsAllowed,
                        "来源于销量分析 summary.conversionVisitorsPercentage。"
                )
        );
    }

    private AiOperationsDashboardOverview.MetricCard coreMetric(
            String key,
            String title,
            BigDecimal value,
            String unit,
            String dashboardState,
            String qualityState,
            boolean businessMetricsAllowed,
            String readyDescription
    ) {
        String state = businessMetricsAllowed ? "ready" : dashboardState;
        String description = businessMetricsAllowed
                ? readyDescription
                : "质量状态 " + qualityState + "，共享销量分析暂不允许业务指标，暂不展示数值。";
        return new AiOperationsDashboardOverview.MetricCard(
                key,
                title,
                state,
                qualityState,
                value,
                unit,
                description
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

    private String cardDescription(NoonSalesSyncReadModel syncStatus, LocalDate latestSalesDate) {
        if (syncStatus == null) {
            return "销量同步状态尚未接入，暂不展示业务数值。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.BACKFILL_RUNNING) {
            return "销量历史回补正在运行，业务指标暂不可用。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.BACKFILL_FAILED) {
            return "销量历史回补失败，业务指标暂不可用。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.EMPTY_REPORT) {
            return "最近一次来源报表为空，不能当作确认零销量。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.MISSING_MAPPING) {
            return "销量导入存在字段或映射问题，业务指标暂不可用。";
        }
        if (latestSalesDate == null) {
            return "当前范围没有可用销量事实，暂不展示业务数值。";
        }
        if (syncStatus.getTaskStatus() == NoonSyncTaskStatus.PARTIAL) {
            return "最新可用销量日期 " + latestSalesDate + "；最近同步部分成功，请结合证据查看质量状态。";
        }
        return "最新可用销量日期 " + latestSalesDate + "；同步状态 " + syncStatus.getState() + "。";
    }

    private String signalDescription(NoonSalesSyncReadModel syncStatus, LocalDate latestSalesDate) {
        if (syncStatus == null) {
            return "销量事实或同步状态未完整接入。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.BACKFILL_RUNNING) {
            return "销量历史回补任务正在运行，暂不生成业务判断。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.BACKFILL_FAILED) {
            return "销量历史回补任务失败，需要先处理同步问题。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.EMPTY_REPORT) {
            return "来源销量报表为空，不能把缺失事实解释为零销量。";
        }
        if (syncStatus.getState() == NoonSalesSyncSurfaceState.MISSING_MAPPING) {
            return "来源字段或店铺/站点映射异常，需要先修复数据质量。";
        }
        if (latestSalesDate == null) {
            return "当前范围需要同步或回补销量数据。";
        }
        return "当前范围最新可用销量日期为 " + latestSalesDate + "。";
    }

    private String evidenceDescription(
            AiOperationsDashboardScope scope,
            NoonSalesSyncReadModel syncStatus,
            LocalDate latestSalesDate
    ) {
        String syncState = syncStatus == null ? "not_connected" : syncStatus.getState().name();
        return "owner=" + scope.getOwnerUserId()
                + ";operator=" + scope.getOperatorUserId()
                + ";store=" + scope.getStoreCode()
                + ";site=" + scope.getSiteCode()
                + ";datePreset=" + scope.getDatePreset()
                + ";dateFrom=" + scope.getDateFrom()
                + ";dateTo=" + scope.getDateTo()
                + ";latestAvailableSalesDate=" + latestSalesDate
                + ";syncState=" + syncState
                + ";correctionState=" + (syncStatus == null ? null : syncStatus.getCorrectionState())
                + ";taskStatus=" + (syncStatus == null ? null : syncStatus.getTaskStatus())
                + ";failureReason=" + (syncStatus == null ? null : syncStatus.getFailureReason())
                + ";diagnosticSummary=" + (syncStatus == null ? null : syncStatus.getDiagnosticSummary())
                + ";dataSufficient=" + (syncStatus != null && syncStatus.isDataSufficient())
                + ";businessMetricsAllowed=" + (syncStatus != null && syncStatus.isBusinessMetricsAllowed())
                + ";sourceBatchIds=" + sourceBatchIds(syncStatus)
                + ";requiredWork=" + requiredWork(syncStatus);
    }

    private String sourceBatchIds(NoonSalesSyncReadModel syncStatus) {
        if (syncStatus == null || syncStatus.getSourceBatchIds().isEmpty()) {
            return "none";
        }
        return syncStatus.getSourceBatchIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
    }

    private String requiredWork(NoonSalesSyncReadModel syncStatus) {
        if (syncStatus == null || syncStatus.getRequiredWork().isEmpty()) {
            return "none";
        }
        return syncStatus.getRequiredWork().stream()
                .map(this::requiredWork)
                .collect(Collectors.joining("|"));
    }

    private String requiredWork(NoonSyncRequiredWork work) {
        if (work == null) {
            return "unknown";
        }
        String target = work.getTarget() == null
                ? "target=none"
                : "target=" + work.getTarget().getDateFrom() + ".." + work.getTarget().getDateTo();
        return work.getPlanKey() + ":" + work.getTriggerMode() + ":" + target;
    }
}
