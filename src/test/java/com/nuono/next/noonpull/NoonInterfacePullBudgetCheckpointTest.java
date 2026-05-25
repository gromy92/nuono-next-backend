package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonInterfacePullBudgetCheckpointTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonInterfacePuller puller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T07:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        puller = new NoonInterfacePuller(foundationService);
    }

    @Test
    void shouldPersistCheckpointAndPartialStateWhenBudgetIsExhausted() {
        NoonPullTaskRecord task = createProductTask();
        RecordingProvider provider = new RecordingProvider(5);

        NoonInterfacePullResult result = puller.execute(
                task.getId(),
                productListRequest()
                        .budget(NoonPullRequestBudget.builder()
                                .maxPagesPerRun(2)
                                .maxRequestsPerRun(2)
                                .maxProductsPerRun(10)
                                .build())
                        .build(),
                provider
        );
        NoonPullTaskRecord persistedTask = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.PARTIAL, result.getStatus());
        assertEquals(NoonPullTaskStatus.PARTIAL, persistedTask.getStatus());
        assertEquals("page:2", persistedTask.getCheckpointCursor());
        assertEquals(2, persistedTask.getProcessedItemCount());
        assertEquals(2, persistedTask.getRequestCount());
        assertEquals("page:3", persistedTask.getNextResumePosition());
        assertEquals("large_store_backfill_in_progress", persistedTask.getReadinessState());
        assertTrue(persistedTask.getLastSafeResponseSummary().contains("pages=2"));
        assertEquals(List.of(1, 2), provider.requestedPages);
    }

    @Test
    void shouldResumeFromCheckpointAfterRestart() {
        NoonPullTaskRecord task = createProductTask();
        puller.execute(
                task.getId(),
                productListRequest()
                        .budget(NoonPullRequestBudget.builder().maxPagesPerRun(2).maxRequestsPerRun(2).build())
                        .build(),
                new RecordingProvider(3)
        );

        RecordingProvider resumeProvider = new RecordingProvider(3);
        NoonInterfacePullResult resumed = puller.execute(
                task.getId(),
                productListRequest()
                        .resumeFromTask(repository.selectTask(task.getId()))
                        .budget(NoonPullRequestBudget.builder().maxPagesPerRun(10).maxRequestsPerRun(10).build())
                        .build(),
                resumeProvider
        );
        NoonPullTaskRecord persistedTask = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.SUCCEEDED, resumed.getStatus());
        assertEquals("page:3", persistedTask.getCheckpointCursor());
        assertEquals(3, persistedTask.getProcessedItemCount());
        assertEquals(3, persistedTask.getRequestCount());
        assertFalse(persistedTask.getNextResumePosition() != null && persistedTask.getNextResumePosition().contains("page:4"));
        assertEquals(List.of(3), resumeProvider.requestedPages);
    }

    private NoonPullTaskRecord createProductTask() {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .scheduleExpression("manual")
                .maxPagesPerRun(2)
                .maxProductsPerRun(10)
                .maxDetailFetchesPerRun(3)
                .maxRequestsPerRun(2)
                .cooldownSeconds(900)
                .concurrencyLimit(1)
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .targetIdentity("catalog:list")
                .build()).orElseThrow();
    }

    private NoonInterfacePullRequest.Builder productListRequest() {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName("product-list")
                .targetIdentity("catalog:list")
                .timeoutSeconds(30);
    }

    private static final class RecordingProvider implements NoonInterfacePullProvider {
        private final int totalPages;
        private final List<Integer> requestedPages = new ArrayList<>();

        private RecordingProvider(int totalPages) {
            this.totalPages = totalPages;
        }

        @Override
        public NoonInterfacePullPage fetchPage(NoonInterfacePullRequest request, int pageNumber) {
            requestedPages.add(pageNumber);
            return NoonInterfacePullPage.builder()
                    .items(List.of(Map.of("sku_parent", "ZP-" + pageNumber)))
                    .pageNumber(pageNumber)
                    .totalItems(totalPages)
                    .hasNextPage(pageNumber < totalPages)
                    .requestCount(1)
                    .build();
        }
    }
}
