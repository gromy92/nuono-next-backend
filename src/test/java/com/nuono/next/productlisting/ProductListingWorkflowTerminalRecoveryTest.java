package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.junit.jupiter.api.Test;

class ProductListingWorkflowTerminalRecoveryTest {

    @Test
    void rejectedEditableAttemptStillRequiresExplicitReopenAfterDraftChanges() {
        ProductListingWorkflowView view = loadWorkflow(
                "{\"psku\":\"NN-CHANGED-PSKU\"}",
                "rejected",
                "partner_sku_already_exists"
        );

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                view.getWriteCertainty()
        );
        assertEquals(ProductListingWorkflowView.NextAction.EDIT_DRAFT, view.getNextAction());
        assertNotNull(view.getRealRunTask());
        assertEquals(20002L, view.getRealRunTask().getTaskId());
    }

    @Test
    void rejectedEditableAttemptStillDominatesWhileCurrentDraftIsUnchanged() {
        ProductListingWorkflowView view = loadWorkflow(
                "{\"psku\":\"NN-TEST-PSKU\"}",
                "rejected",
                "partner_sku_already_exists"
        );

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                view.getWriteCertainty()
        );
        assertEquals(ProductListingWorkflowView.NextAction.EDIT_DRAFT, view.getNextAction());
    }

    @Test
    void unknownWriteOutcomeStillDominatesAfterCurrentDraftChanges() {
        ProductListingWorkflowView view = loadWorkflow(
                "{\"psku\":\"NN-CHANGED-PSKU\"}",
                "failed",
                "noon_create_outcome_unknown"
        );

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(
                ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                view.getWriteCertainty()
        );
        assertEquals(ProductListingWorkflowView.NextAction.NONE, view.getNextAction());
    }

    private ProductListingWorkflowView loadWorkflow(
            String currentDraftJson,
            String realRunStatus,
            String failureCode
    ) {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductListingWorkflowService workflowService = new ProductListingWorkflowService(
                mapper,
                listingService,
                new ProductListingWorkflowProjector(),
                new ProductListingDryRunFreshness(new ObjectMapper())
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingDraftView draft = draftView();
        ProductListingDraftRecord draftRecord = draftRecord(currentDraftJson);
        ProductListingTaskRecord dryRun =
                taskRecord(20001L, "DRY_RUN", "validated", null, null);
        ProductListingTaskRecord realRun =
                taskRecord(20002L, "REAL_RUN", realRunStatus, 20001L, failureCode);
        when(listingService.loadDraft(context, 10001L)).thenReturn(draft);
        when(mapper.selectDraftById(10001L, 10002L)).thenReturn(draftRecord);
        when(mapper.selectLatestDryRunTaskByDraftId(10002L, 10001L)).thenReturn(dryRun);
        when(mapper.selectRealWriteAttemptTaskBySourceTaskId(10002L, 20001L))
                .thenReturn(realRun);
        when(listingService.loadTask(context, 20001L)).thenReturn(taskView(dryRun));
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView(realRun));
        return workflowService.loadWorkflow(context, 10001L);
    }

    private ProductListingDraftView draftView() {
        ProductListingDraftView view = new ProductListingDraftView();
        view.setDraftId(10001L);
        view.setOwnerUserId(10002L);
        view.setStoreCode("STR245027-NAE");
        view.setStatus("ready_for_dry_run");
        return view;
    }

    private ProductListingDraftRecord draftRecord(String json) {
        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setStatus("ready_for_dry_run");
        record.setDraftJson(json);
        return record;
    }

    private ProductListingTaskRecord taskRecord(
            Long id,
            String mode,
            String status,
            Long sourceTaskId,
            String failureCode
    ) {
        ProductListingTaskRecord record = new ProductListingTaskRecord();
        record.setId(id);
        record.setDraftId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setMode(mode);
        record.setStatus(status);
        record.setSourceTaskId(sourceTaskId);
        record.setFailureCode(failureCode);
        record.setInputSnapshotJson("{\"psku\":\"NN-TEST-PSKU\"}");
        return record;
    }

    private ProductListingTaskView taskView(ProductListingTaskRecord record) {
        ProductListingTaskView view = new ProductListingTaskView();
        view.setTaskId(record.getId());
        view.setDraftId(record.getDraftId());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setMode(record.getMode());
        view.setStatus(record.getStatus());
        view.setSourceTaskId(record.getSourceTaskId());
        view.setFailureCode(record.getFailureCode());
        return view;
    }
}
