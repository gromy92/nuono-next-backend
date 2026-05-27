package com.nuono.next.aiopsdashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonsync.NoonBusinessSyncStatusService;
import com.nuono.next.noonsync.NoonSalesCorrectionSurfaceState;
import com.nuono.next.noonsync.NoonSalesSyncReadModel;
import com.nuono.next.noonsync.NoonSalesSyncSurfaceState;
import com.nuono.next.noonsync.NoonSyncFoundationService;
import com.nuono.next.noonsync.NoonSyncTaskStatus;
import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.NoonSalesCsvImportService;
import com.nuono.next.sales.ProductLifecycleClassifier;
import com.nuono.next.sales.ProductLifecycleResult;
import com.nuono.next.sales.SalesAnalyticsService;
import com.nuono.next.sales.SalesAnalyticsSummary;
import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesFactRepository;
import com.nuono.next.sales.SalesImportBatch;
import com.nuono.next.sales.SalesImportBatchQuery;
import com.nuono.next.sales.SalesImportBatchRecord;
import com.nuono.next.sales.SalesProductRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AiOperationsDashboardSalesLifecycleSignalProviderTest {

    @Test
    void contributesDefaultV1SixCategoryLifecycleDistributionWithVersionEvidence() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.latestFactDate = LocalDate.of(2026, 5, 21);
        repository.facts = sixCategoryFacts();
        AiOperationsDashboardSalesLifecycleSignalProvider provider = provider(repository, new NoonSyncFoundationService());

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last30Days"),
                scope()
        );

        assertLifecycleMetric(contribution, "sales_lifecycle_new", "新品", BigDecimal.ONE);
        assertLifecycleMetric(contribution, "sales_lifecycle_growth", "增长", BigDecimal.ONE);
        assertLifecycleMetric(contribution, "sales_lifecycle_stable", "稳定", BigDecimal.ONE);
        assertLifecycleMetric(contribution, "sales_lifecycle_decline", "衰退", BigDecimal.ONE);
        assertLifecycleMetric(contribution, "sales_lifecycle_long_tail", "长尾期", BigDecimal.ONE);
        assertLifecycleMetric(contribution, "sales_lifecycle_data_insufficient", "数据不足", BigDecimal.ONE);

        AiOperationsDashboardOverview.Signal signal = signal(contribution, "sales_lifecycle_distribution");
        assertEquals("sales_lifecycle_distribution", signal.getKey());
        assertEquals("ready", signal.getState());
        assertEquals("info", signal.getSeverity());
        assertTrue(signal.getDescription().contains("DEFAULT_V1"));
        assertTrue(signal.getDescription().contains("数据不足 1"));

        AiOperationsDashboardOverview.EvidenceItem evidence = contribution.getEvidence().get(0);
        assertEquals("sales-lifecycle-default-v1-distribution", evidence.getId());
        assertTrue(evidence.getDescription().contains("ruleVersion=DEFAULT_V1"));
        assertTrue(evidence.getDescription().contains("new=1"));
        assertTrue(evidence.getDescription().contains("growth=1"));
        assertTrue(evidence.getDescription().contains("stable=1"));
        assertTrue(evidence.getDescription().contains("decline=1"));
        assertTrue(evidence.getDescription().contains("longTail=1"));
        assertTrue(evidence.getDescription().contains("data_insufficient=1"));
    }

    @Test
    void doesNotFabricateLifecycleDistributionWhenSalesDataNeedsBackfill() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        AiOperationsDashboardSalesLifecycleSignalProvider provider = provider(repository, new NoonSyncFoundationService());

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last30Days"),
                scope()
        );

        AiOperationsDashboardOverview.MetricCard dataInsufficient =
                metric(contribution, "sales_lifecycle_data_insufficient");
        assertEquals("backfill_required", dataInsufficient.getState());
        assertEquals("backfill_required", dataInsufficient.getQualityState());
        assertNull(dataInsufficient.getValue(), "Unavailable lifecycle distribution must not render fake zero counts");
        assertTrue(contribution.getEvidence().get(0).getDescription().contains("businessMetricsAllowed=false"));
    }

    @Test
    void contributesDeterministicSalesAnomalySignalsWithEvidenceAndDrillThrough() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.latestFactDate = LocalDate.of(2026, 5, 21);
        repository.facts = anomalyFacts();
        AiOperationsDashboardSalesLifecycleSignalProvider provider = provider(repository, new NoonSyncFoundationService());

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last30Days"),
                scope()
        );

        List<String> orderedKeys = contribution.getSignals().stream()
                .map(AiOperationsDashboardOverview.Signal::getKey)
                .filter(key -> key.startsWith("sales_"))
                .collect(Collectors.toList());
        assertEquals(List.of(
                "sales_declining_products",
                "sales_data_insufficient_products",
                "sales_long_tail_products",
                "sales_lifecycle_distribution"
        ), orderedKeys);

        AiOperationsDashboardOverview.Signal decline = signal(contribution, "sales_declining_products");
        assertEquals("warning", decline.getSeverity());
        assertTrue(decline.getDescription().contains("DECLINE"));
        assertTrue(decline.getDrillThroughPath().contains("/data/sales-analysis"));
        assertTrue(decline.getDrillThroughPath().contains("search=DECLINE"));
        assertTrue(decline.getEvidence().get(0).getDescription().contains("ruleVersion=DEFAULT_V1"));
        assertTrue(decline.getEvidence().get(0).getDescription().contains("lifecycle=decline"));

        AiOperationsDashboardOverview.Signal insufficient = signal(contribution, "sales_data_insufficient_products");
        assertEquals("warning", insufficient.getSeverity());
        assertTrue(insufficient.getEvidence().get(0).getDescription().contains("lifecycle=data_insufficient"));

        AiOperationsDashboardOverview.Signal longTail = signal(contribution, "sales_long_tail_products");
        assertEquals("info", longTail.getSeverity());
        assertTrue(longTail.getEvidence().get(0).getDescription().contains("lifecycle=longTail"));
    }

    @Test
    void contributesStaleSalesDataSignalWithoutFabricatingProductAnomalies() {
        RecordingSalesFactRepository repository = new RecordingSalesFactRepository();
        repository.latestFactDate = LocalDate.of(2026, 5, 10);
        repository.facts = anomalyFacts();
        AiOperationsDashboardSalesLifecycleSignalProvider provider = provider(repository, new NoonSyncFoundationService());

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last30Days"),
                scope()
        );

        AiOperationsDashboardOverview.Signal stale = signal(contribution, "sales_stale_data");
        assertEquals("stale", stale.getState());
        assertEquals("warning", stale.getSeverity());
        assertTrue(stale.getDrillThroughPath().contains("/data/sales-analysis"));
        assertTrue(stale.getEvidence().get(0).getDescription().contains("syncState=STALE_LATEST_SALES"));
        assertTrue(stale.getEvidence().get(0).getDescription().contains("latestAvailableSalesDate=2026-05-10"));
        assertTrue(contribution.getSignals().stream()
                .noneMatch(signal -> "sales_declining_products".equals(signal.getKey())));
    }

    @Test
    void representsPossibleStockoutDistortionWhenLifecycleRowsExposeWarningCode() {
        SalesProductRow stockoutRow = new SalesProductRow(
                "STOCKOUT",
                "STOCKOUT-SKU",
                "Stockout Product",
                LocalDate.of(2026, 5, 21),
                List.of(NoonSalesCsvImportService.SOURCE_SYSTEM),
                new ProductLifecycleResult(
                        "stable",
                        "稳定",
                        "销量下降同时出现零库存，先标记为库存失真而非自然衰退。",
                        ProductLifecycleClassifier.DEFAULT_RULE_VERSION,
                        List.of("possible_stockout_distortion")
                ),
                new SalesAnalyticsSummary(
                        9,
                        9,
                        9,
                        0,
                        BigDecimal.valueOf(90),
                        10,
                        20,
                        BigDecimal.valueOf(45),
                        null
                )
        );
        AiOperationsDashboardSalesLifecycleSignalProvider provider =
                new AiOperationsDashboardSalesLifecycleSignalProvider(new StubSalesAnalyticsService(List.of(stockoutRow)));

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last30Days"),
                scope()
        );

        AiOperationsDashboardOverview.Signal stockout = signal(contribution, "sales_possible_stockout_distortion");
        assertEquals("warning", stockout.getSeverity());
        assertTrue(stockout.getDescription().contains("STOCKOUT"));
        assertTrue(stockout.getEvidence().get(0).getDescription().contains("warning=possible_stockout_distortion"));
        assertTrue(stockout.getDrillThroughPath().contains("search=STOCKOUT"));
    }

    @Test
    void representsPersistedStockoutHoldLifecycleQualityAsAnomalyEvidence() {
        SalesProductRow stockoutHoldRow = new SalesProductRow(
                "STOCKOUTHOLD",
                "STOCKOUTHOLD-SKU",
                "Stockout Hold Product",
                LocalDate.of(2026, 5, 21),
                List.of(NoonSalesCsvImportService.SOURCE_SYSTEM),
                new ProductLifecycleResult(
                        "stable",
                        "稳定",
                        "库存异常或断货可能压低销量，暂时保持上一生命周期阶段。",
                        ProductLifecycleClassifier.DEFAULT_RULE_VERSION,
                        List.of("lifecycle_stockout_hold"),
                        "stockout_hold",
                        "{\"reason\":\"stockout_hold\"}"
                ),
                new SalesAnalyticsSummary(
                        9,
                        9,
                        9,
                        0,
                        BigDecimal.valueOf(90),
                        10,
                        20,
                        BigDecimal.valueOf(45),
                        null
                )
        );
        AiOperationsDashboardSalesLifecycleSignalProvider provider =
                new AiOperationsDashboardSalesLifecycleSignalProvider(new StubSalesAnalyticsService(List.of(stockoutHoldRow)));

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last30Days"),
                scope()
        );

        AiOperationsDashboardOverview.Signal stockout = signal(contribution, "sales_possible_stockout_distortion");
        assertTrue(stockout.getDescription().contains("STOCKOUTHOLD"));
        assertTrue(stockout.getEvidence().get(0).getDescription().contains("qualityState=stockout_hold"));
    }

    @Test
    void pendingLifecycleRowsMakeDistributionPendingInsteadOfFakeZeros() {
        SalesProductRow pendingRow = new SalesProductRow(
                "PENDING",
                "PENDING-SKU",
                "Pending Product",
                LocalDate.of(2026, 5, 21),
                List.of(NoonSalesCsvImportService.SOURCE_SYSTEM),
                new ProductLifecycleResult(
                        "pending",
                        "待计算",
                        "生命周期状态尚未完成计算。",
                        ProductLifecycleClassifier.DEFAULT_RULE_VERSION,
                        List.of(),
                        "pending",
                        "{\"reason\":\"lifecycle_pending\"}"
                ),
                new SalesAnalyticsSummary(
                        9,
                        9,
                        9,
                        0,
                        BigDecimal.valueOf(90),
                        10,
                        20,
                        BigDecimal.valueOf(45),
                        null
                )
        );
        AiOperationsDashboardSalesLifecycleSignalProvider provider =
                new AiOperationsDashboardSalesLifecycleSignalProvider(new StubSalesAnalyticsService(List.of(pendingRow)));

        AiOperationsDashboardContribution contribution = provider.contribute(
                new AiOperationsDashboardQuery(10002L, 555L, "STR245027-NAE", "AE", "last30Days"),
                scope()
        );

        AiOperationsDashboardOverview.MetricCard growth = metric(contribution, "sales_lifecycle_growth");
        assertEquals("lifecycle_pending", growth.getState());
        assertEquals("lifecycle_pending", growth.getQualityState());
        assertEquals(null, growth.getValue());
        assertTrue(signal(contribution, "sales_lifecycle_distribution").getDescription().contains("生命周期状态待计算"));
    }

    private static AiOperationsDashboardSalesLifecycleSignalProvider provider(
            RecordingSalesFactRepository repository,
            NoonSyncFoundationService foundation
    ) {
        return new AiOperationsDashboardSalesLifecycleSignalProvider(new SalesAnalyticsService(
                repository,
                new ProductLifecycleClassifier(),
                new NoonBusinessSyncStatusService(foundation)
        ));
    }

    private static void assertLifecycleMetric(
            AiOperationsDashboardContribution contribution,
            String key,
            String label,
            BigDecimal expectedValue
    ) {
        AiOperationsDashboardOverview.MetricCard card = metric(contribution, key);
        assertEquals("生命周期：" + label, card.getTitle());
        assertEquals("ready", card.getState());
        assertEquals("ready", card.getQualityState());
        assertEquals(expectedValue, card.getValue());
        assertEquals("个", card.getUnit());
        assertTrue(card.getDescription().contains("DEFAULT_V1"));
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

    private static AiOperationsDashboardOverview.Signal signal(
            AiOperationsDashboardContribution contribution,
            String key
    ) {
        return contribution.getSignals().stream()
                .filter(signal -> key.equals(signal.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing signal " + key));
    }

    private static List<DailySalesFact> anomalyFacts() {
        List<DailySalesFact> facts = new ArrayList<>();
        addOldAnchor(facts, "DECLINE");
        addProductFacts(facts, "DECLINE", LocalDate.of(2026, 4, 25), 7, 12);
        addProductFacts(facts, "DECLINE", LocalDate.of(2026, 5, 14), 7, 3);
        addOldAnchor(facts, "LONGTAIL");
        addProductFacts(facts, "LONGTAIL", LocalDate.of(2026, 4, 25), 7, 2);
        addOldAnchor(facts, "INSUFFICIENT");
        addProductFacts(facts, "INSUFFICIENT", LocalDate.of(2026, 5, 17), 4, 2);
        return facts;
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
                10002L,
                245027L,
                "STR245027-NAE",
                "AE",
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
                null,
                null
        );
    }

    private static AiOperationsDashboardScope scope() {
        return new AiOperationsDashboardScope(
                10002L,
                555L,
                "STR245027-NAE",
                "AE",
                "last30Days",
                LocalDate.of(2026, 4, 22),
                LocalDate.of(2026, 5, 21)
        );
    }

    private static class RecordingSalesFactRepository implements SalesFactRepository {
        private List<DailySalesFact> facts = List.of();
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
            return facts;
        }

        @Override
        public List<SalesImportBatchRecord> listImportBatches(SalesImportBatchQuery query) {
            return List.of();
        }

        @Override
        public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return latestFactDate;
        }
    }

    private static class StubSalesAnalyticsService extends SalesAnalyticsService {
        private final List<SalesProductRow> rows;

        private StubSalesAnalyticsService(List<SalesProductRow> rows) {
            super(new RecordingSalesFactRepository(), new ProductLifecycleClassifier(), null);
            this.rows = rows;
        }

        @Override
        public SalesAnalyticsSummary getSummary(SalesFactQuery query) {
            return new SalesAnalyticsSummary(
                    9,
                    9,
                    9,
                    0,
                    BigDecimal.valueOf(90),
                    10,
                    20,
                    BigDecimal.valueOf(45),
                    null,
                    new NoonSalesSyncReadModel(
                            NoonSalesSyncSurfaceState.READY,
                            NoonSalesCorrectionSurfaceState.OK,
                            LocalDate.of(2026, 5, 21),
                            NoonSyncTaskStatus.SUCCEEDED,
                            null,
                            "ready",
                            true,
                            true,
                            List.of()
                    ),
                    true
            );
        }

        @Override
        public List<SalesProductRow> listProductRows(SalesFactQuery query) {
            return rows;
        }
    }
}
