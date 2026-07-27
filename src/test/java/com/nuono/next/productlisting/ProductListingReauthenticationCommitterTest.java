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

class ProductListingReauthenticationCommitterTest {

    private ProductListingMapper listingMapper;
    private StoreSyncMapper storeSyncMapper;
    private ProductListingWorkflowService workflowService;
    private ProductListingReauthenticationCommitter committer;
    private BusinessAccessContext context;
    private ProductListingReauthenticationCommitter.ReauthenticationCommit command;

    @BeforeEach
    void setUp() {
        listingMapper = mock(ProductListingMapper.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        workflowService = mock(ProductListingWorkflowService.class);
        committer = new ProductListingReauthenticationCommitter(
                listingMapper,
                storeSyncMapper,
                workflowService
        );
        context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        command = new ProductListingReauthenticationCommitter.ReauthenticationCommit(
                20002L,
                20001L,
                10001L,
                10002L,
                "STR245027-NAE",
                7000L,
                "PRJ240053",
                "verified-user-code",
                "sid=refreshed",
                ProductListingReauthenticationCommitter.ResumeAction
                        .REOPEN_REVIEW
        );
    }

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

    private ProductListingTaskRecord task(
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
        if ("REAL_RUN".equals(mode)) {
            task.setFailureCode("noon_auth_required");
        }
        return task;
    }

    private ProductListingWorkflowView workflow(
            ProductListingWorkflowView.NextAction nextAction,
            Long realRunTaskId
    ) {
        return workflow(
                nextAction,
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                realRunTaskId
        );
    }

    private ProductListingWorkflowView workflow(
            ProductListingWorkflowView.NextAction nextAction,
            ProductListingWorkflowView.WriteCertainty certainty,
            Long realRunTaskId
    ) {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(realRunTaskId);
        ProductListingWorkflowView workflow = new ProductListingWorkflowView();
        workflow.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
        workflow.setWriteCertainty(certainty);
        workflow.setNextAction(nextAction);
        workflow.setRealRunTask(task);
        return workflow;
    }

    private ProductListingReauthenticationCommitter.ReauthenticationCommit
            command(
                    ProductListingReauthenticationCommitter.ResumeAction
                            resumeAction
            ) {
        return new ProductListingReauthenticationCommitter
                .ReauthenticationCommit(
                20002L,
                20001L,
                10001L,
                10002L,
                "STR245027-NAE",
                7000L,
                "PRJ240053",
                "verified-user-code",
                "sid=refreshed",
                resumeAction
        );
    }
}
