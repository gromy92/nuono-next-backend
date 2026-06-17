package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitBatchCommands.DeleteLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteNodeCommand;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchListView;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.LineListView;
import com.nuono.next.intransit.InTransitBatchRecords.LineView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeListView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeView;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightSyncView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncCommitView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InTransitGoodsControllerTest {

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
    void shouldListBatchesUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        InTransitBatchQuery query = new InTransitBatchQuery();
        query.setOwnerUserId(1L);
        query.setTransportMode("SEA");
        BatchListView listView = new BatchListView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<InTransitBatchQuery> captor = ArgumentCaptor.forClass(InTransitBatchQuery.class);
        when(batchService.listBatches(captor.capture())).thenReturn(listView);

        BatchListView result = controller.batches(query, request);

        assertEquals(listView, result);
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("SEA", captor.getValue().getTransportMode());
        verify(accessScopeService).applyReadableBatchScope(eq(context), same(captor.getValue()));
    }

    @Test
    void shouldOverwriteSpoofedOwnerAndOperatorWhenSavingBatch() {
        BusinessAccessContext context = context();
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setTransportMode("AIR");
        BatchView saved = new BatchView();
        saved.setBatchId(53001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<SaveBatchCommand> captor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        when(batchService.saveBatch(captor.capture())).thenReturn(saved);

        BatchView result = controller.saveBatch(command, request);

        assertEquals(53001L, result.getBatchId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        verify(accessScopeService).requireWritableBatchScope(eq(context), same(captor.getValue()));
    }

    @Test
    void shouldListLinesUsingBackendOwnerContextAndRouteBatchId() {
        BusinessAccessContext context = context();
        LineListView lineListView = new LineListView();
        BatchView scopedBatch = scopedBatch();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        when(batchService.listLines(10002L, 53001L)).thenReturn(lineListView);

        LineListView result = controller.lines(53001L, request);

        assertEquals(lineListView, result);
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldOverwriteSpoofedOwnerOperatorAndRouteBatchIdWhenSavingLine() {
        BusinessAccessContext context = context();
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setBatchId(1L);
        command.setSku("SKU-AE-001");
        command.setShippedQuantity(10);
        LineView saved = new LineView();
        saved.setLineId(54001L);
        BatchView scopedBatch = scopedBatch();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        ArgumentCaptor<SaveLineCommand> captor = ArgumentCaptor.forClass(SaveLineCommand.class);
        when(batchService.saveLine(captor.capture())).thenReturn(saved);

        LineView result = controller.saveLine(53001L, command, request);

        assertEquals(54001L, result.getLineId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(53001L, captor.getValue().getBatchId());
        verify(accessScopeService).requireWritableLineScope(eq(context), same(captor.getValue()));
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldOverwriteSpoofedOwnerOperatorAndRouteIdsWhenDeletingLine() {
        BusinessAccessContext context = context();
        LineListView lineListView = new LineListView();
        BatchView scopedBatch = scopedBatch();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        ArgumentCaptor<DeleteLineCommand> captor = ArgumentCaptor.forClass(DeleteLineCommand.class);
        when(batchService.deleteLine(captor.capture())).thenReturn(lineListView);

        LineListView result = controller.deleteLine(53001L, 54001L, request);

        assertEquals(lineListView, result);
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(53001L, captor.getValue().getBatchId());
        assertEquals(54001L, captor.getValue().getLineId());
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldListNodesUsingBackendOwnerContextAndRouteBatchId() {
        BusinessAccessContext context = context();
        NodeListView nodeListView = new NodeListView();
        BatchView scopedBatch = scopedBatch();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        when(batchService.listNodes(10002L, 53001L)).thenReturn(nodeListView);

        NodeListView result = controller.nodes(53001L, request);

        assertEquals(nodeListView, result);
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldOverwriteSpoofedOwnerOperatorAndRouteBatchIdWhenSavingNode() {
        BusinessAccessContext context = context();
        SaveNodeCommand command = new SaveNodeCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setBatchId(1L);
        command.setNodeStatus("exception");
        NodeView saved = new NodeView();
        saved.setNodeId(55001L);
        BatchView scopedBatch = scopedBatch();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        ArgumentCaptor<SaveNodeCommand> captor = ArgumentCaptor.forClass(SaveNodeCommand.class);
        when(batchService.saveNode(captor.capture())).thenReturn(saved);

        NodeView result = controller.saveNode(53001L, command, request);

        assertEquals(55001L, result.getNodeId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(53001L, captor.getValue().getBatchId());
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldOverwriteOwnerOperatorAndRouteIdsWhenDeletingNode() {
        BusinessAccessContext context = context();
        NodeListView nodeListView = new NodeListView();
        BatchView scopedBatch = scopedBatch();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        ArgumentCaptor<DeleteNodeCommand> captor = ArgumentCaptor.forClass(DeleteNodeCommand.class);
        when(batchService.deleteNode(captor.capture())).thenReturn(nodeListView);

        NodeListView result = controller.deleteNode(53001L, 55001L, request);

        assertEquals(nodeListView, result);
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(53001L, captor.getValue().getBatchId());
        assertEquals(55001L, captor.getValue().getNodeId());
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldOverwriteOwnerOperatorWhenPreviewingPluginSync() {
        BusinessAccessContext context = context();
        PluginSyncCommand command = new PluginSyncCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setSourceSystem("CHIC");
        PluginSyncPreviewView preview = new PluginSyncPreviewView();
        preview.setSourceSystem("CHIC");

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<PluginSyncCommand> captor = ArgumentCaptor.forClass(PluginSyncCommand.class);
        when(pluginSyncService.preview(captor.capture())).thenReturn(preview);

        PluginSyncPreviewView result = controller.previewPluginSync(command, request);

        assertEquals("CHIC", result.getSourceSystem());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(context, captor.getValue().getAccessContext());
    }

    @Test
    void shouldInjectOwnerOperatorWhenPluginSyncPayloadOmitsIdentity() {
        BusinessAccessContext context = context();
        PluginSyncCommand command = new PluginSyncCommand();
        command.setSourceSystem("ET");
        PluginSyncPreviewView preview = new PluginSyncPreviewView();
        preview.setSourceSystem("ET");

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<PluginSyncCommand> captor = ArgumentCaptor.forClass(PluginSyncCommand.class);
        when(pluginSyncService.preview(captor.capture())).thenReturn(preview);

        PluginSyncPreviewView result = controller.previewPluginSync(command, request);

        assertEquals("ET", result.getSourceSystem());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(context, captor.getValue().getAccessContext());
    }

    @Test
    void shouldOverwriteOwnerOperatorWhenCommittingPluginSync() {
        BusinessAccessContext context = context();
        PluginSyncCommand command = new PluginSyncCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setSourceSystem("CHIC");
        PluginSyncCommitView commitView = new PluginSyncCommitView();
        commitView.setCommitted(true);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<PluginSyncCommand> captor = ArgumentCaptor.forClass(PluginSyncCommand.class);
        when(pluginSyncService.commit(captor.capture())).thenReturn(commitView);

        PluginSyncCommitView result = controller.commitPluginSync(command, request);

        Assertions.assertTrue(result.isCommitted());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(context, captor.getValue().getAccessContext());
    }

    @Test
    void shouldOverwriteOwnerOperatorWhenSyncingActualFreightCosts() {
        BusinessAccessContext context = context();
        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setSourceSystem("YITONG");
        ActualFreightSyncView syncView = new ActualFreightSyncView();
        syncView.setBillCount(1);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<ActualFreightSyncCommand> captor = ArgumentCaptor.forClass(ActualFreightSyncCommand.class);
        when(freightCostService.syncActualCosts(captor.capture())).thenReturn(syncView);

        ActualFreightSyncView result = controller.syncActualFreightCosts(command, request);

        assertEquals(1, result.getBillCount());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(context, captor.getValue().getAccessContext());
    }

    @Test
    void shouldNotLetSystemAdminOperateInTransitGoodsByMenuOnly() {
        ResponseStatusException denied = new ResponseStatusException(HttpStatus.FORBIDDEN, "系统管理员不能操作店铺业务。");
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenThrow(denied);

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.batches(new InTransitBatchQuery(), request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .build();
    }

    private BatchView scopedBatch() {
        BatchView batch = new BatchView();
        batch.setBatchId(53001L);
        batch.setTargetStoreCode("STR245027-NAE");
        batch.setTargetSiteCode("AE");
        return batch;
    }
}
