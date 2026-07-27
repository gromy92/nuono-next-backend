package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingWorkflowRecoveryProjectorTest {

    private final ProductListingWorkflowProjector projector = new ProductListingWorkflowProjector();

    @Test
    void verifiedNoonWriteWithProjectionFailureOnlyAllowsProjectionReplay() {
        ProductListingTaskView realRun = task(
                "projection_backfill_failed",
                ProductListingNoonWriteResult.succeeded(List.of())
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.VERIFIED, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.REPLAY_PROJECTION, view.getNextAction());
        assertEquals("PROJECTION_BACKFILL_FAILED", view.getReasonCode());
    }

    @Test
    void unknownCreateOutcomeWithoutReferencesOnlyAllowsReadOnlyResultCheck() {
        ProductListingTaskView realRun = task(
                "noon_create_outcome_unknown",
                ProductListingNoonWriteResult.failed(
                        "noon_uncertain_write",
                        "noon_create_outcome_unknown",
                        "unknown",
                        List.of()
                )
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.UNKNOWN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT, view.getNextAction());
        assertEquals("NOON_CREATE_OUTCOME_UNKNOWN", view.getReasonCode());
    }

    @Test
    void persistedCreateReferencesAllowOnlyContinuationAfterCreate() {
        ProductListingNoonWriteStepResult recoveredReference = step(
                "resolve_create_reference",
                "succeeded",
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1",
                null
        );
        ProductListingTaskView realRun = task(
                "noon_create_outcome_unknown",
                ProductListingNoonWriteResult.failed(
                        "noon_uncertain_write",
                        "noon_create_outcome_unknown",
                        "unknown",
                        List.of(recoveredReference)
                )
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.WRITTEN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE, view.getNextAction());
        assertEquals("CREATE_REFERENCE_RECOVERED", view.getReasonCode());
    }

    @Test
    void readbackFailureAfterCompletedWritesOnlyAllowsReadbackVerification() {
        ProductListingNoonWriteStepResult created = step(
                "create_product",
                "succeeded",
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1",
                null
        );
        ProductListingNoonWriteStepResult readback = step(
                "verify_noon_readback",
                "failed",
                null,
                "readback_mismatch"
        );
        ProductListingTaskView realRun = task(
                "readback_mismatch",
                ProductListingNoonWriteResult.failed(
                        "noon_readback",
                        "readback_mismatch",
                        "mismatch",
                        List.of(created, readback)
                )
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.WRITTEN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.VERIFY_READBACK, view.getNextAction());
        assertEquals("READBACK_MISMATCH", view.getReasonCode());
    }

    @Test
    void failedPostCreateWriteOnlyAllowsContinuation() {
        ProductListingNoonWriteStepResult created = step(
                "create_product",
                "succeeded",
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1",
                null
        );
        ProductListingNoonWriteStepResult content = step(
                "upsert_zsku_content_en",
                "failed",
                null,
                "noon_write_failed"
        );
        ProductListingTaskView realRun = task(
                "noon_write_failed",
                ProductListingNoonWriteResult.failed(
                        "noon_api",
                        "noon_write_failed",
                        "write failed",
                        List.of(created, content)
                )
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.WRITTEN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE, view.getNextAction());
        assertEquals("POST_CREATE_WRITE_FAILED", view.getReasonCode());
    }

    @Test
    void resolvedCreateReferenceOverridesEarlierUnknownCreateFailure() {
        ProductListingNoonWriteStepResult createUnknown = step(
                "create_product",
                "failed",
                null,
                "noon_create_outcome_unknown"
        );
        ProductListingNoonWriteStepResult resolved = step(
                "resolve_create_reference",
                "succeeded",
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1",
                null
        );
        ProductListingNoonWriteStepResult upload = step(
                "upload_images",
                "succeeded",
                "uploadedImages=1",
                null
        );
        ProductListingNoonWriteStepResult readBack = step(
                "verify_noon_readback",
                "failed",
                "readBackAttempts=13",
                "readback_mismatch"
        );
        ProductListingTaskView realRun = task(
                "readback_mismatch",
                ProductListingNoonWriteResult.failed(
                        "noon_readback",
                        "readback_mismatch",
                        "mismatch",
                        List.of(createUnknown, resolved, upload, readBack)
                )
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.WriteCertainty.WRITTEN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.VERIFY_READBACK, view.getNextAction());
    }

    @Test
    void terminalFailedTaskWithUnknownCreateEvidenceRemainsUnknown() {
        ProductListingTaskView realRun = terminalTask(
                "failed",
                "noon_create_outcome_unknown",
                ProductListingNoonWriteResult.failed(
                        "noon_uncertain_write",
                        "noon_create_outcome_unknown",
                        "unknown",
                        List.of()
                )
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.UNKNOWN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.NONE, view.getNextAction());
    }

    @Test
    void terminalFailedTaskWithPersistedCreateReferencesRemainsWritten() {
        ProductListingNoonWriteStepResult created = step(
                "create_product",
                "succeeded",
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1",
                null
        );
        ProductListingTaskView realRun = terminalTask(
                "failed",
                "legacy_failure",
                ProductListingNoonWriteResult.failed(
                        "legacy",
                        "legacy_failure",
                        "legacy failure",
                        List.of(created)
                )
        );

        ProductListingWorkflowView view = projector.project(draft(), dryRun(), realRun);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.WRITTEN, view.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.NONE, view.getNextAction());
    }

    private ProductListingDraftView draft() {
        ProductListingDraftView draft = new ProductListingDraftView();
        draft.setDraftId(10001L);
        draft.setStoreCode("STR245027-NAE");
        draft.setStatus("ready_for_dry_run");
        return draft;
    }

    private ProductListingTaskView dryRun() {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(20001L);
        task.setDraftId(10001L);
        task.setMode("DRY_RUN");
        task.setStatus("validated");
        return task;
    }

    private ProductListingTaskView task(String failureCode, ProductListingNoonWriteResult noonResult) {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(20002L);
        task.setDraftId(10001L);
        task.setMode("REAL_RUN");
        task.setStatus("written_verify_failed");
        task.setFailureCode(failureCode);
        task.setNoonResult(noonResult);
        return task;
    }

    private ProductListingTaskView terminalTask(
            String status,
            String failureCode,
            ProductListingNoonWriteResult noonResult
    ) {
        ProductListingTaskView task = task(failureCode, noonResult);
        task.setStatus(status);
        return task;
    }

    private ProductListingNoonWriteStepResult step(
            String stepKey,
            String status,
            String externalReference,
            String failureCode
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey(stepKey);
        step.setStatus(status);
        step.setExternalReference(externalReference);
        step.setFailureCode(failureCode);
        return step;
    }
}
