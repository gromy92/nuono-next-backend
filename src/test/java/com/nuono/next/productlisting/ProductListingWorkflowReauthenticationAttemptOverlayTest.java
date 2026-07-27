package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.junit.jupiter.api.Test;

class ProductListingWorkflowReauthenticationAttemptOverlayTest {

    @Test
    void workflowReloadProjectsDurablePendingRecoveryAsWait() {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductListingReauthenticationAttemptMapper attemptMapper =
                mock(ProductListingReauthenticationAttemptMapper.class);
        ProductListingWorkflowService workflowService =
                new ProductListingWorkflowService(
                        mapper,
                        listingService,
                        new ProductListingWorkflowProjector(),
                        new ProductListingDryRunFreshness(new ObjectMapper()),
                        attemptMapper,
                        new ProductListingReauthenticationAttemptProjector()
                );
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L,
                        90001L,
                        "STR245027-NAE"
                );
        ProductListingDraftView draft = draftView();
        ProductListingTaskRecord dryRun = taskRecord(
                20001L,
                "DRY_RUN",
                "validated",
                null
        );
        ProductListingTaskRecord realRun = taskRecord(
                20002L,
                "REAL_RUN",
                "failed",
                20001L
        );
        realRun.setFailureCode("noon_auth_required");
        ProductListingTaskView realRunView = taskView(realRun);
        realRunView.setFailureCode("noon_auth_required");
        when(listingService.loadDraft(context, 10001L)).thenReturn(draft);
        when(mapper.selectDraftById(10001L, 10002L))
                .thenReturn(draftRecord());
        when(mapper.selectCurrentRealRunTaskByDraftId(10002L, 10001L))
                .thenReturn(realRun);
        when(mapper.selectTaskById(20001L, 10002L)).thenReturn(dryRun);
        when(listingService.loadTask(context, 20001L))
                .thenReturn(taskView(dryRun));
        when(listingService.loadTask(context, 20002L))
                .thenReturn(realRunView);
        ProductListingReauthenticationAttemptRecord attempt =
                new ProductListingReauthenticationAttemptRecord();
        attempt.setStatus("PENDING");
        attempt.setRecoveryStatus("COALESCING");
        when(attemptMapper.selectAttemptState(20002L, 10002L))
                .thenReturn(attempt);

        ProductListingWorkflowView result =
                workflowService.loadWorkflow(context, 10001L);

        assertEquals(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION,
                result.getNextAction()
        );
        assertEquals("NOON_AUTH_RECOVERY_PENDING", result.getReasonCode());
    }

    private ProductListingDraftView draftView() {
        ProductListingDraftView draft = new ProductListingDraftView();
        draft.setDraftId(10001L);
        draft.setOwnerUserId(10002L);
        draft.setStoreCode("STR245027-NAE");
        draft.setStatus("ready_for_dry_run");
        return draft;
    }

    private ProductListingDraftRecord draftRecord() {
        ProductListingDraftRecord draft = new ProductListingDraftRecord();
        draft.setId(10001L);
        draft.setOwnerUserId(10002L);
        draft.setStoreCode("STR245027-NAE");
        draft.setStatus("ready_for_dry_run");
        draft.setDraftJson("{\"psku\":\"NN-TEST\"}");
        return draft;
    }

    private ProductListingTaskRecord taskRecord(
            Long id,
            String mode,
            String status,
            Long sourceTaskId
    ) {
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(id);
        task.setDraftId(10001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setMode(mode);
        task.setStatus(status);
        task.setSourceTaskId(sourceTaskId);
        task.setInputSnapshotJson("{\"psku\":\"NN-TEST\"}");
        return task;
    }

    private ProductListingTaskView taskView(
            ProductListingTaskRecord record
    ) {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(record.getId());
        task.setDraftId(record.getDraftId());
        task.setOwnerUserId(record.getOwnerUserId());
        task.setStoreCode(record.getStoreCode());
        task.setMode(record.getMode());
        task.setStatus(record.getStatus());
        task.setSourceTaskId(record.getSourceTaskId());
        return task;
    }
}
