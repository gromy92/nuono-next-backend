package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nuono.next.noonsync.NoonBusinessSyncStatusService;
import com.nuono.next.noonsync.NoonSalesSyncSurfaceState;
import com.nuono.next.noonsync.NoonSyncFoundationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesAnalyticsServiceTest {

    @Test
    void listsDailySalesFactsByAuthorizedScopeAndProductFilters() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        SalesAnalyticsService service = new SalesAnalyticsService(repository);
        SalesFactQuery query = new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                "SAMPLE-SKU-001",
                null
        );

        SalesDailyFactsView view = service.listDailyFacts(query);

        assertEquals(10002L, repository.lastQuery.getOwnerUserId());
        assertEquals("STR245027-SAU", repository.lastQuery.getStoreCode());
        assertEquals("SA", repository.lastQuery.getSiteCode());
        assertEquals(LocalDate.of(2026, 4, 30), repository.lastQuery.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 4), repository.lastQuery.getDateTo());
        assertEquals("SAMPLE-SKU-001", repository.lastQuery.getPartnerSku());
        assertEquals(1, view.getItems().size());
        assertEquals(1, view.getTotal());
        assertEquals(1, view.getItems().get(0).getNetUnits());
    }

    @Test
    void returnsEmptyReportQualityStateWhenRangeHasNoUsableFacts() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of();
        repository.batches = List.of(new SalesImportBatchRecord(
                10001L,
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                "header-only.csv",
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                null,
                null,
                0,
                0,
                0,
                "empty",
                null,
                null
        ));
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        SalesDailyFactsView view = service.listDailyFacts(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5),
                null,
                null
        ));

        assertEquals(0, view.getTotal());
        assertEquals(1, view.getQualityStates().size());
        assertEquals("empty_report", view.getQualityStates().get(0).getCode());
        assertEquals(10001L, view.getQualityStates().get(0).getSourceBatchId());
    }

    @Test
    void salesAnalyticsSummaryAndFactsExposeSharedSyncStatusForNoData() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of();
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                new NoonBusinessSyncStatusService(new NoonSyncFoundationService())
        );

        SalesAnalyticsSummary summary = service.getSummary(defaultQuery());
        SalesDailyFactsView factsView = service.listDailyFacts(defaultQuery());

        assertFalse(summary.isBusinessMetricsAvailable());
        assertEquals(NoonSalesSyncSurfaceState.NO_DATA_BACKFILL_REQUIRED, summary.getSyncStatus().getState());
        assertEquals(NoonSalesSyncSurfaceState.NO_DATA_BACKFILL_REQUIRED, factsView.getSyncStatus().getState());
    }

    @Test
    void salesAnalyticsMarksStaleLatestSalesDateWithoutPretendingMissingDaysAreZero() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(
                LocalDate.of(2026, 5, 10),
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                1,
                1,
                0,
                1,
                BigDecimal.TEN,
                null,
                null,
                null,
                null
        ));
        repository.latestFactDate = LocalDate.of(2026, 5, 10);
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                new NoonBusinessSyncStatusService(new NoonSyncFoundationService())
        );

        SalesAnalyticsSummary summary = service.getSummary(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 19),
                null,
                null
        ));

        assertEquals(NoonSalesSyncSurfaceState.STALE_LATEST_SALES, summary.getSyncStatus().getState());
        assertEquals(LocalDate.of(2026, 5, 10), summary.getSyncStatus().getLatestAvailableSalesDate());
    }

    @Test
    void summarizesDailySalesFactsForWorkbenchMetrics() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 10, 9, 2, 8, new BigDecimal("120.50"), 100, 200, new BigDecimal("50"), new BigDecimal("80")),
                fact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-002", "Z57C90A4184D0CFD75218Z-2", 3, 2, 1, 2, new BigDecimal("30.25"), null, 50, null, new BigDecimal("60"))
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        SalesAnalyticsSummary summary = service.getSummary(defaultQuery());

        assertEquals(10, summary.getNetUnits());
        assertEquals(13, summary.getGrossUnits());
        assertEquals(11, summary.getShippedUnits());
        assertEquals(3, summary.getCancelledUnits());
        assertEquals(new BigDecimal("150.75"), summary.getRevenueShipped());
        assertEquals(100, summary.getYourVisitors());
        assertEquals(250, summary.getTotalVisitors());
        assertEquals(new BigDecimal("50"), summary.getConversionVisitorsPercentage());
        assertEquals(new BigDecimal("70"), summary.getBuyBoxVisitorPercentage());
    }

    @Test
    void aggregatesSalesTrendsByRequestedGranularity() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 10, 9, 2, 8, new BigDecimal("120.50"), 100, 200, new BigDecimal("50"), new BigDecimal("80")),
                fact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-002", "Z57C90A4184D0CFD75218Z-2", 3, 2, 1, 2, new BigDecimal("30.25"), null, 50, null, new BigDecimal("60")),
                fact(LocalDate.of(2026, 5, 25), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 1, 1, 0, 1, new BigDecimal("9.99"), 5, 10, new BigDecimal("20"), null)
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        List<SalesTrendBucket> trends = service.getTrends(defaultQuery(), "week");

        assertEquals(2, trends.size());
        assertEquals(LocalDate.of(2026, 5, 18), trends.get(0).getBucketStart());
        assertEquals("2026-W21", trends.get(0).getBucketLabel());
        assertEquals(10, trends.get(0).getNetUnits());
        assertEquals(new BigDecimal("150.75"), trends.get(0).getRevenueShipped());
        assertEquals(LocalDate.of(2026, 5, 25), trends.get(1).getBucketStart());
        assertEquals("2026-W22", trends.get(1).getBucketLabel());
        assertEquals(1, trends.get(1).getNetUnits());
    }

    @Test
    void groupsProductRowsWithLatestFactDateAndSourceSystems() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 10, 9, 2, 8, new BigDecimal("120.50"), 100, 200, new BigDecimal("50"), new BigDecimal("80")),
                legacyFact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 2),
                fact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-002", "Z57C90A4184D0CFD75218Z-2", 3, 2, 1, 2, new BigDecimal("30.25"), null, 50, null, new BigDecimal("60"))
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        List<SalesProductRow> rows = service.listProductRows(defaultQuery());

        assertEquals(2, rows.size());
        assertEquals("SAMPLE-SKU-001", rows.get(0).getPartnerSku());
        assertEquals("Z57C90A4184D0CFD75218Z-1", rows.get(0).getSku());
        assertEquals(LocalDate.of(2026, 5, 19), rows.get(0).getLatestFactDate());
        assertEquals(10, rows.get(0).getNetUnits());
        assertEquals(List.of("legacy_product_sales_data", "noon_productviewsandsalesdata"), rows.get(0).getSourceSystems());
        assertEquals("new", rows.get(0).getLifecycleCode());
        assertEquals("新品", rows.get(0).getLifecycleLabel());
        assertEquals("SAMPLE-SKU-002", rows.get(1).getPartnerSku());
    }

    @Test
    void groupsProductRowsByPartnerSkuWhenExternalSkuChanges() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z-OLD-1", 4, 4, 0, 4, new BigDecimal("40.00"), 20, 30, null, null),
                fact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-001", "Z-NEW-1", 6, 6, 0, 6, new BigDecimal("60.00"), 25, 35, null, null)
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        List<SalesProductRow> rows = service.listProductRows(defaultQuery());

        assertEquals(1, rows.size());
        assertEquals("SAMPLE-SKU-001", rows.get(0).getPartnerSku());
        assertEquals("Z-NEW-1", rows.get(0).getSku());
        assertEquals(10, rows.get(0).getNetUnits());
        assertEquals(LocalDate.of(2026, 5, 19), rows.get(0).getLatestFactDate());
    }

    @Test
    void productRowsExcludeFactsWithoutBusinessPsku() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "-", "Z-MISSING-1", 4, 4, 0, 4, new BigDecimal("40.00"), 20, 30, null, null),
                fact(LocalDate.of(2026, 5, 19), "-", "Z-MISSING-2", 6, 6, 0, 6, new BigDecimal("60.00"), 25, 35, null, null),
                fact(LocalDate.of(2026, 5, 20), "SAMPLE-SKU-001", "Z-VALID-1", 8, 8, 0, 8, new BigDecimal("80.00"), 30, 40, null, null)
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        List<SalesProductRow> rows = service.listProductRows(defaultQuery());

        assertEquals(1, rows.size());
        assertEquals("SAMPLE-SKU-001", rows.get(0).getPartnerSku());
        assertEquals(8, rows.get(0).getNetUnits());
    }

    @Test
    void productRowsExposeLatestFactMetricsBesideRangeTotals() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 4, 4, 0, 4, new BigDecimal("40.00"), 20, 30, new BigDecimal("20"), null),
                fact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 3, 2, 1, 2, new BigDecimal("25.00"), 30, 40, new BigDecimal("40"), null),
                legacyFact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 1)
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        SalesProductRow row = service.listProductRows(defaultQuery()).get(0);

        assertEquals(7, row.getNetUnits());
        assertEquals(new BigDecimal("75.00"), row.getRevenueShipped());
        assertEquals(3, row.getLatestNetUnits());
        assertEquals(4, row.getLatestGrossUnits());
        assertEquals(3, row.getLatestShippedUnits());
        assertEquals(1, row.getLatestCancelledUnits());
        assertEquals(new BigDecimal("35.00"), row.getLatestRevenueShipped());
        assertEquals(30, row.getLatestYourVisitors());
        assertEquals(new BigDecimal("40"), row.getLatestConversionVisitorsPercentage());
    }

    @Test
    void salesFactQueryCarriesBatchPartnerSkuFilterToRepository() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        service.getSummary(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("SAMPLE-SKU-001", "SAMPLE-SKU-002")
        ));

        assertEquals(List.of("SAMPLE-SKU-001", "SAMPLE-SKU-002"), repository.lastQuery.getPartnerSkuList());
    }

    @Test
    void productRowsPreferPersistedLifecycleCurrentStateWhenAvailable() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(
                LocalDate.of(2026, 5, 19),
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                3,
                3,
                0,
                3,
                BigDecimal.TEN,
                12,
                null,
                null,
                null
        ));
        RecordingLifecycleStateRepository lifecycleRepository = new RecordingLifecycleStateRepository();
        lifecycleRepository.currentState = lifecycleState("decline", "衰退", "ready", "DEFAULT_V1");
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                lifecycleRepository
        );

        List<SalesProductRow> rows = service.listProductRows(defaultQuery());

        assertEquals("decline", rows.get(0).getLifecycleCode());
        assertEquals("衰退", rows.get(0).getLifecycleLabel());
        assertEquals("ready", rows.get(0).getLifecycleQualityState());
        assertEquals("DEFAULT_V1", rows.get(0).getLifecycleRuleVersion());
        assertEquals("STR245027-SAU", lifecycleRepository.lastQuery.getStoreCode());
        assertEquals("SAMPLE-SKU-001", lifecycleRepository.lastQuery.getPartnerSku());
    }

    @Test
    void productRowsExposeDataInsufficientLifecycleStateFromBackend() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(
                LocalDate.of(2026, 5, 19),
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                1,
                1,
                0,
                1,
                BigDecimal.TEN,
                null,
                null,
                null,
                null
        ));
        RecordingLifecycleStateRepository lifecycleRepository = new RecordingLifecycleStateRepository();
        lifecycleRepository.currentState = lifecycleState(
                "data_insufficient",
                "数据不足",
                "pv_unresolvable",
                "DEFAULT_V1"
        );
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                lifecycleRepository
        );

        SalesProductRow row = service.listProductRows(defaultQuery()).get(0);

        assertEquals("data_insufficient", row.getLifecycleCode());
        assertEquals("数据不足", row.getLifecycleLabel());
        assertEquals("pv_unresolvable", row.getLifecycleQualityState());
        assertEquals("{\"reason\":\"pv_unresolvable\"}", row.getLifecycleEvidenceJson());
    }

    @Test
    void productRowsExposeStockoutHoldWarningFromPersistedLifecycleQualityState() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(
                LocalDate.of(2026, 5, 19),
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                1,
                1,
                0,
                1,
                BigDecimal.TEN,
                10,
                null,
                null,
                null
        ));
        RecordingLifecycleStateRepository lifecycleRepository = new RecordingLifecycleStateRepository();
        lifecycleRepository.currentState = lifecycleState(
                "stable",
                "稳定",
                "stockout_hold",
                "DEFAULT_V1"
        );
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                lifecycleRepository
        );

        SalesProductRow row = service.listProductRows(defaultQuery()).get(0);

        assertEquals("stockout_hold", row.getLifecycleQualityState());
        assertEquals(List.of("lifecycle_stockout_hold"), row.getLifecycleWarningCodes());
    }

    @Test
    void productRowsExposePendingStateWhenLifecycleHasNotBeenCalculated() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(
                LocalDate.of(2026, 5, 19),
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                1,
                1,
                0,
                1,
                BigDecimal.TEN,
                10,
                null,
                null,
                null
        ));
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                new RecordingLifecycleStateRepository()
        );

        SalesProductRow row = service.listProductRows(defaultQuery()).get(0);

        assertEquals("pending", row.getLifecycleCode());
        assertEquals("待计算", row.getLifecycleLabel());
        assertEquals("pending", row.getLifecycleQualityState());
    }

    @Test
    void productRowsAndExportRespectLifecycleCodeFilterFromAuthorizedQuery() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "STABLE-PSKU", "ZSTABLE-1", 7, 7, 0, 7, BigDecimal.TEN, 77, 120, null, null),
                fact(LocalDate.of(2026, 5, 18), "NEW-PSKU", "ZNEW-1", 3, 3, 0, 3, BigDecimal.TEN, 30, 90, null, null)
        );
        RecordingLifecycleStateRepository lifecycleRepository = new RecordingLifecycleStateRepository();
        lifecycleRepository.currentStates.put(
                "STABLE-PSKU",
                lifecycleState("stable", "稳定", "ready", "DEFAULT_V1")
        );
        lifecycleRepository.currentStates.put(
                "NEW-PSKU",
                lifecycleState("new", "新品", "ready", "DEFAULT_V1")
        );
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                lifecycleRepository
        );
        SalesFactQuery query = new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "stable"
        );

        List<SalesProductRow> rows = service.listProductRows(query);
        String csv = service.exportDailyFactsCsv(query);

        assertEquals(1, rows.size());
        assertEquals("STABLE-PSKU", rows.get(0).getPartnerSku());
        assertEquals("stable", rows.get(0).getLifecycleCode());
        assertEquals(true, csv.contains("STABLE-PSKU"));
        assertEquals(false, csv.contains("NEW-PSKU"));
    }

    @Test
    void productRowsExposeProductDimensionQualitySignals() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 6), "MATCHED-PSKU", "ZMATCHED-1", 1, 1, 0, 1, BigDecimal.TEN, 20, 50, new BigDecimal("4"), null),
                fact(LocalDate.of(2026, 5, 12), "MATCHED-PSKU", "ZMATCHED-1", 2, 2, 0, 2, BigDecimal.TEN, 40, 60, new BigDecimal("4"), null),
                fact(LocalDate.of(2026, 5, 18), "PARTIAL-PSKU", "ZPARTIAL-1", 4, 4, 0, 4, BigDecimal.TEN, 50, 70, new BigDecimal("3"), null),
                fact(LocalDate.of(2026, 5, 18), "UNMATCHED-PSKU", "ZUNMATCHED-1", 8, 8, 0, 8, BigDecimal.TEN, 700, 800, null, null),
                fact(LocalDate.of(2026, 5, 19), "UNMATCHED-PSKU", "ZUNMATCHED-1", 8, 8, 0, 8, BigDecimal.TEN, 700, 800, null, null)
        );
        RecordingProductDimensionRepository dimensionRepository = new RecordingProductDimensionRepository();
        dimensionRepository.snapshots = List.of(
                new SalesProductDimensionSnapshot(
                        "MATCHED-PSKU",
                        "ZMATCHED-1",
                        "Paper Says",
                        "Stationery-Paper-Sticker"
                ),
                new SalesProductDimensionSnapshot(
                        "PARTIAL-PSKU",
                        "ZPARTIAL-1",
                        " ",
                        null
                )
        );
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                null,
                dimensionRepository
        );

        List<SalesProductRow> rows = service.listProductRows(defaultQuery());

        SalesProductRow unmatched = productRow(rows, "UNMATCHED-PSKU");
        assertEquals("UNMATCHED-PSKU", unmatched.getPartnerSku());
        assertEquals(false, unmatched.isDimensionMatched());
        assertEquals(null, unmatched.getDimensionSource());
        assertEquals(null, unmatched.getBrand());
        assertEquals(null, unmatched.getProductFulltype());
        assertEquals(List.of("product_dimension_missing"), unmatched.getDimensionQualityCodes());
        assertEquals(List.of("sales_fact_ready", "product_dimension_missing"), unmatched.getDataQualityCodes());

        SalesProductRow matched = productRow(rows, "MATCHED-PSKU");
        assertEquals("MATCHED-PSKU", matched.getPartnerSku());
        assertEquals("Paper Says", matched.getBrand());
        assertEquals("Stationery-Paper-Sticker", matched.getProductFulltype());
        assertEquals(true, matched.isDimensionMatched());
        assertEquals("PRODUCT_MANAGEMENT", matched.getDimensionSource());
        assertEquals(List.of("product_dimension_matched"), matched.getDimensionQualityCodes());
        assertEquals(List.of("sales_fact_ready", "product_dimension_matched"), matched.getDataQualityCodes());

        SalesProductRow partial = productRow(rows, "PARTIAL-PSKU");
        assertEquals(true, partial.isDimensionMatched());
        assertEquals(List.of("product_dimension_matched", "brand_missing", "backend_fulltype_missing"), partial.getDimensionQualityCodes());
        assertEquals(List.of("sales_fact_ready", "product_dimension_matched", "brand_missing", "backend_fulltype_missing"), partial.getDataQualityCodes());
    }

    @Test
    void productRowsExposeProductMediaAndInventoryFromProductManagement() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 1), "MATCHED-PSKU", "ZMATCHED-1", 5, 5, 0, 5, BigDecimal.TEN, 20, 50, new BigDecimal("4"), null),
                fact(LocalDate.of(2026, 5, 5), "MATCHED-PSKU", "ZMATCHED-1", 10, 10, 0, 10, BigDecimal.TEN, 40, 60, new BigDecimal("4"), null)
        );
        RecordingProductDimensionRepository dimensionRepository = new RecordingProductDimensionRepository();
        dimensionRepository.snapshots = List.of(new SalesProductDimensionSnapshot(
                "MATCHED-PSKU",
                "ZMATCHED-1",
                "Paper Says",
                "Stationery-Paper-Sticker",
                "https://image.example.test/matched.jpg",
                30,
                12,
                8,
                10
        ));
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                null,
                dimensionRepository
        );

        SalesProductRow row = service.listProductRows(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5),
                null,
                null
        )).get(0);

        assertEquals("https://image.example.test/matched.jpg", row.getImageUrl());
        assertEquals(30, row.getCurrentStock());
        assertEquals(12, row.getFbnStock());
        assertEquals(8, row.getSupermallStock());
        assertEquals(10, row.getFbpStock());
        assertEquals(new BigDecimal("10.0"), row.getStockCoverDays());
    }

    @Test
    void filtersProductRowsByPersistedProductDimensionAndQualityCode() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "MATCHED-PSKU", "ZMATCHED-1", 2, 2, 0, 2, BigDecimal.TEN, 40, 60, null, null),
                fact(LocalDate.of(2026, 5, 18), "OTHER-PSKU", "ZOTHER-1", 3, 3, 0, 3, BigDecimal.TEN, 40, 60, null, null),
                fact(LocalDate.of(2026, 5, 18), "UNMATCHED-PSKU", "ZUNMATCHED-1", 4, 4, 0, 4, BigDecimal.TEN, 40, 60, null, null)
        );
        RecordingProductDimensionRepository dimensionRepository = new RecordingProductDimensionRepository();
        dimensionRepository.snapshots = List.of(
                new SalesProductDimensionSnapshot("MATCHED-PSKU", "ZMATCHED-1", "Paper Says", "Stationery-Paper-Sticker"),
                new SalesProductDimensionSnapshot("OTHER-PSKU", "ZOTHER-1", "Other Brand", "Stationery-Paper-Sticker")
        );
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                null,
                dimensionRepository
        );

        List<SalesProductRow> brandRows = service.listProductRows(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                null,
                "Paper Says",
                "Stationery-Paper-Sticker",
                null
        ));
        List<SalesProductRow> missingRows = service.listProductRows(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                null,
                null,
                null,
                "product_dimension_missing"
        ));

        assertEquals(1, brandRows.size());
        assertEquals("MATCHED-PSKU", brandRows.get(0).getPartnerSku());
        assertEquals(1, missingRows.size());
        assertEquals("UNMATCHED-PSKU", missingRows.get(0).getPartnerSku());
    }

    @Test
    void returnsProductDetailWithSourceFactRows() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 10, 9, 2, 8, new BigDecimal("120.50"), 100, 200, new BigDecimal("50"), new BigDecimal("80")),
                legacyFact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 2)
        );
        RecordingProductDimensionRepository dimensionRepository = new RecordingProductDimensionRepository();
        dimensionRepository.snapshots = List.of(new SalesProductDimensionSnapshot(
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "Paper Says",
                "Stationery-Paper-Sticker",
                "https://image.example.test/detail.jpg",
                18,
                9,
                6,
                3
        ));
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                null,
                dimensionRepository
        );

        SalesProductDetail detail = service.getProductDetail(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1"
        ));

        assertEquals("SAMPLE-SKU-001", detail.getPartnerSku());
        assertEquals("Z57C90A4184D0CFD75218Z-1", detail.getSku());
        assertEquals(LocalDate.of(2026, 5, 19), detail.getLatestFactDate());
        assertEquals(10, detail.getSummary().getNetUnits());
        assertEquals(2, detail.getFacts().size());
        assertEquals(List.of("legacy_product_sales_data", "noon_productviewsandsalesdata"), detail.getSourceSystems());
        assertEquals("https://image.example.test/detail.jpg", detail.getImageUrl());
        assertEquals(18, detail.getCurrentStock());
        assertEquals(9, detail.getFbnStock());
        assertEquals(6, detail.getSupermallStock());
        assertEquals(3, detail.getFbpStock());
        assertEquals(new BigDecimal("3.6"), detail.getStockCoverDays());
    }

    @Test
    void productDetailIgnoresExternalSkuWhenPartnerSkuIsPresent() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z-OLD-1", 4, 4, 0, 4, new BigDecimal("40.00"), 20, 30, null, null),
                fact(LocalDate.of(2026, 5, 19), "SAMPLE-SKU-001", "Z-NEW-1", 6, 6, 0, 6, new BigDecimal("60.00"), 25, 35, null, null)
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        SalesProductDetail detail = service.getProductDetail(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "SAMPLE-SKU-001",
                "Z-OLD-1"
        ));

        assertEquals("SAMPLE-SKU-001", detail.getPartnerSku());
        assertEquals("Z-NEW-1", detail.getSku());
        assertEquals(10, detail.getSummary().getNetUnits());
        assertEquals(2, detail.getFacts().size());
        assertEquals(LocalDate.of(2026, 5, 19), detail.getLatestFactDate());
        assertEquals(null, repository.lastQuery.getSku());
    }

    @Test
    void productDetailKeepsExternalSkuWhenPartnerSkuIsPlaceholder() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "-", "Z-MISSING-1", 4, 4, 0, 4, new BigDecimal("40.00"), 20, 30, null, null),
                fact(LocalDate.of(2026, 5, 19), "-", "Z-MISSING-2", 6, 6, 0, 6, new BigDecimal("60.00"), 25, 35, null, null)
        );
        SalesAnalyticsService service = new SalesAnalyticsService(repository);

        SalesProductDetail detail = service.getProductDetail(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "-",
                "Z-MISSING-1"
        ));

        assertEquals("-", detail.getPartnerSku());
        assertEquals("Z-MISSING-1", detail.getSku());
        assertEquals(4, detail.getSummary().getNetUnits());
        assertEquals(1, detail.getFacts().size());
        assertEquals("Z-MISSING-1", repository.lastQuery.getSku());
    }

    @Test
    void exportsDailyFactsWithTheSameAnalyticsQuery() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", 10, 9, 2, 8, new BigDecimal("120.50"), 100, 200, new BigDecimal("50"), new BigDecimal("80"))
        );
        RecordingProductDimensionRepository dimensionRepository = new RecordingProductDimensionRepository();
        dimensionRepository.snapshots = List.of(new SalesProductDimensionSnapshot(
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "Paper Says",
                "Stationery-Paper-Sticker"
        ));
        SalesAnalyticsService service = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                null,
                null,
                dimensionRepository
        );
        SalesFactQuery query = defaultQuery();

        String csv = service.exportDailyFactsCsv(query);

        assertEquals(query, repository.lastQuery);
        String[] lines = csv.split("\\R");
        assertEquals("factDate,sourceSystem,partnerSku,sku,brand,productFulltype,dataQualityCodes,productTitle,netUnits,grossUnits,shippedUnits,cancelledUnits,revenueShipped,yourVisitors,totalVisitors,conversionVisitorsPercentage,buyBoxVisitorPercentage", lines[0]);
        assertEquals("2026-05-18,noon_productviewsandsalesdata,SAMPLE-SKU-001,Z57C90A4184D0CFD75218Z-1,Paper Says,Stationery-Paper-Sticker,sales_fact_ready|product_dimension_matched,Sanitized sample product,8,10,9,2,120.50,100,200,50,80", lines[1]);
    }

    private static class RecordingSalesFactRepository implements SalesFactRepository {
        private SalesFactQuery lastQuery;
        private List<DailySalesFact> facts;
        private List<SalesImportBatchRecord> batches = List.of();
        private LocalDate latestFactDate;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void upsert(DailySalesFact fact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            this.lastQuery = query;
            List<DailySalesFact> source = facts;
            if (source == null) {
                source = List.of(fact());
            }
            java.util.ArrayList<DailySalesFact> result = new java.util.ArrayList<>();
            for (DailySalesFact fact : source) {
                if (query.getOwnerUserId() != null && !query.getOwnerUserId().equals(fact.getOwnerUserId())) {
                    continue;
                }
                if (query.getStoreCode() != null && !query.getStoreCode().equals(fact.getStoreCode())) {
                    continue;
                }
                if (query.getSiteCode() != null && !query.getSiteCode().equals(fact.getSiteCode())) {
                    continue;
                }
                if (query.getDateFrom() != null && fact.getFactDate().isBefore(query.getDateFrom())) {
                    continue;
                }
                if (query.getDateTo() != null && fact.getFactDate().isAfter(query.getDateTo())) {
                    continue;
                }
                if (hasText(query.getPartnerSku()) && !query.getPartnerSku().equals(fact.getPartnerSku())) {
                    continue;
                }
                if (hasText(query.getSku()) && !query.getSku().equals(fact.getSku())) {
                    continue;
                }
                result.add(fact);
            }
            return result;
        }

        @Override
        public List<SalesImportBatchRecord> listImportBatches(SalesImportBatchQuery query) {
            return batches;
        }

        @Override
        public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return latestFactDate;
        }

        private DailySalesFact fact() {
            return fact(
                    LocalDate.of(2026, 4, 30),
                    "SAMPLE-SKU-001",
                    "Z57C90A4184D0CFD75218Z-1",
                    1,
                    1,
                    null,
                    1,
                    new BigDecimal("19.8"),
                    null,
                    null,
                    null,
                    null
            );
        }

        private DailySalesFact fact(
                LocalDate factDate,
                String partnerSku,
                String sku,
                Integer grossUnits,
                Integer shippedUnits,
                Integer cancelledUnits,
                int netUnits,
                BigDecimal revenueShipped,
                Integer yourVisitors,
                Integer totalVisitors,
                BigDecimal conversionVisitorsPercentage,
                BigDecimal buyBoxVisitorPercentage
        ) {
            return new DailySalesFact(
                    NoonSalesCsvImportService.SOURCE_SYSTEM,
                    10001L,
                    10002L,
                    245027L,
                    "STR245027-SAU",
                    "SA",
                    factDate,
                    partnerSku,
                    sku,
                    "Z57C90A4184D0CFD75218Z",
                    "SA",
                    "SAR",
                    "Sanitized sample product",
                    yourVisitors,
                    totalVisitors,
                    grossUnits,
                    shippedUnits,
                    cancelledUnits,
                    netUnits,
                    revenueShipped,
                    buyBoxVisitorPercentage,
                    conversionVisitorsPercentage,
                    null
            );
        }
    }

    private static class RecordingLifecycleStateRepository implements ProductLifecycleStateRepository {
        private ProductLifecycleStateQuery lastQuery;
        private ProductLifecycleCurrentState currentState;
        private java.util.Map<String, ProductLifecycleCurrentState> currentStates = new java.util.HashMap<>();

        @Override
        public void saveCurrentState(ProductLifecycleCurrentState state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveHistory(ProductLifecycleHistoryRecord historyRecord) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveJob(ProductLifecycleJobRecord job) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProductLifecycleCurrentState findCurrentState(ProductLifecycleStateQuery query) {
            this.lastQuery = query;
            ProductLifecycleCurrentState mapped = currentStates.get(query.getPartnerSku());
            if (mapped != null) {
                return mapped;
            }
            return currentState;
        }

        @Override
        public List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query) {
            return List.of();
        }

        @Override
        public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
            return List.of();
        }
    }

    private static class RecordingProductDimensionRepository implements SalesProductDimensionRepository {
        private List<SalesProductDimensionSnapshot> snapshots = List.of();

        @Override
        public List<SalesProductDimensionSnapshot> list(SalesFactQuery query) {
            return snapshots;
        }
    }

    private ProductLifecycleCurrentState lifecycleState(
            String code,
            String label,
            String qualityState,
            String ruleVersion
    ) {
        return new ProductLifecycleCurrentState(
                70001L,
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                code,
                label,
                ruleVersion,
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 1, 1),
                "official",
                qualityState,
                "persisted lifecycle",
                "{\"reason\":\"" + qualityState + "\"}",
                72001L,
                null
        );
    }

    private SalesFactQuery defaultQuery() {
        return new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private SalesProductRow productRow(List<SalesProductRow> rows, String partnerSku) {
        return rows.stream()
                .filter(row -> partnerSku.equals(row.getPartnerSku()))
                .findFirst()
                .orElseThrow();
    }

    private DailySalesFact fact(
            LocalDate factDate,
            String partnerSku,
            String sku,
            Integer grossUnits,
            Integer shippedUnits,
            Integer cancelledUnits,
            int netUnits,
            BigDecimal revenueShipped,
            Integer yourVisitors,
            Integer totalVisitors,
            BigDecimal conversionVisitorsPercentage,
            BigDecimal buyBoxVisitorPercentage
    ) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                factDate,
                partnerSku,
                sku,
                "Z57C90A4184D0CFD75218Z",
                "SA",
                "SAR",
                "Sanitized sample product",
                yourVisitors,
                totalVisitors,
                grossUnits,
                shippedUnits,
                cancelledUnits,
                netUnits,
                revenueShipped,
                buyBoxVisitorPercentage,
                conversionVisitorsPercentage,
                null
        );
    }

    private DailySalesFact legacyFact(LocalDate factDate, String partnerSku, String sku, int netUnits) {
        return new DailySalesFact(
                LegacySalesBackfillService.SOURCE_SYSTEM,
                10002L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                factDate,
                partnerSku,
                sku,
                "Z57C90A4184D0CFD75218Z",
                "SA",
                "SAR",
                "Sanitized sample product",
                null,
                null,
                netUnits,
                netUnits,
                0,
                netUnits,
                BigDecimal.TEN,
                null,
                null,
                null
        );
    }
}
