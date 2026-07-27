package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.junit.jupiter.api.Test;

class ProductListingWorkflowReopenTerminalTest {

    @Test
    void authenticationFailureCannotSupersedeItsSourceDryRun() {
        Fixture fixture = fixture("failed", "noon_auth_required", "cookie expired");

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.workflowService.reopenReview(
                        fixture.context, 20001L)
        );

        verify(fixture.mapper, never())
                .markValidatedDryRunSuperseded(20001L, 10002L);
    }

    @Test
    void unknownWriteOutcomeCannotSupersedeItsSourceDryRun() {
        Fixture fixture = fixture(
                "written_verify_failed",
                "noon_create_outcome_unknown",
                "connection reset"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.workflowService.reopenReview(fixture.context, 20001L)
        );

        verify(fixture.mapper, never()).markValidatedDryRunSuperseded(20001L, 10002L);
    }

    private Fixture fixture(String status, String failureCode, String failureMessage) {
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
        ProductListingTaskRecord dryRun =
                taskRecord(20001L, "DRY_RUN", "validated", null, null, null);
        ProductListingTaskRecord realRun =
                taskRecord(20002L, "REAL_RUN", status, 20001L, failureCode, failureMessage);
        when(listingService.loadTask(context, 20001L)).thenReturn(taskView(dryRun));
        when(mapper.selectTaskByIdForUpdate(20001L, 10002L)).thenReturn(dryRun);
        when(mapper.selectRealWriteAttemptTaskBySourceTaskId(10002L, 20001L))
                .thenReturn(realRun);
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView(realRun));
        when(listingService.loadDraft(context, 10001L)).thenReturn(draftView());
        when(mapper.selectDraftById(10001L, 10002L)).thenReturn(draftRecord());
        return new Fixture(mapper, listingService, workflowService, context, dryRun);
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

    private ProductListingTaskRecord taskRecord(
            Long id,
            String mode,
            String status,
            Long sourceTaskId,
            String failureCode,
            String failureMessage
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
        record.setFailureMessage(failureMessage);
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
        view.setFailureMessage(record.getFailureMessage());
        return view;
    }

    private static final class Fixture {
        private final ProductListingMapper mapper;
        private final ProductListingService listingService;
        private final ProductListingWorkflowService workflowService;
        private final BusinessAccessContext context;
        private final ProductListingTaskRecord dryRun;

        private Fixture(
                ProductListingMapper mapper,
                ProductListingService listingService,
                ProductListingWorkflowService workflowService,
                BusinessAccessContext context,
                ProductListingTaskRecord dryRun
        ) {
            this.mapper = mapper;
            this.listingService = listingService;
            this.workflowService = workflowService;
            this.context = context;
            this.dryRun = dryRun;
        }
    }
}
