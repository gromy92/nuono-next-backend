package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductListingWorkflowProjectorRecoveryTest {

    private final ProductListingWorkflowProjector projector = new ProductListingWorkflowProjector();

    @ParameterizedTest
    @MethodSource("terminalAttemptPolicies")
    void failedOrRejectedAttemptRequiresOneControlledRecoveryAction(
            String status,
            String failureCode,
            String failureMessage,
            ProductListingWorkflowView.WriteCertainty certainty,
            ProductListingWorkflowView.NextAction nextAction
    ) {
        ProductListingTaskView realRun = task(20002L, "REAL_RUN", status, failureCode, null);
        realRun.setFailureMessage(failureMessage);

        ProductListingWorkflowView view = projector.project(
                draft("ready_for_dry_run"),
                task(20001L, "DRY_RUN", "validated", null, null),
                realRun
        );

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(certainty, view.getWriteCertainty());
        assertEquals(nextAction, view.getNextAction());
        assertEquals(failureCode.toUpperCase(), view.getReasonCode());
    }

    private static Stream<Arguments> terminalAttemptPolicies() {
        return Stream.of(
                Arguments.of(
                        "failed",
                        "noon_write_exception",
                        "gateway failed",
                        ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                        ProductListingWorkflowView.NextAction.NONE
                ),
                Arguments.of(
                        "failed",
                        "noon_write_exception",
                        "unauthorized after create response was lost",
                        ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                        ProductListingWorkflowView.NextAction.NONE
                ),
                Arguments.of(
                        "rejected",
                        "dry_run_not_validated",
                        "invalid dry run",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.EDIT_DRAFT
                ),
                Arguments.of(
                        "rejected",
                        "real_write_disabled",
                        "kill switch disabled the write before it started",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "rejected",
                        "dry_run_stale",
                        "the saved draft changed after validation",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "rejected",
                        "confirmation_required",
                        "the operator did not confirm the write",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "failed",
                        "noon_auth_required",
                        "cookie expired with HTTP 307",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.WAIT_FOR_AUTHORIZATION
                ),
                Arguments.of(
                        "failed",
                        "noon_pre_create_failed",
                        "taxonomy lookup failed before create",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "failed",
                        "noon_create_rejected",
                        "create payload rejected without creating a product",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "failed",
                        "noon_warehouse_stock_not_supported",
                        "unsupported fields rejected before create",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                ),
                Arguments.of(
                        "failed",
                        "partner_sku_already_exists",
                        "Noon rejected the duplicate before creating a product",
                        ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                        ProductListingWorkflowView.NextAction.REVIEW_DRAFT
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
