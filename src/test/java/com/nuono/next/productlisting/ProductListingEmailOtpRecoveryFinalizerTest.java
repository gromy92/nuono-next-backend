package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductListingEmailOtpRecoveryFinalizerTest {
    private ProductListingReauthenticationAttemptMapper attemptMapper;
    private ProductListingMapper listingMapper;
    private ProductListingWorkflowService workflowService;
    private ProductListingEmailOtpRecoveryFinalizer finalizer;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        attemptMapper = mock(ProductListingReauthenticationAttemptMapper.class);
        listingMapper = mock(ProductListingMapper.class);
        workflowService = mock(ProductListingWorkflowService.class);
        finalizer = new ProductListingEmailOtpRecoveryFinalizer(
                attemptMapper,
                listingMapper,
                workflowService,
                new ProductListingReauthenticationAttemptProjector()
        );
        context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
    }

    @Test
    void recoveredExactItemResubmitsSameNotStartedTask() {
        ProductListingTaskView task = task();
        ProductListingReauthenticationAttemptRecord attempt =
                recoveredAttempt("RETRY_CREATE");
        ProductListingWorkflowView publishing = new ProductListingWorkflowView();
        publishing.setPhase(ProductListingWorkflowView.Phase.PUBLISHING);
        publishing.setWriteCertainty(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED
        );
        publishing.setNextAction(ProductListingWorkflowView.NextAction.WAIT);
        ProductListingTaskRecord realRun = realRun();
        when(attemptMapper.selectAttemptState(20002L, 10002L))
                .thenReturn(attempt);
        when(attemptMapper.claimRecoveredAttempt(
                20002L, 10002L, 91L, 501L, 0L
        )).thenReturn(1);
        when(listingMapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(realRun);
        when(listingMapper.updateTaskResult(realRun))
                .thenReturn(1);
        when(attemptMapper.completeClaimedAttempt(
                20002L, 10002L, 91L, 501L, 1L
        )).thenReturn(1);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(publishing);

        ProductListingWorkflowView result = finalizer.poll(
                context,
                task,
                workflow(task)
        );

        assertEquals(ProductListingWorkflowView.Phase.PUBLISHING, result.getPhase());
        assertEquals("submitted", realRun.getStatus());
        verify(listingMapper).updateTaskResult(realRun);
        verify(listingMapper, never()).markValidatedDryRunSuperseded(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(attemptMapper).completeClaimedAttempt(
                20002L, 10002L, 91L, 501L, 1L
        );
    }

    @Test
    void failedClaimMakesSecondPollWaitWithoutTouchingListingEvidence() {
        ProductListingTaskView task = task();
        ProductListingReauthenticationAttemptRecord attempt =
                recoveredAttempt("REOPEN_REVIEW");
        when(attemptMapper.selectAttemptState(20002L, 10002L))
                .thenReturn(attempt, attempt);
        when(attemptMapper.claimRecoveredAttempt(
                20002L, 10002L, 91L, 501L, 0L
        )).thenReturn(0);

        ProductListingWorkflowView result = finalizer.poll(
                context,
                task,
                workflow(task)
        );

        assertEquals(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION,
                result.getNextAction()
        );
        verifyNoInteractions(listingMapper);
    }

    @Test
    void persistedUnknownResumeActionRestoresReadOnlyOutcomeCheck() {
        ProductListingTaskView task = task();
        ProductListingReauthenticationAttemptRecord attempt =
                recoveredAttempt("CHECK_CREATE_RESULT");
        ProductListingTaskRecord realRun = realRun();
        when(attemptMapper.selectAttemptState(20002L, 10002L))
                .thenReturn(attempt);
        when(attemptMapper.claimRecoveredAttempt(
                20002L, 10002L, 91L, 501L, 0L
        )).thenReturn(1);
        when(listingMapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(realRun);
        when(listingMapper.updateTaskResult(realRun)).thenReturn(1);
        when(attemptMapper.completeClaimedAttempt(
                20002L, 10002L, 91L, 501L, 1L
        )).thenReturn(1);
        ProductListingWorkflowView check = workflow(task);
        check.setNextAction(
                ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT
        );
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(check);

        ProductListingWorkflowView result = finalizer.poll(
                context,
                task,
                workflow(task)
        );

        assertEquals(
                ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT,
                result.getNextAction()
        );
        assertEquals("noon_create_outcome_unknown", realRun.getFailureCode());
        verify(listingMapper).updateTaskResult(realRun);
    }

    private ProductListingReauthenticationAttemptRecord recoveredAttempt(
            String resumeAction
    ) {
        ProductListingReauthenticationAttemptRecord attempt =
                new ProductListingReauthenticationAttemptRecord();
        attempt.setRealRunTaskId(20002L);
        attempt.setOwnerUserId(10002L);
        attempt.setDraftId(10001L);
        attempt.setProjectId(7000L);
        attempt.setProjectCode("PRJ245027");
        attempt.setStoreCode("STR245027-NAE");
        attempt.setRecoveryId(91L);
        attempt.setRecoveryItemId(501L);
        attempt.setRequestedAuthVersion(4L);
        attempt.setResumeAction(resumeAction);
        attempt.setStatus("PENDING");
        attempt.setVersionNo(0L);
        attempt.setRecoveryItemStatus("RECOVERED");
        attempt.setRecoveryItemRecoveredAt(LocalDateTime.now());
        attempt.setProjectAuthStatus("HEALTHY");
        attempt.setCurrentAuthVersion(5L);
        attempt.setActiveRecoveryId(null);
        return attempt;
    }

    private ProductListingTaskView task() {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(20002L);
        task.setSourceTaskId(20001L);
        task.setDraftId(10001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setMode("REAL_RUN");
        task.setStatus("failed");
        task.setFailureCode("noon_auth_required");
        return task;
    }

    private ProductListingTaskRecord realRun() {
        ProductListingTaskRecord record = new ProductListingTaskRecord();
        record.setId(20002L);
        record.setSourceTaskId(20001L);
        record.setDraftId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setMode("REAL_RUN");
        record.setStatus("failed");
        record.setFailureCode("noon_auth_required");
        return record;
    }

    private ProductListingTaskRecord sourceDryRun() {
        ProductListingTaskRecord record = new ProductListingTaskRecord();
        record.setId(20001L);
        record.setDraftId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setMode("DRY_RUN");
        record.setStatus("validated");
        return record;
    }

    private ProductListingWorkflowView workflow(ProductListingTaskView task) {
        ProductListingWorkflowView workflow = new ProductListingWorkflowView();
        workflow.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
        workflow.setWriteCertainty(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED
        );
        workflow.setNextAction(
                ProductListingWorkflowView.NextAction.REAUTHENTICATE
        );
        workflow.setRealRunTask(task);
        return workflow;
    }
}
