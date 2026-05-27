package com.nuono.next.aiopsdashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import org.junit.jupiter.api.Test;

class AiOperationsDashboardSalesFreshnessSignalProviderTest {

    private static final NoonSyncScope SYNC_SCOPE = NoonSyncScope.of(10002L, null, "STR245027-NAE", "AE");

    @Test
    void contributesLatestAvailableSalesDateAndSelectedScopeAsEvidence() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(LocalDate.of(2026, 5, 20)));
        repository.latestFactDate = LocalDate.of(2026, 5, 20);
        AiOperationsDashboardSalesFreshnessSignalProvider provider =
                new AiOperationsDashboardSalesFreshnessSignalProvider(new SalesAnalyticsService(
                        repository,
                        new ProductLifecycleClassifier(),
                        new NoonBusinessSyncStatusService(new NoonSyncFoundationService())
                ));
        AiOperationsDashboardScope scope = new AiOperationsDashboardScope(
                10002L,
                555L,
                "STR245027-NAE",
                "AE",
                "last7Days",
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 5, 21)
        );

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last7Days"),
                scope
        );

        assertEquals(10002L, repository.lastQuery.getOwnerUserId());
        assertEquals("STR245027-NAE", repository.lastQuery.getStoreCode());
        assertEquals("AE", repository.lastQuery.getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 15), repository.lastQuery.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), repository.lastQuery.getDateTo());

        AiOperationsDashboardOverview.MetricCard card = contribution.getMetricCards().get(0);
        assertEquals("sales_freshness", card.getKey());
        assertEquals("ready", card.getState());
        assertEquals("ready", card.getQualityState());
        assertNull(card.getValue(), "Freshness cards must not fabricate numeric sales values");
        assertTrue(card.getDescription().contains("2026-05-20"));

        AiOperationsDashboardOverview.Signal signal = contribution.getSignals().get(0);
        assertEquals("sales_freshness", signal.getKey());
        assertEquals("ready", signal.getState());
        assertEquals("info", signal.getSeverity());

        assertTrue(contribution.getEvidence().stream()
                .anyMatch(evidence -> "sales-freshness-latest-date".equals(evidence.getId())
                        && evidence.getDescription().contains("latestAvailableSalesDate=2026-05-20")
                        && evidence.getDescription().contains("store=STR245027-NAE")
                        && evidence.getDescription().contains("site=AE")));
    }

    @Test
    void returnsExplicitEmptyStateWithoutFakeValuesWhenSalesDataNeedsBackfill() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of();
        AiOperationsDashboardSalesFreshnessSignalProvider provider =
                new AiOperationsDashboardSalesFreshnessSignalProvider(new SalesAnalyticsService(
                        repository,
                        new ProductLifecycleClassifier(),
                        new NoonBusinessSyncStatusService(new NoonSyncFoundationService())
                ));

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last7Days"),
                scope()
        );

        AiOperationsDashboardOverview.MetricCard card = contribution.getMetricCards().get(0);
        assertEquals("sales_freshness", card.getKey());
        assertEquals("backfill_required", card.getState());
        assertEquals("backfill_required", card.getQualityState());
        assertNull(card.getValue(), "Empty sales state must not render zero as a fabricated business value");
        assertTrue(card.getDescription().contains("没有可用销量事实"));
        assertEquals("backfill_required", contribution.getSignals().get(0).getState());
        assertEquals("warning", contribution.getSignals().get(0).getSeverity());
        assertTrue(contribution.getEvidence().get(0).getDescription()
                .contains("syncState=NO_DATA_BACKFILL_REQUIRED"));
        assertTrue(contribution.getEvidence().get(0).getDescription()
                .contains("requiredWork=sales_backfill"));
    }

    @Test
    void mapsSharedSalesSyncMatrixWithoutCollapsingOperationalStatesIntoEmpty() {
        assertDashboardState(repositoryWithLatestFact(LocalDate.of(2026, 5, 10)), new NoonSyncFoundationService(), "stale");

        NoonSyncFoundationService runningFoundation = new NoonSyncFoundationService();
        runningFoundation.markRunning(runningFoundation.createTask(backfillTask()));
        assertDashboardState(emptyRepository(), runningFoundation, "backfill_running");

        NoonSyncFoundationService failedFoundation = new NoonSyncFoundationService();
        NoonSyncTask failedTask = failedFoundation.markRunning(failedFoundation.createTask(backfillTask()));
        failedFoundation.markFailed(
                failedTask.getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, "provider unavailable")
        );
        AiOperationsDashboardContribution failed = contribution(emptyRepository(), failedFoundation);
        assertEquals("backfill_failed", failed.getMetricCards().get(0).getState());
        assertEquals("provider_unavailable", failed.getMetricCards().get(0).getQualityState());
        assertEquals("backfill_failed", failed.getSignals().get(0).getState());
        assertEquals("provider_unavailable", failed.getEvidence().get(0).getState());
        assertTrue(failed.getEvidence().get(0).getDescription().contains("failureReason=PROVIDER_UNAVAILABLE"));
        assertTrue(failed.getEvidence().get(0).getDescription().contains("diagnosticSummary=provider unavailable"));
    }

    @Test
    void surfacesEmptyReportMissingMappingPartialSuccessAndSourceBatchEvidence() {
        RecordingSalesFactRepository emptyReport = emptyRepository();
        emptyReport.batches = List.of(batch(910L, "empty"));
        AiOperationsDashboardContribution empty = contribution(emptyReport, new NoonSyncFoundationService());
        assertEquals("empty_report", empty.getMetricCards().get(0).getState());
        assertEquals("empty_report", empty.getMetricCards().get(0).getQualityState());
        assertTrue(empty.getEvidence().get(0).getDescription().contains("sourceBatchIds=910"));

        RecordingSalesFactRepository missingMapping = emptyRepository();
        missingMapping.batches = List.of(batch(911L, "failed"));
        AiOperationsDashboardContribution missing = contribution(missingMapping, new NoonSyncFoundationService());
        assertEquals("missing_mapping", missing.getMetricCards().get(0).getState());
        assertEquals("missing_mapping", missing.getMetricCards().get(0).getQualityState());
        assertTrue(missing.getEvidence().get(0).getDescription().contains("sourceBatchIds=911"));

        NoonSyncFoundationService partialFoundation = new NoonSyncFoundationService();
        NoonSyncTask partialTask = partialFoundation.markRunning(partialFoundation.createTask(dailyTask()));
        partialFoundation.markPartial(
                partialTask.getId(),
                NoonSyncFailureReason.TIMEOUT,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, "latest daily report imported with retryable timeout")
        );
        AiOperationsDashboardContribution partial = contribution(
                repositoryWithLatestFact(LocalDate.of(2026, 5, 20)),
                partialFoundation
        );
        assertEquals("partial_success", partial.getMetricCards().get(0).getState());
        assertEquals("partial_success", partial.getMetricCards().get(0).getQualityState());
        assertTrue(partial.getEvidence().get(0).getDescription().contains("taskStatus=PARTIAL"));
    }

    @Test
    void returnsNotConnectedStateWithoutFakeValuesWhenSyncStatusIsUnavailable() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(LocalDate.of(2026, 5, 20)));
        AiOperationsDashboardSalesFreshnessSignalProvider provider =
                new AiOperationsDashboardSalesFreshnessSignalProvider(new SalesAnalyticsService(repository));

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last7Days"),
                scope()
        );

        AiOperationsDashboardOverview.MetricCard card = contribution.getMetricCards().get(0);
        assertEquals("not_connected", card.getState());
        assertNull(card.getValue(), "Disconnected sync state must not render stale or guessed sales values");
        assertTrue(card.getDescription().contains("尚未接入"));
        assertEquals("not_connected", contribution.getSignals().get(0).getState());
        assertTrue(contribution.getEvidence().get(0).getDescription()
                .contains("syncState=not_connected"));
    }

    @Test
    void contributesCoreSalesMetricCardsWhenSharedSalesMetricsAreAllowed() {
        RecordingSalesFactRepository repository = repositoryWithLatestFact(LocalDate.of(2026, 5, 20));

        AiOperationsDashboardContribution contribution = contribution(repository, new NoonSyncFoundationService());

        assertMetricValue(contribution, "sales_revenue_shipped", "销售额", "ready", new BigDecimal("25.50"), null);
        assertMetricValue(contribution, "sales_net_units", "净销量", "ready", new BigDecimal("2"), "件");
        assertMetricValue(contribution, "sales_visitors", "访客", "ready", new BigDecimal("100"), "人");
        assertMetricValue(contribution, "sales_conversion", "转化率", "ready", new BigDecimal("50"), "%");
    }

    @Test
    void coreSalesMetricCardsPropagateQualityStateWithoutFabricatingZeroValues() {
        AiOperationsDashboardContribution backfillRequired = contribution(emptyRepository(), new NoonSyncFoundationService());

        assertMetricUnavailable(backfillRequired, "sales_revenue_shipped", "backfill_required");
        assertMetricUnavailable(backfillRequired, "sales_net_units", "backfill_required");
        assertMetricUnavailable(backfillRequired, "sales_visitors", "backfill_required");
        assertMetricUnavailable(backfillRequired, "sales_conversion", "backfill_required");

        RecordingSalesFactRepository staleRepository = repositoryWithLatestFact(LocalDate.of(2026, 5, 10));
        AiOperationsDashboardContribution stale = contribution(staleRepository, new NoonSyncFoundationService());
        assertMetricUnavailable(stale, "sales_revenue_shipped", "stale");

        NoonSyncFoundationService partialFoundation = new NoonSyncFoundationService();
        NoonSyncTask partialTask = partialFoundation.markRunning(partialFoundation.createTask(dailyTask()));
        partialFoundation.markPartial(
                partialTask.getId(),
                NoonSyncFailureReason.TIMEOUT,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, "latest daily report imported with retryable timeout")
        );
        AiOperationsDashboardContribution partial = contribution(
                repositoryWithLatestFact(LocalDate.of(2026, 5, 20)),
                partialFoundation
        );
        assertMetricValue(partial, "sales_net_units", "净销量", "partial_success", new BigDecimal("2"), "件");
    }

    private static class RecordingSalesFactRepository implements SalesFactRepository {
        private SalesFactQuery lastQuery;
        private List<DailySalesFact> facts = List.of();
        private LocalDate latestFactDate;
        private List<SalesImportBatchRecord> batches = List.of();

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
            lastQuery = query;
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

    private static DailySalesFact fact(LocalDate factDate) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-NAE",
                "AE",
                factDate,
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "Z57C90A4184D0CFD75218Z",
                "AE",
                "AED",
                "Sanitized sample product",
                100,
                200,
                3,
                2,
                1,
                2,
                new BigDecimal("25.50"),
                new BigDecimal("70"),
                new BigDecimal("50"),
                null
        );
    }

    private static AiOperationsDashboardOverview.MetricCard metric(
            AiOperationsDashboardContribution contribution,
            String key
    ) {
        return contribution.getMetricCards().stream()
                .filter(card -> key.equals(card.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric card " + key));
    }

    private static void assertMetricValue(
            AiOperationsDashboardContribution contribution,
            String key,
            String title,
            String expectedQualityState,
            BigDecimal expectedValue,
            String expectedUnit
    ) {
        AiOperationsDashboardOverview.MetricCard card = metric(contribution, key);
        assertEquals(title, card.getTitle());
        assertEquals("ready", card.getState());
        assertEquals(expectedQualityState, card.getQualityState());
        assertEquals(expectedValue, card.getValue());
        assertEquals(expectedUnit, card.getUnit());
    }

    private static void assertMetricUnavailable(
            AiOperationsDashboardContribution contribution,
            String key,
            String expectedQualityState
    ) {
        AiOperationsDashboardOverview.MetricCard card = metric(contribution, key);
        assertEquals(expectedQualityState, card.getState());
        assertEquals(expectedQualityState, card.getQualityState());
        assertNull(card.getValue(), "Unavailable metric " + key + " must not be rendered as zero");
        assertTrue(card.getDescription().contains("暂不展示"));
    }

    private static void assertDashboardState(
            RecordingSalesFactRepository repository,
            NoonSyncFoundationService foundation,
            String expectedState
    ) {
        AiOperationsDashboardContribution contribution = contribution(repository, foundation);
        AiOperationsDashboardOverview.MetricCard card = contribution.getMetricCards().get(0);
        assertEquals(expectedState, card.getState());
        assertEquals(expectedState, contribution.getSignals().get(0).getState());
        assertNull(card.getValue(), "Freshness matrix cards must not fabricate business metric values");
    }

    private static AiOperationsDashboardContribution contribution(
            RecordingSalesFactRepository repository,
            NoonSyncFoundationService foundation
    ) {
        AiOperationsDashboardSalesFreshnessSignalProvider provider =
                new AiOperationsDashboardSalesFreshnessSignalProvider(new SalesAnalyticsService(
                        repository,
                        new ProductLifecycleClassifier(),
                        new NoonBusinessSyncStatusService(foundation)
                ));
        return provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last7Days"),
                scope()
        );
    }

    private static RecordingSalesFactRepository repositoryWithLatestFact(LocalDate latestFactDate) {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.facts = List.of(fact(latestFactDate));
        repository.latestFactDate = latestFactDate;
        return repository;
    }

    private static RecordingSalesFactRepository emptyRepository() {
        return new RecordingSalesFactRepository();
    }

    private static NoonSyncTaskRequest dailyTask() {
        return salesTask(NoonSyncTriggerMode.SCHEDULED_DAILY);
    }

    private static NoonSyncTaskRequest backfillTask() {
        return salesTask(NoonSyncTriggerMode.GAP_BACKFILL);
    }

    private static NoonSyncTaskRequest salesTask(NoonSyncTriggerMode triggerMode) {
        return new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                triggerMode,
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
                10002L,
                245027L,
                "STR245027-NAE",
                "AE",
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

    private static AiOperationsDashboardScope scope() {
        return new AiOperationsDashboardScope(
                10002L,
                555L,
                "STR245027-NAE",
                "AE",
                "last7Days",
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 5, 21)
        );
    }
}
