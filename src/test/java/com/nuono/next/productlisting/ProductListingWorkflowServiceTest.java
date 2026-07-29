package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ProductListingWorkflowServiceTest {

    @Test
    void unresolvedRealRunRestoresItsSourceDryRunInsteadOfAnewerDryRun() {
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
        ProductListingTaskRecord sourceDryRun = taskRecord(20001L, "DRY_RUN", "validated", null);
        ProductListingTaskRecord unresolvedRealRun = taskRecord(20002L, "REAL_RUN", "running", 20001L);
        when(listingService.loadDraft(context, 10001L)).thenReturn(draft);
        when(mapper.selectDraftById(10001L, 10002L)).thenReturn(draftRecord());
        when(mapper.selectCurrentRealRunTaskByDraftId(10002L, 10001L)).thenReturn(unresolvedRealRun);
        when(mapper.selectTaskById(20001L, 10002L)).thenReturn(sourceDryRun);
        when(listingService.loadTask(context, 20001L)).thenReturn(taskView(sourceDryRun));
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView(unresolvedRealRun));

        ProductListingWorkflowView view = workflowService.loadWorkflow(context, 10001L);

        assertEquals(ProductListingWorkflowView.Phase.PUBLISHING, view.getPhase());
        assertEquals(ProductListingWorkflowView.NextAction.WAIT, view.getNextAction());
        assertEquals(20001L, view.getDryRunTask().getTaskId());
        assertEquals(20002L, view.getRealRunTask().getTaskId());
    }

    @Test
    void changedDraftMakesPersistedValidatedDryRunStaleInWorkflowRead() {
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
        ProductListingDraftRecord currentDraft = draftRecord();
        currentDraft.setDraftJson("{\"psku\":\"NN-CHANGED-PSKU\"}");
        ProductListingTaskRecord oldDryRun = taskRecord(20001L, "DRY_RUN", "validated", null);
        when(listingService.loadDraft(context, 10001L)).thenReturn(draftView());
        when(mapper.selectDraftById(10001L, 10002L)).thenReturn(currentDraft);
        when(mapper.selectCurrentRealRunTaskByDraftId(10002L, 10001L)).thenReturn(null);
        when(mapper.selectLatestDryRunTaskByDraftId(10002L, 10001L)).thenReturn(oldDryRun);
        when(listingService.loadTask(context, 20001L)).thenReturn(taskView(oldDryRun));

        ProductListingWorkflowView view = workflowService.loadWorkflow(context, 10001L);

        assertEquals(ProductListingWorkflowView.Phase.EDITING, view.getPhase());
        assertEquals(ProductListingWorkflowView.NextAction.REVIEW_DRAFT, view.getNextAction());
        assertEquals("superseded", view.getDryRunTask().getStatus());
        assertEquals("draft_changed_after_validation", view.getDryRunTask().getFailureCode());
    }

    @Test
    void reopenReviewSupersedesValidatedDryRunBeforeReturningEditableWorkflow() {
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
        ProductListingTaskRecord dryRunRecord = taskRecord(20001L, "DRY_RUN", "validated", null);
        ProductListingTaskView validated = taskView(dryRunRecord);
        ProductListingTaskView superseded = taskView(dryRunRecord);
        superseded.setStatus("superseded");
        when(listingService.loadTask(context, 20001L)).thenReturn(validated, superseded);
        when(mapper.selectTaskByIdForUpdate(20001L, 10002L)).thenReturn(dryRunRecord);
        when(mapper.selectRealWriteAttemptTaskBySourceTaskId(10002L, 20001L)).thenReturn(null);
        when(mapper.markValidatedDryRunSuperseded(20001L, 10002L)).thenReturn(1);
        when(listingService.loadDraft(context, 10001L)).thenReturn(draftView());
        when(mapper.selectDraftById(10001L, 10002L)).thenReturn(draftRecord());
        when(mapper.selectCurrentRealRunTaskByDraftId(10002L, 10001L)).thenReturn(null);
        ProductListingTaskRecord supersededRecord = taskRecord(20001L, "DRY_RUN", "superseded", null);
        when(mapper.selectLatestDryRunTaskByDraftId(10002L, 10001L)).thenReturn(supersededRecord);

        ProductListingWorkflowView view = workflowService.reopenReview(context, 20001L);

        assertEquals(ProductListingWorkflowView.Phase.EDITING, view.getPhase());
        assertEquals(ProductListingWorkflowView.NextAction.REVIEW_DRAFT, view.getNextAction());
        InOrder order = inOrder(listingService, mapper);
        order.verify(listingService).loadTask(context, 20001L);
        order.verify(mapper).selectTaskByIdForUpdate(20001L, 10002L);
        order.verify(mapper).selectRealWriteAttemptTaskBySourceTaskId(10002L, 20001L);
        order.verify(mapper).markValidatedDryRunSuperseded(20001L, 10002L);
    }

    @Test
    void reopenReviewRechecksStatusFromTheLockedDryRunBeforeMarking() {
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
        ProductListingTaskRecord validatedRecord =
                taskRecord(20001L, "DRY_RUN", "validated", null);
        ProductListingTaskRecord lockedRecord =
                taskRecord(20001L, "DRY_RUN", "validation_failed", null);
        when(listingService.loadTask(context, 20001L)).thenReturn(taskView(validatedRecord));
        when(mapper.selectTaskByIdForUpdate(20001L, 10002L)).thenReturn(lockedRecord);

        assertThrows(
                IllegalArgumentException.class,
                () -> workflowService.reopenReview(context, 20001L)
        );

        verify(mapper).selectTaskByIdForUpdate(20001L, 10002L);
        verify(mapper).selectRealWriteAttemptTaskBySourceTaskId(10002L, 20001L);
        verify(mapper, never()).markValidatedDryRunSuperseded(20001L, 10002L);
    }

    @Test
    void unreopenedRejectedAttemptDominatesANewerValidatedDryRun() {
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
        ProductListingTaskRecord rejected = taskRecord(20002L, "REAL_RUN", "rejected", 20001L);
        ProductListingTaskRecord latestDryRun = taskRecord(20003L, "DRY_RUN", "validated", null);
        when(listingService.loadDraft(context, 10001L)).thenReturn(draftView());
        when(mapper.selectDraftById(10001L, 10002L)).thenReturn(draftRecord());
        when(mapper.selectCurrentRealRunTaskByDraftId(10002L, 10001L)).thenReturn(rejected);
        when(mapper.selectLatestDryRunTaskByDraftId(10002L, 10001L)).thenReturn(latestDryRun);
        when(mapper.selectRealWriteAttemptTaskBySourceTaskId(10002L, 20003L)).thenReturn(null);
        when(listingService.loadTask(context, 20003L)).thenReturn(taskView(latestDryRun));
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView(rejected));

        ProductListingWorkflowView view = workflowService.loadWorkflow(context, 10001L);

        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, view.getPhase());
        assertEquals(ProductListingWorkflowView.NextAction.NONE, view.getNextAction());
        assertEquals(20002L, view.getRealRunTask().getTaskId());
    }

    @Test
    void correctedValidationFailedDryRunCanBeReviewedAndKeepsPriorFieldIssuesAsHistory() {
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
        ProductListingTaskRecord failedDryRun =
                taskRecord(20001L, "DRY_RUN", "validation_failed", null);
        ProductListingTaskView failedView = taskView(failedDryRun);
        failedView.setValidationIssues(List.of(new ProductListingValidationIssue(
                "psku", "error", "psku_required", "PSKU is required.")));
        when(listingService.loadDraft(context, 10001L)).thenReturn(draftView());
        when(mapper.selectDraftById(10001L, 10002L)).thenReturn(draftRecord());
        when(mapper.selectCurrentRealRunTaskByDraftId(10002L, 10001L)).thenReturn(null);
        when(mapper.selectLatestDryRunTaskByDraftId(10002L, 10001L)).thenReturn(failedDryRun);
        when(mapper.selectRealWriteAttemptTaskBySourceTaskId(10002L, 20001L)).thenReturn(null);
        when(listingService.loadTask(context, 20001L)).thenReturn(failedView);

        ProductListingWorkflowView view = workflowService.loadWorkflow(context, 10001L);

        assertEquals(ProductListingWorkflowView.Phase.EDITING, view.getPhase());
        assertEquals(ProductListingWorkflowView.NextAction.REVIEW_DRAFT, view.getNextAction());
        assertEquals("DRAFT_READY_AFTER_VALIDATION_FIX", view.getReasonCode());
        assertEquals(1, view.getDryRunTask().getValidationIssues().size());
    }

    private ProductListingDraftView draftView() {
        ProductListingDraftView view = new ProductListingDraftView();
        view.setDraftId(10001L);
        view.setOwnerUserId(10002L);
        view.setStoreCode("STR245027-NAE");
        view.setStatus("ready_for_dry_run");
        return view;
    }

    private ProductListingDraftRecord draftRecord() {
        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setStatus("ready_for_dry_run");
        record.setDraftJson("{\"psku\":\"NN-TEST-PSKU\"}");
        return record;
    }

    private ProductListingTaskRecord taskRecord(Long id, String mode, String status, Long sourceTaskId) {
        ProductListingTaskRecord record = new ProductListingTaskRecord();
        record.setId(id);
        record.setDraftId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setMode(mode);
        record.setStatus(status);
        record.setSourceTaskId(sourceTaskId);
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
        return view;
    }
}
