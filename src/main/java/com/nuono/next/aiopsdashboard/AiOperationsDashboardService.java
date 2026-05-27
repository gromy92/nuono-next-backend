package com.nuono.next.aiopsdashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiOperationsDashboardService {

    private static final List<String> QUALITY_STATES = List.of(
            "not_connected",
            "ready",
            "sync_in_progress",
            "empty",
            "stale",
            "backfill_required",
            "backfill_running",
            "backfill_failed",
            "empty_report",
            "missing_mapping",
            "workspace_empty",
            "provider_unavailable",
            "runtime_disabled",
            "partial_success"
    );

    private final AiOperationsDashboardSignalProviderRegistry signalProviderRegistry;
    private final Supplier<LocalDate> todaySupplier;

    @Autowired
    public AiOperationsDashboardService(AiOperationsDashboardSignalProviderRegistry signalProviderRegistry) {
        this(signalProviderRegistry, LocalDate::now);
    }

    AiOperationsDashboardService(
            AiOperationsDashboardSignalProviderRegistry signalProviderRegistry,
            Supplier<LocalDate> todaySupplier
    ) {
        this.signalProviderRegistry = signalProviderRegistry;
        this.todaySupplier = todaySupplier;
    }

    public AiOperationsDashboardOverview overview(AiOperationsDashboardQuery query) {
        AiOperationsDashboardQuery safeQuery = query == null
                ? new AiOperationsDashboardQuery(null, null, null, null, "last7Days")
                : query;
        String datePreset = normalizeDatePreset(safeQuery.getDatePreset());
        LocalDate dateTo = todaySupplier.get();
        LocalDate dateFrom = resolveDateFrom(datePreset, dateTo);
        AiOperationsDashboardScope scope = new AiOperationsDashboardScope(
                safeQuery.getOwnerUserId(),
                safeQuery.getOperatorUserId(),
                safeQuery.getStoreCode(),
                safeQuery.getSiteCode(),
                datePreset,
                dateFrom,
                dateTo
        );
        AiOperationsDashboardContribution contribution = signalProviderRegistry.contribute(safeQuery, scope);
        List<AiOperationsDashboardOverview.Signal> signals = contribution.getSignals();
        String summaryState = signals.isEmpty() ? "empty" : "partial_success";
        List<AiOperationsDashboardOverview.MetricCard> metricCards = new ArrayList<>(List.of(
                metric("sales", "销量概览", "销量事实将在 A02 接入。"),
                metric("selection", "选品信号", "选品与 1688 信号将在 A03 接入。"),
                metric("operations", "履约与文件", "商品、文件、物流信号将在 A04 接入。")
        ));
        metricCards.addAll(contribution.getMetricCards());
        List<AiOperationsDashboardOverview.EvidenceItem> evidenceItems = new ArrayList<>(contribution.getEvidence());
        evidenceItems.addAll(signals.stream()
                .flatMap(signal -> signal.getEvidence().stream())
                .collect(Collectors.toList()));

        return new AiOperationsDashboardOverview(
                scope,
                new AiOperationsDashboardOverview.Summary(
                        "AI运营看板",
                        summaryState,
                        signals.isEmpty() ? "当前范围暂无已接入经营信号。" : "当前范围已有部分经营信号接入。"
                ),
                metricCards,
                signals,
                new AiOperationsDashboardOverview.AiSummary(
                        "AI今日总结",
                        "runtime_disabled",
                        "runtime_disabled",
                        null,
                        null
                ),
                new AiOperationsDashboardOverview.ItemCollection<>(
                        "runtime_disabled",
                        "AI建议",
                        List.of()
                ),
                new AiOperationsDashboardOverview.ItemCollection<>(
                        evidenceItems.isEmpty() ? "not_connected" : "partial_success",
                        "证据来源",
                        evidenceItems
                ),
                QUALITY_STATES
        );
    }

    private AiOperationsDashboardOverview.MetricCard metric(String key, String title, String description) {
        return new AiOperationsDashboardOverview.MetricCard(
                key,
                title,
                "not_connected",
                "not_connected",
                null,
                null,
                description
        );
    }

    private String normalizeDatePreset(String datePreset) {
        if (!StringUtils.hasText(datePreset)) {
            return "last7Days";
        }
        String normalized = datePreset.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("today".equals(lower)) {
            return "today";
        }
        if ("last30days".equals(lower)) {
            return "last30Days";
        }
        return "last7Days";
    }

    private LocalDate resolveDateFrom(String datePreset, LocalDate dateTo) {
        if ("today".equals(datePreset)) {
            return dateTo;
        }
        if ("last30Days".equals(datePreset)) {
            return dateTo.minusDays(29);
        }
        return dateTo.minusDays(6);
    }
}
