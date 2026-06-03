package com.nuono.next.productanalysis;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductLifecycleAnalysisService {

    private static final int FORECAST_WINDOW_DAYS = 90;

    private final ProductLifecycleAnalysisReadModelRepository repository;
    private final ProductLifecycleStagePeriodProvider stagePeriodProvider;
    private final ProductLifecycleTimelineProjector timelineProjector;

    public ProductLifecycleAnalysisService(ProductLifecycleAnalysisReadModelRepository repository) {
        this(repository, query -> new ProductLifecycleStagePeriodConfig(Map.of()), new ProductLifecycleTimelineProjector());
    }

    @Autowired
    public ProductLifecycleAnalysisService(
            ProductLifecycleAnalysisReadModelRepository repository,
            ProductLifecycleStagePeriodProvider stagePeriodProvider,
            ProductLifecycleTimelineProjector timelineProjector
    ) {
        this.repository = repository;
        this.stagePeriodProvider = stagePeriodProvider;
        this.timelineProjector = timelineProjector;
    }

    public ProductLifecycleAnalysisOverviewView getOverview(ProductLifecycleAnalysisQuery query) {
        ProductLifecycleStagePeriodConfig periodConfig = stagePeriodProvider.resolveStagePeriods(query);
        List<ProductLifecycleAnalysisRowView> rows = repository.listRows(query).stream()
                .filter(row -> !isLocalTestProduct(row))
                .map(this::normalizeMissingListingDate)
                .map(row -> row.withProjection(project(row, periodConfig)))
                .collect(Collectors.toList());
        return new ProductLifecycleAnalysisOverviewView(summaryFromRows(query, rows), rows);
    }

    private ProductLifecycleAnalysisRowView normalizeMissingListingDate(ProductLifecycleAnalysisRowView row) {
        if (row.getListingDate() == null
                || "missing".equals(row.getListingDateSource())
                || "pulled".equals(row.getListingDateSource())) {
            return row.withMissingListingDateState();
        }
        if (isPartialFactWindowListingEvidence(row)) {
            return row.withMissingListingDateState();
        }
        return row;
    }

    private boolean isPartialFactWindowListingEvidence(ProductLifecycleAnalysisRowView row) {
        if (!"pv".equals(row.getListingDateSource()) && !"sales".equals(row.getListingDateSource())) {
            return false;
        }
        if (row.getListingDate() == null || row.getLatestFactDate() == null) {
            return true;
        }
        long observedDays = ChronoUnit.DAYS.between(row.getListingDate(), row.getLatestFactDate()) + 1;
        return observedDays > 0 && observedDays < 60;
    }

    private ProductLifecycleTimelineProjection project(
            ProductLifecycleAnalysisRowView row,
            ProductLifecycleStagePeriodConfig periodConfig
    ) {
        return timelineProjector.project(new ProductLifecycleTimelineProjectionInput(
                row.getLifecycleCode(),
                row.getLifecycleLabel(),
                currentStageStartDate(row),
                row.getAnalysisDate(),
                periodConfig,
                FORECAST_WINDOW_DAYS
        ));
    }

    private LocalDate currentStageStartDate(ProductLifecycleAnalysisRowView row) {
        if (row.getCurrentStageStartDate() != null) {
            return row.getCurrentStageStartDate();
        }
        if ("new".equals(row.getLifecycleCode())) {
            return row.getListingDate();
        }
        return null;
    }

    private ProductLifecycleAnalysisSummaryView summaryFromRows(
            ProductLifecycleAnalysisQuery query,
            List<ProductLifecycleAnalysisRowView> rows
    ) {
        int readyCount = 0;
        int expectedLifecycleChangeCount = 0;
        for (ProductLifecycleAnalysisRowView row : rows) {
            if ("ready".equals(row.getAnalysisState())) {
                readyCount += 1;
            }
            if ("ready".equals(row.getProjectionState()) && row.getNextTransitionDate() != null) {
                expectedLifecycleChangeCount += 1;
            }
        }
        return new ProductLifecycleAnalysisSummaryView(
                query.getStoreCode(),
                query.getSiteCode(),
                rows.size(),
                readyCount,
                rows.size() - readyCount,
                expectedLifecycleChangeCount,
                FORECAST_WINDOW_DAYS
        );
    }

    private boolean isLocalTestProduct(ProductLifecycleAnalysisRowView row) {
        String partnerSku = normalize(row.getPartnerSku());
        if (partnerSku.matches("\\d+") || partnerSku.contains("test")) {
            return true;
        }
        String title = normalize(row.getProductTitle());
        return title.contains("[pm-smoke") || title.contains("[pm-round") || title.contains("[pm-flow");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
