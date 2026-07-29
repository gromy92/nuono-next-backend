package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProductListingEmailOtpReauthenticationServiceTest {
    private ProductListingReauthenticationAttemptMapper attemptMapper;
    private NoonProjectAuthRecoveryQueue recoveryQueue;
    private NoonAuthRecoveryProperties properties;
    private NoonSessionGateway sessionGateway;
    private ProductListingEmailOtpReauthenticationService service;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        attemptMapper = mock(ProductListingReauthenticationAttemptMapper.class);
        recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setTrustedSenderDomains("noon.partners");
        sessionGateway = mock(NoonSessionGateway.class);
        ProductListingReauthenticationAttemptProjector projector =
                new ProductListingReauthenticationAttemptProjector();
        service = new ProductListingEmailOtpReauthenticationService(
                new ProductListingEmailOtpRecoveryEnqueuer(
                        attemptMapper,
                        recoveryQueue,
                        properties,
                        sessionGateway
                ),
                mock(ProductListingEmailOtpRecoveryFinalizer.class),
                projector
        );
        context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
    }

    @Test
    void unifiedSharedEmailRoutesRecoveryWithoutProjectMailAuthCode() {
        StoreSyncStoreRecord project = project();
        project.setNoonPartnerMailAuthCode(null);
        when(sessionGateway.configuredMerchantEmail())
                .thenReturn("shared@example.com");

        assertTrue(service.applies(project));
    }

    @Test
    void missingProjectAndUnifiedEmailCredentialsDoesNotRouteRecovery() {
        StoreSyncStoreRecord project = project();
        project.setNoonPartnerMailAuthCode(null);
        when(sessionGateway.configuredMerchantEmail())
                .thenThrow(new IllegalStateException("not configured"));

        assertFalse(service.applies(project));
    }

    @Test
    void enqueuePersistsExactSourceLessItemWithoutUsingStoredPassword() {
        ProductListingTaskView task = task();
        ProductListingReauthenticationAttemptRecord persisted =
                pendingAttempt();
        when(attemptMapper.selectAttemptForUpdate(20002L, 10002L))
                .thenReturn(null, persisted);
        when(sessionGateway.configuredMerchantEmail())
                .thenReturn("shared@example.com");
        when(recoveryQueue.enqueueProject(
                10002L,
                "PRJ245027",
                "STR245027-NAE"
        )).thenReturn(Optional.of(91L));
        when(attemptMapper.selectSourceLessRecoveryItem(
                91L,
                10002L,
                "PRJ245027"
        )).thenReturn(recoveryItem());

        ProductListingWorkflowView result = service.enqueue(
                context,
                task,
                workflow(task),
                project(),
                site(),
                ProductListingReauthenticationCommitter.ResumeAction
                        .REOPEN_REVIEW
        );

        assertEquals(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION,
                result.getNextAction()
        );
        ArgumentCaptor<ProductListingReauthenticationAttemptRecord> saved =
                ArgumentCaptor.forClass(
                        ProductListingReauthenticationAttemptRecord.class
                );
        verify(attemptMapper).insertPendingAttempt(saved.capture());
        assertEquals(91L, saved.getValue().getRecoveryId());
        assertEquals(501L, saved.getValue().getRecoveryItemId());
        assertEquals(4L, saved.getValue().getRequestedAuthVersion());
        assertEquals("REOPEN_REVIEW", saved.getValue().getResumeAction());
        verify(sessionGateway, never()).authorizeMerchantLoginCandidate(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void duplicatePendingAttemptDoesNotEnqueueAnotherRecovery() {
        when(attemptMapper.selectAttemptForUpdate(20002L, 10002L))
                .thenReturn(pendingAttempt());

        ProductListingWorkflowView result = service.enqueue(
                context,
                task(),
                workflow(task()),
                project(),
                site(),
                ProductListingReauthenticationCommitter.ResumeAction
                        .REOPEN_REVIEW
        );

        assertEquals(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION,
                result.getNextAction()
        );
        verify(recoveryQueue, never()).enqueueProject(any(), any(), any());
        verify(attemptMapper, never()).insertPendingAttempt(any());
    }

    @Test
    void missingTrustedSenderConfigurationLeavesListingUntouched() {
        properties.setTrustedSenderDomains("");
        when(attemptMapper.selectAttemptForUpdate(20002L, 10002L))
                .thenReturn(null);

        ProductListingReauthenticationException error = assertThrows(
                ProductListingReauthenticationException.class,
                () -> service.enqueue(
                        context,
                        task(),
                        workflow(task()),
                        project(),
                        site(),
                        ProductListingReauthenticationCommitter.ResumeAction
                                .REOPEN_REVIEW
                )
        );

        assertEquals(
                true,
                error.getMessage().contains(
                        "NUONO_NOON_AUTH_RECOVERY_TRUSTED_SENDER_DOMAINS"
                )
        );
        verify(recoveryQueue, never()).enqueueProject(any(), any(), any());
        verify(attemptMapper, never()).insertPendingAttempt(any());
    }

    private ProductListingReauthenticationAttemptRecord recoveryItem() {
        ProductListingReauthenticationAttemptRecord item =
                new ProductListingReauthenticationAttemptRecord();
        item.setRecoveryId(91L);
        item.setRecoveryItemId(501L);
        item.setRequestedAuthVersion(4L);
        item.setRecoveryItemStatus("PENDING");
        return item;
    }

    private ProductListingReauthenticationAttemptRecord pendingAttempt() {
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
        attempt.setResumeAction("REOPEN_REVIEW");
        attempt.setStatus("PENDING");
        attempt.setVersionNo(0L);
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

    private StoreSyncStoreRecord project() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setId(7000L);
        project.setProjectCode("PRJ245027");
        project.setStoreCode("PRJ245027");
        project.setNoonPartnerMailAuthCode("stored-mail-auth-code");
        project.setNoonPartnerPwd("stale-password-must-not-be-used");
        return project;
    }

    private StoreSyncStoreRecord site() {
        StoreSyncStoreRecord site = new StoreSyncStoreRecord();
        site.setProjectCode("PRJ245027");
        site.setStoreCode("STR245027-NAE");
        site.setSite("AE");
        return site;
    }
}
