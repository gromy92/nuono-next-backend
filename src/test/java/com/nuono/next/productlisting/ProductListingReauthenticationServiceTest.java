package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.MerchantAuthorization;
import com.nuono.next.noon.NoonSessionGateway.MerchantProject;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.NoonCatalogConnectionProbe;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductListingReauthenticationServiceTest {

    private ProductListingService listingService;
    private ProductListingWorkflowService workflowService;
    private StoreSyncMapper storeSyncMapper;
    private NoonSessionGateway noonSessionGateway;
    private ProductListingEmailOtpReauthenticationService
            emailOtpReauthenticationService;
    private NoonCatalogConnectionProbe catalogConnectionProbe;
    private ProductListingReauthenticationCommitter committer;
    private ProductListingReauthenticationService service;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        listingService = mock(ProductListingService.class);
        workflowService = mock(ProductListingWorkflowService.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        noonSessionGateway = mock(NoonSessionGateway.class);
        emailOtpReauthenticationService =
                mock(ProductListingEmailOtpReauthenticationService.class);
        catalogConnectionProbe = mock(NoonCatalogConnectionProbe.class);
        committer = mock(ProductListingReauthenticationCommitter.class);
        service = new ProductListingReauthenticationService(
                listingService,
                workflowService,
                storeSyncMapper,
                emailOtpReauthenticationService,
                new ProductListingPasswordReauthenticationService(
                        noonSessionGateway,
                        catalogConnectionProbe,
                        committer
                )
        );
        context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
    }

    @Test
    void verifiedProjectSessionIsCommittedAndResumesSameCreateAttempt() {
        ProductListingTaskView task = reauthenticationTask();
        ProductListingWorkflowView required = reauthenticationWorkflow(task);
        ProductListingWorkflowView publishing = new ProductListingWorkflowView();
        publishing.setPhase(ProductListingWorkflowView.Phase.PUBLISHING);
        publishing.setWriteCertainty(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED
        );
        publishing.setNextAction(ProductListingWorkflowView.NextAction.WAIT);
        StoreSyncStoreRecord project = project();
        StoreSyncStoreRecord site = site();
        MerchantAuthorization authorization = MerchantAuthorization.authorized(
                new MerchantProject("PRJ245027", "PAPERSAY", null, null),
                "sid=refreshed",
                "verified-user-code"
        );
        when(listingService.loadTask(context, 20002L)).thenReturn(task);
        when(workflowService.loadWorkflow(context, 10001L)).thenReturn(required);
        when(storeSyncMapper.selectOwnerProject(10002L, "STR245027-NAE"))
                .thenReturn(project);
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE"))
                .thenReturn(site);
        when(noonSessionGateway.authorizeMerchantLoginCandidate(
                10002L,
                "login-account@example.com",
                "stored-password",
                "PRJ245027",
                "STR245027-NAE"
        )).thenReturn(authorization);
        when(catalogConnectionProbe.verify(
                10002L,
                "project-user@example.com",
                "sid=refreshed",
                "PRJ245027",
                "STR245027-NAE",
                "AE",
                "245027"
        )).thenReturn(JsonNodeFactory.instance.objectNode());
        when(committer.commit(
                org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.any(
                        ProductListingReauthenticationCommitter.ReauthenticationCommit.class)
        )).thenReturn(publishing);

        ProductListingWorkflowView result = service.reauthenticate(context, 20002L);

        assertEquals(ProductListingWorkflowView.Phase.PUBLISHING, result.getPhase());
        verify(catalogConnectionProbe).verify(
                10002L,
                "project-user@example.com",
                "sid=refreshed",
                "PRJ245027",
                "STR245027-NAE",
                "AE",
                "245027"
        );
        verify(committer).commit(
                org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.any(
                        ProductListingReauthenticationCommitter.ReauthenticationCommit.class)
        );
    }

    @Test
    void failedReadOnlyProbeLeavesWorkflowEvidenceUnchanged() {
        ProductListingTaskView task = reauthenticationTask();
        when(listingService.loadTask(context, 20002L)).thenReturn(task);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(reauthenticationWorkflow(task));
        when(storeSyncMapper.selectOwnerProject(10002L, "STR245027-NAE"))
                .thenReturn(project());
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE"))
                .thenReturn(site());
        when(noonSessionGateway.authorizeMerchantLoginCandidate(
                10002L,
                "login-account@example.com",
                "stored-password",
                "PRJ245027",
                "STR245027-NAE"
        )).thenReturn(MerchantAuthorization.authorized(
                new MerchantProject("PRJ245027", "PAPERSAY", null, null),
                "sid=expired",
                "verified-user-code"
        ));
        when(catalogConnectionProbe.verify(
                10002L,
                "project-user@example.com",
                "sid=expired",
                "PRJ245027",
                "STR245027-NAE",
                "AE",
                "245027"
        )).thenThrow(new NoonHttpException(401, "unauthorized", "/offer/list/noon"));

        ProductListingReauthenticationException exception = assertThrows(
                ProductListingReauthenticationException.class,
                () -> service.reauthenticate(context, 20002L)
        );

        assertEquals(
                "Noon 重新授权未通过，只读商品接口验证未完成；原上架任务保持不变。",
                exception.getMessage()
        );
        verify(committer, never()).commit(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void storedEmailOtpCredentialQueuesRecoveryForSameCreateAttempt() {
        ProductListingTaskView task = reauthenticationTask();
        ProductListingWorkflowView required = reauthenticationWorkflow(task);
        ProductListingWorkflowView pending = reauthenticationWorkflow(task);
        pending.setNextAction(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION
        );
        StoreSyncStoreRecord project = project();
        project.setNoonPartnerMailAuthCode("stored-mail-auth-code");
        StoreSyncStoreRecord site = site();
        when(listingService.loadTask(context, 20002L)).thenReturn(task);
        when(workflowService.loadWorkflow(context, 10001L)).thenReturn(required);
        when(storeSyncMapper.selectOwnerProject(10002L, "STR245027-NAE"))
                .thenReturn(project);
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE"))
                .thenReturn(site);
        when(emailOtpReauthenticationService.applies(project)).thenReturn(true);
        when(emailOtpReauthenticationService.enqueue(
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
        verify(emailOtpReauthenticationService).enqueue(
                context,
                task,
                required,
                project,
                site,
                ProductListingReauthenticationCommitter.ResumeAction
                        .RETRY_CREATE
        );
        verify(noonSessionGateway, never()).authorizeMerchantLoginCandidate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void unknownCreateAuthenticationRecoveryUsesReadOnlyLookupResumeAction() {
        ProductListingTaskView task = reauthenticationTask();
        task.setStatus("written_verify_failed");
        ProductListingWorkflowView required = reauthenticationWorkflow(task);
        required.setWriteCertainty(
                ProductListingWorkflowView.WriteCertainty.UNKNOWN);
        ProductListingWorkflowView check = new ProductListingWorkflowView();
        check.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
        check.setWriteCertainty(
                ProductListingWorkflowView.WriteCertainty.UNKNOWN);
        check.setNextAction(
                ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT);
        MerchantAuthorization authorization = MerchantAuthorization.authorized(
                new MerchantProject("PRJ245027", "PAPERSAY", null, null),
                "sid=refreshed",
                "verified-user-code"
        );
        when(listingService.loadTask(context, 20002L)).thenReturn(task);
        when(workflowService.loadWorkflow(context, 10001L))
                .thenReturn(required);
        when(storeSyncMapper.selectOwnerProject(10002L, "STR245027-NAE"))
                .thenReturn(project());
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE"))
                .thenReturn(site());
        when(noonSessionGateway.authorizeMerchantLoginCandidate(
                10002L,
                "login-account@example.com",
                "stored-password",
                "PRJ245027",
                "STR245027-NAE"
        )).thenReturn(authorization);
        when(catalogConnectionProbe.verify(
                10002L,
                "project-user@example.com",
                "sid=refreshed",
                "PRJ245027",
                "STR245027-NAE",
                "AE",
                "245027"
        )).thenReturn(JsonNodeFactory.instance.objectNode());
        when(committer.commit(
                org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.any(
                        ProductListingReauthenticationCommitter
                                .ReauthenticationCommit.class)
        )).thenReturn(check);

        service.reauthenticate(context, 20002L);

        ArgumentCaptor<ProductListingReauthenticationCommitter
                .ReauthenticationCommit> command =
                ArgumentCaptor.forClass(
                        ProductListingReauthenticationCommitter
                                .ReauthenticationCommit.class);
        verify(committer).commit(
                org.mockito.ArgumentMatchers.eq(context),
                command.capture()
        );
        assertEquals(
                ProductListingReauthenticationCommitter.ResumeAction
                        .CHECK_CREATE_RESULT,
                command.getValue().getResumeAction()
        );
    }

    @Test
    void nonReauthenticationWorkflowStopsBeforeCredentialOrNoonAccess() {
        ProductListingTaskView task = reauthenticationTask();
        ProductListingWorkflowView workflow = reauthenticationWorkflow(task);
        workflow.setNextAction(ProductListingWorkflowView.NextAction.NONE);
        when(listingService.loadTask(context, 20002L)).thenReturn(task);
        when(workflowService.loadWorkflow(context, 10001L)).thenReturn(workflow);

        assertThrows(
                ProductListingReauthenticationException.class,
                () -> service.reauthenticate(context, 20002L)
        );

        verify(storeSyncMapper, never()).selectOwnerProject(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(noonSessionGateway, never()).authorizeMerchantLoginCandidate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private ProductListingTaskView reauthenticationTask() {
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

    private ProductListingWorkflowView reauthenticationWorkflow(
            ProductListingTaskView task
    ) {
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
        project.setNoonPartnerId("245027");
        project.setNoonPartnerUser("login-account@example.com");
        project.setNoonPartnerProjectUser("project-user@example.com");
        project.setNoonPartnerUserCode("stored-user-code");
        project.setNoonPartnerPwd("stored-password");
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
