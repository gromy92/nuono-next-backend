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


class ProductListingReauthenticationGuardTest extends ProductListingReauthenticationServiceTestSupport {

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

}
