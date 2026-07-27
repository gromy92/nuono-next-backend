package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.junit.jupiter.api.Test;

class ProductListingEmailOtpRoutingTest {

    @Test
    void mailAuthCodeWinsOverAStoredPasswordAndOnlyQueuesRecovery() {
        ProductListingService listingService = mock(ProductListingService.class);
        ProductListingWorkflowService workflowService =
                mock(ProductListingWorkflowService.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductListingEmailOtpReauthenticationService emailService =
                mock(ProductListingEmailOtpReauthenticationService.class);
        ProductListingPasswordReauthenticationService passwordService =
                mock(ProductListingPasswordReauthenticationService.class);
        ProductListingReauthenticationService service =
                new ProductListingReauthenticationService(
                        listingService,
                        workflowService,
                        storeSyncMapper,
                        emailService,
                        passwordService
                );
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L,
                        90001L,
                        "STR245027-NAE"
                );
        ProductListingTaskView task = task();
        ProductListingWorkflowView required = workflow(task);
        ProductListingWorkflowView pending = workflow(task);
        pending.setNextAction(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION
        );
        StoreSyncStoreRecord project = project();
        StoreSyncStoreRecord site = site();
        when(listingService.loadTask(context, 20002L)).thenReturn(task);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(required);
        when(storeSyncMapper.selectOwnerProject(
                10002L,
                "STR245027-NAE"
        )).thenReturn(project);
        when(storeSyncMapper.selectOwnerStore(
                10002L,
                "STR245027-NAE"
        )).thenReturn(site);
        when(emailService.applies(project)).thenReturn(true);
        when(emailService.enqueue(
                context,
                task,
                required,
                project,
                site,
                ProductListingReauthenticationCommitter.ResumeAction
                        .RETRY_CREATE
        )).thenReturn(pending);

        ProductListingWorkflowView result =
                service.reauthenticate(context, 20002L);

        assertEquals(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION,
                result.getNextAction()
        );
        verify(emailService).enqueue(
                context,
                task,
                required,
                project,
                site,
                ProductListingReauthenticationCommitter.ResumeAction
                        .RETRY_CREATE
        );
        verify(passwordService, never()).reauthenticate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
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
        project.setNoonPartnerMailAuthCode("mail-auth-code");
        project.setNoonPartnerPwd("stale-password");
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
