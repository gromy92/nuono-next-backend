package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProcurementControllerAccessTest {

    @Mock
    private ObjectProvider<LocalDbProcurementService> procurementServiceProvider;

    @Mock
    private ObjectProvider<LocalDbProcurementAutoInquiryService> autoInquiryServiceProvider;

    @Mock
    private ObjectProvider<LocalDbAliAiBulkInquiryReadService> aliAiBulkInquiryReadServiceProvider;

    @Mock
    private ObjectProvider<LocalDbAliAiBulkInquiryCreateService> aliAiBulkInquiryCreateServiceProvider;

    @Mock
    private ObjectProvider<LocalDbAliAiBulkInquiryCreatePageProbeService> aliAiBulkInquiryCreatePageProbeServiceProvider;

    @Mock
    private ObjectProvider<LocalDbAliUnpaidOrderCreateService> aliUnpaidOrderCreateServiceProvider;

    @Mock
    private LocalDbProcurementService procurementService;

    @Mock
    private LocalDbProcurementAutoInquiryService autoInquiryService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProcurementController controller;

    @BeforeEach
    void setUp() {
        controller = new ProcurementController(
                procurementServiceProvider,
                autoInquiryServiceProvider,
                aliAiBulkInquiryReadServiceProvider,
                aliAiBulkInquiryCreateServiceProvider,
                aliAiBulkInquiryCreatePageProbeServiceProvider,
                aliUnpaidOrderCreateServiceProvider,
                businessAccessResolver
        );
    }

    @Test
    void candidatePoolIgnoresQueryOwnerAndUsesTrustedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(procurementServiceProvider.getIfAvailable()).thenReturn(procurementService);
        when(procurementService.buildCandidatePool(10002L, "PO-1")).thenReturn(new ProcurementCandidatePoolView());

        controller.candidatePool(99999L, "PO-1", request);

        verify(procurementService).buildCandidatePool(10002L, "PO-1");
    }

    @Test
    void candidatePoolRequiresAccessBeforeServiceAvailabilityCheck() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.candidatePool(99999L, null, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(procurementServiceProvider, never()).getIfAvailable();
        verify(procurementService, never()).buildCandidatePool(any(), any());
    }

    @Test
    void selectCandidateOverwritesBodyOwnerBeforeServiceCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(procurementServiceProvider.getIfAvailable()).thenReturn(procurementService);
        when(procurementService.selectCandidate(any())).thenReturn(new ProcurementCandidatePoolView());

        ProcurementDecisionCommand command = new ProcurementDecisionCommand();
        command.setOwnerUserId(99999L);
        command.setOrderNo("PO-1");
        command.setDemandItemId(11L);
        command.setCandidateId(22L);

        controller.selectCandidate(command, request);

        ArgumentCaptor<ProcurementDecisionCommand> captor =
                ArgumentCaptor.forClass(ProcurementDecisionCommand.class);
        verify(procurementService).selectCandidate(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("PO-1", captor.getValue().getOrderNo());
        assertEquals(11L, captor.getValue().getDemandItemId());
        assertEquals(22L, captor.getValue().getCandidateId());
    }

    @Test
    void startAutoInquiryOverwritesOwnerAndOperatorBeforeServiceCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(autoInquiryServiceProvider.getIfAvailable()).thenReturn(autoInquiryService);
        when(autoInquiryService.startAutoInquiry(any())).thenReturn(new ProcurementAutoInquiryWorkbenchView());

        ProcurementAutoInquiryStartCommand command = new ProcurementAutoInquiryStartCommand();
        command.setOwnerUserId(99999L);
        command.setOperatorUserId(88888L);
        command.setDemandItemId(11L);
        command.setCandidateId(22L);

        controller.startAutoInquiry(command, request);

        ArgumentCaptor<ProcurementAutoInquiryStartCommand> captor =
                ArgumentCaptor.forClass(ProcurementAutoInquiryStartCommand.class);
        verify(autoInquiryService).startAutoInquiry(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(555L, captor.getValue().getOperatorUserId());
        assertEquals(11L, captor.getValue().getDemandItemId());
        assertEquals(22L, captor.getValue().getCandidateId());
    }

    @Test
    void selectCandidateCreatesCommandWhenBodyIsNullAndAppliesTrustedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT))
                .thenReturn(procurementContext());
        when(procurementServiceProvider.getIfAvailable()).thenReturn(procurementService);
        when(procurementService.selectCandidate(any())).thenReturn(new ProcurementCandidatePoolView());

        controller.selectCandidate(null, request);

        ArgumentCaptor<ProcurementDecisionCommand> captor =
                ArgumentCaptor.forClass(ProcurementDecisionCommand.class);
        verify(procurementService).selectCandidate(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
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
