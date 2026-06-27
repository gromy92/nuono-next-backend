package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.DeleteNodeCommand;
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
import org.springframework.dao.DuplicateKeyException;
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
        matched.setStandardForwarderName("义特");
        matched.setRawForwarderName("义特");
        matched.setNormalizedRawForwarderName("义特物流");
        matched.setQualityStatus("forwarder_matched");
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matched);
        BatchRow saved = batch(53001L, "in_transit", "义特", "forwarder_matched");
        saved.setTargetStoreCode("DB");
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(saved);

        BatchView result = service.saveBatch(command);

        assertEquals(53001L, result.getBatchId());
        assertEquals("DB", result.getTargetStoreCode());
        ArgumentCaptor<BatchRow> rowCaptor = ArgumentCaptor.forClass(BatchRow.class);
        verify(mapper).updateBatch(rowCaptor.capture());
        assertEquals("DB", rowCaptor.getValue().getTargetStoreCode());
        assertEquals("义特", rowCaptor.getValue().getRawForwarderName());
        assertEquals("义特", rowCaptor.getValue().getStandardForwarderName());
        assertAudit("batch_updated", "batch", 53001L);
    }

    @Test
    void shouldPreserveExistingTrackingFieldsWhenUpdatingNonDraftBatchWithPartialCommand() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setRawForwarderName("义特物流");
        command.setTargetStoreCode("DB");
        command.setBatchStatus("in_transit");

        ForwarderResolveView matched = new ForwarderResolveView();
        matched.setStandardForwarderId(51001L);
        matched.setStandardForwarderCode("YITE");
        matched.setStandardForwarderName("义特");
        matched.setRawForwarderName("义特");
        matched.setNormalizedRawForwarderName("义特物流");
        matched.setQualityStatus("forwarder_matched");
        when(mapper.selectBatchById(10002L, 53001L))
                .thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matched);

        service.saveBatch(command);

        ArgumentCaptor<BatchRow> rowCaptor = ArgumentCaptor.forClass(BatchRow.class);
        verify(mapper).updateBatch(rowCaptor.capture());
        assertEquals("AIR", rowCaptor.getValue().getTransportMode());
        assertEquals("DB", rowCaptor.getValue().getTargetStoreCode());
        assertEquals("AE", rowCaptor.getValue().getTargetSiteCode());
        assertEquals("FBN-DXB", rowCaptor.getValue().getTargetWarehouseName());
        assertEquals("TRK-001", rowCaptor.getValue().getTrackingNo());
        assertEquals("CONT-001", rowCaptor.getValue().getContainerNo());
    }

    @Test
    void shouldNormalizeDestinationToRuhOrDbWhenSavingBatch() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setRawForwarderName("启客物流");
        command.setTransportMode("AIR");
        command.setTargetStoreCode("STORE-RUH01S");
        command.setTargetSiteCode("SA");
        command.setTargetWarehouseName("FBN-RUH");
        command.setBatchStatus("in_transit");

        ForwarderResolveView matched = new ForwarderResolveView();
        matched.setStandardForwarderId(51002L);
        matched.setStandardForwarderCode("QIKE");
        matched.setStandardForwarderName("启客");
        matched.setRawForwarderName("启客");
        matched.setNormalizedRawForwarderName("启客物流");
        matched.setQualityStatus("forwarder_matched");
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matched);
        when(mapper.nextBatchId()).thenReturn(53004L);
        BatchRow persisted = batch(53004L, "in_transit", "启客", "forwarder_matched");
        persisted.setStandardForwarderId(51002L);
        persisted.setStandardForwarderCode("QIKE");
        persisted.setStandardForwarderName("启客");
        persisted.setTargetStoreCode("RUH");
        persisted.setTargetSiteCode("SA");
        persisted.setTargetWarehouseName("FBN-RUH");
        when(mapper.selectBatchById(10002L, 53004L)).thenReturn(persisted);

        BatchView result = service.saveBatch(command);

        assertEquals("RUH", result.getTargetStoreCode());
        ArgumentCaptor<BatchRow> rowCaptor = ArgumentCaptor.forClass(BatchRow.class);
        verify(mapper).insertBatch(rowCaptor.capture());
        assertEquals("RUH", rowCaptor.getValue().getTargetStoreCode());
        assertEquals("启客", rowCaptor.getValue().getRawForwarderName());
        assertEquals("启客", rowCaptor.getValue().getStandardForwarderName());
    }

    @Test
    void shouldSaveExternalShipmentAndSourcePlanningFieldsOnBatch() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setRawForwarderName("启客");
        command.setTransportMode("AIR");
        command.setTargetStoreCode("RUH");
        command.setTargetWarehouseName("FBN-RUH");
        command.setBatchReferenceNo("XGGEKSA04075");
        command.setExternalShipmentNo("CSAISHIPAGS260531220621GALG");
        command.setSourceCreatedAt(LocalDateTime.parse("2026-05-29T16:05:45"));
        command.setEstimatedDepartureAt(LocalDateTime.parse("2026-05-31T10:00:00"));
        command.setEstimatedArrivalAt(LocalDateTime.parse("2026-06-01T11:00:00"));
        command.setDeliveryAppointmentText("2026-06-04 16:00-18:00");
        command.setBatchStatus("in_transit");

        ForwarderResolveView matched = new ForwarderResolveView();
        matched.setStandardForwarderId(51002L);
        matched.setStandardForwarderCode("QIKE");
        matched.setStandardForwarderName("启客");
        matched.setRawForwarderName("启客");
        matched.setNormalizedRawForwarderName("启客");
        matched.setQualityStatus("forwarder_matched");
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matched);
        when(mapper.nextBatchId()).thenReturn(53005L);
        BatchRow persisted = batch(53005L, "in_transit", "启客", "forwarder_matched");
        persisted.setStandardForwarderId(51002L);
        persisted.setStandardForwarderCode("QIKE");
        persisted.setStandardForwarderName("启客");
        persisted.setTargetStoreCode("RUH");
        persisted.setTargetWarehouseName("FBN-RUH");
        persisted.setBatchReferenceNo("XGGEKSA04075");
        persisted.setExternalShipmentNo("CSAISHIPAGS260531220621GALG");
        persisted.setSourceCreatedAt(LocalDateTime.parse("2026-05-29T16:05:45"));
        persisted.setEstimatedDepartureAt(LocalDateTime.parse("2026-05-31T10:00:00"));
        persisted.setEstimatedArrivalAt(LocalDateTime.parse("2026-06-01T11:00:00"));
        persisted.setDeliveryAppointmentText("2026-06-04 16:00-18:00");
        when(mapper.selectBatchById(10002L, 53005L)).thenReturn(persisted);

        BatchView result = service.saveBatch(command);

        assertEquals("CSAISHIPAGS260531220621GALG", result.getExternalShipmentNo());
        assertEquals(LocalDateTime.parse("2026-05-29T16:05:45"), result.getSourceCreatedAt());
        assertEquals(LocalDateTime.parse("2026-05-31T10:00:00"), result.getEstimatedDepartureAt());
        assertEquals(LocalDateTime.parse("2026-06-01T11:00:00"), result.getEstimatedArrivalAt());
        assertEquals("2026-06-04 16:00-18:00", result.getDeliveryAppointmentText());

        ArgumentCaptor<BatchRow> rowCaptor = ArgumentCaptor.forClass(BatchRow.class);
        verify(mapper).insertBatch(rowCaptor.capture());
        assertEquals("CSAISHIPAGS260531220621GALG", rowCaptor.getValue().getExternalShipmentNo());
        assertEquals(LocalDateTime.parse("2026-05-29T16:05:45"), rowCaptor.getValue().getSourceCreatedAt());
        assertEquals(LocalDateTime.parse("2026-05-31T10:00:00"), rowCaptor.getValue().getEstimatedDepartureAt());
        assertEquals(LocalDateTime.parse("2026-06-01T11:00:00"), rowCaptor.getValue().getEstimatedArrivalAt());
        assertEquals("2026-06-04 16:00-18:00", rowCaptor.getValue().getDeliveryAppointmentText());
    }

    @Test
    void shouldRejectNewBatchWhenActiveReferenceAlreadyExistsForOwner() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchReferenceNo(" F2604304851631 ");
        command.setBatchStatus("draft");
        BatchRow existing = batch(53020L, "in_transit", "易通", "forwarder_matched");
        existing.setBatchReferenceNo("F2604304851631");
        when(mapper.selectBatchByReferenceNo(10002L, "F2604304851631")).thenReturn(existing);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.saveBatch(command)
        );

        assertEquals("在途批次号已存在，请打开已有批次继续更新：F2604304851631", exception.getMessage());
        verify(mapper, never()).insertBatch(any(BatchRow.class));
    }

    @Test
    void shouldConvertBatchReferenceUniqueConstraintViolationToConflictMessage() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchReferenceNo("F2604304851631");
        command.setBatchStatus("draft");
        when(mapper.selectBatchByReferenceNo(10002L, "F2604304851631")).thenReturn(null);
        when(mapper.nextBatchId()).thenReturn(53021L);
        when(mapper.insertBatch(any(BatchRow.class))).thenThrow(new DuplicateKeyException(
                "Duplicate entry '10002-F2604304851631' for key 'uk_in_transit_batch_reference_active'"
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.saveBatch(command)
        );

        assertEquals("在途批次号已存在，请打开已有批次继续更新：F2604304851631", exception.getMessage());
    }

    @Test
    void shouldRejectUnsupportedDestination() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setTransportMode("AIR");
        command.setTargetStoreCode("JED");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveBatch(command)
        );

        assertEquals("目的地只支持 RUH 利雅得或 DB 迪拜。", exception.getMessage());
        verify(mapper, never()).insertBatch(any(BatchRow.class));
    }

    @Test
    void shouldRejectBatchWhenStandardForwarderIdDoesNotBelongToOwner() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setStandardForwarderId(99999L);
        command.setTransportMode("AIR");
        command.setTargetStoreCode("DB");
        command.setTargetWarehouseName("FBN-DXB");
        command.setBatchStatus("in_transit");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveBatch(command)
        );

        assertEquals("标准货代不存在。", exception.getMessage());
        verify(mapper).selectForwarderById(10002L, 99999L);
        verify(mapper, never()).insertBatch(any(BatchRow.class));
        verify(mapper, never()).updateBatch(any(BatchRow.class));
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
        query.setTargetStoreCode("迪拜");
        query.setSkuKeyword("SKU-AE-001");
        query.setEtaFrom(LocalDate.parse("2026-06-01"));
        query.setEtaTo(LocalDate.parse("2026-06-30"));
        when(mapper.countBatches(any(InTransitBatchQuery.class))).thenReturn(1);
        when(mapper.listBatches(any(InTransitBatchQuery.class))).thenReturn(List.of(
                batch(53002L, "in_transit", "义特", "forwarder_matched")
        ));

        BatchListView result = service.listBatches(query);

        assertEquals(1, result.getItems().size());
        assertEquals("AIR", result.getItems().get(0).getTransportMode());
        assertEquals("义特", result.getItems().get(0).getRawForwarderName());
        assertFalse(result.getItems().get(0).getFieldNames().contains("purchaseOrderNo"));
        assertFalse(result.getItems().get(0).getFieldNames().contains("feeStatus"));

        ArgumentCaptor<InTransitBatchQuery> captor = ArgumentCaptor.forClass(InTransitBatchQuery.class);
        verify(mapper).listBatches(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("AIR", captor.getValue().getTransportMode());
        assertEquals("DB", captor.getValue().getTargetStoreCode());
        assertEquals("SKU-AE-001", captor.getValue().getSkuKeyword());
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getPageSize());
        assertEquals(0, captor.getValue().getOffset());
        assertEquals(20, captor.getValue().getLimit());
        assertEquals("createdAt", captor.getValue().getSortField());
        assertEquals("DESC", captor.getValue().getSortDirectionSql());
    }

    @Test
    void shouldPaginateBatchListAndReturnTotalCount() {
        InTransitBatchQuery query = new InTransitBatchQuery();
        query.setOwnerUserId(10002L);
        query.setPage(3);
        query.setPageSize(25);
        query.setSortField("etaDate");
        query.setSortDirection("desc");
        when(mapper.countBatches(any(InTransitBatchQuery.class))).thenReturn(126);
        when(mapper.listBatches(any(InTransitBatchQuery.class))).thenReturn(List.of(
                batch(53008L, "in_transit", "义特", "forwarder_matched")
        ));

        BatchListView result = service.listBatches(query);

        assertEquals(126, result.getTotalCount());
        assertEquals(3, result.getPage());
        assertEquals(25, result.getPageSize());
        assertEquals(1, result.getItems().size());
        ArgumentCaptor<InTransitBatchQuery> captor = ArgumentCaptor.forClass(InTransitBatchQuery.class);
        verify(mapper).listBatches(captor.capture());
        assertEquals(25, captor.getValue().getLimit());
        assertEquals(50, captor.getValue().getOffset());
        assertEquals("etaDate", captor.getValue().getSortField());
        assertEquals("DESC", captor.getValue().getSortDirectionSql());
    }

    @Test
    void shouldFallbackUnsupportedBatchListSortToCreatedAtDesc() {
        InTransitBatchQuery query = new InTransitBatchQuery();
        query.setOwnerUserId(10002L);
        query.setSortField("batchStatus");
        query.setSortDirection("asc");
        when(mapper.countBatches(any(InTransitBatchQuery.class))).thenReturn(1);
        when(mapper.listBatches(any(InTransitBatchQuery.class))).thenReturn(List.of(
                batch(53009L, "in_transit", "义特", "forwarder_matched")
        ));

        service.listBatches(query);

        ArgumentCaptor<InTransitBatchQuery> captor = ArgumentCaptor.forClass(InTransitBatchQuery.class);
        verify(mapper).listBatches(captor.capture());
        assertEquals("createdAt", captor.getValue().getSortField());
        assertEquals("DESC", captor.getValue().getSortDirectionSql());
    }

    @Test
    void shouldSaveLineAndRefreshBatchAggregatesFromProductLines() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("BATCH-001-BOX-1");
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

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.nextLineId()).thenReturn(54001L);
        when(mapper.selectLineById(10002L, 53001L, 54001L)).thenReturn(line(54001L, 53001L, "SKU-AE-001", 10, 4, 6));
        BatchAggregateRow aggregate = aggregate(2, 2, 18, 7, 11, 4, new BigDecimal("25.000000"), new BigDecimal("0.500000"));
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
        assertEquals(2, aggregateCaptor.getValue().getBoxCount());
        assertEquals(18, aggregateCaptor.getValue().getShippedQuantityTotal());
        assertEquals(7, aggregateCaptor.getValue().getReceivedQuantityTotal());
        assertEquals(11, aggregateCaptor.getValue().getRemainingQuantityTotal());
        assertEquals(4, aggregateCaptor.getValue().getCartonCountTotal());
        assertEquals(new BigDecimal("25.000000"), aggregateCaptor.getValue().getTotalWeightKg());
        assertEquals(new BigDecimal("0.500000"), aggregateCaptor.getValue().getTotalVolumeCbm());
        assertAudit("line_created", "line", 54001L);
    }

    @Test
    void shouldAllowSourceSkuToBeBlankWhenPskuIsProvided() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("BATCH-001-BOX-1");
        command.setPsku("PSKU-AE-ONLY");
        command.setShippedQuantity(4);
        command.setReceivedQuantity(0);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.nextLineId()).thenReturn(54004L);
        LineRow persisted = line(54004L, 53001L, null, 4, 0, 4);
        persisted.setPsku("PSKU-AE-ONLY");
        when(mapper.selectLineById(10002L, 53001L, 54004L)).thenReturn(persisted);
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(1, 1, 4, 0, 4, null, null, null));

        LineView result = service.saveLine(command);

        assertEquals("PSKU-AE-ONLY", result.getPsku());
        ArgumentCaptor<LineRow> lineCaptor = ArgumentCaptor.forClass(LineRow.class);
        verify(mapper).insertLine(lineCaptor.capture());
        assertEquals(null, lineCaptor.getValue().getSku());
        assertEquals("PSKU-AE-ONLY", lineCaptor.getValue().getPsku());
    }

    @Test
    void shouldRejectLineWhenPskuIsMissing() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("BATCH-001-BOX-1");
        command.setSku("SOURCE-SKU-ONLY");
        command.setShippedQuantity(5);
        command.setReceivedQuantity(0);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveLine(command)
        );

        assertEquals("PSKU不能为空。", exception.getMessage());
        verify(mapper, never()).insertLine(any(LineRow.class));
    }

    @Test
    void shouldRejectLineWhenBoxNoIsMissing() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setPsku("PSKU-AE-001");
        command.setShippedQuantity(5);
        command.setReceivedQuantity(0);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveLine(command)
        );

        assertEquals("箱号不能为空。", exception.getMessage());
        verify(mapper, never()).insertLine(any(LineRow.class));
    }

    @Test
    void shouldExposeMatchedProductSummaryWhenListingLines() {
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        LineRow row = line(54001L, 53001L, "SKU-AE-001", 10, 4, 6);
        row.setMatchedProductId(62001L);
        row.setProductSkuParent("PARENT-AE-001");
        row.setProductTitle("Noon 折叠手机壳");
        row.setProductImageUrl("https://cdn.example.com/noon-phone-case.jpg");
        row.setExternalBoxNo("X25090553672");
        row.setPackageWeightKg(new BigDecimal("12.500000"));
        row.setPackageLengthCm(new BigDecimal("45.000000"));
        row.setPackageWidthCm(new BigDecimal("35.000000"));
        row.setPackageHeightCm(new BigDecimal("30.000000"));
        row.setPackageVolumeCbm(new BigDecimal("0.047250"));
        row.setMeasuredWeightKg(new BigDecimal("12.800000"));
        row.setMeasuredLengthCm(new BigDecimal("46.000000"));
        row.setMeasuredWidthCm(new BigDecimal("36.000000"));
        row.setMeasuredHeightCm(new BigDecimal("31.000000"));
        row.setMeasuredVolumeCbm(new BigDecimal("0.051336"));
        row.setPackageStatus("已封箱");
        row.setLogisticsStatus("发往海外");
        when(mapper.listLines(10002L, 53001L)).thenReturn(List.of(row));

        var result = service.listLines(10002L, 53001L);

        LineView view = result.getItems().get(0);
        assertEquals(62001L, view.getMatchedProductId());
        assertEquals("PARENT-AE-001", view.getProductSkuParent());
        assertEquals("Noon 折叠手机壳", view.getProductTitle());
        assertEquals("https://cdn.example.com/noon-phone-case.jpg", view.getProductImageUrl());
        assertEquals("X25090553672", view.getExternalBoxNo());
        assertEquals(new BigDecimal("12.500000"), view.getPackageWeightKg());
        assertEquals(new BigDecimal("45.000000"), view.getPackageLengthCm());
        assertEquals(new BigDecimal("35.000000"), view.getPackageWidthCm());
        assertEquals(new BigDecimal("30.000000"), view.getPackageHeightCm());
        assertEquals(new BigDecimal("0.047250"), view.getPackageVolumeCbm());
        assertEquals(new BigDecimal("12.800000"), view.getMeasuredWeightKg());
        assertEquals(new BigDecimal("46.000000"), view.getMeasuredLengthCm());
        assertEquals(new BigDecimal("36.000000"), view.getMeasuredWidthCm());
        assertEquals(new BigDecimal("31.000000"), view.getMeasuredHeightCm());
        assertEquals(new BigDecimal("0.051336"), view.getMeasuredVolumeCbm());
        assertEquals("已封箱", view.getPackageStatus());
        assertEquals("发往海外", view.getLogisticsStatus());
    }

    @Test
    void shouldCreatePackageAndAttachLineWhenBoxNoIsProvided() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("XGGEUAE04029-1");
        command.setExternalBoxNo("X25090553672");
        command.setPackageTrackingNo("TRACK-QIKE-1");
        command.setPackageWeightKg(new BigDecimal("12.500000"));
        command.setPackageLengthCm(new BigDecimal("45.000000"));
        command.setPackageWidthCm(new BigDecimal("35.000000"));
        command.setPackageHeightCm(new BigDecimal("30.000000"));
        command.setPackageVolumeCbm(new BigDecimal("0.047250"));
        command.setPackageVolumeWeightKg(new BigDecimal("7.900000"));
        command.setPackageChargeableWeightKg(new BigDecimal("12.500000"));
        command.setMeasuredWeightKg(new BigDecimal("12.800000"));
        command.setMeasuredLengthCm(new BigDecimal("46.000000"));
        command.setMeasuredWidthCm(new BigDecimal("36.000000"));
        command.setMeasuredHeightCm(new BigDecimal("31.000000"));
        command.setMeasuredVolumeCbm(new BigDecimal("0.051336"));
        command.setPackageStatus("已封箱");
        command.setLogisticsStatus("发往海外");
        command.setSku("SKU-QIKE-001");
        command.setPsku("PSKU-QIKE-001");
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
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(1, 1, 20, 0, 20, null, null, null));

        LineView result = service.saveLine(command);

        assertEquals(58001L, result.getPackageId());
        assertEquals("XGGEUAE04029-1", result.getBoxNo());
        ArgumentCaptor<PackageRow> packageCaptor = ArgumentCaptor.forClass(PackageRow.class);
        verify(mapper).insertPackage(packageCaptor.capture());
        assertEquals(58001L, packageCaptor.getValue().getId());
        assertEquals(53001L, packageCaptor.getValue().getBatchId());
        assertEquals("XGGEUAE04029-1", packageCaptor.getValue().getBoxNo());
        assertEquals("X25090553672", packageCaptor.getValue().getExternalBoxNo());
        assertEquals("TRACK-QIKE-1", packageCaptor.getValue().getTrackingNo());
        assertEquals(new BigDecimal("12.500000"), packageCaptor.getValue().getWeightKg());
        assertEquals(new BigDecimal("45.000000"), packageCaptor.getValue().getLengthCm());
        assertEquals(new BigDecimal("35.000000"), packageCaptor.getValue().getWidthCm());
        assertEquals(new BigDecimal("30.000000"), packageCaptor.getValue().getHeightCm());
        assertEquals(new BigDecimal("0.047250"), packageCaptor.getValue().getVolumeCbm());
        assertEquals(new BigDecimal("7.900000"), packageCaptor.getValue().getVolumeWeightKg());
        assertEquals(new BigDecimal("12.500000"), packageCaptor.getValue().getChargeableWeightKg());
        assertEquals(new BigDecimal("12.800000"), packageCaptor.getValue().getMeasuredWeightKg());
        assertEquals(new BigDecimal("46.000000"), packageCaptor.getValue().getMeasuredLengthCm());
        assertEquals(new BigDecimal("36.000000"), packageCaptor.getValue().getMeasuredWidthCm());
        assertEquals(new BigDecimal("31.000000"), packageCaptor.getValue().getMeasuredHeightCm());
        assertEquals(new BigDecimal("0.051336"), packageCaptor.getValue().getMeasuredVolumeCbm());
        assertEquals("已封箱", packageCaptor.getValue().getPackageStatus());
        assertEquals("发往海外", packageCaptor.getValue().getLogisticsStatus());
        ArgumentCaptor<LineRow> lineCaptor = ArgumentCaptor.forClass(LineRow.class);
        verify(mapper).insertLine(lineCaptor.capture());
        assertEquals(58001L, lineCaptor.getValue().getPackageId());
        assertEquals("XGGEUAE04029-1", lineCaptor.getValue().getBoxNo());
    }

    @Test
    void shouldClearStalePackageStatusFieldsWhenFullSyncHasNoStatus() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("XGGEKSA04076-1");
        command.setPackageWeightKg(new BigDecimal("19.900000"));
        command.setPackageLengthCm(new BigDecimal("50.000000"));
        command.setPackageWidthCm(new BigDecimal("41.000000"));
        command.setPackageHeightCm(new BigDecimal("41.000000"));
        command.setPackageVolumeCbm(new BigDecimal("0.084050"));
        command.setPackageVolumeWeightKg(new BigDecimal("14.000000"));
        command.setPackageSnapshotAuthoritative(true);
        command.setSku("SGGRB219");
        command.setPsku("SGGRB219");
        command.setShippedQuantity(30);
        command.setReceivedQuantity(0);

        PackageRow existingPackage = new PackageRow();
        existingPackage.setId(58001L);
        existingPackage.setOwnerUserId(10002L);
        existingPackage.setBatchId(53001L);
        existingPackage.setBoxNo("XGGEKSA04076-1");
        existingPackage.setExternalBoxNo("OLD-BOX");
        existingPackage.setTrackingNo("OLD-TRACK");
        existingPackage.setPackageStatus("旧状态");
        existingPackage.setLogisticsStatus("2");

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "启客", "forwarder_unmatched"));
        when(mapper.selectPackageByBoxNo(10002L, 53001L, "XGGEKSA04076-1")).thenReturn(existingPackage);
        when(mapper.nextLineId()).thenReturn(54003L);
        LineRow persisted = line(54003L, 53001L, "SGGRB219", 30, 0, 30);
        persisted.setPackageId(58001L);
        persisted.setBoxNo("XGGEKSA04076-1");
        when(mapper.selectLineById(10002L, 53001L, 54003L)).thenReturn(persisted);
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(1, 1, 30, 0, 30, null, null, null));

        service.saveLine(command);

        ArgumentCaptor<PackageRow> packageCaptor = ArgumentCaptor.forClass(PackageRow.class);
        verify(mapper).updatePackage(packageCaptor.capture());
        assertNull(packageCaptor.getValue().getExternalBoxNo());
        assertNull(packageCaptor.getValue().getTrackingNo());
        assertNull(packageCaptor.getValue().getPackageStatus());
        assertNull(packageCaptor.getValue().getLogisticsStatus());
    }

    @Test
    void shouldPreserveExistingPackageSpecsWhenAuthoritativeSyncHasNoSpecs() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("24-1");
        command.setExternalBoxNo("X26043050648");
        command.setPackageSnapshotAuthoritative(true);
        command.setSku("PAPERSAYSB293");
        command.setPsku("PAPERSAYSB293");
        command.setShippedQuantity(48);
        command.setReceivedQuantity(0);

        PackageRow existingPackage = new PackageRow();
        existingPackage.setId(58002L);
        existingPackage.setOwnerUserId(10002L);
        existingPackage.setBatchId(53001L);
        existingPackage.setBoxNo("24-1");
        existingPackage.setExternalBoxNo("X26043050648");
        existingPackage.setWeightKg(new BigDecimal("7.000000"));
        existingPackage.setLengthCm(new BigDecimal("40.000000"));
        existingPackage.setWidthCm(new BigDecimal("40.000000"));
        existingPackage.setHeightCm(new BigDecimal("30.000000"));
        existingPackage.setVolumeCbm(new BigDecimal("0.048000"));
        existingPackage.setChargeableWeightKg(null);
        existingPackage.setMeasuredWeightKg(new BigDecimal("42.240000"));
        existingPackage.setMeasuredLengthCm(new BigDecimal("68.600000"));
        existingPackage.setMeasuredWidthCm(new BigDecimal("50.600000"));
        existingPackage.setMeasuredHeightCm(new BigDecimal("29.700000"));
        existingPackage.setMeasuredVolumeCbm(new BigDecimal("0.103000"));

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "易通", "forwarder_matched"));
        when(mapper.selectPackageByBoxNo(10002L, 53001L, "24-1")).thenReturn(existingPackage);
        when(mapper.nextLineId()).thenReturn(54005L);
        LineRow persisted = line(54005L, 53001L, "PAPERSAYSB293", 48, 0, 48);
        persisted.setPackageId(58002L);
        persisted.setBoxNo("24-1");
        when(mapper.selectLineById(10002L, 53001L, 54005L)).thenReturn(persisted);
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(1, 1, 48, 0, 48, null, new BigDecimal("7.000000"), new BigDecimal("0.048000")));

        service.saveLine(command);

        ArgumentCaptor<PackageRow> packageCaptor = ArgumentCaptor.forClass(PackageRow.class);
        verify(mapper).updatePackage(packageCaptor.capture());
        assertEquals(new BigDecimal("7.000000"), packageCaptor.getValue().getWeightKg());
        assertEquals(new BigDecimal("40.000000"), packageCaptor.getValue().getLengthCm());
        assertEquals(new BigDecimal("40.000000"), packageCaptor.getValue().getWidthCm());
        assertEquals(new BigDecimal("30.000000"), packageCaptor.getValue().getHeightCm());
        assertEquals(new BigDecimal("0.048000"), packageCaptor.getValue().getVolumeCbm());
        assertEquals(new BigDecimal("7.000000"), packageCaptor.getValue().getChargeableWeightKg());
        assertEquals(new BigDecimal("42.240000"), packageCaptor.getValue().getMeasuredWeightKg());
        assertEquals(new BigDecimal("68.600000"), packageCaptor.getValue().getMeasuredLengthCm());
        assertEquals(new BigDecimal("50.600000"), packageCaptor.getValue().getMeasuredWidthCm());
        assertEquals(new BigDecimal("29.700000"), packageCaptor.getValue().getMeasuredHeightCm());
        assertEquals(new BigDecimal("0.103000"), packageCaptor.getValue().getMeasuredVolumeCbm());
    }

    @Test
    void shouldSoftDeleteStaleSyncedPackagesAndLinesThenRefreshAggregate() {
        BatchAggregateRow aggregate = aggregate(1, 1, 120, 0, 120, null, null, null);
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate);

        service.reconcileSyncedDetails(
                10002L,
                90001L,
                53001L,
                List.of("XGGEUAE04029-1"),
                List.of("XGGEUAE04029-1\nPAPERSAYSB011")
        );

        verify(mapper).softDeleteLinesNotInSyncedDetails(
                10002L,
                53001L,
                List.of("XGGEUAE04029-1"),
                List.of("XGGEUAE04029-1\nPAPERSAYSB011"),
                90001L
        );
        verify(mapper).softDeletePackagesNotInSyncedBoxes(
                10002L,
                53001L,
                List.of("XGGEUAE04029-1"),
                90001L
        );
        verify(mapper).refreshBatchAggregate(eq(10002L), eq(53001L), any(BatchAggregateRow.class));
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
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(0, 0, null, null, null, null, null, null));
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
        command.setBoxNo("BATCH-001-BOX-1");
        command.setSku("SKU-AE-001");
        command.setPsku("PSKU-AE-001");
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
        command.setBoxNo("BATCH-001-BOX-1");
        command.setSku("SKU-AE-002");
        command.setPsku("PSKU-AE-002");
        command.setShippedQuantity(3);
        command.setReceivedQuantity(0);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "draft", "历史货代A", "forwarder_unmatched"));
        when(mapper.nextLineId()).thenReturn(54002L);
        when(mapper.selectLineById(10002L, 53001L, 54002L)).thenReturn(line(54002L, 53001L, "SKU-AE-002", 3, 0, 3));
        when(mapper.aggregateLines(10002L, 53001L)).thenReturn(aggregate(1, 1, 3, 0, 3, null, null, null));

        service.saveLine(command);

        ArgumentCaptor<BatchAggregateRow> aggregateCaptor = ArgumentCaptor.forClass(BatchAggregateRow.class);
        verify(mapper).refreshBatchAggregate(eq(10002L), eq(53001L), aggregateCaptor.capture());
        assertEquals(1, aggregateCaptor.getValue().getSkuCount());
        assertEquals(1, aggregateCaptor.getValue().getBoxCount());
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
    void shouldKeepBatchDraftWhenLatestNodeWouldPromoteIncompleteTrackingBatch() {
        SaveNodeCommand command = new SaveNodeCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setNodeStatus("in_transit");
        command.setNodeHappenedAt(LocalDateTime.parse("2026-06-17T13:54:17"));
        command.setDescription("运输中");

        BatchRow incompleteDraft = batch(53001L, "draft", "启客", "forwarder_matched");
        incompleteDraft.setTransportMode(null);
        incompleteDraft.setTargetStoreCode("RUH");
        incompleteDraft.setTargetWarehouseName(null);
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(incompleteDraft);
        when(mapper.nextNodeId()).thenReturn(55003L);
        when(mapper.selectNodeById(10002L, 53001L, 55003L)).thenReturn(node(
                55003L,
                53001L,
                "in_transit",
                LocalDateTime.parse("2026-06-17T13:54:17"),
                "运输中"
        ));
        when(mapper.selectLatestNode(10002L, 53001L)).thenReturn(latestNode(
                "in_transit",
                LocalDateTime.parse("2026-06-17T13:54:17"),
                "运输中"
        ));

        service.saveNode(command);

        ArgumentCaptor<BatchLatestNodeRow> latestCaptor = ArgumentCaptor.forClass(BatchLatestNodeRow.class);
        verify(mapper).refreshBatchLatestNode(eq(10002L), eq(53001L), latestCaptor.capture());
        assertEquals("in_transit", latestCaptor.getValue().getLatestNodeStatus());
        assertEquals("draft", latestCaptor.getValue().getDerivedBatchStatus());
    }

    @Test
    void shouldKeepBatchDraftWhenLatestNodeWouldPromoteBatchWithMissingPackageChargeableWeight() {
        SaveNodeCommand command = new SaveNodeCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setNodeStatus("in_transit");
        command.setNodeHappenedAt(LocalDateTime.parse("2026-06-17T13:54:17"));
        command.setDescription("运输中");

        BatchRow readyExceptPackageChargeable = batch(53001L, "draft", "启客", "forwarder_matched");
        readyExceptPackageChargeable.setTransportMode("AIR");
        readyExceptPackageChargeable.setTargetStoreCode("RUH");
        readyExceptPackageChargeable.setTargetWarehouseName("FBN-RUH");
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(readyExceptPackageChargeable);
        when(mapper.countPackagesWithGoodsLinesMissingChargeable(10002L, 53001L)).thenReturn(16);
        when(mapper.nextNodeId()).thenReturn(55004L);
        when(mapper.selectNodeById(10002L, 53001L, 55004L)).thenReturn(node(
                55004L,
                53001L,
                "in_transit",
                LocalDateTime.parse("2026-06-17T13:54:17"),
                "运输中"
        ));
        when(mapper.selectLatestNode(10002L, 53001L)).thenReturn(latestNode(
                "in_transit",
                LocalDateTime.parse("2026-06-17T13:54:17"),
                "运输中"
        ));

        service.saveNode(command);

        ArgumentCaptor<BatchLatestNodeRow> latestCaptor = ArgumentCaptor.forClass(BatchLatestNodeRow.class);
        verify(mapper).refreshBatchLatestNode(eq(10002L), eq(53001L), latestCaptor.capture());
        assertEquals("in_transit", latestCaptor.getValue().getLatestNodeStatus());
        assertEquals("draft", latestCaptor.getValue().getDerivedBatchStatus());
    }

    @Test
    void shouldUpdateExistingLogisticsNodeAndRefreshLatestBatchStatus() {
        SaveNodeCommand command = new SaveNodeCommand();
        command.setNodeId(55001L);
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setNodeStatus("customs_released");
        command.setNodeHappenedAt(LocalDateTime.parse("2026-06-02T09:30:00"));
        command.setDescription("清关已放行");
        command.setOperatorName("运营B");

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.selectNodeById(10002L, 53001L, 55001L))
                .thenReturn(node(55001L, 53001L, "customs_clearance", LocalDateTime.parse("2026-06-01T10:30:00"), "清关中"))
                .thenReturn(node(55001L, 53001L, "customs_released", LocalDateTime.parse("2026-06-02T09:30:00"), "清关已放行"));
        when(mapper.selectLatestNode(10002L, 53001L)).thenReturn(latestNode("customs_released", LocalDateTime.parse("2026-06-02T09:30:00"), "清关已放行"));

        NodeView result = service.saveNode(command);

        assertEquals(55001L, result.getNodeId());
        assertEquals("customs_released", result.getNodeStatus());
        assertEquals(LocalDateTime.parse("2026-06-02T09:30:00"), result.getNodeHappenedAt());
        assertEquals("清关已放行", result.getDescription());

        ArgumentCaptor<NodeRow> nodeCaptor = ArgumentCaptor.forClass(NodeRow.class);
        verify(mapper).updateNode(nodeCaptor.capture());
        assertEquals(55001L, nodeCaptor.getValue().getId());
        assertEquals("customs_released", nodeCaptor.getValue().getNodeStatus());
        assertEquals(90001L, nodeCaptor.getValue().getUpdatedBy());
        verify(mapper, never()).insertNode(any(NodeRow.class));
        assertAudit("logistics_node_updated", "logistics_node", 55001L);
    }

    @Test
    void shouldSoftDeleteLogisticsNodeAndRefreshLatestBatchStatus() {
        DeleteNodeCommand command = new DeleteNodeCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setNodeId(55001L);

        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch(53001L, "in_transit", "义特物流", "forwarder_matched"));
        when(mapper.selectNodeById(10002L, 53001L, 55001L))
                .thenReturn(node(55001L, 53001L, "exception", LocalDateTime.parse("2026-06-01T10:30:00"), "到港后清关异常"));
        when(mapper.selectLatestNode(10002L, 53001L)).thenReturn(null);
        when(mapper.listNodes(10002L, 53001L)).thenReturn(List.of());

        var result = service.deleteNode(command);

        assertEquals(0, result.getItems().size());
        verify(mapper).deleteNode(10002L, 53001L, 55001L, 90001L);
        ArgumentCaptor<BatchLatestNodeRow> latestCaptor = ArgumentCaptor.forClass(BatchLatestNodeRow.class);
        verify(mapper).refreshBatchLatestNode(eq(10002L), eq(53001L), latestCaptor.capture());
        assertNull(latestCaptor.getValue().getLatestNodeStatus());
        assertEquals("draft", latestCaptor.getValue().getDerivedBatchStatus());
        assertAudit("logistics_node_deleted", "logistics_node", 55001L);
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
        row.setStandardForwarderName("forwarder_matched".equals(qualityStatus) ? "义特" : null);
        row.setRawForwarderName(rawForwarderName);
        row.setNormalizedRawForwarderName(rawForwarderName == null ? null : rawForwarderName.toLowerCase());
        row.setForwarderQualityStatus(qualityStatus);
        row.setTransportMode("AIR");
        row.setBatchStatus(status);
        row.setTargetStoreCode("DB");
        row.setTargetSiteCode("AE");
        row.setTargetWarehouseName("FBN-DXB");
        row.setDepartureDate(LocalDate.parse("2026-05-20"));
        row.setEtaDate(LocalDate.parse("2026-06-08"));
        row.setTrackingNo("TRK-001");
        row.setContainerNo("CONT-001");
        row.setBatchReferenceNo("BATCH-001");
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
        return row;
    }

    private BatchAggregateRow aggregate(
            Integer skuCount,
            Integer boxCount,
            Integer shippedQuantityTotal,
            Integer receivedQuantityTotal,
            Integer remainingQuantityTotal,
            Integer cartonCountTotal,
            BigDecimal totalWeightKg,
            BigDecimal totalVolumeCbm
    ) {
        BatchAggregateRow row = new BatchAggregateRow();
        row.setSkuCount(skuCount);
        row.setBoxCount(boxCount);
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
