package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderCommand;
import com.nuono.next.intransit.InTransitBatchCommands.ConfirmImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.PreviewImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchListView;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportConfirmView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewView;
import com.nuono.next.intransit.InTransitBatchRecords.LineListView;
import com.nuono.next.intransit.InTransitBatchRecords.LineView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeListView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderView;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InTransitGoodsControllerTest {

    @Mock
    private InTransitForwarderService service;

    @Mock
    private InTransitBatchService batchService;

    @Mock
    private InTransitImportService importService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;

    @Mock
    private HttpServletRequest request;

    private InTransitGoodsController controller;

    @BeforeEach
    void setUp() {
        controller = new InTransitGoodsController(service, batchService, importService, businessAccessResolver, accessScopeService);
    }

    @Test
    void shouldExposeContractBehindInTransitGoodsCapability() {
        BusinessAccessContext context = context();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(service.contract()).thenReturn(InTransitContractView.build());

        InTransitContractView result = controller.contracts(request);

        assertEquals(2, result.getTransportModes().size());
        assertEquals("SEA", result.getTransportModes().get(0).getCode());
    }

    @Test
    void shouldOverwriteSpoofedOwnerAndOperatorWhenSavingForwarder() {
        BusinessAccessContext context = context();
        SaveForwarderCommand command = new SaveForwarderCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setForwarderCode("YITE");
        command.setForwarderName("义特物流");
        ForwarderView saved = new ForwarderView();
        saved.setId(51001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<SaveForwarderCommand> captor = ArgumentCaptor.forClass(SaveForwarderCommand.class);
        when(service.saveForwarder(captor.capture())).thenReturn(saved);

        ForwarderView result = controller.saveForwarder(command, request);

        assertEquals(51001L, result.getId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
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
    void shouldOverwriteOwnerOperatorWhenPreviewingImport() throws Exception {
        BusinessAccessContext context = context();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "历史在途.csv",
                "text/csv",
                "批次号,SKU,发货数量\nBATCH-001,SKU-AE-001,10\n".getBytes()
        );
        ImportPreviewView preview = new ImportPreviewView();
        preview.setImportBatchId(56001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<PreviewImportCommand> captor = ArgumentCaptor.forClass(PreviewImportCommand.class);
        when(importService.preview(captor.capture())).thenReturn(preview);

        ImportPreviewView result = controller.previewImport(file, request);

        assertEquals(56001L, result.getImportBatchId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals("历史在途.csv", captor.getValue().getFileName());
        assertEquals(context, captor.getValue().getAccessContext());
    }

    @Test
    void shouldDownloadImportTemplateBehindInTransitGoodsCapability() {
        BusinessAccessContext context = context();
        byte[] template = new byte[]{1, 2, 3};

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(importService.buildTemplate()).thenReturn(template);

        ResponseEntity<byte[]> response = controller.downloadImportTemplate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(template, response.getBody());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.getHeaders().getContentType().toString());
        Assertions.assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("in-transit-goods-import-template.xlsx"));
    }

    @Test
    void shouldOverwriteOwnerOperatorAndRouteImportIdWhenConfirmingImport() {
        BusinessAccessContext context = context();
        ImportConfirmView confirmView = new ImportConfirmView();
        confirmView.setImportBatchId(56001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<ConfirmImportCommand> captor = ArgumentCaptor.forClass(ConfirmImportCommand.class);
        when(importService.confirm(captor.capture())).thenReturn(confirmView);

        ImportConfirmView result = controller.confirmImport(56001L, request);

        assertEquals(56001L, result.getImportBatchId());
        assertEquals(56001L, captor.getValue().getImportBatchId());
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
