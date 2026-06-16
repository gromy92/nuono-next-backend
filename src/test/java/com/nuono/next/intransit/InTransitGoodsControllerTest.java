package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderCommand;
import com.nuono.next.intransit.InTransitBatchCommands.ConfirmImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.PreviewImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanOrder;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanOrderBox;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncLine;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.nuono.next.intransit.InTransitBatchRecords.BatchListView;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportConfirmView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewView;
import com.nuono.next.intransit.InTransitBatchRecords.LineListView;
import com.nuono.next.intransit.InTransitBatchRecords.LineView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeListView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeView;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.SaveRateCardVersionCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightSyncView;
import com.nuono.next.intransit.InTransitFreightCostRecords.BatchFreightCostView;
import com.nuono.next.intransit.InTransitFreightCostRecords.ForwarderFreightComparisonView;
import com.nuono.next.intransit.InTransitFreightCostRecords.FreightStatisticsView;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionView;
import com.nuono.next.intransit.InTransitFreightCostRecords.SkuFreightCostHistoryView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncCommitView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncPlanView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
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
                service,
                batchService,
                importService,
                pluginSyncService,
                freightCostService,
                businessAccessResolver,
                accessScopeService
        );
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
    void shouldLogPluginExtensionVersionForSyncAndEtPlanRequests() {
        Logger logger = (Logger) LoggerFactory.getLogger(InTransitGoodsController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            BusinessAccessContext context = context();
            when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                    .thenReturn(context);
            when(request.getHeader("X-Nuono-Extension-Version")).thenReturn("0.0.99");
            when(request.getHeader("X-Nuono-Extension-Build")).thenReturn("20260616-et-versioned-spec-only-guard");

            PluginSyncBatch batch = new PluginSyncBatch();
            batch.setBatchNo("F2604304851631");
            PluginSyncPackage pluginPackage = new PluginSyncPackage();
            pluginPackage.setBoxNo("X26043050345");
            PluginSyncLine line = new PluginSyncLine();
            line.setPsku("SGGRB148");
            pluginPackage.setLines(java.util.List.of(line));
            batch.setPackages(java.util.List.of(pluginPackage));
            PluginSyncCommand command = new PluginSyncCommand();
            command.setSourceSystem("ET");
            command.setBatches(java.util.List.of(batch));
            when(pluginSyncService.preview(any(PluginSyncCommand.class))).thenReturn(new PluginSyncPreviewView());

            controller.previewPluginSync(command, request);

            EtBoxSyncPlanOrderBox box = new EtBoxSyncPlanOrderBox();
            box.setBoxId("X26043050345");
            EtBoxSyncPlanOrder order = new EtBoxSyncPlanOrder();
            order.setShipOrderId("F2604304851631");
            order.setBoxes(java.util.List.of(box));
            EtBoxSyncPlanCommand planCommand = new EtBoxSyncPlanCommand();
            planCommand.setSourceSystem("ET");
            planCommand.setShipOrders(java.util.List.of(order));
            when(pluginSyncService.planEtBoxSync(any(EtBoxSyncPlanCommand.class))).thenReturn(new EtBoxSyncPlanView());

            controller.planEtBoxSync(planCommand, request);

            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            Assertions.assertTrue(logged.contains("extensionVersion=0.0.99"), logged);
            Assertions.assertTrue(logged.contains("extensionBuild=20260616-et-versioned-spec-only-guard"), logged);
            Assertions.assertTrue(logged.contains("inTransit plugin-sync preview received"), logged);
            Assertions.assertTrue(logged.contains("inTransit plugin-sync et box-sync-plan received"), logged);
        } finally {
            logger.detachAppender(appender);
        }
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
    void shouldReadBatchFreightCostsAfterBatchAccessCheck() {
        BusinessAccessContext context = context();
        BatchView scopedBatch = scopedBatch();
        BatchFreightCostView costView = new BatchFreightCostView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        when(freightCostService.batchActualCosts(10002L, 53001L)).thenReturn(costView);

        BatchFreightCostView result = controller.batchFreightCosts(53001L, request);

        assertEquals(costView, result);
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldReadFreightStatisticsUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        FreightStatisticsView statisticsView = new FreightStatisticsView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(freightCostService.statistics(eq(10002L), eq(null), eq(null), eq(30700002L)))
                .thenReturn(statisticsView);

        FreightStatisticsView result = controller.freightStatistics(null, null, 30700002L, request);

        assertEquals(statisticsView, result);
    }

    @Test
    void shouldReadSkuFreightHistoryUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        SkuFreightCostHistoryView historyView = new SkuFreightCostHistoryView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(freightCostService.skuHistory(eq(10002L), eq("SGGRB148"), eq("SA"), eq(null), eq(null)))
                .thenReturn(historyView);

        SkuFreightCostHistoryView result = controller.skuFreightHistory("SGGRB148", "SA", null, null, request);

        assertEquals(historyView, result);
    }

    @Test
    void shouldReadForwarderComparisonUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        ForwarderFreightComparisonView comparisonView = new ForwarderFreightComparisonView();

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        when(freightCostService.forwarderComparison(eq(10002L), eq("SGGRB148"), eq("SA"), eq("SEA"), eq("RUH")))
                .thenReturn(comparisonView);

        ForwarderFreightComparisonView result = controller.forwarderFreightComparison(
                "SGGRB148",
                "SA",
                "SEA",
                "RUH",
                request
        );

        assertEquals(comparisonView, result);
    }

    @Test
    void shouldOverwriteOwnerOperatorWhenSavingRateCardVersion() {
        BusinessAccessContext context = context();
        SaveRateCardVersionCommand command = new SaveRateCardVersionCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setForwarderCode("YITONG");
        RateCardVersionView saved = new RateCardVersionView();
        saved.setRateCardVersionId(64001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<SaveRateCardVersionCommand> captor = ArgumentCaptor.forClass(SaveRateCardVersionCommand.class);
        when(freightCostService.saveRateCardVersion(captor.capture())).thenReturn(saved);

        RateCardVersionView result = controller.saveFreightRateCardVersion(command, request);

        assertEquals(64001L, result.getRateCardVersionId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
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
