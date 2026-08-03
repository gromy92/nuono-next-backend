package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductListingWorkflowProjectorTest {

    private final ProductListingWorkflowProjector projector = new ProductListingWorkflowProjector();

    @Test
    void validatedDryRunIsReadyForOneExplicitConfirmation() {
        ProductListingDraftView draft = draft("ready_for_dry_run");
        ProductListingTaskView dryRun = task(20001L, "DRY_RUN", "validated", null, null);

        ProductListingWorkflowView view = projector.project(draft, dryRun, null);

        assertEquals(ProductListingWorkflowView.Phase.READY_TO_CONFIRM, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.NOT_STARTED, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.CONFIRM_PUBLISH, view.getNextAction());
        assertEquals("DRY_RUN_VALIDATED", view.getReasonCode());
        assertEquals(draft, view.getDraft());
        assertEquals(dryRun, view.getDryRunTask());
    }

    @ParameterizedTest
    @MethodSource("publishingStatuses")
    void submittedOrRunningRealRunIsPublishingAndOnlyAllowsWaiting(
            String status,
            ProductListingWorkflowView.WriteCertainty certainty
    ) {
        ProductListingTaskView realRun = task(20002L, "REAL_RUN", status, null, null);

        ProductListingWorkflowView view = projector.project(
                draft("ready_for_dry_run"),
                task(20001L, "DRY_RUN", "validated", null, null),
                realRun
        );

        assertEquals(ProductListingWorkflowView.Phase.PUBLISHING, view.getPhase());
        assertEquals(certainty, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.WAIT, view.getNextAction());
        assertEquals("REAL_RUN_" + status.toUpperCase(), view.getReasonCode());
    }

    private static Stream<Arguments> publishingStatuses() {
        return Stream.of(
                Arguments.of("submitted", ProductListingWorkflowView.WriteCertainty.NOT_STARTED),
                Arguments.of("running", ProductListingWorkflowView.WriteCertainty.UNKNOWN)
        );
    }

    @Test
    void succeededRealRunIsPublishedAndHasNoFurtherAction() {
        ProductListingTaskView realRun = task(20002L, "REAL_RUN", "succeeded", null, null);

        ProductListingWorkflowView view = projector.project(
                draft("ready_for_dry_run"),
                task(20001L, "DRY_RUN", "validated", null, null),
                realRun
        );

        assertEquals(ProductListingWorkflowView.Phase.PUBLISHED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.VERIFIED, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.NONE, view.getNextAction());
        assertEquals("REAL_RUN_SUCCEEDED", view.getReasonCode());
    }

    @Test
    void unknownRealRunCombinationFailsClosedWithoutReopeningConfirmation() {
        ProductListingTaskView realRun = task(
                20002L,
                "REAL_RUN",
                "future_status",
                "future_failure",
                null
        );

        ProductListingWorkflowView view = projector.project(
                draft("ready_for_dry_run"),
                task(20001L, "DRY_RUN", "validated", null, null),
                realRun
        );

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.UNKNOWN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.NONE, view.getNextAction());
        assertEquals("UNMAPPED_REAL_RUN_STATE", view.getReasonCode());
    }

    @Test
    void unknownCreateLookupAuthenticationFailureWaitsForSharedAuthorization() {
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_create_outcome_unknown");
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "noon_uncertain_write",
                        "noon_create_outcome_unknown",
                        "unknown",
                        java.util.List.of(create)
                );
        ProductListingTaskView realRun = task(
                20002L,
                "REAL_RUN",
                "written_verify_failed",
                "noon_auth_required",
                result
        );

        ProductListingWorkflowView view = projector.project(
                draft("ready_for_dry_run"),
                task(20001L, "DRY_RUN", "validated", null, null),
                realRun
        );

        assertEquals(
                ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                view.getWriteCertainty()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.WAIT_FOR_AUTHORIZATION,
                view.getNextAction()
        );
    }

    @Test
    void preCreateLookupReferencesDoNotProveThatThisTaskCreatedTheProduct() {
        ProductListingNoonWriteStepResult preflight = new ProductListingNoonWriteStepResult();
        preflight.setStepKey("pre_create_absence_verified");
        preflight.setStatus("failed");
        preflight.setFailureCode("partner_sku_already_exists");
        preflight.setExternalReference(
                "skuParent=Z-EXISTING-PRODUCT;pskuCode=EXISTING-NOON-PSKU"
        );
        preflight.setWriteMayHaveOccurred(false);
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "validation",
                "partner_sku_already_exists",
                "Noon already contains this PSKU.",
                java.util.List.of(preflight)
        );
        ProductListingTaskView realRun = task(
                20002L,
                "REAL_RUN",
                "failed",
                "partner_sku_already_exists",
                result
        );

        ProductListingWorkflowView view = projector.project(
                draft("ready_for_dry_run"),
                task(20001L, "DRY_RUN", "validated", null, null),
                realRun
        );

        assertEquals(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                view.getWriteCertainty()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.REVIEW_DRAFT,
                view.getNextAction()
        );
    }

    @Test
    void correctedValidationFailedDraftCanRunAReplacementDryRun() {
        ProductListingWorkflowView view = projector.project(
                draft("ready_for_dry_run"),
                task(20001L, "DRY_RUN", "validation_failed", "validation_failed", null),
                null
        );

        assertEquals(ProductListingWorkflowView.Phase.EDITING, view.getPhase());
        assertEquals(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                view.getWriteCertainty()
        );
        assertEquals(ProductListingWorkflowView.NextAction.REVIEW_DRAFT, view.getNextAction());
        assertEquals("DRAFT_READY_AFTER_VALIDATION_FIX", view.getReasonCode());
        assertEquals("草稿已修正，请重新执行上架检查。", view.getMessage());
    }

    @ParameterizedTest
    @MethodSource("editableDraftPolicies")
    void draftWithoutCurrentValidatedDryRunStaysInEditing(
            String draftStatus,
            String dryRunStatus,
            ProductListingWorkflowView.NextAction nextAction
    ) {
        ProductListingTaskView dryRun = dryRunStatus == null
                ? null
                : task(20001L, "DRY_RUN", dryRunStatus, null, null);

        ProductListingWorkflowView view = projector.project(draft(draftStatus), dryRun, null);

        assertEquals(ProductListingWorkflowView.Phase.EDITING, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.NOT_STARTED, view.getWriteCertainty());
        assertEquals(nextAction, view.getNextAction());
    }

    private static Stream<Arguments> editableDraftPolicies() {
        return Stream.of(
                Arguments.of(
                        "ready_for_dry_run",
                        null,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "ready_for_dry_run",
                        "superseded",
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "ready_for_dry_run",
                        "validation_failed",
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "draft",
                        "validation_failed",
                        ProductListingWorkflowView.NextAction.EDIT_DRAFT
                ),
                Arguments.of(
                        "draft",
                        null,
                        ProductListingWorkflowView.NextAction.EDIT_DRAFT
                )
        );
    }

    private ProductListingDraftView draft(String status) {
        ProductListingDraftView draft = new ProductListingDraftView();
        draft.setDraftId(10001L);
        draft.setStoreCode("STR245027-NAE");
        draft.setStatus(status);
        return draft;
    }

    private ProductListingTaskView task(
            Long taskId,
            String mode,
            String status,
            String failureCode,
            ProductListingNoonWriteResult noonResult
    ) {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(taskId);
        task.setDraftId(10001L);
        task.setMode(mode);
        task.setStatus(status);
        task.setFailureCode(failureCode);
        task.setNoonResult(noonResult);
        return task;
    }
}
