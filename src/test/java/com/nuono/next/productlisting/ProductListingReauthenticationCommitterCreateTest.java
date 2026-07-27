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


class ProductListingReauthenticationCommitterCreateTest extends ProductListingReauthenticationCommitterTestSupport {

    @Test
    void notStartedAuthenticationRecoveryResubmitsSameTaskWithoutReopeningDryRun() {
        ProductListingTaskRecord realRun = task(
                20002L,
                "REAL_RUN",
                "failed",
                20001L
        );
        command = command(
                ProductListingReauthenticationCommitter.ResumeAction
                        .RETRY_CREATE
        );
        ProductListingWorkflowView required = workflow(
                ProductListingWorkflowView.NextAction.REAUTHENTICATE,
                20002L
        );
        ProductListingWorkflowView publishing = workflow(
                ProductListingWorkflowView.NextAction.WAIT,
                20002L
        );
        publishing.setPhase(ProductListingWorkflowView.Phase.PUBLISHING);
        when(listingMapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(realRun);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(required, publishing);
        when(storeSyncMapper.updateProjectReauthenticationSuccess(
                7000L,
                10002L,
                "verified-user-code",
                "sid=refreshed",
                10002L
        )).thenReturn(1);
        when(listingMapper.updateTaskResult(realRun))
                .thenReturn(1);

        ProductListingWorkflowView result = committer.commit(context, command);

        assertEquals(ProductListingWorkflowView.Phase.PUBLISHING, result.getPhase());
        assertEquals("submitted", realRun.getStatus());
        assertEquals(null, realRun.getFailureCode());
        verify(storeSyncMapper).updateProjectReauthenticationSuccess(
                7000L,
                10002L,
                "verified-user-code",
                "sid=refreshed",
                10002L
        );
        verify(listingMapper).updateTaskResult(realRun);
        verify(listingMapper, never()).markValidatedDryRunSuperseded(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void changedWorkflowCannotPersistCookieOrReopenDryRun() {
        ProductListingTaskRecord realRun = task(
                20002L,
                "REAL_RUN",
                "failed",
                20001L
        );
        when(listingMapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(realRun);
        when(workflowService.loadWorkflow(context, 10001L)).thenReturn(
                workflow(ProductListingWorkflowView.NextAction.NONE, 20002L)
        );

        assertThrows(
                ProductListingReauthenticationException.class,
                () -> committer.commit(context, command)
        );

        verify(storeSyncMapper, never()).updateProjectReauthenticationSuccess(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(listingMapper, never()).markValidatedDryRunSuperseded(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void writtenTaskAuthenticationRecoveryAdvancesOnlyToContinuation() {
        ProductListingTaskRecord realRun = task(
                20002L,
                "REAL_RUN",
                "written_verify_failed",
                20001L
        );
        ProductListingReauthenticationCommitter.ReauthenticationCommit
                writtenCommand = command(
                        ProductListingReauthenticationCommitter.ResumeAction
                                .CONTINUE_AFTER_CREATE
                );
        ProductListingWorkflowView required = workflow(
                ProductListingWorkflowView.NextAction.REAUTHENTICATE,
                ProductListingWorkflowView.WriteCertainty.WRITTEN,
                20002L
        );
        ProductListingWorkflowView continuation = workflow(
                ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE,
                ProductListingWorkflowView.WriteCertainty.WRITTEN,
                20002L
        );
        when(listingMapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(realRun);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(required, continuation);
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
                ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE,
                result.getNextAction()
        );
        assertEquals("written_verify_failed", realRun.getStatus());
        assertEquals(
                "noon_write_continuation_failed",
                realRun.getFailureCode()
        );
        verify(listingMapper, never()).selectTaskByIdForUpdate(
                20001L,
                10002L
        );
        verify(listingMapper, never()).markValidatedDryRunSuperseded(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

}
