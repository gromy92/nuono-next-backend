package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonProductSyncBridgeServiceTest {

    @Test
    void productSyncDoesNotExposeDefaultDailyPlan() {
        NoonProductSyncBridgeService service = new NoonProductSyncBridgeService(new NoonSyncFoundationService());

        List<NoonSyncTriggerMode> triggerModes = service.productPlanCatalog()
                .stream()
                .map(NoonSyncPlanDefinition::getTriggerMode)
                .collect(Collectors.toList());

        assertFalse(triggerModes.contains(NoonSyncTriggerMode.SCHEDULED_DAILY));
        assertTrue(service.productPlanCatalog()
                .stream()
                .map(NoonSyncPlanDefinition::getKey)
                .collect(Collectors.toList())
                .containsAll(List.of("product_initialization", "product_explicit_refresh")));
    }

    @Test
    void workspaceEmptyMapsToInitializationNeededWithSharedSyncVocabulary() {
        NoonProductSyncBridgeService service = new NoonProductSyncBridgeService(new NoonSyncFoundationService());

        NoonProductSyncReadModel readModel = service.describeProductSurface(new NoonProductSyncBridgeInput(
                NoonProductWorkspaceState.WORKSPACE_EMPTY,
                null,
                null,
                false
        ));

        assertEquals(NoonProductSyncSurfaceState.INITIALIZATION_NEEDED, readModel.getSurfaceState());
        assertEquals("product_initialization", readModel.getRequiredWork().get(0).getPlanKey());
        assertEquals(NoonSyncDataDomain.PRODUCT, readModel.getRequiredWork().get(0).getDataDomain());
        assertEquals(NoonSyncTriggerMode.ONBOARDING, readModel.getRequiredWork().get(0).getTriggerMode());
    }

    @Test
    void runningAndFailedProductTasksSurfaceTypedReadModelStates() {
        NoonSyncFoundationService foundationService = new NoonSyncFoundationService();
        NoonProductSyncBridgeService service = new NoonProductSyncBridgeService(foundationService);
        NoonSyncTask running = foundationService.markRunning(foundationService.createTask(productTask("STR245027-SAU")));
        NoonSyncTask failed = foundationService.markFailed(
                foundationService.createTask(productTask("STR245027-NAE")).getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe("product-trace", "Noon unavailable; cookie=secret")
        );

        NoonProductSyncReadModel runningModel = service.describeProductSurface(new NoonProductSyncBridgeInput(
                NoonProductWorkspaceState.WORKSPACE_EMPTY,
                running,
                null,
                false
        ));
        NoonProductSyncReadModel failedModel = service.describeProductSurface(new NoonProductSyncBridgeInput(
                NoonProductWorkspaceState.WORKSPACE_EMPTY,
                failed,
                null,
                false
        ));

        assertEquals(NoonProductSyncSurfaceState.INITIALIZATION_RUNNING, runningModel.getSurfaceState());
        assertEquals(NoonSyncTaskStatus.RUNNING, runningModel.getTaskStatus());
        assertEquals(NoonProductSyncSurfaceState.INITIALIZATION_FAILED, failedModel.getSurfaceState());
        assertEquals(NoonSyncFailureReason.PROVIDER_UNAVAILABLE, failedModel.getFailureReason());
        assertFalse(failedModel.getDiagnosticSummary().contains("secret"));
    }

    @Test
    void explicitRefreshRequiresDraftMergeDecisionAndNeverEnablesExternalWrite() {
        NoonProductSyncBridgeService service = new NoonProductSyncBridgeService(new NoonSyncFoundationService());

        NoonProductExplicitRefreshPolicy keepDraft = service.planExplicitRefresh(true, "keep_draft");
        NoonProductExplicitRefreshPolicy useNoon = service.planExplicitRefresh(true, "use_noon");

        assertTrue(keepDraft.isConfirmationRequired());
        assertEquals("keep_draft", keepDraft.getMergePolicy());
        assertEquals(NoonProductRefreshDraftHandling.PRESERVE_LOCAL_DRAFT, keepDraft.getDraftHandling());
        assertTrue(useNoon.isConfirmationRequired());
        assertEquals(NoonProductRefreshDraftHandling.OVERWRITE_LOCAL_DRAFT_WITH_NOON, useNoon.getDraftHandling());
        assertFalse(keepDraft.isExternalWriteAllowed());
        assertFalse(useNoon.isExternalWriteAllowed());
    }

    private NoonSyncTaskRequest productTask(String storeCode) {
        return new NoonSyncTaskRequest(
                NoonSyncDataDomain.PRODUCT,
                NoonSyncTriggerMode.ONBOARDING,
                NoonSyncScope.of(10002L, 245027L, storeCode, "SA"),
                NoonSyncTarget.identity(storeCode),
                NoonSyncRetryPolicy.RETRYABLE
        );
    }
}
