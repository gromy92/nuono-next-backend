package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductLifecycleCalculationJobServiceTest {

    @Test
    void standardRunUsesTransitionPolicyAndRecordsJobCounts() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentStates.put(query("A"), current(query("A"), "new", "新品"));
        TestSource source = new TestSource();
        source.queries = List.of(query("A"));
        source.facts.put(query("A"), growthFacts());
        ProductLifecycleCalculationJobService service = service(repository, source);

        ProductLifecycleJobRecord job = service.run(scope(false));

        assertEquals("succeeded", job.getStatus());
        assertEquals(1, job.getProcessedCount());
        assertEquals(1, job.getChangedCount());
        assertEquals(0, job.getHeldCount());
        assertEquals(0, job.getDataInsufficientCount());
        assertEquals("growth", repository.currentStates.get(query("A")).getLifecycleCode());
        assertEquals(1, repository.savedHistory.size());
    }

    @Test
    void publishedLifecycleRuleProviderStampsCurrentHistoryAndJobWithRuleVersion() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentStates.put(query("A"), current(query("A"), "new", "新品"));
        TestSource source = new TestSource();
        source.queries = List.of(query("A"));
        source.facts.put(query("A"), growthFacts());
        ProductLifecycleCalculationJobService service = service(
                repository,
                source,
                scope -> new ProductLifecycleRuleSet("LIFECYCLE_CONFIG_v1", false)
        );

        ProductLifecycleJobRecord job = service.run(scope(false));
        ProductLifecycleCurrentState current = repository.currentStates.get(query("A"));

        assertEquals("LIFECYCLE_CONFIG_v1", job.getRuleVersion());
        assertEquals("growth", current.getLifecycleCode());
        assertEquals("增长", current.getLifecycleLabel());
        assertEquals("LIFECYCLE_CONFIG_v1", current.getRuleVersion());
        assertEquals("LIFECYCLE_CONFIG_v1", repository.savedHistory.get(0).getRuleVersion());
    }

    @Test
    void lifecycleHistoryKeepsTypedVersionEvidenceWhenProviderChanges() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentStates.put(query("A"), current(query("A"), "new", "新品"));
        TestSource source = new TestSource();
        source.queries = List.of(query("A"));
        source.facts.put(query("A"), growthFacts());
        ProductLifecycleRuleSet[] ruleSet = {
                new ProductLifecycleRuleSet(
                        "RULE_v1",
                        false,
                        "LIFECYCLE_CONFIG_v1",
                        "生命周期 v1",
                        "运营发布"
                )
        };
        ProductLifecycleCalculationJobService service = service(repository, source, scope -> ruleSet[0]);

        service.run(scope(false));
        ProductLifecycleHistoryRecord firstHistory = repository.savedHistory.get(0);
        source.facts.put(query("A"), declineFacts());
        ruleSet[0] = new ProductLifecycleRuleSet(
                "RULE_v2",
                false,
                "LIFECYCLE_CONFIG_v2",
                "生命周期 v2",
                "运营发布"
        );
        service.run(scope(true));

        assertEquals(2, repository.savedHistory.size());
        assertTrue(firstHistory.getEvidenceJson().contains("\"lifecycleVersionNo\":\"LIFECYCLE_CONFIG_v1\""));
        assertTrue(!firstHistory.getEvidenceJson().contains("LIFECYCLE_CONFIG_v2"));
        assertTrue(repository.savedHistory.get(1).getEvidenceJson().contains("\"lifecycleVersionNo\":\"LIFECYCLE_CONFIG_v2\""));
    }

    @Test
    void controlledRerunDoesNotDuplicateHistoryWhenCurrentStateAlreadyMatches() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentStates.put(query("A"), current(query("A"), "new", "新品"));
        TestSource source = new TestSource();
        source.queries = List.of(query("A"));
        source.facts.put(query("A"), growthFacts());
        ProductLifecycleCalculationJobService service = service(repository, source);

        service.run(scope(false));
        ProductLifecycleJobRecord rerun = service.run(scope(true));

        assertEquals("succeeded", rerun.getStatus());
        assertEquals(1, rerun.getProcessedCount());
        assertEquals(0, rerun.getChangedCount());
        assertEquals(1, repository.savedHistory.size());
    }

    @Test
    void stockoutRunHoldsCurrentStageAndCountsHeldProduct() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentStates.put(query("A"), current(query("A"), "stable", "稳定"));
        TestSource source = new TestSource();
        source.queries = List.of(query("A"));
        source.facts.put(query("A"), declineFacts());
        source.stockoutQueries.add(query("A"));
        ProductLifecycleCalculationJobService service = service(repository, source);

        ProductLifecycleJobRecord job = service.run(scope(false));

        assertEquals(1, job.getHeldCount());
        assertEquals("stable", repository.currentStates.get(query("A")).getLifecycleCode());
        assertEquals("stockout_hold", repository.currentStates.get(query("A")).getQualityState());
    }

    @Test
    void partialFailureIsReadableAndDoesNotClaimFullSuccess() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentStates.put(query("A"), current(query("A"), "new", "新品"));
        TestSource source = new TestSource();
        source.queries = List.of(query("A"), query("B"));
        source.facts.put(query("A"), growthFacts());
        source.failingQueries.add(query("B"));
        ProductLifecycleCalculationJobService service = service(repository, source);

        ProductLifecycleJobRecord job = service.run(scope(false));

        assertEquals("partial_failed", job.getStatus());
        assertEquals(1, job.getProcessedCount());
        assertEquals(1, job.getChangedCount());
        assertTrue(job.getFailureSummaryJson().contains("B"));
        assertEquals(1, repository.savedJobs.size());
    }

    @Test
    void schedulePolicyUsesThreeDayCadenceAndYesterdayCutoff() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ProductLifecycleSchedulePolicy policy = new ProductLifecycleSchedulePolicy(clock);

        assertEquals(3, policy.getCadenceDays());
        assertEquals(LocalDate.of(2026, 5, 22), LocalDate.now(clock));
        assertEquals(LocalDate.of(2026, 5, 21), policy.defaultAnchorDate());
    }

    private ProductLifecycleCalculationJobService service(RecordingRepository repository, TestSource source) {
        return service(repository, source, ProductLifecycleRuleProvider.defaultV1());
    }

    private ProductLifecycleCalculationJobService service(
            RecordingRepository repository,
            TestSource source,
            ProductLifecycleRuleProvider ruleProvider
    ) {
        return new ProductLifecycleCalculationJobService(
                source,
                repository,
                new ProductLifecycleListingDateResolver(),
                new ProductLifecycleFeatureBuilder(),
                new ProductLifecycleSalesCorrectionService(),
                new ProductLifecycleClassifier(),
                new ProductLifecycleTransitionService(repository),
                ruleProvider
        );
    }

    private ProductLifecycleCalculationScope scope(boolean rerun) {
        return new ProductLifecycleCalculationScope(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 20),
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                rerun
        );
    }

    private List<DailySalesFact> growthFacts() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts("A", analysisDate.minusDays(29), 15, 2, 20));
        facts.addAll(facts("A", analysisDate.minusDays(14), 15, 4, 20));
        return facts;
    }

    private List<DailySalesFact> declineFacts() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts("A", analysisDate.minusDays(29), 15, 6, 20));
        facts.addAll(facts("A", analysisDate.minusDays(14), 15, 2, 20));
        return facts;
    }

    private List<DailySalesFact> facts(String suffix, LocalDate startDate, int dayCount, int units, Integer pv) {
        List<DailySalesFact> facts = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            facts.add(fact(suffix, startDate.plusDays(i), units, pv));
        }
        return facts;
    }

    private DailySalesFact fact(String suffix, LocalDate date, int netUnits, Integer pv) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                date,
                "SAMPLE-SKU-" + suffix,
                "ZSKU-" + suffix,
                "ZCONF-" + suffix,
                "SA",
                "SAR",
                "Sanitized sample product " + suffix,
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

    private ProductLifecycleCurrentState current(ProductLifecycleStateQuery query, String code, String label) {
        return new ProductLifecycleCurrentState(
                70001L,
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getPartnerSku(),
                query.getSku(),
                code,
                label,
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                LocalDate.of(2026, 5, 17),
                LocalDate.of(2026, 1, 1),
                "official",
                "ready",
                "previous",
                "{\"reason\":\"previous\"}",
                72000L,
                null
        );
    }

    private ProductLifecycleStateQuery query(String suffix) {
        return new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-" + suffix,
                "ZSKU-" + suffix
        );
    }

    private static class TestSource implements ProductLifecycleCalculationSource {
        private List<ProductLifecycleStateQuery> queries = List.of();
        private final Map<ProductLifecycleStateQuery, List<DailySalesFact>> facts = new HashMap<>();
        private final List<ProductLifecycleStateQuery> failingQueries = new ArrayList<>();
        private final List<ProductLifecycleStateQuery> stockoutQueries = new ArrayList<>();

        @Override
        public List<ProductLifecycleStateQuery> listProductScopes(ProductLifecycleCalculationScope scope) {
            return queries;
        }

        @Override
        public ProductLifecycleListingSignals loadListingSignals(
                ProductLifecycleStateQuery query,
                LocalDate analysisDate
        ) {
            if (failingQueries.contains(query)) {
                throw new IllegalStateException("fixture failure for " + query.getPartnerSku());
            }
            return new ProductLifecycleListingSignals(
                    query,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null,
                    null,
                    null,
                    60,
                    60,
                    60,
                    0
            );
        }

        @Override
        public List<DailySalesFact> loadFacts(ProductLifecycleStateQuery query, LocalDate from, LocalDate to) {
            return facts.getOrDefault(query, List.of());
        }

        @Override
        public List<SalesActivityWindowRecord> loadActivityWindows(
                ProductLifecycleStateQuery query,
                LocalDate from,
                LocalDate to
        ) {
            return List.of();
        }

        @Override
        public boolean isStockoutDistorted(
                ProductLifecycleStateQuery query,
                ProductLifecycleFeatureSnapshot features
        ) {
            return stockoutQueries.contains(query);
        }
    }

    private static class RecordingRepository implements ProductLifecycleStateRepository {
        private long nextJobId = 72001L;
        private final Map<ProductLifecycleStateQuery, ProductLifecycleCurrentState> currentStates = new HashMap<>();
        private final List<ProductLifecycleHistoryRecord> savedHistory = new ArrayList<>();
        private final List<ProductLifecycleJobRecord> savedJobs = new ArrayList<>();

        @Override
        public Long nextJobId() {
            return nextJobId++;
        }

        @Override
        public void saveCurrentState(ProductLifecycleCurrentState state) {
            currentStates.put(new ProductLifecycleStateQuery(
                    state.getOwnerUserId(),
                    state.getStoreCode(),
                    state.getSiteCode(),
                    state.getPartnerSku(),
                    state.getSku()
            ), state);
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
            return savedHistory;
        }

        @Override
        public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
            return savedJobs;
        }
    }
}
