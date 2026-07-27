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


abstract class ProductListingReauthenticationServiceTestSupport {


    protected ProductListingService listingService;
    protected ProductListingWorkflowService workflowService;
    protected StoreSyncMapper storeSyncMapper;
    protected NoonSessionGateway noonSessionGateway;
    protected ProductListingEmailOtpReauthenticationService
            emailOtpReauthenticationService;
    protected NoonCatalogConnectionProbe catalogConnectionProbe;
    protected ProductListingReauthenticationCommitter committer;
    protected ProductListingReauthenticationService service;
    protected BusinessAccessContext context;

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

    protected ProductListingTaskView reauthenticationTask() {
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

    protected ProductListingWorkflowView reauthenticationWorkflow(
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

    protected StoreSyncStoreRecord project() {
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

    protected StoreSyncStoreRecord site() {
        StoreSyncStoreRecord site = new StoreSyncStoreRecord();
        site.setProjectCode("PRJ245027");
        site.setStoreCode("STR245027-NAE");
        site.setSite("AE");
        return site;
    }
}
