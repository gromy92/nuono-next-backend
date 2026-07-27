package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ProductListingWorkflowControllerTest {

    @Test
    void workflowReadUsesProductListingBusinessContext() {
        ProductListingWorkflowService workflowService = mock(ProductListingWorkflowService.class);
        ProductListingCreateOutcomeService createOutcomeService =
                mock(ProductListingCreateOutcomeService.class);
        ProductListingReauthenticationService reauthenticationService =
                mock(ProductListingReauthenticationService.class);
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        ProductListingWorkflowController controller = new ProductListingWorkflowController(
                workflowService, createOutcomeService, reauthenticationService, accessResolver
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingWorkflowView expected = new ProductListingWorkflowView();
        expected.setPhase(ProductListingWorkflowView.Phase.EDITING);
        when(accessResolver.requireBusinessContext(
                request, BusinessCapability.PRODUCT_LISTING)).thenReturn(context);
        when(workflowService.loadWorkflow(context, 10001L)).thenReturn(expected);

        ProductListingWorkflowView actual = controller.workflow(10001L, request);

        assertEquals(expected, actual);
        verify(workflowService).loadWorkflow(context, 10001L);
    }

    @Test
    void reopenAndCreateOutcomeChecksUseTheSameBusinessContextBoundary() {
        ProductListingWorkflowService workflowService = mock(ProductListingWorkflowService.class);
        ProductListingCreateOutcomeService createOutcomeService =
                mock(ProductListingCreateOutcomeService.class);
        ProductListingReauthenticationService reauthenticationService =
                mock(ProductListingReauthenticationService.class);
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        ProductListingWorkflowController controller = new ProductListingWorkflowController(
                workflowService, createOutcomeService, reauthenticationService, accessResolver
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingWorkflowView reopened = new ProductListingWorkflowView();
        ProductListingCreateOutcomeVerificationView verified =
                ProductListingCreateOutcomeVerificationView.notFound(
                        20002L, "NN-TEST-PSKU", 1, false);
        when(accessResolver.requireBusinessContext(
                request, BusinessCapability.PRODUCT_LISTING)).thenReturn(context);
        when(workflowService.reopenReview(context, 20001L)).thenReturn(reopened);
        when(createOutcomeService.verify(context, 20002L)).thenReturn(verified);

        assertEquals(reopened, controller.reopenReview(20001L, request));
        assertEquals(verified, controller.verifyCreateOutcome(20002L, request));
        verify(workflowService).reopenReview(context, 20001L);
        verify(createOutcomeService).verify(context, 20002L);
    }

    @Test
    void confirmedNotCreatedReturnsTheReopenedAuthoritativeWorkflow() {
        ProductListingWorkflowService workflowService = mock(ProductListingWorkflowService.class);
        ProductListingCreateOutcomeService createOutcomeService =
                mock(ProductListingCreateOutcomeService.class);
        ProductListingReauthenticationService reauthenticationService =
                mock(ProductListingReauthenticationService.class);
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        ProductListingWorkflowController controller = new ProductListingWorkflowController(
                workflowService,
                createOutcomeService,
                reauthenticationService,
                accessResolver
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingWorkflowView expected = new ProductListingWorkflowView();
        expected.setPhase(ProductListingWorkflowView.Phase.EDITING);
        when(accessResolver.requireBusinessContext(
                request, BusinessCapability.PRODUCT_LISTING)).thenReturn(context);
        when(createOutcomeService.confirmNotCreated(context, 20002L))
                .thenReturn(10001L);
        when(workflowService.loadWorkflow(context, 10001L)).thenReturn(expected);

        assertEquals(expected, controller.confirmNotCreated(20002L, request));
        verify(createOutcomeService).confirmNotCreated(context, 20002L);
        verify(workflowService).loadWorkflow(context, 10001L);
    }

    @Test
    void listingScopedReauthenticationUsesProductListingContextAndReturnsWorkflow() {
        ProductListingWorkflowService workflowService = mock(ProductListingWorkflowService.class);
        ProductListingCreateOutcomeService createOutcomeService =
                mock(ProductListingCreateOutcomeService.class);
        ProductListingReauthenticationService reauthenticationService =
                mock(ProductListingReauthenticationService.class);
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        ProductListingWorkflowController controller = new ProductListingWorkflowController(
                workflowService,
                createOutcomeService,
                reauthenticationService,
                accessResolver
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingWorkflowView expected = new ProductListingWorkflowView();
        expected.setPhase(ProductListingWorkflowView.Phase.EDITING);
        when(accessResolver.requireBusinessContext(
                request, BusinessCapability.PRODUCT_LISTING)).thenReturn(context);
        when(reauthenticationService.reauthenticate(context, 20002L)).thenReturn(expected);

        assertEquals(expected, controller.reauthenticate(20002L, request));
        verify(reauthenticationService).reauthenticate(context, 20002L);
    }

    @Test
    void reauthenticationStatusPollUsesTheSameBusinessContextBoundary() {
        ProductListingWorkflowService workflowService =
                mock(ProductListingWorkflowService.class);
        ProductListingCreateOutcomeService createOutcomeService =
                mock(ProductListingCreateOutcomeService.class);
        ProductListingReauthenticationService reauthenticationService =
                mock(ProductListingReauthenticationService.class);
        BusinessAccessResolver accessResolver =
                mock(BusinessAccessResolver.class);
        ProductListingWorkflowController controller =
                new ProductListingWorkflowController(
                        workflowService,
                        createOutcomeService,
                        reauthenticationService,
                        accessResolver
                );
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L,
                        90001L,
                        "STR245027-NAE"
                );
        ProductListingWorkflowView pending =
                new ProductListingWorkflowView();
        pending.setNextAction(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION
        );
        when(accessResolver.requireBusinessContext(
                request,
                BusinessCapability.PRODUCT_LISTING
        )).thenReturn(context);
        when(reauthenticationService.reauthenticationStatus(
                context,
                20002L
        )).thenReturn(pending);

        assertEquals(
                pending,
                controller.reauthenticationStatus(20002L, request)
        );
        verify(reauthenticationService).reauthenticationStatus(
                context,
                20002L
        );
    }

    @Test
    void listingScopedReauthenticationFailureIsConflict() {
        ProductListingWorkflowService workflowService = mock(ProductListingWorkflowService.class);
        ProductListingCreateOutcomeService createOutcomeService =
                mock(ProductListingCreateOutcomeService.class);
        ProductListingReauthenticationService reauthenticationService =
                mock(ProductListingReauthenticationService.class);
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        ProductListingWorkflowController controller = new ProductListingWorkflowController(
                workflowService,
                createOutcomeService,
                reauthenticationService,
                accessResolver
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        when(accessResolver.requireBusinessContext(
                request, BusinessCapability.PRODUCT_LISTING)).thenReturn(context);
        when(reauthenticationService.reauthenticate(context, 20002L))
                .thenThrow(new ProductListingReauthenticationException("授权未通过"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.reauthenticate(20002L, request)
        );

        assertEquals(409, exception.getStatus().value());
    }
}
