package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonProductRefreshServiceTest {

    private InMemoryNoonPullRepository repository;
    private NoonProductRefreshService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T08:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundationService =
                new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        service = new NoonProductRefreshService(
                foundationService,
                new NoonInterfacePuller(foundationService),
                new NoonProductDetailBackfillPlanner()
        );
    }

    @Test
    void shouldRequireConfirmationWhenLocalDraftCouldBeAffected() {
        NoonProductRefreshResult result = service.refresh(NoonProductRefreshCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .skuParent("ZPARENT-1")
                .reason(NoonProductRefreshReason.MANUAL_SYNC_FROM_NOON)
                .hasLocalDraft(true)
                .build(), successProvider());

        assertEquals("CONFIRMATION_REQUIRED", result.getState());
        assertEquals(0, repository.listTasks().size());
    }

    @Test
    void shouldCreateManualRefreshInterfaceTaskWhenUserKeepsLocalDraft() {
        NoonProductRefreshResult result = service.refresh(NoonProductRefreshCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .skuParent("ZPARENT-1")
                .reason(NoonProductRefreshReason.MANUAL_SYNC_FROM_NOON)
                .hasLocalDraft(true)
                .mergePolicy(NoonProductRefreshMergePolicy.KEEP_LOCAL_DRAFT)
                .build(), successProvider());
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertEquals("SUCCEEDED", result.getState());
        assertEquals(NoonPullTriggerMode.MANUAL_REFRESH, task.getTriggerMode());
        assertEquals("detail:ZPARENT-1", task.getTargetIdentity());
        assertNotNull(task.getSourceBatchId());
        assertTrue(result.isPreserveDrafts());
        assertFalse(result.isPublishFlowTriggered());
    }

    @Test
    void shouldAllowExplicitUseNoonOverwriteAfterConfirmation() {
        NoonProductRefreshResult result = service.refresh(NoonProductRefreshCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .skuParent("ZPARENT-1")
                .reason(NoonProductRefreshReason.MANUAL_SYNC_FROM_NOON)
                .hasLocalDraft(true)
                .mergePolicy(NoonProductRefreshMergePolicy.USE_NOON_OVERWRITE)
                .build(), successProvider());

        assertEquals("SUCCEEDED", result.getState());
        assertFalse(result.isPreserveDrafts());
        assertFalse(result.isPublishFlowTriggered());
        assertEquals(1, repository.listTasks().size());
    }

    @Test
    void shouldUseSharedFailureVocabularyForMissingBaselineRecovery() {
        NoonProductRefreshResult result = service.refresh(NoonProductRefreshCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .skuParent("ZPARENT-1")
                .reason(NoonProductRefreshReason.MISSING_BASELINE_RECOVERY)
                .build(), failingProvider("401 auth required"));
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertEquals("FAILED", result.getState());
        assertEquals(NoonPullTriggerMode.GAP_BACKFILL, task.getTriggerMode());
        assertEquals("auth_required", task.getFailureType());
        assertEquals("MANUAL_ACTION", task.getRetryAction());
    }

    @Test
    void shouldRunBoundedPublishReadbackDetailRefreshWithoutFullProductSync() {
        NoonProductRefreshResult result = service.refresh(NoonProductRefreshCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .skuParent("ZPARENT-1")
                .reason(NoonProductRefreshReason.PUBLISH_READBACK_DETAIL_REFRESH)
                .allSkuParents(List.of("ZPARENT-1", "ZPARENT-2", "ZPARENT-3"))
                .build(), successProvider());
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertEquals("SUCCEEDED", result.getState());
        assertEquals(NoonPullTriggerMode.READBACK_CHECK, task.getTriggerMode());
        assertEquals(List.of("ZPARENT-1"), result.getRequestedDetailSkuParents());
        assertFalse(result.isBlindFullStoreDetailFetch());
        assertFalse(result.isPublishFlowTriggered());
    }

    private NoonInterfacePullProvider successProvider() {
        return (request, pageNumber) -> NoonInterfacePullPage.builder()
                .items(List.of(Map.of("sku_parent", "ZPARENT-1")))
                .pageNumber(pageNumber)
                .totalItems(1)
                .hasNextPage(false)
                .requestCount(1)
                .build();
    }

    private NoonInterfacePullProvider failingProvider(String message) {
        return (request, pageNumber) -> {
            throw new NoonInterfacePullException(message);
        };
    }
}
