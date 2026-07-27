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


class ProductListingReauthenticationExecutionTest extends ProductListingReauthenticationServiceTestSupport {

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
}
