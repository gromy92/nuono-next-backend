package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SavePackageCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.NodeRow;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncLine;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncNode;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncSourceBatchExpectation;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanOrder;
import com.nuono.next.intransit.InTransitPluginSyncCommands.EtBoxSyncPlanOrderBox;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncPlanView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncStateRow;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncCommitView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitPluginSyncServiceTest {

    @Mock
    private InTransitGoodsMapper mapper;

    @Mock
    private InTransitBatchService batchService;

    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;

    private InTransitPluginSyncService service;

    @BeforeEach
    void setUp() {
        service = new InTransitPluginSyncService(mapper, batchService, accessScopeService);
    }

    @Test
    void shouldPreviewPluginSyncAndDetectCreateUpdateWork() {
        PluginSyncCommand command = sampleCommand();
        BatchRow existingBatch = batch(53001L);
        LineRow existingLine = line(54001L);
        NodeRow existingNode = node(55001L);
        when(mapper.selectBatchByReferenceNo(10002L, "XGGEKSA04075")).thenReturn(existingBatch);
        when(mapper.selectLineByBoxNoAndPsku(10002L, 53001L, "NO1-1", "SGGRB219")).thenReturn(existingLine);
        when(mapper.selectNodeByStatusDescriptionAndHappenedAt(
                10002L,
                53001L,
                "departed_origin",
                LocalDateTime.parse("2026-06-02T12:00:00"),
                "发往海外"
        )).thenReturn(existingNode);

        PluginSyncPreviewView result = service.preview(command);

        assertEquals(true, result.isCommittable());
        assertEquals("CHIC", result.getSourceSystem());
        assertEquals("启客", result.getForwarderName());
        assertEquals(1, result.getBatchCount());
        assertEquals(1, result.getPackageCount());
        assertEquals(2, result.getLineCount());
        assertEquals(1, result.getNodeCount());
        assertEquals(0, result.getNewBatchCount());
        assertEquals(1, result.getUpdateBatchCount());
        assertEquals(1, result.getNewLineCount());
        assertEquals(1, result.getUpdateLineCount());
        assertEquals(0, result.getNewNodeCount());
        assertEquals(1, result.getSkippedNodeCount());
    }

    @Test
    void shouldCommitPluginSyncThroughBatchLineAndNodeServices() {
        PluginSyncCommand command = sampleCommand();
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        PluginSyncCommitView result = service.commit(command);

        assertEquals(true, result.isCommitted());
        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals(10002L, batchCaptor.getValue().getOwnerUserId());
        assertEquals(90001L, batchCaptor.getValue().getOperatorUserId());
        assertEquals("启客", batchCaptor.getValue().getRawForwarderName());
        assertEquals("XGGEKSA04075", batchCaptor.getValue().getBatchReferenceNo());
        assertEquals("CSAISHIPAGS260531220621GALG", batchCaptor.getValue().getExternalShipmentNo());
        assertEquals(LocalDateTime.parse("2026-05-29T16:05:45"), batchCaptor.getValue().getSourceCreatedAt());
        assertEquals(LocalDateTime.parse("2026-05-31T10:00:00"), batchCaptor.getValue().getEstimatedDepartureAt());
        assertEquals(LocalDateTime.parse("2026-06-01T11:00:00"), batchCaptor.getValue().getEstimatedArrivalAt());
        assertEquals("2026-06-04 16:00-18:00", batchCaptor.getValue().getDeliveryAppointmentText());
        assertEquals("AIR", batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());

        ArgumentCaptor<SaveLineCommand> lineCaptor = ArgumentCaptor.forClass(SaveLineCommand.class);
        verify(batchService, org.mockito.Mockito.times(2)).saveLine(lineCaptor.capture());
        SaveLineCommand firstLine = lineCaptor.getAllValues().get(0);
        assertEquals(53010L, firstLine.getBatchId());
        assertEquals("NO1-1", firstLine.getBoxNo());
        assertEquals("X25090553672", firstLine.getExternalBoxNo());
        assertEquals(new BigDecimal("12.500000"), firstLine.getPackageWeightKg());
        assertEquals(new BigDecimal("45.000000"), firstLine.getPackageLengthCm());
        assertEquals(new BigDecimal("35.000000"), firstLine.getPackageWidthCm());
        assertEquals(new BigDecimal("30.000000"), firstLine.getPackageHeightCm());
        assertEquals(new BigDecimal("0.047250"), firstLine.getPackageVolumeCbm());
        assertEquals(new BigDecimal("7.900000"), firstLine.getPackageVolumeWeightKg());
        assertEquals(new BigDecimal("12.500000"), firstLine.getPackageChargeableWeightKg());
        assertEquals(new BigDecimal("12.800000"), firstLine.getMeasuredWeightKg());
        assertEquals(new BigDecimal("46.000000"), firstLine.getMeasuredLengthCm());
        assertEquals(new BigDecimal("36.000000"), firstLine.getMeasuredWidthCm());
        assertEquals(new BigDecimal("31.000000"), firstLine.getMeasuredHeightCm());
        assertEquals(new BigDecimal("0.051336"), firstLine.getMeasuredVolumeCbm());
        assertEquals("已封箱", firstLine.getPackageStatus());
        assertEquals("发往海外", firstLine.getLogisticsStatus());
        assertEquals("SGGRB219", firstLine.getPsku());
        assertNull(firstLine.getCartonWeightKg());
        assertNull(firstLine.getCartonVolumeCbm());

        ArgumentCaptor<SaveNodeCommand> nodeCaptor = ArgumentCaptor.forClass(SaveNodeCommand.class);
        verify(batchService).saveNode(nodeCaptor.capture());
        assertEquals(53010L, nodeCaptor.getValue().getBatchId());
        assertEquals("departed_origin", nodeCaptor.getValue().getNodeStatus());
        assertEquals(LocalDateTime.parse("2026-06-02T12:00:00"), nodeCaptor.getValue().getNodeHappenedAt());
        assertEquals("发往海外", nodeCaptor.getValue().getDescription());
    }

    @Test
    void shouldDowngradeIncompleteNewPluginBatchToDraftInsteadOfTracking() {
        PluginSyncCommand command = sampleEtCommand();
        PluginSyncBatch batch = command.getBatches().get(0);
        batch.setTransportMode(null);
        batch.setTargetWarehouseName(null);
        batch.setDestination("RUH");
        batch.setBatchStatus("in_transit");
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        service.commit(command);

        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("draft", batchCaptor.getValue().getBatchStatus());
        assertNull(batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());
        assertNull(batchCaptor.getValue().getTargetWarehouseName());
    }

    @Test
    void shouldApplyYiteRouteFallbackBeforePreviewAndCommitValidation() {
        PluginSyncCommand command = sampleYiteCommand();
        PluginSyncBatch batch = command.getBatches().get(0);
        batch.setBatchNo("YT2606133690");
        batch.setTransportMode(null);
        batch.setDestination(null);
        batch.setTargetWarehouseName(null);
        PluginSyncPackage itemPackage = batch.getPackages().get(0);
        itemPackage.setWeightKg(new BigDecimal("12.650000"));
        itemPackage.setLengthCm(new BigDecimal("60.000000"));
        itemPackage.setWidthCm(new BigDecimal("40.000000"));
        itemPackage.setHeightCm(new BigDecimal("35.000000"));
        itemPackage.setVolumeCbm(new BigDecimal("0.084000"));
        itemPackage.setVolumeWeightKg(new BigDecimal("14.000000"));
        itemPackage.setChargeableWeightKg(new BigDecimal("14.000000"));

        PluginSyncPreviewView preview = service.preview(command);

        assertEquals(true, preview.isCommittable());
        assertTrue(preview.getIssues().stream().noneMatch(issue -> "error".equals(issue.getLevel())));

        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53030L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        service.commit(command);

        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("in_transit", batchCaptor.getValue().getBatchStatus());
        assertEquals("SEA", batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());
        assertEquals("FBN-RUH", batchCaptor.getValue().getTargetWarehouseName());
    }

    @Test
    void shouldKeepChicBatchDraftWhenPackagesWithGoodsLinesMissChargeableWeight() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncBatch batch = command.getBatches().get(0);
        batch.setBatchNo("XGGEKSA04078");
        batch.setTransportMode(null);
        batch.setDestination(null);
        batch.setTargetWarehouseName(null);
        batch.setBatchStatus("in_transit");
        PluginSyncPackage itemPackage = batch.getPackages().get(0);
        itemPackage.setWeightKg(null);
        itemPackage.setLengthCm(null);
        itemPackage.setWidthCm(null);
        itemPackage.setHeightCm(null);
        itemPackage.setVolumeCbm(null);
        itemPackage.setVolumeWeightKg(null);
        itemPackage.setChargeableWeightKg(null);

        PluginSyncPreviewView preview = service.preview(command);

        assertEquals(true, preview.isCommittable());
        assertTrue(preview.getIssues().stream().anyMatch(issue ->
                "package.chargeableWeightKg".equals(issue.getField())
                        && "warning".equals(issue.getLevel())
                        && "XGGEKSA04078".equals(issue.getBatchNo())
        ));

        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53078L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        PluginSyncCommitView result = service.commit(command);

        assertTrue(result.getIssues().stream().anyMatch(issue ->
                "package.chargeableWeightKg".equals(issue.getField())
                        && "warning".equals(issue.getLevel())
                        && "XGGEKSA04078".equals(issue.getBatchNo())
        ));
        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("draft", batchCaptor.getValue().getBatchStatus());
        assertEquals("AIR", batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());
        assertEquals("FBN-RUH", batchCaptor.getValue().getTargetWarehouseName());
    }

    @Test
    void shouldKeepExistingTrackingFieldsWhenPluginPayloadOmitsThem() {
        PluginSyncCommand command = sampleEtCommand();
        PluginSyncBatch batch = command.getBatches().get(0);
        batch.setTransportMode(null);
        batch.setTargetWarehouseName(null);
        batch.setBatchStatus("in_transit");
        batch.getPackages().get(0).setChargeableWeightKg(new BigDecimal("9.500000"));
        BatchRow existingBatch = batch(53010L);
        existingBatch.setBatchReferenceNo("F2604304851631");
        existingBatch.setTransportMode("SEA");
        existingBatch.setTargetStoreCode("RUH");
        existingBatch.setTargetWarehouseName("ETRUH01整箱仓");
        when(mapper.selectBatchByReferenceNo(10002L, "F2604304851631")).thenReturn(existingBatch);
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        service.commit(command);

        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("in_transit", batchCaptor.getValue().getBatchStatus());
        assertEquals("SEA", batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());
        assertEquals("ETRUH01整箱仓", batchCaptor.getValue().getTargetWarehouseName());
    }

    @Test
    void shouldReconcileExistingBatchBoxesAndLinesBeforeSavingPluginDetails() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
        expectation.setBatchNo("XGGEKSA04075");
        expectation.setBoxNum(1);
        expectation.setTotalQuantity(50);
        command.setSourceBatchExpectations(List.of(expectation));
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        service.commit(command);

        verify(batchService).reconcileSyncedDetails(
                10002L,
                90001L,
                53010L,
                List.of("NO1-1"),
                List.of("NO1-1\nSGGRB219", "NO1-1\nPAPERSAYSB011")
        );
    }

    @Test
    void shouldNotReconcilePartialPluginPayloadWithoutSourceExpectation() {
        PluginSyncCommand command = sampleCommand();
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        service.commit(command);

        verify(batchService, never()).reconcileSyncedDetails(any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotReconcileWhenPayloadDoesNotCoverSourceExpectation() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
        expectation.setBatchNo("XGGEKSA04075");
        expectation.setBoxNum(2);
        expectation.setTotalQuantity(50);
        command.setSourceBatchExpectations(List.of(expectation));

        PluginSyncPreviewView preview = service.preview(command);

        assertEquals(false, preview.isCommittable());
        assertTrue(preview.getIssues().stream().anyMatch(issue ->
                "sourceBatchExpectations.boxNum".equals(issue.getField())
        ));
    }

    @Test
    void shouldDerivePackageChargeableWeightWhenPluginOmitsIt() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncPackage itemPackage = command.getBatches().get(0).getPackages().get(0);
        itemPackage.setWeightKg(new BigDecimal("4.200000"));
        itemPackage.setVolumeWeightKg(new BigDecimal("7.300000"));
        itemPackage.setChargeableWeightKg(null);
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        service.commit(command);

        ArgumentCaptor<SaveLineCommand> lineCaptor = ArgumentCaptor.forClass(SaveLineCommand.class);
        verify(batchService, org.mockito.Mockito.times(2)).saveLine(lineCaptor.capture());
        assertEquals(new BigDecimal("7.300000"), lineCaptor.getAllValues().get(0).getPackageChargeableWeightKg());
    }

    @Test
    void shouldRejectCommitWhenPreviewContainsErrors() {
        PluginSyncCommand command = sampleCommand();
        command.getBatches().get(0).getPackages().get(0).getLines().get(0).setPsku("");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.commit(command));

        assertEquals("插件同步预览存在错误，不能落库。", exception.getMessage());
        verify(batchService, never()).saveBatch(any(SaveBatchCommand.class));
        verify(batchService, never()).saveLine(any(SaveLineCommand.class));
    }

    @Test
    void shouldKeepPreviewCommittableWhenOnlyPluginMetadataIsDirty() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncBatch batch = command.getBatches().get(0);
        batch.setTransportMode("3");
        batch.setDestination("Saudi Arabia");
        batch.setEstimatedArrivalAt("待定");
        batch.getNodes().get(0).setNodeStatus("启客自定义节点");
        batch.getNodes().get(0).setNodeTime("待确认");
        batch.getNodes().get(0).setDescription("启客自定义节点");

        PluginSyncPreviewView result = service.preview(command);

        assertEquals(true, result.isCommittable());
        assertEquals(4, result.getIssues().size());
        assertEquals(List.of("transportMode", "estimatedArrivalAt", "nodeStatus", "nodeTime"),
                result.getIssues().stream()
                        .map(InTransitPluginSyncRecords.PluginSyncIssueView::getField)
                        .collect(Collectors.toList()));
        assertEquals("warning", result.getIssues().get(0).getLevel());
        assertEquals("XGGEKSA04075", result.getIssues().get(0).getBatchNo());
        assertTrue(result.getIssues().get(0).getMessage().contains("3"));
    }

    @Test
    void shouldWarnWhenPluginBatchHasNoPackages() {
        PluginSyncCommand command = sampleCommand();
        command.getBatches().get(0).setPackages(List.of());

        PluginSyncPreviewView result = service.preview(command);

        assertEquals(true, result.isCommittable());
        assertEquals(0, result.getPackageCount());
        assertEquals(0, result.getLineCount());
        assertEquals(1, result.getIssues().size());
        assertEquals("warning", result.getIssues().get(0).getLevel());
        assertEquals("XGGEKSA04075", result.getIssues().get(0).getBatchNo());
        assertEquals("packages", result.getIssues().get(0).getField());
        assertTrue(result.getIssues().get(0).getMessage().contains("只会更新批次层"));
        assertEquals(0, result.getBatches().get(0).getPackageCount());
        assertEquals(0, result.getBatches().get(0).getLineCount());
    }

    @Test
    void shouldWarnWhenPluginBatchHasPackagesButNoSkuLines() {
        PluginSyncCommand command = sampleCommand();
        command.getBatches().get(0).getPackages().get(0).setLines(List.of());

        PluginSyncPreviewView result = service.preview(command);

        assertEquals(true, result.isCommittable());
        assertEquals(1, result.getPackageCount());
        assertEquals(0, result.getLineCount());
        assertEquals(1, result.getIssues().size());
        assertEquals("warning", result.getIssues().get(0).getLevel());
        assertEquals("XGGEKSA04075", result.getIssues().get(0).getBatchNo());
        assertEquals("packages.lines", result.getIssues().get(0).getField());
        assertTrue(result.getIssues().get(0).getMessage().contains("会更新批次和箱子信息"));
        assertEquals(1, result.getBatches().get(0).getPackageCount());
        assertEquals(0, result.getBatches().get(0).getLineCount());
    }

    @Test
    void shouldCommitPackageSnapshotWhenPackageHasNoSkuLines() {
        PluginSyncCommand command = sampleCommand();
        command.getBatches().get(0).getPackages().get(0).setLines(List.of());
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        PluginSyncCommitView result = service.commit(command);

        assertEquals(true, result.isCommitted());
        ArgumentCaptor<SavePackageCommand> packageCaptor = ArgumentCaptor.forClass(SavePackageCommand.class);
        verify(batchService).savePackage(packageCaptor.capture());
        SavePackageCommand itemPackage = packageCaptor.getValue();
        assertEquals(10002L, itemPackage.getOwnerUserId());
        assertEquals(90001L, itemPackage.getOperatorUserId());
        assertEquals(53010L, itemPackage.getBatchId());
        assertEquals("NO1-1", itemPackage.getBoxNo());
        assertEquals("X25090553672", itemPackage.getExternalBoxNo());
        assertEquals(new BigDecimal("12.500000"), itemPackage.getPackageWeightKg());
        assertEquals(new BigDecimal("45.000000"), itemPackage.getPackageLengthCm());
        assertEquals(new BigDecimal("35.000000"), itemPackage.getPackageWidthCm());
        assertEquals(new BigDecimal("30.000000"), itemPackage.getPackageHeightCm());
        assertEquals(new BigDecimal("0.047250"), itemPackage.getPackageVolumeCbm());
        assertEquals(new BigDecimal("7.900000"), itemPackage.getPackageVolumeWeightKg());
        assertEquals(new BigDecimal("12.500000"), itemPackage.getPackageChargeableWeightKg());
        verify(batchService, never()).saveLine(any(SaveLineCommand.class));
        verify(batchService, never()).reconcileSyncedDetails(any(), any(), any(), any(), any());
    }

    @Test
    void shouldCommitPackageSnapshotBeforeSkuLinesWhenPackageHasSpecs() {
        PluginSyncCommand command = sampleCommand();
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        PluginSyncCommitView result = service.commit(command);

        assertEquals(true, result.isCommitted());
        ArgumentCaptor<SavePackageCommand> packageCaptor = ArgumentCaptor.forClass(SavePackageCommand.class);
        verify(batchService).savePackage(packageCaptor.capture());
        SavePackageCommand itemPackage = packageCaptor.getValue();
        assertEquals("NO1-1", itemPackage.getBoxNo());
        assertEquals("X25090553672", itemPackage.getExternalBoxNo());
        assertEquals(new BigDecimal("12.500000"), itemPackage.getPackageWeightKg());
        assertEquals(new BigDecimal("45.000000"), itemPackage.getPackageLengthCm());
        assertEquals(new BigDecimal("35.000000"), itemPackage.getPackageWidthCm());
        assertEquals(new BigDecimal("30.000000"), itemPackage.getPackageHeightCm());
        assertEquals(new BigDecimal("12.500000"), itemPackage.getPackageChargeableWeightKg());
        assertEquals(false, itemPackage.isPackageSnapshotAuthoritative());
        verify(batchService, times(2)).saveLine(any(SaveLineCommand.class));
    }

    @Test
    void shouldRejectDuplicateBatchNoInSamePluginPayload() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncBatch duplicate = sampleCommand().getBatches().get(0);
        duplicate.getPackages().get(0).setBoxNo("NO1-2");
        duplicate.getPackages().get(0).getLines().get(0).setPsku("SGGRB220");
        duplicate.getPackages().get(0).getLines().get(1).setPsku("PAPERSAYSB012");
        command.setBatches(List.of(command.getBatches().get(0), duplicate));

        PluginSyncPreviewView result = service.preview(command);

        assertEquals(false, result.isCommittable());
        assertTrue(result.getIssues().stream().anyMatch(issue ->
                "batchNo".equals(issue.getField()) && issue.getMessage().contains("同一 payload 内批次号不能重复")
        ));
    }

    @Test
    void shouldSkipCancelledSourceRowsBeforeDuplicateValidationAndCommit() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncBatch active = command.getBatches().get(0);
        active.setExternalShipmentNo("ACTIVE-SHIPMENT");
        PluginSyncBatch cancelled = sampleCommand().getBatches().get(0);
        cancelled.setStatus("13");
        cancelled.setExternalShipmentNo("CANCELLED-SHIPMENT");
        command.setBatches(List.of(cancelled, active));
        PluginSyncSourceBatchExpectation firstExpectation = new PluginSyncSourceBatchExpectation();
        firstExpectation.setBatchNo("XGGEKSA04075");
        firstExpectation.setBoxNum(16);
        firstExpectation.setTotalQuantity(916);
        PluginSyncSourceBatchExpectation secondExpectation = new PluginSyncSourceBatchExpectation();
        secondExpectation.setBatchNo("XGGEKSA04075");
        secondExpectation.setBoxNum(1);
        secondExpectation.setTotalQuantity(120);
        command.setSourceBatchExpectations(List.of(firstExpectation, secondExpectation));

        PluginSyncPreviewView preview = service.preview(command);

        assertEquals(true, preview.isCommittable());
        assertEquals(1, preview.getBatchCount());
        assertTrue(preview.getIssues().stream().noneMatch(issue ->
                "batchNo".equals(issue.getField()) && issue.getMessage().contains("重复")
        ));

        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        service.commit(command);

        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("ACTIVE-SHIPMENT", batchCaptor.getValue().getExternalShipmentNo());
    }

    @Test
    void shouldRejectBoxNoEqualToBatchNoForEverySourceSystem() {
        PluginSyncCommand command = sampleCommand();
        command.setSourceSystem("ET");
        command.setForwarderName("易通");
        command.getBatches().get(0).getPackages().get(0).setBoxNo("XGGEKSA04075");

        PluginSyncPreviewView result = service.preview(command);

        assertEquals(false, result.isCommittable());
        assertTrue(result.getIssues().stream().anyMatch(issue ->
                "boxNo".equals(issue.getField()) && issue.getMessage().contains("箱号不能等于批次号")
        ));
    }

    @Test
    void shouldRejectWhenSourceBatchExpectationDoesNotMatchDetails() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
        expectation.setBatchNo("XGGEKSA04075");
        expectation.setBoxNum(2);
        expectation.setTotalQuantity(916);
        command.setSourceBatchExpectations(List.of(expectation));

        PluginSyncPreviewView result = service.preview(command);

        assertEquals(false, result.isCommittable());
        assertTrue(result.getIssues().stream().anyMatch(issue ->
                "sourceBatchExpectations.boxNum".equals(issue.getField())
                        && issue.getMessage().contains("来源列表箱数 2")
        ));
        assertTrue(result.getIssues().stream().anyMatch(issue ->
                "sourceBatchExpectations.totalQuantity".equals(issue.getField())
                        && issue.getMessage().contains("来源列表商品总数 916")
        ));
    }

    @Test
    void shouldCommitCoreDataWhenOnlyOptionalPluginMetadataIsDirty() {
        PluginSyncCommand command = sampleCommand();
        PluginSyncBatch batch = command.getBatches().get(0);
        batch.setTransportMode("3");
        batch.setDestination("Saudi Arabia");
        batch.setEstimatedArrivalAt("待定");
        batch.getNodes().get(0).setNodeStatus("启客自定义节点");
        batch.getNodes().get(0).setNodeTime("待确认");
        batch.getNodes().get(0).setDescription("启客自定义节点");
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53010L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        PluginSyncCommitView result = service.commit(command);

        assertEquals(true, result.isCommitted());
        assertEquals(true, result.isCommittable());
        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertNull(batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());
        assertNull(batchCaptor.getValue().getEstimatedArrivalAt());
        verify(batchService, never()).saveNode(any(SaveNodeCommand.class));
    }

    @Test
    void shouldSaveEtPayloadWithoutPackageSpecsOrLogisticsNodesAsDraft() {
        PluginSyncCommand command = sampleEtCommand();

        PluginSyncPreviewView preview = service.preview(command);

        assertEquals(true, preview.isCommittable());
        assertEquals("ET", preview.getSourceSystem());
        assertEquals("易通", preview.getForwarderName());
        assertEquals(1, preview.getBatchCount());
        assertEquals(1, preview.getPackageCount());
        assertEquals(1, preview.getLineCount());
        assertEquals(0, preview.getNodeCount());
        assertTrue(preview.getIssues().stream().anyMatch(issue ->
                "package.chargeableWeightKg".equals(issue.getField())
                        && "warning".equals(issue.getLevel())
        ));

        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53020L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        PluginSyncCommitView result = service.commit(command);

        assertEquals(true, result.isCommitted());
        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("易通", batchCaptor.getValue().getRawForwarderName());
        assertEquals("F2604304851631", batchCaptor.getValue().getBatchReferenceNo());
        assertEquals("draft", batchCaptor.getValue().getBatchStatus());
        assertEquals("SEA", batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());
        assertEquals("ETRUH01整箱仓", batchCaptor.getValue().getTargetWarehouseName());
        assertEquals(LocalDateTime.parse("2026-04-30T10:14:51"), batchCaptor.getValue().getSourceCreatedAt());
        assertEquals(LocalDateTime.parse("2026-05-22T00:00:00"), batchCaptor.getValue().getEstimatedDepartureAt());
        assertEquals(LocalDateTime.parse("2026-06-15T00:00:00"), batchCaptor.getValue().getEstimatedArrivalAt());
        assertEquals(LocalDate.parse("2026-06-15"), batchCaptor.getValue().getEtaDate());

        ArgumentCaptor<SaveLineCommand> lineCaptor = ArgumentCaptor.forClass(SaveLineCommand.class);
        verify(batchService).saveLine(lineCaptor.capture());
        SaveLineCommand line = lineCaptor.getValue();
        assertEquals(53020L, line.getBatchId());
        assertEquals("24-1", line.getBoxNo());
        assertEquals("X26043047357", line.getExternalBoxNo());
        assertEquals("PAPERSAYSB293", line.getPsku());
        assertEquals("PAPERSAYSB293", line.getSku());
        assertEquals("PAPERSAYSB293", line.getMsku());
        assertEquals("粉盒马克笔24支48色", line.getProductName());
        assertEquals(48, line.getShippedQuantity());
        assertEquals(0, line.getReceivedQuantity());
        assertEquals("已完结", line.getPackageStatus());
        assertNull(line.getPackageLengthCm());
        assertNull(line.getPackageWidthCm());
        assertNull(line.getPackageHeightCm());
        assertNull(line.getPackageWeightKg());
        verify(batchService, never()).saveNode(any(SaveNodeCommand.class));
    }

    @Test
    void shouldPlanEtBoxDetailCallsFromBackendCompleteness() {
        EtBoxSyncPlanCommand command = sampleEtPlanCommand(false);
        BatchRow batch = batch(53020L);
        batch.setBatchReferenceNo("F2604304851631");
        when(mapper.selectBatchByReferenceNo(10002L, "F2604304851631")).thenReturn(batch);
        when(mapper.selectEtBoxSyncState(10002L, 53020L, "X26043047357", "24-1"))
                .thenReturn(etState(true, true, 1));
        when(mapper.selectEtBoxSyncState(10002L, 53020L, "X26043047358", "24-2"))
                .thenReturn(etState(true, false, 1));
        when(mapper.selectEtBoxSyncState(10002L, 53020L, "X26043047359", "24-3"))
                .thenReturn(etState(true, true, 0));

        EtBoxSyncPlanView result = service.planEtBoxSync(command);

        assertEquals("ET", result.getSourceSystem());
        assertEquals("易通", result.getForwarderName());
        assertEquals(1, result.getOrderCount());
        assertEquals(4, result.getBoxCount());
        assertEquals(1, result.getSkipCount());
        assertEquals(1, result.getFetchBoxSpecCount());
        assertEquals(1, result.getFetchBoxLinesCount());
        assertEquals(1, result.getFetchAllCount());
        assertEquals(List.of("SKIP", "FETCH_BOX_SPEC", "FETCH_BOX_LINES", "FETCH_ALL"),
                result.getBoxes().stream()
                        .map(InTransitPluginSyncRecords.EtBoxSyncPlanBoxView::getAction)
                        .collect(Collectors.toList()));
        assertEquals(List.of("known_complete", "missing_package", "missing_lines", "new_box"),
                result.getBoxes().stream()
                        .map(InTransitPluginSyncRecords.EtBoxSyncPlanBoxView::getReason)
                        .collect(Collectors.toList()));
    }

    @Test
    void shouldForceFullEtBoxSyncPlanWhenRequested() {
        EtBoxSyncPlanCommand command = sampleEtPlanCommand(true);

        EtBoxSyncPlanView result = service.planEtBoxSync(command);

        assertEquals(true, result.isForceFullSync());
        assertEquals(4, result.getFetchAllCount());
        assertEquals(0, result.getSkipCount());
        assertTrue(result.getBoxes().stream().allMatch(box -> "FETCH_ALL".equals(box.getAction())));
        assertTrue(result.getBoxes().stream().allMatch(box -> "force_full_sync".equals(box.getReason())));
    }

    @Test
    void shouldMapYiteSourceTextsInBackendWithoutPluginBusinessNormalization() {
        PluginSyncCommand command = sampleYiteCommand();

        PluginSyncPreviewView preview = service.preview(command);

        assertEquals(true, preview.isCommittable());
        assertEquals("YITE", preview.getSourceSystem());
        assertEquals("义特", preview.getForwarderName());
        assertEquals(1, preview.getBatchCount());
        assertEquals(1, preview.getPackageCount());
        assertEquals(1, preview.getLineCount());
        assertEquals(4, preview.getNodeCount());
        assertTrue(preview.getIssues().isEmpty());

        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53030L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        PluginSyncCommitView result = service.commit(command);

        assertEquals(true, result.isCommitted());
        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("义特", batchCaptor.getValue().getRawForwarderName());
        assertEquals("YT2605306913", batchCaptor.getValue().getBatchReferenceNo());
        assertEquals("6a1979a93a8e5260c465758b", batchCaptor.getValue().getExternalShipmentNo());
        assertEquals("in_transit", batchCaptor.getValue().getBatchStatus());
        assertEquals("SEA", batchCaptor.getValue().getTransportMode());
        assertEquals("RUH", batchCaptor.getValue().getTargetStoreCode());
        assertEquals("noon-FBN", batchCaptor.getValue().getTargetWarehouseName());

        ArgumentCaptor<SaveNodeCommand> nodeCaptor = ArgumentCaptor.forClass(SaveNodeCommand.class);
        verify(batchService, org.mockito.Mockito.times(4)).saveNode(nodeCaptor.capture());
        assertEquals(List.of("in_transit", "departed_origin", "handed_to_forwarder", "created"),
                nodeCaptor.getAllValues().stream()
                        .map(SaveNodeCommand::getNodeStatus)
                        .collect(Collectors.toList()));
    }

    private PluginSyncCommand sampleCommand() {
        PluginSyncLine line = new PluginSyncLine();
        line.setPsku("SGGRB219");
        line.setSku("SGGRB219");
        line.setProductName("高弹力头巾美容帽");
        line.setShippedQuantity(30);
        line.setReceivedQuantity(0);

        PluginSyncLine secondLine = new PluginSyncLine();
        secondLine.setPsku("PAPERSAYSB011");
        secondLine.setSku("PAPERSAYSB011");
        secondLine.setProductName("11-nfc215-10");
        secondLine.setShippedQuantity(20);
        secondLine.setReceivedQuantity(0);

        PluginSyncPackage itemPackage = new PluginSyncPackage();
        itemPackage.setBoxNo("NO1-1");
        itemPackage.setExternalBoxNo("X25090553672");
        itemPackage.setWeightKg(new BigDecimal("12.500000"));
        itemPackage.setLengthCm(new BigDecimal("45.000000"));
        itemPackage.setWidthCm(new BigDecimal("35.000000"));
        itemPackage.setHeightCm(new BigDecimal("30.000000"));
        itemPackage.setVolumeCbm(new BigDecimal("0.047250"));
        itemPackage.setVolumeWeightKg(new BigDecimal("7.900000"));
        itemPackage.setChargeableWeightKg(new BigDecimal("12.500000"));
        itemPackage.setMeasuredWeightKg(new BigDecimal("12.800000"));
        itemPackage.setMeasuredLengthCm(new BigDecimal("46.000000"));
        itemPackage.setMeasuredWidthCm(new BigDecimal("36.000000"));
        itemPackage.setMeasuredHeightCm(new BigDecimal("31.000000"));
        itemPackage.setMeasuredVolumeCbm(new BigDecimal("0.051336"));
        itemPackage.setPackageStatus("已封箱");
        itemPackage.setLogisticsStatus("发往海外");
        itemPackage.setLines(List.of(line, secondLine));

        PluginSyncNode node = new PluginSyncNode();
        node.setNodeStatus("departed_origin");
        node.setNodeTime("2026-06-02 12:00:00");
        node.setDescription("发往海外");

        PluginSyncBatch batch = new PluginSyncBatch();
        batch.setBatchNo("XGGEKSA04075");
        batch.setExternalShipmentNo("CSAISHIPAGS260531220621GALG");
        batch.setSourceCreatedAt("2026-05-29 16:05:45");
        batch.setEstimatedDepartureAt("2026-05-31 10:00:00");
        batch.setEstimatedArrivalAt("2026-06-01 11:00:00");
        batch.setDeliveryAppointmentText("2026-06-04 16:00-18:00");
        batch.setTransportMode("AIR");
        batch.setDestination("RUH");
        batch.setTargetWarehouseName("FBN-RUH");
        batch.setDepartureDate(LocalDate.parse("2026-06-02"));
        batch.setPackages(List.of(itemPackage));
        batch.setNodes(List.of(node));

        PluginSyncCommand command = new PluginSyncCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setSourceSystem("CHIC");
        command.setBatches(List.of(batch));
        return command;
    }

    private PluginSyncCommand sampleEtCommand() {
        PluginSyncLine line = new PluginSyncLine();
        line.setPsku("PAPERSAYSB293");
        line.setSku("PAPERSAYSB293");
        line.setMsku("PAPERSAYSB293");
        line.setProductName("粉盒马克笔24支48色");
        line.setStoreCode("");
        line.setSiteCode("");
        line.setShippedQuantity(48);
        line.setReceivedQuantity(0);

        PluginSyncPackage itemPackage = new PluginSyncPackage();
        itemPackage.setBoxNo("24-1");
        itemPackage.setExternalBoxNo("X26043047357");
        itemPackage.setTrackingNo("");
        itemPackage.setPackageStatus("已完结");
        itemPackage.setLines(List.of(line));

        PluginSyncBatch batch = new PluginSyncBatch();
        batch.setBatchNo("F2604304851631");
        batch.setBatchStatus("in_transit");
        batch.setTransportMode("SEA");
        batch.setDestination("RUH");
        batch.setTargetWarehouseName("ETRUH01整箱仓");
        batch.setSourceCreatedAt("2026-04-30 10:14:51");
        batch.setEstimatedDepartureAt("2026-05-22 00:00:00");
        batch.setEstimatedArrivalAt("2026-06-15 00:00:00");
        batch.setOfficialEtaDate(LocalDate.parse("2026-06-15"));
        batch.setTrackingNo("");
        batch.setContainerNo("");
        batch.setPackages(List.of(itemPackage));
        batch.setNodes(List.of());

        PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
        expectation.setBatchNo("F2604304851631");
        expectation.setBoxNum(1);
        expectation.setTotalQuantity(48);

        PluginSyncCommand command = new PluginSyncCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setSourceSystem("ET");
        command.setForwarderName("易通");
        command.setSourceBatchExpectations(List.of(expectation));
        command.setBatches(List.of(batch));
        return command;
    }

    private PluginSyncCommand sampleYiteCommand() {
        PluginSyncLine line = new PluginSyncLine();
        line.setPsku("SGGRB142");
        line.setSku("SGGRB142");
        line.setProductName("8个装-透明抽屉隔板");
        line.setShippedQuantity(40);
        line.setReceivedQuantity(0);

        PluginSyncPackage itemPackage = new PluginSyncPackage();
        itemPackage.setBoxNo("YT2605306913U001");
        itemPackage.setExternalBoxNo("6a1979aa3a8e5260c465758c");
        itemPackage.setWeightKg(new BigDecimal("12.650000"));
        itemPackage.setLengthCm(new BigDecimal("60.000000"));
        itemPackage.setWidthCm(new BigDecimal("40.000000"));
        itemPackage.setHeightCm(new BigDecimal("35.000000"));
        itemPackage.setVolumeCbm(new BigDecimal("0.084000"));
        itemPackage.setVolumeWeightKg(new BigDecimal("14.000000"));
        itemPackage.setChargeableWeightKg(new BigDecimal("14.000000"));
        itemPackage.setPackageStatus("转运中");
        itemPackage.setLogisticsStatus("转运中");
        itemPackage.setLines(List.of(line));

        PluginSyncNode schedule = new PluginSyncNode();
        schedule.setNodeStatus("预配航期 ETD: 6-7 ETA:6-30");
        schedule.setNodeTime("2026-06-03 17:10:22");
        schedule.setDescription("预配航期 ETD: 6-7 ETA:6-30");

        PluginSyncNode shipped = new PluginSyncNode();
        shipped.setNodeStatus("shiped");
        shipped.setNodeTime("2026-06-03 13:16:41");
        shipped.setDescription("义乌仓库 发往 宁波港");

        PluginSyncNode picked = new PluginSyncNode();
        picked.setNodeStatus("pickup");
        picked.setNodeTime("2026-06-01 15:49:53");
        picked.setDescription("义乌仓库 已收货");

        PluginSyncNode created = new PluginSyncNode();
        created.setNodeStatus("shipment.create");
        created.setNodeTime("2026-05-29 19:34:01");
        created.setDescription("已下单");

        PluginSyncBatch batch = new PluginSyncBatch();
        batch.setBatchNo("YT2605306913");
        batch.setSourceStatus("转运中");
        batch.setRawStatus("转运中");
        batch.setTransportMode("沙特海运双清");
        batch.setDestination("利雅得");
        batch.setTargetWarehouseName("noon-FBN");
        batch.setExternalShipmentNo("6a1979a93a8e5260c465758b");
        batch.setSourceCreatedAt("2026-05-29 19:34:01");
        batch.setPackages(List.of(itemPackage));
        batch.setNodes(List.of(schedule, shipped, picked, created));

        PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
        expectation.setBatchNo("YT2605306913");
        expectation.setBoxNum(1);
        expectation.setTotalQuantity(40);

        PluginSyncCommand command = new PluginSyncCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setSourceSystem("YITE");
        command.setForwarderName("义特");
        command.setSourceBatchExpectations(List.of(expectation));
        command.setBatches(List.of(batch));
        return command;
    }

    private EtBoxSyncPlanCommand sampleEtPlanCommand(boolean forceFullSync) {
        EtBoxSyncPlanOrderBox complete = new EtBoxSyncPlanOrderBox();
        complete.setBoxId("X26043047357");
        complete.setClientBoxId("24-1");
        EtBoxSyncPlanOrderBox missingSpec = new EtBoxSyncPlanOrderBox();
        missingSpec.setBoxId("X26043047358");
        missingSpec.setClientBoxId("24-2");
        EtBoxSyncPlanOrderBox missingLines = new EtBoxSyncPlanOrderBox();
        missingLines.setBoxId("X26043047359");
        missingLines.setClientBoxId("24-3");
        EtBoxSyncPlanOrderBox newBox = new EtBoxSyncPlanOrderBox();
        newBox.setBoxId("X26043047360");
        newBox.setClientBoxId("24-4");

        EtBoxSyncPlanOrder order = new EtBoxSyncPlanOrder();
        order.setShipOrderId("F2604304851631");
        order.setBoxes(List.of(complete, missingSpec, missingLines, newBox));

        EtBoxSyncPlanCommand command = new EtBoxSyncPlanCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setSourceSystem("ET");
        command.setForwarderName("易通");
        command.setForceFullSync(forceFullSync);
        command.setShipOrders(List.of(order));
        return command;
    }

    private EtBoxSyncStateRow etState(boolean packageExists, boolean packageSpecComplete, int lineCount) {
        EtBoxSyncStateRow row = new EtBoxSyncStateRow();
        row.setPackageExists(packageExists);
        row.setPackageSpecComplete(packageSpecComplete);
        row.setLineCount(lineCount);
        return row;
    }

    private BatchRow batch(Long id) {
        BatchRow row = new BatchRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchReferenceNo("XGGEKSA04075");
        row.setRawForwarderName("启客");
        row.setTransportMode("AIR");
        row.setBatchStatus("in_transit");
        row.setTargetStoreCode("RUH");
        row.setTargetWarehouseName("FBN-RUH");
        return row;
    }

    private LineRow line(Long id) {
        LineRow row = new LineRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchId(53001L);
        row.setBoxNo("NO1-1");
        row.setPsku("SGGRB219");
        return row;
    }

    private NodeRow node(Long id) {
        NodeRow row = new NodeRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchId(53001L);
        row.setNodeStatus("departed_origin");
        row.setNodeHappenedAt(LocalDateTime.parse("2026-06-02T12:00:00"));
        row.setDescription("发往海外");
        return row;
    }
}
