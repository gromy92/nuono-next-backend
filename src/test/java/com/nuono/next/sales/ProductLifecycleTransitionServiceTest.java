package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleTransitionServiceTest {

    @Test
    void writesHistoryWhenCandidateChangesCurrentLifecycleStage() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentState = current("new", "新品", "ready");
        ProductLifecycleTransitionService service = new ProductLifecycleTransitionService(repository);

        service.apply(command(result("growth", "增长", "ready"), false));

        assertEquals("growth", repository.savedCurrentStates.get(0).getLifecycleCode());
        assertEquals(1, repository.savedHistory.size());
        assertEquals("new", repository.savedHistory.get(0).getPreviousLifecycleCode());
        assertEquals("growth", repository.savedHistory.get(0).getLifecycleCode());
        assertEquals("lifecycle_transition", repository.savedHistory.get(0).getTransitionReason());
    }

    @Test
    void stockoutDistortionHoldsPreviousStageAndRecordsHoldEvidence() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentState = current("stable", "稳定", "ready");
        ProductLifecycleTransitionService service = new ProductLifecycleTransitionService(repository);

        service.apply(command(result("decline", "衰退", "ready"), true));

        assertEquals("stable", repository.savedCurrentStates.get(0).getLifecycleCode());
        assertEquals("stockout_hold", repository.savedCurrentStates.get(0).getQualityState());
        assertTrue(repository.savedCurrentStates.get(0).getEvidenceJson().contains("stockout_hold"));
        assertEquals(0, repository.savedHistory.size());
    }

    @Test
    void firstRunStockoutDoesNotInventLifecycleStage() {
        RecordingRepository repository = new RecordingRepository();
        ProductLifecycleTransitionService service = new ProductLifecycleTransitionService(repository);

        service.apply(command(result("decline", "衰退", "ready"), true));

        assertEquals("data_insufficient", repository.savedCurrentStates.get(0).getLifecycleCode());
        assertEquals("stockout_no_previous_state", repository.savedCurrentStates.get(0).getQualityState());
        assertEquals(0, repository.savedHistory.size());
    }

    @Test
    void dataInsufficientCandidateHoldsPreviousStageInsteadOfCreatingNoisyDowngrade() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentState = current("stable", "稳定", "ready");
        ProductLifecycleTransitionService service = new ProductLifecycleTransitionService(repository);

        service.apply(command(result("data_insufficient", "数据不足", "data_insufficient"), false));

        assertEquals("stable", repository.savedCurrentStates.get(0).getLifecycleCode());
        assertEquals("data_insufficient_hold", repository.savedCurrentStates.get(0).getQualityState());
        assertEquals(0, repository.savedHistory.size());
    }

    @Test
    void sameStageRerunUpdatesCurrentStateWithoutDuplicateTransitionHistory() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentState = current("growth", "增长", "ready");
        ProductLifecycleTransitionService service = new ProductLifecycleTransitionService(repository);

        service.apply(command(result("growth", "增长", "ready"), false));

        assertEquals("growth", repository.savedCurrentStates.get(0).getLifecycleCode());
        assertEquals("ready", repository.savedCurrentStates.get(0).getQualityState());
        assertEquals(0, repository.savedHistory.size());
    }

    @Test
    void idempotentRerunDoesNotDuplicateTransitionHistoryAfterCurrentStateIsUpdated() {
        RecordingRepository repository = new RecordingRepository();
        repository.currentState = current("new", "新品", "ready");
        ProductLifecycleTransitionService service = new ProductLifecycleTransitionService(repository);

        service.apply(command(result("growth", "增长", "ready"), false));
        service.apply(command(result("growth", "增长", "ready"), false));

        assertEquals("growth", repository.currentState.getLifecycleCode());
        assertEquals(1, repository.savedHistory.size());
    }

    private ProductLifecycleTransitionCommand command(ProductLifecycleResult candidate, boolean stockoutDistorted) {
        return new ProductLifecycleTransitionCommand(
                query(),
                candidate,
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 1, 1),
                "official",
                72001L,
                stockoutDistorted
        );
    }

    private ProductLifecycleResult result(String code, String label, String qualityState) {
        return new ProductLifecycleResult(
                code,
                label,
                "candidate explanation",
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                List.of(),
                qualityState,
                "{\"reason\":\"candidate\"}"
        );
    }

    private ProductLifecycleCurrentState current(String code, String label, String qualityState) {
        return new ProductLifecycleCurrentState(
                70001L,
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                code,
                label,
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                LocalDate.of(2026, 5, 17),
                LocalDate.of(2026, 1, 1),
                "official",
                qualityState,
                "previous explanation",
                "{\"reason\":\"previous\"}",
                72000L,
                LocalDateTime.of(2026, 5, 17, 3, 0)
        );
    }

    private ProductLifecycleStateQuery query() {
        return new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1"
        );
    }

    private static class RecordingRepository implements ProductLifecycleStateRepository {
        private ProductLifecycleCurrentState currentState;
        private final List<ProductLifecycleCurrentState> savedCurrentStates = new ArrayList<>();
        private final List<ProductLifecycleHistoryRecord> savedHistory = new ArrayList<>();

        @Override
        public void saveCurrentState(ProductLifecycleCurrentState state) {
            savedCurrentStates.add(state);
            currentState = state;
        }

        @Override
        public void saveHistory(ProductLifecycleHistoryRecord historyRecord) {
            savedHistory.add(historyRecord);
        }

        @Override
        public void saveJob(ProductLifecycleJobRecord job) {
        }

        @Override
        public ProductLifecycleCurrentState findCurrentState(ProductLifecycleStateQuery query) {
            return currentState;
        }

        @Override
        public List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query) {
            return savedHistory;
        }

        @Override
        public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
            return List.of();
        }
    }
}
