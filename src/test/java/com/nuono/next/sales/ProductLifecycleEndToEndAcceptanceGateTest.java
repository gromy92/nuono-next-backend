package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.aiopsdashboard.AiOperationsDashboardContribution;
import com.nuono.next.aiopsdashboard.AiOperationsDashboardOverview;
import com.nuono.next.aiopsdashboard.AiOperationsDashboardQuery;
import com.nuono.next.aiopsdashboard.AiOperationsDashboardSalesLifecycleSignalProvider;
import com.nuono.next.aiopsdashboard.AiOperationsDashboardScope;
import com.nuono.next.noonsync.NoonBusinessSyncStatusService;
import com.nuono.next.noonsync.NoonSyncFoundationService;
import com.nuono.next.salesforecast.SalesForecastFeatureBuilder;
import com.nuono.next.salesforecast.SalesForecastFeatureSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductLifecycleEndToEndAcceptanceGateTest {

    private static final Long OWNER = 10002L;
    private static final Long OPERATOR = 555L;
    private static final String STORE = "STR245027-SAU";
    private static final String SITE = "SA";
    private static final LocalDate ANCHOR = LocalDate.of(2026, 5, 21);

    @Test
    void nearRealFixturesPassLifecycleAcceptanceGateAcrossConsumers() {
        AcceptanceSource source = new AcceptanceSource();
        AcceptanceLifecycleRepository lifecycleRepository = new AcceptanceLifecycleRepository();
        lifecycleRepository.currentStates.put(query("STOCKOUT-HOLD"), currentState(query("STOCKOUT-HOLD"), "stable", "稳定"));
        lifecycleRepository.currentStates.put(query("GROWTH-MOVE"), currentState(query("GROWTH-MOVE"), "new", "新品"));

        ProductLifecycleCalculationJobService jobService = new ProductLifecycleCalculationJobService(
                source,
                lifecycleRepository,
                new ProductLifecycleListingDateResolver(),
                new ProductLifecycleFeatureBuilder(),
                new ProductLifecycleSalesCorrectionService(),
                new ProductLifecycleClassifier(),
                new ProductLifecycleTransitionService(lifecycleRepository)
        );

        ProductLifecycleJobRecord firstRun = jobService.run(scope(false));
        ProductLifecycleJobRecord rerun = jobService.run(scope(true));

        assertEquals("succeeded", firstRun.getStatus());
        assertEquals(source.queries.size(), firstRun.getProcessedCount());
        assertEquals(1, firstRun.getHeldCount());
        assertEquals(1, firstRun.getChangedCount());
        assertEquals("succeeded", rerun.getStatus());
        assertEquals(1, lifecycleRepository.savedHistory.size(), "Controlled rerun must not duplicate transition history");

        ProductLifecycleCurrentState oldStable = lifecycleRepository.currentStates.get(query("OLD-STABLE"));
        assertFalse("new".equals(oldStable.getLifecycleCode()), "60-day historical signals must not initialize as new");
        assertEquals("inventory", oldStable.getListingDateSource());
        assertResultCarriesAuditEvidence(oldStable);

        ProductLifecycleCurrentState stockoutHold = lifecycleRepository.currentStates.get(query("STOCKOUT-HOLD"));
        assertEquals("stable", stockoutHold.getLifecycleCode());
        assertEquals("stockout_hold", stockoutHold.getQualityState());
        assertTrue(stockoutHold.getExplanation().contains("库存"));

        ProductLifecycleCurrentState pvEstimated = lifecycleRepository.currentStates.get(query("PV-ESTIMATED"));
        assertEquals("stable", pvEstimated.getLifecycleCode());
        assertTrue(pvEstimated.getEvidenceJson().contains("pv_estimated"));
        assertFalse("data_insufficient".equals(pvEstimated.getLifecycleCode()));

        ProductLifecycleCurrentState activityCorrected = lifecycleRepository.currentStates.get(query("ACTIVITY-CORRECTED"));
        assertEquals("stable", activityCorrected.getLifecycleCode(), "Activity uplift must be corrected before growth classification");
        assertTrue(activityCorrected.getEvidenceJson().contains("\"correctedRecent15Sales\":60.0000"));
        assertTrue(activityCorrected.getEvidenceJson().contains("\"correctedPrevious15Sales\":60.0000"));

        ProductLifecycleCurrentState lowVolume = lifecycleRepository.currentStates.get(query("LOW-VOLUME"));
        assertEquals("longTail", lowVolume.getLifecycleCode());
        assertTrue(lowVolume.getEvidenceJson().contains("low_volume_volatility_guard"));

        ProductLifecycleCurrentState growthMove = lifecycleRepository.currentStates.get(query("GROWTH-MOVE"));
        assertEquals("growth", growthMove.getLifecycleCode());
        assertEquals("GROWTH-MOVE", lifecycleRepository.savedHistory.get(0).getPartnerSku());

        AcceptanceSalesFactRepository salesRepository = new AcceptanceSalesFactRepository(source.allFacts());
        SalesAnalyticsService salesAnalyticsService = new SalesAnalyticsService(
                salesRepository,
                new ProductLifecycleClassifier(),
                new NoonBusinessSyncStatusService(new NoonSyncFoundationService()),
                lifecycleRepository
        );
        List<SalesProductRow> productRows = salesAnalyticsService.listProductRows(salesQuery());
        List<SalesForecastFeatureSnapshot> forecastSnapshots = new SalesForecastFeatureBuilder(
                new ProductLifecycleClassifier(),
                lifecycleRepository
        ).build(source.allFacts(), ANCHOR);
        AiOperationsDashboardContribution dashboard = new AiOperationsDashboardSalesLifecycleSignalProvider(
                salesAnalyticsService
        ).contribute(aiQuery(), aiScope());

        assertEquals(
                lifecycleRepository.currentStates.get(query("GROWTH-MOVE")).getLifecycleCode(),
                productRow(productRows, "GROWTH-MOVE").getLifecycleCode()
        );
        assertEquals(
                productRow(productRows, "GROWTH-MOVE").getLifecycleCode(),
                forecastSnapshot(forecastSnapshots, "GROWTH-MOVE").getLifecycleCode()
        );
        assertEquals(BigDecimal.ONE, metric(dashboard, "sales_lifecycle_growth").getValue());
        assertEquals(BigDecimal.valueOf(4), metric(dashboard, "sales_lifecycle_stable").getValue());
        assertEquals(BigDecimal.ONE, metric(dashboard, "sales_lifecycle_long_tail").getValue());
        assertTrue(signal(dashboard, "sales_possible_stockout_distortion")
                .getEvidence().get(0).getDescription().contains("qualityState=stockout_hold"));
    }

    private void assertResultCarriesAuditEvidence(ProductLifecycleCurrentState state) {
        assertEquals(ProductLifecycleResult.DEFAULT_RULE_VERSION, state.getRuleVersion());
        assertEquals(ANCHOR, state.getAnalysisDate());
        assertNotNull(state.getListingDate());
        assertNotNull(state.getListingDateSource());
        assertNotNull(state.getQualityState());
        assertNotNull(state.getExplanation());
        assertTrue(state.getEvidenceJson().contains("recent15Sales"));
        assertTrue(state.getEvidenceJson().contains("qualityReasons"));
    }

    private static ProductLifecycleCalculationScope scope(boolean rerun) {
        return new ProductLifecycleCalculationScope(
                OWNER,
                STORE,
                SITE,
                ANCHOR,
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                rerun
        );
    }

    private static SalesFactQuery salesQuery() {
        return new SalesFactQuery(OWNER, STORE, SITE, ANCHOR.minusDays(59), ANCHOR, null, null);
    }

    private static AiOperationsDashboardQuery aiQuery() {
        return new AiOperationsDashboardQuery(OWNER, OPERATOR, STORE, SITE, "last60Days");
    }

    private static AiOperationsDashboardScope aiScope() {
        return new AiOperationsDashboardScope(
                OWNER,
                OPERATOR,
                STORE,
                SITE,
                "last60Days",
                ANCHOR.minusDays(59),
                ANCHOR
        );
    }

    private static SalesProductRow productRow(List<SalesProductRow> rows, String partnerSku) {
        return rows.stream()
                .filter(row -> partnerSku.equals(row.getPartnerSku()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing sales analytics row " + partnerSku));
    }

    private static SalesForecastFeatureSnapshot forecastSnapshot(
            List<SalesForecastFeatureSnapshot> snapshots,
            String partnerSku
    ) {
        return snapshots.stream()
                .filter(snapshot -> partnerSku.equals(snapshot.getPartnerSku()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing forecast snapshot " + partnerSku));
    }

    private static AiOperationsDashboardOverview.MetricCard metric(
            AiOperationsDashboardContribution contribution,
            String key
    ) {
        return contribution.getMetricCards().stream()
                .filter(card -> key.equals(card.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric " + key));
    }

    private static AiOperationsDashboardOverview.Signal signal(
            AiOperationsDashboardContribution contribution,
            String key
    ) {
        return contribution.getSignals().stream()
                .filter(item -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing signal " + key));
    }

    private static ProductLifecycleCurrentState currentState(
            ProductLifecycleStateQuery query,
            String code,
            String label
    ) {
        return new ProductLifecycleCurrentState(
                80000L + Math.abs(query.getPartnerSku().hashCode() % 1000),
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getPartnerSku(),
                query.getSku(),
                code,
                label,
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                ANCHOR.minusDays(3),
                ANCHOR.minusDays(90),
                "official",
                "ready",
                "previous lifecycle state",
                "{\"reason\":\"previous\"}",
                70000L,
                LocalDateTime.of(2026, 5, 18, 8, 0)
        );
    }

    private static ProductLifecycleStateQuery query(String partnerSku) {
        return new ProductLifecycleStateQuery(OWNER, STORE, SITE, partnerSku, partnerSku + "-SKU");
    }

    private static DailySalesFact fact(String partnerSku, LocalDate date, int netUnits, Integer pv) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                OWNER,
                245027L,
                STORE,
                SITE,
                date,
                partnerSku,
                partnerSku + "-SKU",
                partnerSku + "-CONFIG",
                SITE,
                "SAR",
                "Acceptance fixture " + partnerSku,
                pv,
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

    private static class AcceptanceSource implements ProductLifecycleCalculationSource {
        private final List<ProductLifecycleStateQuery> queries = List.of(
                query("OLD-STABLE"),
                query("STOCKOUT-HOLD"),
                query("PV-ESTIMATED"),
                query("ACTIVITY-CORRECTED"),
                query("LOW-VOLUME"),
                query("GROWTH-MOVE")
        );
        private final Map<ProductLifecycleStateQuery, List<DailySalesFact>> facts = new HashMap<>();

        private AcceptanceSource() {
            addOldStable();
            addStockoutHold();
            addPvEstimated();
            addActivityCorrected();
            addLowVolume();
            addGrowthMove();
        }

        @Override
        public List<ProductLifecycleStateQuery> listProductScopes(ProductLifecycleCalculationScope scope) {
            return queries;
        }

        @Override
        public ProductLifecycleListingSignals loadListingSignals(
                ProductLifecycleStateQuery query,
                LocalDate analysisDate
        ) {
            return new ProductLifecycleListingSignals(
                    query,
                    null,
                    ANCHOR.minusDays(59),
                    ANCHOR.minusDays(59),
                    ANCHOR.minusDays(59),
                    ANCHOR.minusDays(2),
                    60,
                    60,
                    60,
                    60
            );
        }

        @Override
        public List<DailySalesFact> loadFacts(ProductLifecycleStateQuery query, LocalDate from, LocalDate to) {
            return facts.getOrDefault(query, List.of()).stream()
                    .filter(fact -> !fact.getFactDate().isBefore(from) && !fact.getFactDate().isAfter(to))
                    .collect(Collectors.toList());
        }

        @Override
        public List<SalesActivityWindowRecord> loadActivityWindows(
                ProductLifecycleStateQuery query,
                LocalDate from,
                LocalDate to
        ) {
            if (!"ACTIVITY-CORRECTED".equals(query.getPartnerSku())) {
                return List.of();
            }
            return List.of(new SalesActivityWindowRecord(
                    90001L,
                    OWNER,
                    STORE,
                    SITE,
                    "Ramadan uplift fixture",
                    "seasonal",
                    "all",
                    ANCHOR.minusDays(14),
                    ANCHOR,
                    new BigDecimal("2.0000"),
                    true,
                    1,
                    OPERATOR,
                    OPERATOR
            ));
        }

        @Override
        public boolean isStockoutDistorted(
                ProductLifecycleStateQuery query,
                ProductLifecycleFeatureSnapshot features
        ) {
            return "STOCKOUT-HOLD".equals(query.getPartnerSku());
        }

        private List<DailySalesFact> allFacts() {
            return facts.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }

        private void addOldStable() {
            addDailyFacts("OLD-STABLE", ANCHOR.minusDays(59), 30, 1, 20);
            addDailyFacts("OLD-STABLE", ANCHOR.minusDays(29), 15, 4, 20);
            addDailyFacts("OLD-STABLE", ANCHOR.minusDays(14), 15, 4, 20);
        }

        private void addStockoutHold() {
            addDailyFacts("STOCKOUT-HOLD", ANCHOR.minusDays(59), 30, 1, 20);
            addDailyFacts("STOCKOUT-HOLD", ANCHOR.minusDays(29), 15, 6, 20);
            addDailyFacts("STOCKOUT-HOLD", ANCHOR.minusDays(14), 15, 2, 20);
        }

        private void addPvEstimated() {
            addDailyFacts("PV-ESTIMATED", ANCHOR.minusDays(59), 30, 1, 20);
            addDailyFacts("PV-ESTIMATED", ANCHOR.minusDays(29), 15, 2, 20);
            addDailyFacts("PV-ESTIMATED", ANCHOR.minusDays(14), 15, 2, null);
        }

        private void addActivityCorrected() {
            addDailyFacts("ACTIVITY-CORRECTED", ANCHOR.minusDays(59), 30, 1, 20);
            addDailyFacts("ACTIVITY-CORRECTED", ANCHOR.minusDays(29), 15, 4, 20);
            addDailyFacts("ACTIVITY-CORRECTED", ANCHOR.minusDays(14), 15, 8, 20);
        }

        private void addLowVolume() {
            addDailyFacts("LOW-VOLUME", ANCHOR.minusDays(29), 13, 0, 1);
            addDailyFacts("LOW-VOLUME", ANCHOR.minusDays(16), 1, 1, 10);
            addDailyFacts("LOW-VOLUME", ANCHOR.minusDays(15), 14, 0, 1);
            addDailyFacts("LOW-VOLUME", ANCHOR.minusDays(1), 1, 1, 10);
        }

        private void addGrowthMove() {
            addDailyFacts("GROWTH-MOVE", ANCHOR.minusDays(59), 30, 1, 20);
            addDailyFacts("GROWTH-MOVE", ANCHOR.minusDays(29), 15, 1, 20);
            addDailyFacts("GROWTH-MOVE", ANCHOR.minusDays(14), 15, 3, 30);
        }

        private void addDailyFacts(String partnerSku, LocalDate start, int days, int units, Integer pv) {
            ProductLifecycleStateQuery query = query(partnerSku);
            List<DailySalesFact> productFacts = facts.computeIfAbsent(query, ignored -> new ArrayList<>());
            for (int i = 0; i < days; i++) {
                productFacts.add(fact(partnerSku, start.plusDays(i), units, pv));
            }
        }
    }

    private static class AcceptanceLifecycleRepository implements ProductLifecycleStateRepository {
        private long nextJobId = 91001L;
        private long nextStateId = 82001L;
        private final Map<ProductLifecycleStateQuery, ProductLifecycleCurrentState> currentStates = new HashMap<>();
        private final List<ProductLifecycleHistoryRecord> savedHistory = new ArrayList<>();
        private final List<ProductLifecycleJobRecord> savedJobs = new ArrayList<>();

        @Override
        public Long nextJobId() {
            return nextJobId++;
        }

        @Override
        public void saveCurrentState(ProductLifecycleCurrentState state) {
            ProductLifecycleCurrentState stateWithId = state.getId() == null
                    ? new ProductLifecycleCurrentState(
                            nextStateId++,
                            state.getOwnerUserId(),
                            state.getStoreCode(),
                            state.getSiteCode(),
                            state.getPartnerSku(),
                            state.getSku(),
                            state.getLifecycleCode(),
                            state.getLifecycleLabel(),
                            state.getRuleVersion(),
                            state.getAnalysisDate(),
                            state.getListingDate(),
                            state.getListingDateSource(),
                            state.getQualityState(),
                            state.getExplanation(),
                            state.getEvidenceJson(),
                            state.getLastJobId(),
                            state.getUpdatedAt()
                    )
                    : state;
            currentStates.put(query(state.getPartnerSku()), stateWithId);
        }

        @Override
        public void saveHistory(ProductLifecycleHistoryRecord historyRecord) {
            savedHistory.add(historyRecord);
        }

        @Override
        public void saveJob(ProductLifecycleJobRecord job) {
            savedJobs.add(job);
        }

        @Override
        public ProductLifecycleCurrentState findCurrentState(ProductLifecycleStateQuery query) {
            return currentStates.get(query);
        }

        @Override
        public List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query) {
            return savedHistory.stream()
                    .filter(record -> query.getPartnerSku().equals(record.getPartnerSku())
                            && query.getSku().equals(record.getSku()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
            return savedJobs;
        }
    }

    private static class AcceptanceSalesFactRepository implements SalesFactRepository {
        private final List<DailySalesFact> facts;

        private AcceptanceSalesFactRepository(List<DailySalesFact> facts) {
            this.facts = facts;
        }

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
            return facts.stream()
                    .filter(fact -> query.getOwnerUserId().equals(fact.getOwnerUserId()))
                    .filter(fact -> query.getStoreCode().equals(fact.getStoreCode()))
                    .filter(fact -> query.getSiteCode().equals(fact.getSiteCode()))
                    .filter(fact -> query.getPartnerSku() == null
                            || query.getPartnerSku().equals(fact.getPartnerSku()))
                    .filter(fact -> query.getSku() == null || query.getSku().equals(fact.getSku()))
                    .filter(fact -> query.getDateFrom() == null
                            || !fact.getFactDate().isBefore(query.getDateFrom()))
                    .filter(fact -> query.getDateTo() == null
                            || !fact.getFactDate().isAfter(query.getDateTo()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<SalesImportBatchRecord> listImportBatches(SalesImportBatchQuery query) {
            return List.of();
        }

        @Override
        public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return ANCHOR;
        }
    }
}
