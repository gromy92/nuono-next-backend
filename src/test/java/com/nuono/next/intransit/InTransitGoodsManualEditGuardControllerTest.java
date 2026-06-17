package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitBatchCommands.DeleteNodeCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InTransitGoodsManualEditGuardControllerTest {

    @Mock
    private InTransitBatchService batchService;

    @Mock
    private InTransitPluginSyncService pluginSyncService;

    @Mock
    private InTransitFreightCostService freightCostService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;

    @Mock
    private HttpServletRequest request;

    private InTransitGoodsController controller;

    @BeforeEach
    void setUp() {
        controller = new InTransitGoodsController(
                batchService,
                pluginSyncService,
                freightCostService,
                businessAccessResolver,
                accessScopeService
        );
    }

    @Test
    void shouldRejectManualBatchBaseUpdateWhenBatchAlreadyInTransit() {
        BusinessAccessContext context = context();
        SaveBatchCommand command = new SaveBatchCommand();
        command.setBatchId(53001L);
        command.setBatchStatus("draft");

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(batch("in_transit"));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.saveBatch(command, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(batchService, never()).saveBatch(any(SaveBatchCommand.class));
    }

    @Test
    void shouldRejectManualNodeUpdateWhenBatchAlreadyInTransit() {
        BusinessAccessContext context = context();
        SaveNodeCommand command = new SaveNodeCommand();
        command.setNodeStatus("exception");

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(batch("in_transit"));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.saveNode(53001L, command, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(batchService, never()).saveNode(any(SaveNodeCommand.class));
    }

    @Test
    void shouldRejectManualNodeDeleteWhenBatchAlreadyInTransit() {
        BusinessAccessContext context = context();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(batch("in_transit"));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.deleteNode(53001L, 55001L, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(batchService, never()).deleteNode(any(DeleteNodeCommand.class));
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .build();
    }

    private BatchView batch(String status) {
        BatchView batch = new BatchView();
        batch.setBatchId(53001L);
        batch.setBatchStatus(status);
        return batch;
    }
}
