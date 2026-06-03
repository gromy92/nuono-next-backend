package com.nuono.next.productanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.sales.ProductLifecycleCalculationJobService;
import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleJobRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProductLifecycleAnalysisServiceTest {

    @Test
    void overviewUsesReadModelRowsAndSummary() {
        ProductLifecycleAnalysisReadModelRepository repository = new ProductLifecycleAnalysisReadModelRepository() {
            @Override
            public ProductLifecycleAnalysisSummaryView getSummary(ProductLifecycleAnalysisQuery query) {
                return new ProductLifecycleAnalysisSummaryView(query.getStoreCode(), query.getSiteCode(), 3, 2, 1);
            }

            @Override
            public List<ProductLifecycleAnalysisRowView> listRows(ProductLifecycleAnalysisQuery query) {
                return List.of(
                        new ProductLifecycleAnalysisRowView(
                                "MILKYWAYA09",
                                "z580978e7ed8f9491b50bz-1",
                                "Galaxy Star Projector",
                                "https://example.test/image.jpeg",
                                "milkyway",
                                "home_decor-lighting-table_lamps",
                                "stable",
                                "稳定",
                                "ready",
                                "可分析",
                                LocalDate.of(2026, 5, 21),
                                LocalDate.of(2026, 5, 1),
                                "official",
                                "DEFAULT_V1",
                                21,
                                15,
                                LocalDate.of(2026, 5, 20)
                        ),
                        new ProductLifecycleAnalysisRowView(
                                "MILKYWAYA01",
                                "zbd2a2638dca8ecc9337bz-1",
                                "Galaxy Projector",
                                null,
                                "milkyway",
                                "home_decor-lighting-table_lamps",
                                "data_insufficient",
                                "数据不足",
                                "data_insufficient",
                                "数据不足",
                                LocalDate.of(2026, 5, 21),
                                LocalDate.of(2026, 5, 13),
                                "official",
                                "DEFAULT_V1",
                                2,
                                0,
                                null
                        ),
                        new ProductLifecycleAnalysisRowView(
                                "MILKYWAY-test-2",
                                "e88b84c5724b433d",
                                "POCOCO Galaxy Lite Star Projector",
                                null,
                                null,
                                null,
                                "new",
                                "新品",
                                "ready",
                                "可分析",
                                LocalDate.of(2026, 5, 21),
                                LocalDate.of(2026, 5, 13),
                                "pulled",
                                "DEFAULT_V1",
                                0,
                                0,
                                null
                        )
                );
            }
        };
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(repository);

        ProductLifecycleAnalysisOverviewView overview = service.getOverview(new ProductLifecycleAnalysisQuery(
                10002L,
                "STR245027-NAE",
                "AE"
        ));

        assertEquals(2, overview.getSummary().getTotalProductCount());
        assertEquals(1, overview.getSummary().getReadyProductCount());
        assertEquals(1, overview.getSummary().getMissingParameterProductCount());
        assertEquals(2, overview.getRows().size());
        ProductLifecycleAnalysisRowView row = overview.getRows().get(0);
        assertEquals("MILKYWAYA09", row.getPartnerSku());
        assertEquals("Galaxy Star Projector", row.getProductTitle());
        assertEquals("稳定", row.getLifecycleLabel());
        assertEquals("可分析", row.getAnalysisStateLabel());
        assertEquals(21, row.getCurrentStock());
        assertEquals(15, row.getRecent30DaySales());
    }

    @Test
    void overviewAddsNinetyDayLifecycleProjectionToReadyRows() {
        ProductLifecycleAnalysisReadModelRepository repository = new SingleRowRepository(
                new ProductLifecycleAnalysisRowView(
                        "MILKYWAYA09",
                        "z580978e7ed8f9491b50bz-1",
                        "Galaxy Star Projector",
                        null,
                        "milkyway",
                        "home_decor-lighting-table_lamps",
                        "new",
                        "新品期",
                        "ready",
                        "可分析",
                        LocalDate.of(2026, 5, 21),
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 1),
                        "official",
                        "DEFAULT_V1",
                        21,
                        15,
                        LocalDate.of(2026, 5, 20)
                )
        );
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(
                repository,
                query -> new ProductLifecycleStagePeriodConfig(Map.of(
                        "new", 30,
                        "growth", 45,
                        "stable", 180,
                        "decline", 30
                )),
                new ProductLifecycleTimelineProjector()
        );

        ProductLifecycleAnalysisOverviewView overview = service.getOverview(new ProductLifecycleAnalysisQuery(
                10002L,
                "STR245027-NAE",
                "AE"
        ));

        assertEquals(1, overview.getSummary().getExpectedLifecycleChangeProductCount());
        ProductLifecycleAnalysisRowView row = overview.getRows().get(0);
        assertEquals("ready", row.getProjectionState());
        assertEquals(21, row.getCurrentStageElapsedDays());
        assertEquals(9, row.getCurrentStageRemainingDays());
        assertEquals("growth", row.getNextLifecycleCode());
        assertEquals(LocalDate.of(2026, 5, 31), row.getNextTransitionDate());
        assertEquals(90, row.getFutureTimeline().size());
    }

    @Test
    void overviewReturnsMissingProjectionStateWhenPeriodConfigIsIncomplete() {
        ProductLifecycleAnalysisReadModelRepository repository = new SingleRowRepository(
                new ProductLifecycleAnalysisRowView(
                        "MILKYWAYA10",
                        "z580978e7ed8f9491b50bz-2",
                        "Galaxy Star Projector Pro",
                        null,
                        "milkyway",
                        "home_decor-lighting-table_lamps",
                        "growth",
                        "成长期",
                        "ready",
                        "可分析",
                        LocalDate.of(2026, 5, 21),
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 5, 1),
                        "official",
                        "DEFAULT_V1",
                        21,
                        15,
                        LocalDate.of(2026, 5, 20)
                )
        );
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(
                repository,
                query -> new ProductLifecycleStagePeriodConfig(Map.of("new", 60)),
                new ProductLifecycleTimelineProjector()
        );

        ProductLifecycleAnalysisOverviewView overview = service.getOverview(new ProductLifecycleAnalysisQuery(
                10002L,
                "STR245027-NAE",
                "AE"
        ));

        assertEquals(0, overview.getSummary().getExpectedLifecycleChangeProductCount());
        ProductLifecycleAnalysisRowView row = overview.getRows().get(0);
        assertEquals("lifecycle_period_config_missing", row.getProjectionState());
        assertEquals("生命周期周期参数缺失，无法计算。", row.getProjectionMessage());
        assertEquals(0, row.getFutureTimeline().size());
        assertEquals(List.of("growth.durationDays"), row.getProjectionMissingRequirements());
    }

    @Test
    void overviewTreatsLegacyPulledListingDateAsMissingListingDate() {
        ProductLifecycleAnalysisReadModelRepository repository = new SingleRowRepository(
                new ProductLifecycleAnalysisRowView(
                        "MILKYWAYA06",
                        "zb432f2cac4d3162612b8z-1",
                        "Astronaut Star Space Projector",
                        null,
                        "milkyway",
                        "home_decor-lighting-table_lamps",
                        "new",
                        "新品",
                        "ready",
                        "可分析",
                        LocalDate.of(2026, 5, 21),
                        LocalDate.of(2026, 5, 14),
                        LocalDate.of(2026, 5, 14),
                        "pulled",
                        "DEFAULT_V1",
                        0,
                        0,
                        null
                )
        );
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(
                repository,
                query -> new ProductLifecycleStagePeriodConfig(Map.of(
                        "new", 60,
                        "growth", 45,
                        "stable", 180,
                        "decline", 30
                )),
                new ProductLifecycleTimelineProjector()
        );

        ProductLifecycleAnalysisOverviewView overview = service.getOverview(new ProductLifecycleAnalysisQuery(
                10002L,
                "STR245027-NAE",
                "AE"
        ));

        assertEquals(0, overview.getSummary().getReadyProductCount());
        assertEquals(1, overview.getSummary().getMissingParameterProductCount());
        ProductLifecycleAnalysisRowView row = overview.getRows().get(0);
        assertEquals("data_insufficient", row.getLifecycleCode());
        assertEquals("数据不足", row.getLifecycleLabel());
        assertEquals("data_insufficient", row.getAnalysisState());
        assertEquals(null, row.getListingDate());
        assertEquals("missing", row.getListingDateSource());
        assertEquals("lifecycle_data_insufficient", row.getProjectionState());
    }

    @Test
    void overviewTreatsPartialPvListingDateAsMissingListingDate() {
        ProductLifecycleAnalysisReadModelRepository repository = new SingleRowRepository(
                new ProductLifecycleAnalysisRowView(
                        "MILKYWAYA09",
                        "z580978e7ed8f9491b50bz-1",
                        "Galaxy Star Projector",
                        null,
                        "milkyway",
                        "home_decor-lighting-table_lamps",
                        "stable",
                        "稳定",
                        "ready",
                        "可分析",
                        LocalDate.of(2026, 5, 21),
                        LocalDate.of(2026, 5, 1),
                        null,
                        "pv",
                        "DEFAULT_V1",
                        21,
                        12,
                        LocalDate.of(2026, 5, 24)
                )
        );
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(repository);

        ProductLifecycleAnalysisOverviewView overview = service.getOverview(new ProductLifecycleAnalysisQuery(
                10002L,
                "STR245027-NAE",
                "AE"
        ));

        assertEquals(0, overview.getSummary().getReadyProductCount());
        ProductLifecycleAnalysisRowView row = overview.getRows().get(0);
        assertEquals("data_insufficient", row.getLifecycleCode());
        assertEquals("data_insufficient", row.getAnalysisState());
        assertEquals(null, row.getListingDate());
        assertEquals("missing", row.getListingDateSource());
    }

    @Test
    void overviewKeepsFullSummaryWhenReadRowsAreDisplayLimited() {
        ProductLifecycleAnalysisReadModelRepository repository = new ProductLifecycleAnalysisReadModelRepository() {
            @Override
            public ProductLifecycleAnalysisSummaryView getSummary(ProductLifecycleAnalysisQuery query) {
                return new ProductLifecycleAnalysisSummaryView(query.getStoreCode(), query.getSiteCode(), 408, 40, 368);
            }

            @Override
            public List<ProductLifecycleAnalysisRowView> listRows(ProductLifecycleAnalysisQuery query) {
                return IntStream.range(0, 200)
                        .mapToObj(index -> new ProductLifecycleAnalysisRowView(
                                "PAPERSAYS" + index,
                                "z" + index,
                                "Papersay Product " + index,
                                null,
                                "PAPERSAY",
                                "stationery-labels_indexes_stamps-identification_badges",
                                "data_insufficient",
                                "数据不足",
                                "data_insufficient",
                                "数据不足",
                                LocalDate.of(2026, 6, 2),
                                null,
                                "missing",
                                "DEFAULT_V1",
                                0,
                                0,
                                null
                        ))
                        .collect(Collectors.toList());
            }
        };
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(repository);

        ProductLifecycleAnalysisOverviewView overview = service.getOverview(new ProductLifecycleAnalysisQuery(
                307L,
                "STR108065-NSA",
                "SA"
        ));

        assertEquals(408, overview.getSummary().getTotalProductCount());
        assertEquals(40, overview.getSummary().getReadyProductCount());
        assertEquals(368, overview.getSummary().getMissingParameterProductCount());
        assertEquals(200, overview.getRows().size());
    }

    @Test
    void recalculateRunsLifecycleJobWithManualScopeAndReturnsJobSummary() {
        RecordingJobService jobService = new RecordingJobService(new ProductLifecycleJobRecord(
                72010L,
                307L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2026, 5, 22),
                "DEFAULT_V1",
                "succeeded",
                37,
                2,
                1,
                12,
                null,
                LocalDateTime.of(2026, 5, 23, 9, 0),
                LocalDateTime.of(2026, 5, 23, 9, 1),
                10002L,
                "product_analysis_manual"
        ));
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(
                new EmptyRepository(),
                query -> new ProductLifecycleStagePeriodConfig(Map.of()),
                new ProductLifecycleTimelineProjector(),
                jobService,
                () -> LocalDate.of(2026, 5, 22)
        );

        ProductLifecycleAnalysisRecalculationView view = service.recalculate(
                new ProductLifecycleAnalysisQuery(307L, "STR245027-NAE", "AE"),
                10002L
        );

        assertEquals(307L, jobService.capturedScope.getOwnerUserId());
        assertEquals("STR245027-NAE", jobService.capturedScope.getStoreCode());
        assertEquals("AE", jobService.capturedScope.getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 22), jobService.capturedScope.getAnchorDate());
        assertEquals("DEFAULT_V1", jobService.capturedScope.getRuleVersion());
        assertEquals(true, jobService.capturedScope.isRerun());
        assertEquals(10002L, jobService.capturedScope.getTriggeredByUserId());
        assertEquals("product_analysis_manual", jobService.capturedScope.getTriggerSource());
        assertEquals(72010L, view.getJobId());
        assertEquals("succeeded", view.getStatus());
        assertEquals("生命周期计算完成。", view.getMessage());
        assertEquals(37, view.getProcessedCount());
        assertEquals(2, view.getChangedCount());
        assertEquals(1, view.getHeldCount());
        assertEquals(12, view.getDataInsufficientCount());
    }

    @Test
    void resolveDataOwnerUsesProductMasterOwnerAndFallsBackToAccessOwner() {
        OwnerResolvingRepository repository = new OwnerResolvingRepository(307L);
        ProductLifecycleAnalysisService service = new ProductLifecycleAnalysisService(repository);

        assertEquals(307L, service.resolveDataOwnerUserId("STR245027-NAE", "AE", 10002L));

        repository.ownerUserId = null;
        assertEquals(10002L, service.resolveDataOwnerUserId("STR245027-NAE", "AE", 10002L));
    }

    private static class SingleRowRepository implements ProductLifecycleAnalysisReadModelRepository {
        private final ProductLifecycleAnalysisRowView row;

        private SingleRowRepository(ProductLifecycleAnalysisRowView row) {
            this.row = row;
        }

        @Override
        public ProductLifecycleAnalysisSummaryView getSummary(ProductLifecycleAnalysisQuery query) {
            return new ProductLifecycleAnalysisSummaryView(query.getStoreCode(), query.getSiteCode(), 1, 1, 0);
        }

        @Override
        public List<ProductLifecycleAnalysisRowView> listRows(ProductLifecycleAnalysisQuery query) {
            return List.of(row);
        }
    }

    private static class EmptyRepository implements ProductLifecycleAnalysisReadModelRepository {
        @Override
        public ProductLifecycleAnalysisSummaryView getSummary(ProductLifecycleAnalysisQuery query) {
            return ProductLifecycleAnalysisSummaryView.empty(query.getStoreCode(), query.getSiteCode());
        }

        @Override
        public List<ProductLifecycleAnalysisRowView> listRows(ProductLifecycleAnalysisQuery query) {
            return List.of();
        }
    }

    private static class OwnerResolvingRepository extends EmptyRepository {
        private Long ownerUserId;

        private OwnerResolvingRepository(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        @Override
        public Long findDataOwnerUserId(String storeCode, String siteCode) {
            return ownerUserId;
        }
    }

    private static class RecordingJobService extends ProductLifecycleCalculationJobService {
        private final ProductLifecycleJobRecord job;
        private ProductLifecycleCalculationScope capturedScope;

        private RecordingJobService(ProductLifecycleJobRecord job) {
            super(null, null, null, null, null, null, null);
            this.job = job;
        }

        @Override
        public ProductLifecycleJobRecord run(ProductLifecycleCalculationScope scope) {
            this.capturedScope = scope;
            return job;
        }
    }
}
