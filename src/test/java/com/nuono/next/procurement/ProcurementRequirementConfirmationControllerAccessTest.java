package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProcurementRequirementConfirmationControllerAccessTest {

    @Mock
    private ObjectProvider<LocalDbProcurementRequirementConfirmationService> requirementConfirmationServiceProvider;

    @Mock
    private ObjectProvider<LocalDbProcurementCandidatePoolService> candidatePoolServiceProvider;

    @Mock
    private LocalDbProcurementRequirementConfirmationService requirementConfirmationService;

    @Mock
    private LocalDbProcurementCandidatePoolService candidatePoolService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Mock
    private ProcurementRequirementConfirmationMapper procurementRequirementConfirmationMapper;

    private ProcurementRequirementConfirmationController controller;

    @BeforeEach
    void setUp() {
        controller = new ProcurementRequirementConfirmationController(
                requirementConfirmationServiceProvider,
                candidatePoolServiceProvider,
                businessAccessResolver
        );
    }

    @Test
    void demandsIgnoresQueryOwnerAndRequiresAccessBeforeServiceLookup() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(requirementConfirmationServiceProvider.getIfAvailable()).thenReturn(requirementConfirmationService);
        when(requirementConfirmationService.listDemands(10002L, "pending", "milk", 2, 50))
                .thenReturn(new ProcurementRequirementConfirmationListView());

        controller.demands(99999L, "pending", "milk", 2, 50, request);

        InOrder order = inOrder(businessAccessResolver, requirementConfirmationServiceProvider);
        order.verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.PROCUREMENT);
        order.verify(requirementConfirmationServiceProvider).getIfAvailable();
        verify(requirementConfirmationService).listDemands(10002L, "pending", "milk", 2, 50);
    }

    @Test
    void demandIgnoresQueryOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(requirementConfirmationServiceProvider.getIfAvailable()).thenReturn(requirementConfirmationService);
        when(requirementConfirmationService.getDemandDetail(77L, 10002L))
                .thenReturn(new ProcurementRequirementConfirmationDetailView());

        controller.demand(77L, 99999L, request);

        verify(requirementConfirmationService).getDemandDetail(77L, 10002L);
    }

    @Test
    void initializePoolOverwritesBodyOwnerAndOperatorBeforeServiceCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(candidatePoolServiceProvider.getIfAvailable()).thenReturn(candidatePoolService);
        when(candidatePoolService.initializePool(eq(77L), any()))
                .thenReturn(new ProcurementRequirementConfirmationDetailView());

        ProcurementRequirementConfirmationCommands.InitializePoolCommand command =
                new ProcurementRequirementConfirmationCommands.InitializePoolCommand();
        command.setOwnerUserId(99999L);
        command.setOperatorUserId(88888L);
        command.setTriggerInquiry(true);

        controller.initializePool(77L, command, request);

        ArgumentCaptor<ProcurementRequirementConfirmationCommands.InitializePoolCommand> captor =
                ArgumentCaptor.forClass(ProcurementRequirementConfirmationCommands.InitializePoolCommand.class);
        verify(candidatePoolService).initializePool(eq(77L), captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(555L, captor.getValue().getOperatorUserId());
        assertEquals(true, captor.getValue().getTriggerInquiry());
    }

    @Test
    void initializePoolRejectsNullBodyAfterAccessAndBeforeProviderLookup() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.initializePool(77L, null, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("缺少操作参数。", error.getReason());
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.PROCUREMENT);
        verify(candidatePoolServiceProvider, never()).getIfAvailable();
        verify(candidatePoolService, never()).initializePool(any(), any());
    }

    @Test
    void recordReplyPreservesPathVariablesAndPassesTrustedOwnerAndOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(candidatePoolServiceProvider.getIfAvailable()).thenReturn(candidatePoolService);
        when(candidatePoolService.recordPoolItemReply(eq(77L), eq(88L), any()))
                .thenReturn(new ProcurementRequirementConfirmationDetailView());

        ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand command =
                new ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand();
        command.setOwnerUserId(99999L);
        command.setOperatorUserId(88888L);
        command.setQuotePriceText("12.50 SAR");

        controller.recordPoolItemReply(77L, 88L, command, request);

        ArgumentCaptor<ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand> captor =
                ArgumentCaptor.forClass(ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand.class);
        verify(candidatePoolService).recordPoolItemReply(eq(77L), eq(88L), captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(555L, captor.getValue().getOperatorUserId());
        assertEquals("12.50 SAR", captor.getValue().getQuotePriceText());
    }

    @Test
    void resolverRejectionPreventsProviderAndServiceCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.demands(99999L, null, null, null, null, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(requirementConfirmationServiceProvider, never()).getIfAvailable();
        verify(candidatePoolServiceProvider, never()).getIfAvailable();
        verify(requirementConfirmationService, never()).listDemands(any(), any(), any(), any(), any());
    }

    @Test
    void bootstrapBranchStillResolvesAccessBeforeProviderLookup() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(candidatePoolServiceProvider.getIfAvailable()).thenReturn(null);

        ProcurementRequirementConfirmationCommands.InitializePoolCommand command =
                new ProcurementRequirementConfirmationCommands.InitializePoolCommand();
        command.setOwnerUserId(99999L);
        command.setOperatorUserId(88888L);

        ProcurementRequirementConfirmationDetailView view = controller.initializePool(77L, command, request);

        InOrder order = inOrder(businessAccessResolver, candidatePoolServiceProvider);
        order.verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.PROCUREMENT);
        order.verify(candidatePoolServiceProvider).getIfAvailable();
        assertEquals("bootstrap-only", view.getMode());
        assertEquals(false, view.isReady());
    }

    @Test
    void guardUsesBackendConfirmedContextErrorMessages() {
        ProcurementCandidatePoolPermissionGuard guard =
                new ProcurementCandidatePoolPermissionGuard(procurementRequirementConfirmationMapper);
        ProcurementRequirementConfirmationCommands.InitializePoolCommand missingOperator =
                new ProcurementRequirementConfirmationCommands.InitializePoolCommand();
        missingOperator.setOwnerUserId(10002L);

        IllegalArgumentException missingOwner = assertThrows(
                IllegalArgumentException.class,
                () -> guard.resolveWriteContext(null, "初始化待选池")
        );
        IllegalArgumentException missingOperatorError = assertThrows(
                IllegalArgumentException.class,
                () -> guard.resolveWriteContext(missingOperator, "初始化待选池")
        );

        assertEquals("缺少后端确认的老板上下文，暂时不能初始化待选池。", missingOwner.getMessage());
        assertEquals("缺少后端确认的操作人，暂时不能初始化待选池。", missingOperatorError.getMessage());
        verify(procurementRequirementConfirmationMapper, never()).selectOperatorContext(any());
    }

    private BusinessAccessContext procurementContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(555L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.BOSS)
                .roleLevel(1)
                .roleName("老板")
                .menuPaths(java.util.Set.of("/purchase/order"))
                .build();
    }
}
