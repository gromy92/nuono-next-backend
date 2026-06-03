package com.nuono.next.productanalysis;

import com.nuono.next.sales.ProductLifecycleCalculationJobService;
import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleJobRecord;
import com.nuono.next.sales.ProductLifecycleResult;
import com.nuono.next.sales.ProductLifecycleSchedulePolicy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductLifecycleAnalysisService {

    private static final int FORECAST_WINDOW_DAYS = 90;
    private static final String MANUAL_TRIGGER_SOURCE = "product_analysis_manual";

    private final ProductLifecycleAnalysisReadModelRepository repository;
    private final ProductLifecycleStagePeriodProvider stagePeriodProvider;
    private final ProductLifecycleTimelineProjector timelineProjector;
    private final ProductLifecycleCalculationJobService jobService;
    private final Supplier<LocalDate> anchorDateSupplier;

    public ProductLifecycleAnalysisService(ProductLifecycleAnalysisReadModelRepository repository) {
        this(
                repository,
                query -> new ProductLifecycleStagePeriodConfig(Map.of()),
                new ProductLifecycleTimelineProjector(),
                null,
                () -> LocalDate.now().minusDays(1)
        );
    }

    public ProductLifecycleAnalysisService(
            ProductLifecycleAnalysisReadModelRepository repository,
            ProductLifecycleStagePeriodProvider stagePeriodProvider,
            ProductLifecycleTimelineProjector timelineProjector
    ) {
        this(repository, stagePeriodProvider, timelineProjector, null, () -> LocalDate.now().minusDays(1));
    }

    @Autowired
    public ProductLifecycleAnalysisService(
            ProductLifecycleAnalysisReadModelRepository repository,
            ProductLifecycleStagePeriodProvider stagePeriodProvider,
            ProductLifecycleTimelineProjector timelineProjector,
            ProductLifecycleCalculationJobService jobService,
            ProductLifecycleSchedulePolicy schedulePolicy
    ) {
        this(repository, stagePeriodProvider, timelineProjector, jobService, schedulePolicy::defaultAnchorDate);
    }

    ProductLifecycleAnalysisService(
            ProductLifecycleAnalysisReadModelRepository repository,
            ProductLifecycleStagePeriodProvider stagePeriodProvider,
            ProductLifecycleTimelineProjector timelineProjector,
            ProductLifecycleCalculationJobService jobService,
            Supplier<LocalDate> anchorDateSupplier
    ) {
        this.repository = repository;
        this.stagePeriodProvider = stagePeriodProvider;
        this.timelineProjector = timelineProjector;
        this.jobService = jobService;
        this.anchorDateSupplier = anchorDateSupplier;
    }

    public ProductLifecycleAnalysisOverviewView getOverview(ProductLifecycleAnalysisQuery query) {
        ProductLifecycleStagePeriodConfig periodConfig = stagePeriodProvider.resolveStagePeriods(query);
        ProductLifecycleAnalysisSummaryView persistedSummary = repository.getSummary(query);
        List<ProductLifecycleAnalysisRowView> rawRows = repository.listRows(query);
        List<ProductLifecycleAnalysisRowView> rows = rawRows.stream()
                .filter(row -> !isLocalTestProduct(row))
                .map(this::normalizeMissingListingDate)
                .map(row -> row.withProjection(project(row, periodConfig)))
                .collect(Collectors.toList());
        return new ProductLifecycleAnalysisOverviewView(
                summaryForOverview(query, persistedSummary, rawRows.size(), rows),
                rows
        );
    }

    public Long resolveDataOwnerUserId(String storeCode, String siteCode, Long fallbackOwnerUserId) {
        Long dataOwnerUserId = repository.findDataOwnerUserId(storeCode, siteCode);
        return dataOwnerUserId == null ? fallbackOwnerUserId : dataOwnerUserId;
    }

    public ProductLifecycleAnalysisRecalculationView recalculate(
            ProductLifecycleAnalysisQuery query,
            Long triggeredByUserId
    ) {
        if (jobService == null) {
            throw new IllegalStateException("商品生命周期计算服务不可用。");
        }
        LocalDate anchorDate = anchorDateSupplier == null ? LocalDate.now().minusDays(1) : anchorDateSupplier.get();
        ProductLifecycleJobRecord job = jobService.runHistoricalBackfill(new ProductLifecycleCalculationScope(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                anchorDate,
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                true,
                false,
                triggeredByUserId,
                MANUAL_TRIGGER_SOURCE
        ));
        return new ProductLifecycleAnalysisRecalculationView(
                job.getId(),
                job.getStatus(),
                messageFor(job.getStatus()),
                job.getStoreCode(),
                job.getSiteCode(),
                job.getAnchorDate(),
                job.getProcessedCount(),
                job.getChangedCount(),
                job.getHeldCount(),
                job.getDataInsufficientCount()
        );
    }

    private String messageFor(String status) {
        if ("succeeded".equals(status)) {
            return "生命周期计算完成。";
        }
        if ("partial_failed".equals(status)) {
            return "生命周期计算部分失败，请查看任务失败明细。";
        }
        return "生命周期计算失败，请检查数据和配置。";
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

    private ProductLifecycleAnalysisSummaryView summaryForOverview(
            ProductLifecycleAnalysisQuery query,
            ProductLifecycleAnalysisSummaryView persistedSummary,
            int rawRowCount,
            List<ProductLifecycleAnalysisRowView> rows
    ) {
        ProductLifecycleAnalysisSummaryView rowSummary = summaryFromRows(query, rows);
        if (persistedSummary != null && persistedSummary.getTotalProductCount() > rawRowCount) {
            return new ProductLifecycleAnalysisSummaryView(
                    query.getStoreCode(),
                    query.getSiteCode(),
                    persistedSummary.getTotalProductCount(),
                    persistedSummary.getReadyProductCount(),
                    persistedSummary.getMissingParameterProductCount(),
                    rowSummary.getExpectedLifecycleChangeProductCount(),
                    FORECAST_WINDOW_DAYS
            );
        }
        return rowSummary;
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
