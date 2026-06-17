package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitForwarderControllerTest {

    @Mock
    private InTransitForwarderService forwarderService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Mock
    private HttpServletRequest request;

    private InTransitForwarderController controller;

    @BeforeEach
    void setUp() {
        controller = new InTransitForwarderController(forwarderService, businessAccessResolver);
    }

    @Test
    void shouldExposeContractBehindInTransitGoodsCapability() {
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context());
        when(forwarderService.contract()).thenReturn(InTransitContractView.build());

        InTransitContractView result = controller.contracts(request);

        assertEquals(2, result.getTransportModes().size());
        assertEquals("SEA", result.getTransportModes().get(0).getCode());
    }

    @Test
    void shouldOverwriteSpoofedOwnerAndOperatorWhenSavingForwarder() {
        SaveForwarderCommand command = new SaveForwarderCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setForwarderCode("YITE");
        command.setForwarderName("义特物流");
        ForwarderView saved = new ForwarderView();
        saved.setId(51001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context());
        ArgumentCaptor<SaveForwarderCommand> captor = ArgumentCaptor.forClass(SaveForwarderCommand.class);
        when(forwarderService.saveForwarder(captor.capture())).thenReturn(saved);

        ForwarderView result = controller.saveForwarder(command, request);

        assertEquals(51001L, result.getId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .build();
    }
}
