package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ReassignShippingOrderLinesCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehouseShippingLineReassignmentServiceTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseShippingLineReassignmentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        service = new WarehouseShippingLineReassignmentService(mapper, new ObjectMapper());
    }

    @Test
    void reassignsLinesToExistingUnsubmittedSegmentAndRebuildsSummaries() {
        ShippingOrderRecord order = order("NOT_SUBMITTED");
        ShippingOrderLineRecord source = line(291001L, 292001L, "AIR");
        ShippingOrderLineRecord refreshed = line(291001L, 292002L, "SEA");
        ShippingOrderSegmentRecord target = segment(292002L, "SEA", "NOT_SUBMITTED");
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);
        when(mapper.listShippingOrderLines(290001L))
                .thenReturn(List.of(source))
                .thenReturn(List.of(refreshed));
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(target));
        when(mapper.reassignShippingOrderLines(
                290001L, 307L, List.of(291001L), 292002L, "SEA", 307L
        )).thenReturn(1);
        when(mapper.resetShippingOrderSegmentsAfterReassignment(
                290001L, List.of(292001L, 292002L), 307L
        )).thenReturn(2);
        when(mapper.recalculateShippingOrderSegmentAggregates(290001L, 307L)).thenReturn(1);
        when(mapper.updateShippingOrderTransportSummary(
                290001L, 307L, "{\"SEA\":20}", 307L
        )).thenReturn(1);
        when(mapper.refreshShippingOrderHeaderState(290001L, 307L, 307L)).thenReturn(1);

        service.reassign(order, command("292002", "SEA"), 307L);

        verify(mapper).softDeleteEmptyShippingOrderSegments(290001L, 307L);
        verify(mapper).recalculateShippingOrderSegmentAggregates(290001L, 307L);
        verify(mapper).updateShippingOrderTransportSummary(
                eq(290001L), eq(307L), eq("{\"SEA\":20}"), eq(307L)
        );
        verify(mapper).resetShippingOrderSegmentsAfterReassignment(
                290001L, List.of(292001L, 292002L), 307L
        );
        verify(mapper).refreshShippingOrderHeaderState(290001L, 307L, 307L);
    }

    @Test
    void createsANewTransportSegmentWhenTargetIsOmitted() {
        ShippingOrderRecord order = order("NOT_SUBMITTED");
        ShippingOrderLineRecord source = line(291001L, 292001L, "AIR");
        ShippingOrderLineRecord refreshed = line(291001L, 292003L, "SEA");
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);
        when(mapper.listShippingOrderLines(290001L))
                .thenReturn(List.of(source))
                .thenReturn(List.of(refreshed));
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of());
        when(mapper.nextShippingOrderSegmentId()).thenReturn(292003L);
        when(mapper.insertShippingOrderSegment(any(ShippingOrderSegmentRecord.class), eq(307L))).thenReturn(1);
        when(mapper.reassignShippingOrderLines(
                290001L, 307L, List.of(291001L), 292003L, "SEA", 307L
        )).thenReturn(1);
        when(mapper.resetShippingOrderSegmentsAfterReassignment(
                290001L, List.of(292001L, 292003L), 307L
        )).thenReturn(2);
        when(mapper.recalculateShippingOrderSegmentAggregates(290001L, 307L)).thenReturn(1);
        when(mapper.updateShippingOrderTransportSummary(
                290001L, 307L, "{\"SEA\":20}", 307L
        )).thenReturn(1);
        when(mapper.refreshShippingOrderHeaderState(290001L, 307L, 307L)).thenReturn(1);

        service.reassign(order, command(null, "SEA"), 307L);

        ArgumentCaptor<ShippingOrderSegmentRecord> inserted =
                ArgumentCaptor.forClass(ShippingOrderSegmentRecord.class);
        verify(mapper).insertShippingOrderSegment(inserted.capture(), eq(307L));
        assertThat(inserted.getValue()).satisfies(segment -> {
            assertThat(segment.id).isEqualTo(292003L);
            assertThat(segment.siteCode).isEqualTo("SA");
            assertThat(segment.transportMode).isEqualTo("SEA");
            assertThat(segment.shippingSubmitStatus).isEqualTo("NOT_SUBMITTED");
        });
    }

    @Test
    void rejectsSubmittedOrderBeforeChangingLines() {
        ShippingOrderRecord order = order("SUBMITTED");
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);

        assertThatThrownBy(() -> service.reassign(order, command(null, "SEA"), 307L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有未提交发货");

        verify(mapper, never()).reassignShippingOrderLines(
                eq(290001L), eq(307L), eq(List.of(291001L)), eq(292003L), eq("SEA"), eq(307L)
        );
        verify(mapper, never()).updateShippingOrderTransportSummary(
                eq(290001L), eq(307L), anyString(), eq(307L)
        );
    }

    @Test
    void rejectsUnknownOrderLifecycleBeforeChangingLines() {
        ShippingOrderRecord order = order("FUTURE_STATE");
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);

        assertThatThrownBy(() -> service.reassign(order, command(null, "SEA"), 307L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有未提交发货");

        verify(mapper, never()).reassignShippingOrderLines(
                eq(290001L), eq(307L), eq(List.of(291001L)), eq(292003L), eq("SEA"), eq(307L)
        );
    }

    @Test
    void rejectsUnknownLineLifecycleBeforeChangingSegments() {
        ShippingOrderRecord order = order("NOT_SUBMITTED");
        ShippingOrderLineRecord source = line(291001L, 292001L, "AIR");
        source.shippingSubmitStatus = "FUTURE_STATE";
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of(source));

        assertThatThrownBy(() -> service.reassign(order, command(null, "SEA"), 307L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有未提交仓库");

        verify(mapper, never()).reassignShippingOrderLines(
                eq(290001L), eq(307L), eq(List.of(291001L)), eq(292003L), eq("SEA"), eq(307L)
        );
    }

    @Test
    void rejectsUnknownTargetSegmentLifecycle() {
        ShippingOrderRecord order = order("NOT_SUBMITTED");
        ShippingOrderLineRecord source = line(291001L, 292001L, "AIR");
        ShippingOrderSegmentRecord target = segment(292002L, "SEA", "FUTURE_STATE");
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of(source));
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.reassign(order, command("292002", "SEA"), 307L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有未提交的目标分区");
    }

    private static ReassignShippingOrderLinesCommand command(String targetSegmentId, String mode) {
        ReassignShippingOrderLinesCommand command = new ReassignShippingOrderLinesCommand();
        command.lineIds = List.of("291001");
        command.targetSegmentId = targetSegmentId;
        command.targetTransportMode = mode;
        return command;
    }

    private static ShippingOrderRecord order(String submitStatus) {
        ShippingOrderRecord order = new ShippingOrderRecord();
        order.id = 290001L;
        order.ownerUserId = 307L;
        order.shippingOrderNo = "SO-290001";
        order.shippingSubmitStatus = submitStatus;
        return order;
    }

    private static ShippingOrderLineRecord line(Long id, Long segmentId, String mode) {
        ShippingOrderLineRecord line = new ShippingOrderLineRecord();
        line.id = id;
        line.shippingOrderSegmentId = segmentId;
        line.siteCode = "SA";
        line.plannedTransportMode = mode;
        line.shippingSubmitStatus = "NOT_SUBMITTED";
        line.quantity = 20;
        return line;
    }

    private static ShippingOrderSegmentRecord segment(Long id, String mode, String submitStatus) {
        ShippingOrderSegmentRecord segment = new ShippingOrderSegmentRecord();
        segment.id = id;
        segment.siteCode = "SA";
        segment.transportMode = mode;
        segment.shippingSubmitStatus = submitStatus;
        return segment;
    }
}
