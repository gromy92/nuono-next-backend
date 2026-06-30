package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleStateServiceTest {

    @Test
    void returnsCurrentStateHistoryAndJobsForStoreSiteSkuScope() {
        RecordingProductLifecycleStateRepository repository = new RecordingProductLifecycleStateRepository();
        repository.currentState = sampleCurrentState();
        repository.history = List.of(sampleHistory());
        repository.jobs = List.of(sampleJob());
        ProductLifecycleStateService service = new ProductLifecycleStateService(repository);

        ProductLifecycleStateOverview overview = service.getOverview(query());

        assertEquals(query(), repository.lastCurrentQuery);
        assertEquals(query(), repository.lastHistoryQuery);
        assertEquals(10002L, repository.lastJobQuery.getOwnerUserId());
        assertEquals("STR245027-SAU", repository.lastJobQuery.getStoreCode());
        assertEquals("SA", repository.lastJobQuery.getSiteCode());
        assertEquals("growth", overview.getCurrentState().getLifecycleCode());
        assertEquals("成长期", overview.getCurrentState().getLifecycleLabel());
        assertEquals("DEFAULT_V1", overview.getCurrentState().getRuleVersion());
        assertEquals(LocalDate.of(2026, 5, 20), overview.getCurrentState().getAnalysisDate());
        assertEquals("official", overview.getCurrentState().getListingDateSource());
        assertEquals("ready", overview.getCurrentState().getQualityState());
        assertEquals(1, overview.getHistory().size());
        assertEquals("new", overview.getHistory().get(0).getPreviousLifecycleCode());
        assertEquals(1, overview.getJobs().size());
        assertEquals("succeeded", overview.getJobs().get(0).getStatus());
    }

    @Test
    void returnsEmptyOverviewWhenCurrentStateHasNotBeenCalculated() {
        RecordingProductLifecycleStateRepository repository = new RecordingProductLifecycleStateRepository();
        ProductLifecycleStateService service = new ProductLifecycleStateService(repository);

        ProductLifecycleStateOverview overview = service.getOverview(query());

        assertNull(overview.getCurrentState());
        assertEquals(List.of(), overview.getHistory());
        assertEquals(List.of(), overview.getJobs());
    }

    @Test
    void productLifecycleStateQueryIdentityIgnoresExternalSku() {
        ProductLifecycleStateQuery oldExternalSku = new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z-OLD-1"
        );
        ProductLifecycleStateQuery newExternalSku = new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z-NEW-1"
        );
        ProductLifecycleStateQuery differentPsku = new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-002",
                "Z-NEW-1"
        );

        assertEquals(oldExternalSku, newExternalSku);
        assertEquals(oldExternalSku.hashCode(), newExternalSku.hashCode());
        org.junit.jupiter.api.Assertions.assertNotEquals(oldExternalSku, differentPsku);
    }

    @Test
    void upsertsCurrentStateAndRecordsTransitionHistoryThroughRepository() {
        RecordingProductLifecycleStateRepository repository = new RecordingProductLifecycleStateRepository();
        ProductLifecycleStateService service = new ProductLifecycleStateService(repository);

        service.saveCurrentState(sampleCurrentState());
        service.recordHistory(sampleHistory());
        service.recordJob(sampleJob());

        assertEquals("growth", repository.savedCurrentStates.get(0).getLifecycleCode());
        assertEquals("new", repository.savedHistory.get(0).getPreviousLifecycleCode());
        assertEquals("succeeded", repository.savedJobs.get(0).getStatus());
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

    private ProductLifecycleCurrentState sampleCurrentState() {
        return new ProductLifecycleCurrentState(
                70001L,
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "growth",
                "成长期",
                "DEFAULT_V1",
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 3, 20),
                "official",
                "ready",
                "订正后近 15 天销量和 PV 均增长。",
                "{\"salesGrowth15\":0.42}",
                72001L,
                LocalDateTime.of(2026, 5, 20, 3, 0)
        );
    }

    private ProductLifecycleHistoryRecord sampleHistory() {
        return new ProductLifecycleHistoryRecord(
                71001L,
                70001L,
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "new",
                "新品期",
                "growth",
                "成长期",
                "DEFAULT_V1",
                LocalDate.of(2026, 5, 20),
                "growth_path_sales_and_pv",
                "{\"salesGrowth15\":0.42}",
                LocalDateTime.of(2026, 5, 20, 3, 1)
        );
    }

    private ProductLifecycleJobRecord sampleJob() {
        return new ProductLifecycleJobRecord(
                72001L,
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 20),
                "DEFAULT_V1",
                "succeeded",
                42,
                7,
                3,
                2,
                null,
                LocalDateTime.of(2026, 5, 20, 3, 0),
                LocalDateTime.of(2026, 5, 20, 3, 5)
        );
    }

    private static class RecordingProductLifecycleStateRepository implements ProductLifecycleStateRepository {
        private ProductLifecycleStateQuery lastCurrentQuery;
        private ProductLifecycleStateQuery lastHistoryQuery;
        private ProductLifecycleJobQuery lastJobQuery;
        private ProductLifecycleCurrentState currentState;
        private List<ProductLifecycleHistoryRecord> history = List.of();
        private List<ProductLifecycleJobRecord> jobs = List.of();
        private final List<ProductLifecycleCurrentState> savedCurrentStates = new ArrayList<>();
        private final List<ProductLifecycleHistoryRecord> savedHistory = new ArrayList<>();
        private final List<ProductLifecycleJobRecord> savedJobs = new ArrayList<>();

        @Override
        public void saveCurrentState(ProductLifecycleCurrentState state) {
            savedCurrentStates.add(state);
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
            this.lastCurrentQuery = query;
            return currentState;
        }

        @Override
        public List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query) {
            this.lastHistoryQuery = query;
            return history;
        }

        @Override
        public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
            this.lastJobQuery = query;
            return jobs;
        }
    }
}
