package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class ProductListingReauthenticationCommitterContinuationTest extends ProductListingReauthenticationCommitterTestSupport {

    @Test
    void unknownCreateAuthenticationRecoveryReturnsOnlyToReadOnlyLookup() {
        ProductListingTaskRecord realRun = task(
                20002L,
                "REAL_RUN",
                "written_verify_failed",
                20001L
        );
        ProductListingReauthenticationCommitter.ReauthenticationCommit
                unknownCommand = command(
                        ProductListingReauthenticationCommitter.ResumeAction
                                .CHECK_CREATE_RESULT
                );
        ProductListingWorkflowView required = workflow(
                ProductListingWorkflowView.NextAction.REAUTHENTICATE,
                ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                20002L
        );
        ProductListingWorkflowView check = workflow(
                ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT,
                ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                20002L
        );
        when(listingMapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(realRun);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(required, check);
        when(storeSyncMapper.updateProjectReauthenticationSuccess(
                7000L,
                10002L,
                "verified-user-code",
                "sid=refreshed",
                10002L
        )).thenReturn(1);
        when(listingMapper.updateTaskResult(realRun)).thenReturn(1);

        ProductListingWorkflowView result =
                committer.commit(context, unknownCommand);

        assertEquals(
                ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT,
                result.getNextAction()
        );
        assertEquals(
                "noon_create_outcome_unknown",
                realRun.getFailureCode()
        );
        verify(listingMapper, never()).markValidatedDryRunSuperseded(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void writtenReadBackAuthenticationRecoveryAdvancesOnlyToReadBack() {
        ProductListingTaskRecord realRun = task(
                20002L,
                "REAL_RUN",
                "written_verify_failed",
                20001L
        );
        ProductListingReauthenticationCommitter.ReauthenticationCommit
                writtenCommand = command(
                        ProductListingReauthenticationCommitter.ResumeAction
                                .VERIFY_READBACK
                );
        ProductListingWorkflowView required = workflow(
                ProductListingWorkflowView.NextAction.REAUTHENTICATE,
                ProductListingWorkflowView.WriteCertainty.WRITTEN,
                20002L
        );
        ProductListingWorkflowView readBack = workflow(
                ProductListingWorkflowView.NextAction.VERIFY_READBACK,
                ProductListingWorkflowView.WriteCertainty.WRITTEN,
                20002L
        );
        when(listingMapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(realRun);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(required, readBack);
        when(storeSyncMapper.updateProjectReauthenticationSuccess(
                7000L,
                10002L,
                "verified-user-code",
                "sid=refreshed",
                10002L
        )).thenReturn(1);
        when(listingMapper.updateTaskResult(realRun)).thenReturn(1);

        ProductListingWorkflowView result =
                committer.commit(context, writtenCommand);

        assertEquals(
                ProductListingWorkflowView.NextAction.VERIFY_READBACK,
                result.getNextAction()
        );
        assertEquals("noon_listing_readback_failed", realRun.getFailureCode());
        verify(listingMapper, never()).markValidatedDryRunSuperseded(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

}
