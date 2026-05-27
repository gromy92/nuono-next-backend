package com.nuono.next.aiopsdashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonsync.NoonBusinessSyncStatusService;
import com.nuono.next.noonsync.NoonSyncDataDomain;
import com.nuono.next.noonsync.NoonSyncDiagnostic;
import com.nuono.next.noonsync.NoonSyncFailureReason;
import com.nuono.next.noonsync.NoonSyncFoundationService;
import com.nuono.next.noonsync.NoonSyncRetryPolicy;
import com.nuono.next.noonsync.NoonSyncScope;
import com.nuono.next.noonsync.NoonSyncTarget;
import com.nuono.next.noonsync.NoonSyncTask;
import com.nuono.next.noonsync.NoonSyncTaskRequest;
import com.nuono.next.noonsync.NoonSyncTriggerMode;
import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.NoonSalesCsvImportService;
import com.nuono.next.sales.ProductLifecycleClassifier;
import com.nuono.next.sales.SalesAnalyticsService;
import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesFactRepository;
import com.nuono.next.sales.SalesImportBatch;
import com.nuono.next.sales.SalesImportBatchQuery;
import com.nuono.next.sales.SalesImportBatchRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AiOperationsDashboardA02IntegrationGateTest {

    private static final Long OWNER_USER_ID = 10002L;
    private static final String STORE_CODE = "STR245027-NAE";
    private static final String SITE_CODE = "AE";
    private static final NoonSyncScope SYNC_SCOPE = NoonSyncScope.of(OWNER_USER_ID, null, STORE_CODE, SITE_CODE);

    @Test
    void overviewCombinesA02SalesFactsLifecycleAnomaliesEvidenceAndDrillThroughWithoutAiOrWrites() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.latestFactDate = LocalDate.of(2026, 5, 21);
        repository.facts = sixCategoryFacts();

        AiOperationsDashboardOverview overview = overview(repository, new NoonSyncFoundationService());

        assertEquals("partial_success", overview.getSummary().getState());
        assertEquals("runtime_disabled", overview.getAiSummary().getState());
        assertEquals("runtime_disabled", overview.getSuggestions().getState());
        assertTrue(overview.getSuggestions().getItems().isEmpty());

        assertNotNull(metric(overview, "sales_revenue_shipped").getValue());
        assertNotNull(metric(overview, "sales_net_units").getValue());
        assertEquals(new BigDecimal("1"), metric(overview, "sales_lifecycle_data_insufficient").getValue());

        assertEquals("ready", signal(overview, "sales_freshness").getState());
        AiOperationsDashboardOverview.Signal declining = signal(overview, "sales_declining_products");
        assertEquals("warning", declining.getSeverity());
        assertTrue(declining.getDrillThroughPath().contains("/data/sales-analysis"));
        assertTrue(declining.getDrillThroughPath().contains("search=DECLINE"));
        assertTrue(declining.getEvidence().get(0).getDescription().contains("ruleVersion=DEFAULT_V1"));

        List<String> lifecycleSignalOrder = overview.getSignals().stream()
                .map(AiOperationsDashboardOverview.Signal::getKey)
                .filter(key -> key.startsWith("sales_") && !"sales_freshness".equals(key))
                .collect(Collectors.toList());
        assertEquals(List.of(
                "sales_declining_products",
                "sales_data_insufficient_products",
                "sales_long_tail_products",
                "sales_lifecycle_distribution"
        ), lifecycleSignalOrder);

        assertEvidence(overview, "sales-freshness-latest-date", "latestAvailableSalesDate=2026-05-21");
        assertEvidence(overview, "sales-lifecycle-default-v1-distribution", "data_insufficient=1");
        assertEvidence(overview, "sales_declining_products-evidence", "lifecycle=decline");

        assertEquals(0, repository.saveBatchCalls);
        assertEquals(0, repository.upsertCalls);
    }

    @Test
    void overviewKeepsSyncReadinessStatesExplicitWithoutFakeMetricsOrAiOutput() {
        assertUnavailableOverview(
                overview(emptyRepository(), new NoonSyncFoundationService()),
                "backfill_required",
                "backfill_required"
        );

        RecordingSalesFactRepository emptyReport = emptyRepository();
        emptyReport.batches = List.of(batch(910L, "empty"));
        assertUnavailableOverview(overview(emptyReport, new NoonSyncFoundationService()), "empty_report", "empty_report");

        NoonSyncFoundationService runningFoundation = new NoonSyncFoundationService();
        runningFoundation.markRunning(runningFoundation.createTask(backfillTask()));
        assertUnavailableOverview(overview(emptyRepository(), runningFoundation), "backfill_running", "backfill_running");

        NoonSyncFoundationService failedFoundation = new NoonSyncFoundationService();
        NoonSyncTask failedTask = failedFoundation.markRunning(failedFoundation.createTask(backfillTask()));
        failedFoundation.markFailed(
                failedTask.getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, "provider unavailable")
        );
        assertUnavailableOverview(overview(emptyRepository(), failedFoundation), "backfill_failed", "provider_unavailable");

        RecordingSalesFactRepository staleRepository = new RecordingSalesFactRepository();
        staleRepository.latestFactDate = LocalDate.of(2026, 5, 10);
        staleRepository.facts = sixCategoryFacts();
        AiOperationsDashboardOverview stale = overview(staleRepository, new NoonSyncFoundationService());
        assertUnavailableOverview(stale, "stale", "stale");
        assertEquals("stale", signal(stale, "sales_stale_data").getState());
    }

    private static AiOperationsDashboardOverview overview(
            RecordingSalesFactRepository repository,
            NoonSyncFoundationService foundation
    ) {
        SalesAnalyticsService salesAnalyticsService = new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                new NoonBusinessSyncStatusService(foundation)
        );
        AiOperationsDashboardService service = new AiOperationsDashboardService(
                new AiOperationsDashboardSignalProviderRegistry(List.of(
                        new AiOperationsDashboardSalesFreshnessSignalProvider(salesAnalyticsService),
                        new AiOperationsDashboardSalesLifecycleSignalProvider(salesAnalyticsService)
                )),
                () -> LocalDate.of(2026, 5, 21)
        );
        return service.overview(new AiOperationsDashboardQuery(
                OWNER_USER_ID,
                555L,
                STORE_CODE,
                SITE_CODE,
                "last30Days"
        ));
    }

    private static void assertUnavailableOverview(
            AiOperationsDashboardOverview overview,
            String expectedState,
            String expectedQualityState
    ) {
        assertEquals("runtime_disabled", overview.getAiSummary().getState());
        assertTrue(overview.getSuggestions().getItems().isEmpty());
        assertEquals(expectedState, metric(overview, "sales_freshness").getState());
        assertEquals(expectedQualityState, metric(overview, "sales_freshness").getQualityState());
        assertNull(metric(overview, "sales_revenue_shipped").getValue());
        assertNull(metric(overview, "sales_net_units").getValue());
        assertNull(metric(overview, "sales_visitors").getValue());
        assertNull(metric(overview, "sales_conversion").getValue());
        assertTrue(metric(overview, "sales_lifecycle_data_insufficient").getValue() == null
                || "ready".equals(metric(overview, "sales_lifecycle_data_insufficient").getState()));
        assertFalse(overview.getSuggestions().getItems().stream()
                .anyMatch(item -> item.getTitle() != null && item.getTitle().contains("采购")));
    }

    private static AiOperationsDashboardOverview.MetricCard metric(
            AiOperationsDashboardOverview overview,
            String key
    ) {
        return overview.getMetricCards().stream()
                .filter(card -> key.equals(card.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric card " + key));
    }

    private static AiOperationsDashboardOverview.Signal signal(
            AiOperationsDashboardOverview overview,
            String key
    ) {
        return overview.getSignals().stream()
                .filter(signal -> key.equals(signal.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing signal " + key));
    }

    private static void assertEvidence(
            AiOperationsDashboardOverview overview,
            String id,
            String expectedDescription
    ) {
        assertTrue(overview.getEvidence().getItems().stream()
                .anyMatch(item -> id.equals(item.getId())
                        && item.getDescription().contains(expectedDescription)));
    }

    private static RecordingSalesFactRepository emptyRepository() {
        return new RecordingSalesFactRepository();
    }

    private static List<DailySalesFact> sixCategoryFacts() {
        List<DailySalesFact> facts = new ArrayList<>();
        addProductFacts(facts, "NEW", LocalDate.of(2026, 5, 18), 3, 1);
        addOldAnchor(facts, "GROWTH");
        addProductFacts(facts, "GROWTH", LocalDate.of(2026, 4, 25), 7, 3);
        addProductFacts(facts, "GROWTH", LocalDate.of(2026, 5, 14), 7, 12);
        addOldAnchor(facts, "STABLE");
        addProductFacts(facts, "STABLE", LocalDate.of(2026, 4, 25), 7, 10);
        addProductFacts(facts, "STABLE", LocalDate.of(2026, 5, 14), 7, 11);
        addOldAnchor(facts, "DECLINE");
        addProductFacts(facts, "DECLINE", LocalDate.of(2026, 4, 25), 7, 12);
        addProductFacts(facts, "DECLINE", LocalDate.of(2026, 5, 14), 7, 3);
        addOldAnchor(facts, "LONGTAIL");
        addProductFacts(facts, "LONGTAIL", LocalDate.of(2026, 4, 25), 7, 2);
        addOldAnchor(facts, "INSUFFICIENT");
        addProductFacts(facts, "INSUFFICIENT", LocalDate.of(2026, 5, 17), 4, 2);
        return facts;
    }

    private static void addOldAnchor(List<DailySalesFact> facts, String partnerSku) {
        facts.add(fact(LocalDate.of(2026, 4, 1), partnerSku, 0));
    }

    private static void addProductFacts(
            List<DailySalesFact> facts,
            String partnerSku,
            LocalDate startDate,
            int dayCount,
            int totalUnits
    ) {
        int baseUnits = totalUnits / dayCount;
        int extraUnits = totalUnits % dayCount;
        for (int i = 0; i < dayCount; i++) {
            int unit = baseUnits + (i < extraUnits ? 1 : 0);
            facts.add(fact(startDate.plusDays(i), partnerSku, unit));
        }
    }

    private static DailySalesFact fact(LocalDate factDate, String partnerSku, int netUnits) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                OWNER_USER_ID,
                245027L,
                STORE_CODE,
                SITE_CODE,
                factDate,
                partnerSku,
                partnerSku + "-SKU",
                partnerSku + "-CONFIG",
                "AE",
                "AED",
                "Product " + partnerSku,
                10,
                20,
                netUnits,
                netUnits,
                0,
                netUnits,
                BigDecimal.TEN,
                null,
                BigDecimal.valueOf(50),
                null
        );
    }

    private static NoonSyncTaskRequest backfillTask() {
        return new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.GAP_BACKFILL,
                SYNC_SCOPE,
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 21)),
                NoonSyncRetryPolicy.RETRYABLE
        );
    }

    private static SalesImportBatchRecord batch(Long id, String status) {
        return new SalesImportBatchRecord(
                id,
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                "productviewsandsalesdata.csv",
                OWNER_USER_ID,
                245027L,
                STORE_CODE,
                SITE_CODE,
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 5, 21),
                0,
                0,
                0,
                status,
                null,
                LocalDateTime.of(2026, 5, 21, 8, 0)
        );
    }

    private static class RecordingSalesFactRepository implements SalesFactRepository {
        private List<DailySalesFact> facts = List.of();
        private LocalDate latestFactDate;
        private List<SalesImportBatchRecord> batches = List.of();
        private int saveBatchCalls;
        private int upsertCalls;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            saveBatchCalls++;
            throw new AssertionError("A02 dashboard must not create sales import batches");
        }

        @Override
        public void upsert(DailySalesFact fact) {
            upsertCalls++;
            throw new AssertionError("A02 dashboard must not write sales facts");
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            return facts;
        }

        @Override
        public List<SalesImportBatchRecord> listImportBatches(SalesImportBatchQuery query) {
            return batches;
        }

        @Override
        public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return latestFactDate;
        }
    }
}
