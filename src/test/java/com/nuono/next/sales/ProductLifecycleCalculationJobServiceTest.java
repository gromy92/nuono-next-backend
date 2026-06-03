package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductLifecycleCalculationJobServiceTest {

    @Test
    void historicalBackfillReplaysAnchorsAndWritesTransitionHistory() {
        ProductLifecycleStateQuery product = new ProductLifecycleStateQuery(
                307L,
                "STR108065-NAE",
                "AE",
                "PAPERSAYSB091",
                "z-paper-1"
        );
        FakeSource source = new FakeSource(product, List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3)
        ));
        FakeRepository repository = new FakeRepository();
        RecordingClassifier classifier = new RecordingClassifier();
        ProductLifecycleCalculationJobService service = new ProductLifecycleCalculationJobService(
                source,
                repository,
                new ProductLifecycleListingDateResolver(),
                new ProductLifecycleFeatureBuilder(),
                new ProductLifecycleSalesCorrectionService(),
                classifier,
                new ProductLifecycleTransitionService(repository)
        );

        ProductLifecycleJobRecord job = service.runHistoricalBackfill(new ProductLifecycleCalculationScope(
                307L,
                "STR108065-NAE",
                "AE",
                LocalDate.of(2026, 1, 3),
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                true
        ));

        assertEquals(1, repository.resetCount);
        assertEquals(List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3)
        ), classifier.analysisDates);
        ProductLifecycleCurrentState current = repository.findCurrentState(product);
        assertEquals("growth", current.getLifecycleCode());
        assertEquals(LocalDate.of(2026, 1, 3), current.getAnalysisDate());
        assertEquals(1, repository.history.size());
        ProductLifecycleHistoryRecord transition = repository.history.get(0);
        assertEquals("new", transition.getPreviousLifecycleCode());
        assertEquals("growth", transition.getLifecycleCode());
        assertEquals(LocalDate.of(2026, 1, 2), transition.getAnalysisDate());
        assertEquals("succeeded", job.getStatus());
        assertEquals(3, job.getProcessedCount());
        assertEquals(1, job.getChangedCount());
        assertEquals(0, job.getDataInsufficientCount());
    }

    private static class FakeSource implements ProductLifecycleCalculationSource {
        private final ProductLifecycleStateQuery product;
        private final List<LocalDate> historicalAnchorDates;

        private FakeSource(ProductLifecycleStateQuery product, List<LocalDate> historicalAnchorDates) {
            this.product = product;
            this.historicalAnchorDates = historicalAnchorDates;
        }

        public List<LocalDate> listHistoricalAnchorDates(ProductLifecycleCalculationScope scope) {
            return historicalAnchorDates;
        }

        @Override
        public List<ProductLifecycleStateQuery> listProductScopes(ProductLifecycleCalculationScope scope) {
            return List.of(product);
        }

        @Override
        public ProductLifecycleListingSignals loadListingSignals(
                ProductLifecycleStateQuery query,
                LocalDate analysisDate
        ) {
            return new ProductLifecycleListingSignals(
                    query,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0
            );
        }

        @Override
        public List<DailySalesFact> loadFacts(ProductLifecycleStateQuery query, LocalDate from, LocalDate to) {
            return List.of();
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
        public boolean isStockoutDistorted(ProductLifecycleStateQuery query, ProductLifecycleFeatureSnapshot features) {
            return false;
        }
    }

    private static class RecordingClassifier extends ProductLifecycleClassifier {
        private final List<LocalDate> analysisDates = new ArrayList<>();

        @Override
        public ProductLifecycleResult classify(ProductLifecycleClassificationInput input, ProductLifecycleRuleSet ruleSet) {
            LocalDate analysisDate = input.getAnalysisDate();
            analysisDates.add(analysisDate);
            if (LocalDate.of(2026, 1, 1).equals(analysisDate)) {
                return new ProductLifecycleResult(
                        "new",
                        "新品",
                        "测试新品阶段。",
                        ProductLifecycleResult.DEFAULT_RULE_VERSION,
                        List.of(),
                        "ready",
                        "{\"reason\":\"test_new\"}"
                );
            }
            return new ProductLifecycleResult(
                    "growth",
                    "增长",
                    "测试增长阶段。",
                    ProductLifecycleResult.DEFAULT_RULE_VERSION,
                    List.of(),
                    "ready",
                    "{\"reason\":\"test_growth\"}"
            );
        }
    }

    private static class FakeRepository implements ProductLifecycleStateRepository {
        private long nextCurrentId = 90000L;
        private long nextHistoryId = 91000L;
        private long nextJobId = 92000L;
        private int resetCount;
        private final Map<ProductLifecycleStateQuery, ProductLifecycleCurrentState> currentStates = new LinkedHashMap<>();
        private final List<ProductLifecycleHistoryRecord> history = new ArrayList<>();
        private final List<ProductLifecycleJobRecord> jobs = new ArrayList<>();

        public void resetScope(ProductLifecycleCalculationScope scope) {
            resetCount++;
            currentStates.clear();
            history.clear();
        }

        @Override
        public Long nextJobId() {
            return nextJobId++;
        }

        @Override
        public void saveCurrentState(ProductLifecycleCurrentState state) {
            ProductLifecycleCurrentState persisted = state.getId() == null
                    ? copyWithId(state, nextCurrentId++)
                    : state;
            currentStates.put(queryOf(persisted), persisted);
        }

        @Override
        public void saveHistory(ProductLifecycleHistoryRecord historyRecord) {
            ProductLifecycleHistoryRecord persisted = historyRecord.getId() == null
                    ? copyWithId(historyRecord, nextHistoryId++)
                    : historyRecord;
            history.add(persisted);
        }

        @Override
        public void saveJob(ProductLifecycleJobRecord job) {
            jobs.add(job);
        }

        @Override
        public ProductLifecycleCurrentState findCurrentState(ProductLifecycleStateQuery query) {
            return currentStates.get(query);
        }

        @Override
        public List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query) {
            return history;
        }

        @Override
        public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
            return jobs;
        }

        private ProductLifecycleStateQuery queryOf(ProductLifecycleCurrentState state) {
            return new ProductLifecycleStateQuery(
                    state.getOwnerUserId(),
                    state.getStoreCode(),
                    state.getSiteCode(),
                    state.getPartnerSku(),
                    state.getSku()
            );
        }

        private ProductLifecycleCurrentState copyWithId(ProductLifecycleCurrentState state, Long id) {
            return new ProductLifecycleCurrentState(
                    id,
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
                    state.getUpdatedAt() == null ? LocalDateTime.now() : state.getUpdatedAt()
            );
        }

        private ProductLifecycleHistoryRecord copyWithId(ProductLifecycleHistoryRecord record, Long id) {
            return new ProductLifecycleHistoryRecord(
                    id,
                    record.getCurrentStateId(),
                    record.getOwnerUserId(),
                    record.getStoreCode(),
                    record.getSiteCode(),
                    record.getPartnerSku(),
                    record.getSku(),
                    record.getPreviousLifecycleCode(),
                    record.getPreviousLifecycleLabel(),
                    record.getLifecycleCode(),
                    record.getLifecycleLabel(),
                    record.getRuleVersion(),
                    record.getAnalysisDate(),
                    record.getTransitionReason(),
                    record.getEvidenceJson(),
                    record.getChangedAt() == null ? LocalDateTime.now() : record.getChangedAt()
            );
        }
    }
}
