package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchAggregateRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchLatestNodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchListView;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.LineView;
import com.nuono.next.intransit.InTransitBatchRecords.NodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.NodeView;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitBatchServiceTest {

    @Mock
    private InTransitGoodsMapper mapper;

    @Mock
    private InTransitForwarderService forwarderService;

    @Mock
    private InTransitOperationAuditService auditService;

    private InTransitBatchService service;

    @BeforeEach
    void setUp() {
        service = new InTransitBatchService(mapper, forwarderService, auditService);
    }

    @Test
    void shouldSaveDraftBatchAndExposeMissingFieldHints() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setRawForwarderName("历史货代A");
        command.setBatchStatus("draft");

        ForwarderResolveView unmatched = ForwarderResolveView.unmatched("历史货代A", "历史货代a");
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(unmatched);
        when(mapper.nextBatchId()).thenReturn(53001L);
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "draft", "历史货代A", "forwarder_unmatched"));

        BatchView result = service.saveBatch(command);

        assertEquals(53001L, result.getBatchId());
        assertEquals("draft", result.getBatchStatus());
        assertEquals("forwarder_unmatched", result.getForwarderQualityStatus());
        assertEquals(List.of("transportMode", "targetStoreCode", "targetWarehouseName"), result.getMissingFields());
        verify(mapper).insertBatch(any(BatchRow.class));
        assertAudit("batch_created", "batch", 53001L);
    }

    @Test
    void shouldAuditBatchUpdate() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setRawForwarderName("义特物流");
        command.setTransportMode("AIR");
        command.setTargetStoreCode("STR245027-NAE");
        command.setTargetSiteCode("AE");
        command.setTargetWarehouseName("FBN-DXB");
        command.setBatchStatus("in_transit");

        ForwarderResolveView matched = new ForwarderResolveView();
        matched.setStandardForwarderId(51001L);
        matched.setStandardForwarderCode("YITE");
        matched.setStandardForwarderName("义特物流");
        matched.setQualityStatus("forwarder_matched");
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matched);
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));

        BatchView result = service.saveBatch(command);

        assertEquals(53001L, result.getBatchId());
        verify(mapper).updateBatch(any(BatchRow.class));
        assertAudit("batch_updated", "batch", 53001L);
    }

    @Test
    void shouldRejectUnsupportedTransportMode() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setTransportMode("RAIL");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveBatch(command)
        );

        assertEquals("运输方式只支持海运或空运。", exception.getMessage());
        verify(mapper, never()).insertBatch(any(BatchRow.class));
    }

    @Test
    void shouldRejectNonDraftBatchWhenBasicFieldsAreMissing() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchStatus("in_transit");
        command.setTransportMode("SEA");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.saveBatch(command)
        );

        assertEquals("在途批次进入跟踪前需补齐：forwarder,targetStoreCode,targetWarehouseName。", exception.getMessage());
        verify(mapper, never()).insertBatch(any(BatchRow.class));
    }

    @Test
    void shouldListBatchesByFiltersAndKeepNoPurchaseOrFeeFieldsInView() {
        InTransitBatchQuery query = new InTransitBatchQuery();
        query.setOwnerUserId(10002L);
        query.setTransportMode("AIR");
        query.setTargetStoreCode("XINGYAO");
        query.setSkuKeyword("SKU-AE-001");
        query.setEtaFrom(LocalDate.parse("2026-06-01"));
        query.setEtaTo(LocalDate.parse("2026-06-30"));
        when(mapper.listBatches(any(InTransitBatchQuery.class))).thenReturn(List.of(
                batch(53002L, "in_transit", "义特物流", "forwarder_matched")
        ));

        BatchListView result = service.listBatches(query);

        assertEquals(1, result.getItems().size());
        assertEquals("AIR", result.getItems().get(0).getTransportMode());
        assertEquals("义特物流", result.getItems().get(0).getRawForwarderName());
        assertFalse(result.getItems().get(0).getFieldNames().contains("purchaseOrderNo"));
        assertFalse(result.getItems().get(0).getFieldNames().contains("feeStatus"));

        ArgumentCaptor<InTransitBatchQuery> captor = ArgumentCaptor.forClass(InTransitBatchQuery.class);
        verify(mapper).listBatches(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("AIR", captor.getValue().getTransportMode());
        assertEquals("SKU-AE-001", captor.getValue().getSkuKeyword());
    }

    @Test
    void shouldSaveLineAndRefreshBatchAggregatesFromProductLines() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setSku("SKU-AE-001");
        command.setMsku("MSKU-AE-001");
        command.setPsku("PSKU-AE-001");
        command.setProductName("折叠手机壳");
        command.setStoreCode("STR245027-NAE");
        command.setSiteCode("AE");
        command.setShippedQuantity(10);
        command.setReceivedQuantity(4);
        command.setCartonCount(2);
        command.setUnitsPerCarton(5);
        command.setCartonWeightKg(new BigDecimal("12.500000"));
        command.setCartonVolumeCbm(new BigDecimal("0.250000"));
        command.setRemark("首批发货");

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.nextLineId()).thenReturn(54001L);
        when(mapper.selectLineById(10002L, 53001L, 54001L)).thenReturn(line(54001L, 53001L, "SKU-AE-001", 10, 4, 6));
        BatchAggregateRow aggregate = aggregate(2, 18, 7, 11, 4, new BigDecimal("25.000000"), new BigDecimal("0.500000"));
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate);

        LineView result = service.saveLine(command);

        assertEquals(54001L, result.getLineId());
        assertEquals("SKU-AE-001", result.getSku());
        assertEquals(10, result.getShippedQuantity());
        assertEquals(4, result.getReceivedQuantity());
        assertEquals(6, result.getRemainingQuantity());

        ArgumentCaptor<LineRow> lineCaptor = ArgumentCaptor.forClass(LineRow.class);
        verify(mapper).insertLine(lineCaptor.capture());
        assertEquals(6, lineCaptor.getValue().getRemainingQuantity());
        assertEquals(new BigDecimal("12.500000"), lineCaptor.getValue().getCartonWeightKg());

        ArgumentCaptor<BatchAggregateRow> aggregateCaptor = ArgumentCaptor.forClass(BatchAggregateRow.class);
        verify(mapper).refreshBatchAggregate(eq(10002L), eq(53001L), aggregateCaptor.capture());
        assertEquals(2, aggregateCaptor.getValue().getSkuCount());
        assertEquals(18, aggregateCaptor.getValue().getShippedQuantityTotal());
        assertEquals(7, aggregateCaptor.getValue().getReceivedQuantityTotal());
        assertEquals(11, aggregateCaptor.getValue().getRemainingQuantityTotal());
        assertEquals(4, aggregateCaptor.getValue().getCartonCountTotal());
        assertEquals(new BigDecimal("25.000000"), aggregateCaptor.getValue().getTotalWeightKg());
        assertEquals(new BigDecimal("0.500000"), aggregateCaptor.getValue().getTotalVolumeCbm());
        assertAudit("line_created", "line", 54001L);
    }

    @Test
    void shouldExposeMatchedProductSummaryWhenListingLines() {
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        LineRow row = line(54001L, 53001L, "SKU-AE-001", 10, 4, 6);
        row.setMatchedProductId(62001L);
        row.setProductSkuParent("PARENT-AE-001");
        row.setProductTitle("Noon 折叠手机壳");
        row.setProductImageUrl("https://cdn.example.com/noon-phone-case.jpg");
        when(mapper.listLines(10002L, 53001L)).thenReturn(List.of(row));

        var result = service.listLines(10002L, 53001L);

        LineView view = result.getItems().get(0);
        assertEquals(62001L, view.getMatchedProductId());
        assertEquals("PARENT-AE-001", view.getProductSkuParent());
        assertEquals("Noon 折叠手机壳", view.getProductTitle());
        assertEquals("https://cdn.example.com/noon-phone-case.jpg", view.getProductImageUrl());
    }

    @Test
    void shouldCreatePackageAndAttachLineWhenBoxNoIsProvided() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("XGGEUAE04029-1");
        command.setSku("SKU-QIKE-001");
        command.setShippedQuantity(20);
        command.setReceivedQuantity(0);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "启客", "forwarder_unmatched"));
        when(mapper.selectPackageByBoxNo(10002L, 53001L, "XGGEUAE04029-1")).thenReturn(null);
        when(mapper.nextPackageId()).thenReturn(58001L);
        when(mapper.nextLineId()).thenReturn(54003L);
        LineRow persisted = line(54003L, 53001L, "SKU-QIKE-001", 20, 0, 20);
        persisted.setPackageId(58001L);
        persisted.setBoxNo("XGGEUAE04029-1");
        when(mapper.selectLineById(10002L, 53001L, 54003L)).thenReturn(persisted);
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(1, 20, 0, 20, null, null, null));

        LineView result = service.saveLine(command);

        assertEquals(58001L, result.getPackageId());
        assertEquals("XGGEUAE04029-1", result.getBoxNo());
        ArgumentCaptor<PackageRow> packageCaptor = ArgumentCaptor.forClass(PackageRow.class);
        verify(mapper).insertPackage(packageCaptor.capture());
        assertEquals(58001L, packageCaptor.getValue().getId());
        assertEquals(53001L, packageCaptor.getValue().getBatchId());
        assertEquals("XGGEUAE04029-1", packageCaptor.getValue().getBoxNo());
        ArgumentCaptor<LineRow> lineCaptor = ArgumentCaptor.forClass(LineRow.class);
        verify(mapper).insertLine(lineCaptor.capture());
        assertEquals(58001L, lineCaptor.getValue().getPackageId());
        assertEquals("XGGEUAE04029-1", lineCaptor.getValue().getBoxNo());
    }

    @Test
    void shouldAuditDeletedLineWithoutInventorySideEffect() {
        var command = new InTransitBatchCommands.DeleteLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setLineId(54001L);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.selectLineById(10002L, 53001L, 54001L)).thenReturn(line(54001L, 53001L, "SKU-AE-001", 10, 4, 6));
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(0, null, null, null, null, null, null));
        when(mapper.listLines(10002L, 53001L)).thenReturn(List.of());

        service.deleteLine(command);

        verify(mapper).deleteLine(10002L, 53001L, 54001L, 90001L);
        assertAudit("line_deleted", "line", 54001L);
    }

    @Test
    void shouldRejectLineWhenReceivedQuantityExceedsShippedQuantity() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setSku("SKU-AE-001");
        command.setShippedQuantity(5);
        command.setReceivedQuantity(6);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.saveLine(command)
        );

        assertEquals("已入仓数量不能大于发货数量。", exception.getMessage());
        verify(mapper, never()).insertLine(any(LineRow.class));
    }

    @Test
    void shouldKeepNullWeightAndVolumeAggregatesWhenLineMeasuresAreMissing() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setSku("SKU-AE-002");
        command.setShippedQuantity(3);
        command.setReceivedQuantity(0);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "draft", "历史货代A", "forwarder_unmatched"));
        when(mapper.nextLineId()).thenReturn(54002L);
        when(mapper.selectLineById(10002L, 53001L, 54002L)).thenReturn(line(54002L, 53001L, "SKU-AE-002", 3, 0, 3));
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(1, 3, 0, 3, null, null, null));

        service.saveLine(command);

        ArgumentCaptor<BatchAggregateRow> aggregateCaptor = ArgumentCaptor.forClass(BatchAggregateRow.class);
        verify(mapper).refreshBatchAggregate(eq(10002L), eq(53001L), aggregateCaptor.capture());
        assertEquals(1, aggregateCaptor.getValue().getSkuCount());
        assertEquals(3, aggregateCaptor.getValue().getRemainingQuantityTotal());
        assertEquals(null, aggregateCaptor.getValue().getCartonCountTotal());
        assertEquals(null, aggregateCaptor.getValue().getTotalWeightKg());
        assertEquals(null, aggregateCaptor.getValue().getTotalVolumeCbm());
    }

    @Test
    void shouldSaveLogisticsNodeAndRefreshLatestBatchStatus() {
        SaveNodeCommand command = new SaveNodeCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setNodeStatus("exception");
        command.setNodeHappenedAt(LocalDateTime.parse("2026-06-01T10:30:00"));
        command.setDescription("到港后清关异常");
        command.setOperatorName("运营A");

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.nextNodeId()).thenReturn(55001L);
        when(mapper.selectNodeById(10002L, 53001L, 55001L)).thenReturn(node(
                55001L,
                53001L,
                "exception",
                LocalDateTime.parse("2026-06-01T10:30:00"),
                "到港后清关异常"
        ));
        BatchLatestNodeRow latest = latestNode("exception", LocalDateTime.parse("2026-06-01T10:30:00"), "到港后清关异常");
        when(mapper.selectLatestNode(10002L, 53001L)).thenReturn(latest);

        NodeView result = service.saveNode(command);

        assertEquals(55001L, result.getNodeId());
        assertEquals("exception", result.getNodeStatus());
        assertEquals(LocalDateTime.parse("2026-06-01T10:30:00"), result.getNodeHappenedAt());
        assertEquals("到港后清关异常", result.getDescription());
        assertEquals("运营A", result.getOperatorName());

        ArgumentCaptor<NodeRow> nodeCaptor = ArgumentCaptor.forClass(NodeRow.class);
        verify(mapper).insertNode(nodeCaptor.capture());
        assertEquals("exception", nodeCaptor.getValue().getNodeStatus());
        assertEquals(90001L, nodeCaptor.getValue().getCreatedBy());

        ArgumentCaptor<BatchLatestNodeRow> latestCaptor = ArgumentCaptor.forClass(BatchLatestNodeRow.class);
        verify(mapper).refreshBatchLatestNode(eq(10002L), eq(53001L), latestCaptor.capture());
        assertEquals("exception", latestCaptor.getValue().getLatestNodeStatus());
        assertEquals("exception", latestCaptor.getValue().getDerivedBatchStatus());
        assertEquals(LocalDateTime.parse("2026-06-01T10:30:00"), latestCaptor.getValue().getLatestNodeHappenedAt());
        assertAudit("logistics_node_added", "logistics_node", 55001L);
    }

    @Test
    void shouldRejectFreeTextLogisticsNodeStatus() {
        SaveNodeCommand command = new SaveNodeCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setNodeStatus("delayed_at_port");

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveNode(command)
        );

        assertEquals("物流节点状态不支持自由文本。", exception.getMessage());
        verify(mapper, never()).insertNode(any(NodeRow.class));
    }

    @Test
    void shouldListLogisticsNodesByBatchInMapperOrder() {
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.listNodes(10002L, 53001L)).thenReturn(List.of(
                node(55002L, 53001L, "in_transit", LocalDateTime.parse("2026-06-02T08:00:00"), "海上运输"),
                node(55001L, 53001L, "handed_to_forwarder", LocalDateTime.parse("2026-05-29T09:00:00"), "交货代")
        ));

        var result = service.listNodes(10002L, 53001L);

        assertEquals(2, result.getItems().size());
        assertEquals(55002L, result.getItems().get(0).getNodeId());
        assertEquals("in_transit", result.getItems().get(0).getNodeStatus());
        assertEquals(55001L, result.getItems().get(1).getNodeId());
    }

    private BatchRow batch(Long id, String status, String rawForwarderName, String qualityStatus) {
        BatchRow row = new BatchRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setStandardForwarderId("forwarder_matched".equals(qualityStatus) ? 51001L : null);
        row.setStandardForwarderCode("forwarder_matched".equals(qualityStatus) ? "YITE" : null);
        row.setStandardForwarderName("forwarder_matched".equals(qualityStatus) ? "义特物流" : null);
        row.setRawForwarderName(rawForwarderName);
        row.setNormalizedRawForwarderName(rawForwarderName == null ? null : rawForwarderName.toLowerCase());
        row.setForwarderQualityStatus(qualityStatus);
        row.setTransportMode("AIR");
        row.setBatchStatus(status);
        row.setTargetStoreCode("XINGYAO");
        row.setTargetSiteCode("AE");
        row.setTargetWarehouseName("FBN-DXB");
        row.setDepartureDate(LocalDate.parse("2026-05-20"));
        row.setEtaDate(LocalDate.parse("2026-06-08"));
        row.setTrackingNo("TRK-001");
        row.setContainerNo("CONT-001");
        row.setBatchReferenceNo("BATCH-001");
        row.setRemark("首批在途维护");
        row.setMissingFieldsJson("[\"transportMode\",\"targetStoreCode\",\"targetWarehouseName\"]");
        return row;
    }

    private LineRow line(Long id, Long batchId, String sku, Integer shippedQuantity, Integer receivedQuantity, Integer remainingQuantity) {
        LineRow row = new LineRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchId(batchId);
        row.setSku(sku);
        row.setMsku("MSKU-" + sku);
        row.setPsku("PSKU-" + sku);
        row.setProductName("折叠手机壳");
        row.setStoreCode("STR245027-NAE");
        row.setSiteCode("AE");
        row.setShippedQuantity(shippedQuantity);
        row.setReceivedQuantity(receivedQuantity);
        row.setRemainingQuantity(remainingQuantity);
        row.setCartonCount(2);
        row.setUnitsPerCarton(5);
        row.setCartonWeightKg(new BigDecimal("12.500000"));
        row.setCartonVolumeCbm(new BigDecimal("0.250000"));
        row.setRemark("首批发货");
        return row;
    }

    private BatchAggregateRow aggregate(
            Integer skuCount,
            Integer shippedQuantityTotal,
            Integer receivedQuantityTotal,
            Integer remainingQuantityTotal,
            Integer cartonCountTotal,
            BigDecimal totalWeightKg,
            BigDecimal totalVolumeCbm
    ) {
        BatchAggregateRow row = new BatchAggregateRow();
        row.setSkuCount(skuCount);
        row.setShippedQuantityTotal(shippedQuantityTotal);
        row.setReceivedQuantityTotal(receivedQuantityTotal);
        row.setRemainingQuantityTotal(remainingQuantityTotal);
        row.setCartonCountTotal(cartonCountTotal);
        row.setTotalWeightKg(totalWeightKg);
        row.setTotalVolumeCbm(totalVolumeCbm);
        return row;
    }

    private NodeRow node(Long id, Long batchId, String nodeStatus, LocalDateTime happenedAt, String description) {
        NodeRow row = new NodeRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchId(batchId);
        row.setNodeStatus(nodeStatus);
        row.setNodeHappenedAt(happenedAt);
        row.setDescription(description);
        row.setOperatorName("运营A");
        row.setCreatedBy(90001L);
        row.setUpdatedBy(90001L);
        return row;
    }

    private BatchLatestNodeRow latestNode(String nodeStatus, LocalDateTime happenedAt, String description) {
        BatchLatestNodeRow row = new BatchLatestNodeRow();
        row.setLatestNodeStatus(nodeStatus);
        row.setLatestNodeHappenedAt(happenedAt);
        row.setLatestNodeDescription(description);
        return row;
    }

    private void assertAudit(String operationType, String targetType, Long targetId) {
        ArgumentCaptor<InTransitOperationAuditService.AuditCommand> auditCaptor =
                ArgumentCaptor.forClass(InTransitOperationAuditService.AuditCommand.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals(operationType, auditCaptor.getValue().getOperationType());
        assertEquals(targetType, auditCaptor.getValue().getTargetType());
        assertEquals(targetId, auditCaptor.getValue().getTargetId());
        assertEquals(10002L, auditCaptor.getValue().getOwnerUserId());
        assertEquals(90001L, auditCaptor.getValue().getOperatorUserId());
    }
}
