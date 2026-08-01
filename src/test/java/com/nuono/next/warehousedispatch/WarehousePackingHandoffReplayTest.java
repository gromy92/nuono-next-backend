package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePackingHandoffReplayTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void linkedLegacyReplayAcceptsTheOldAtomicHandoffReceipt() {
        stubLinkedChain("SHIPPED", "SHIPPED");
        when(mapper.selectLegacyDispatchPlanHandoffReceiptId(
                340001L,
                "WDH-340001-1"
        )).thenReturn(390001L);

        var view = service.shipPackingList(access(), "830001");

        assertThat(view.status).isEqualTo("SHIPPED");
        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void linkedLegacyReplayWithoutAnAtomicReceiptFailsClosed() {
        stubLinkedChain("SHIPPED", "SHIPPED");

        assertThatThrownBy(() -> service.shipPackingList(access(), "830001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("库存交接凭证");

        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
    }

    @Test
    void linkedLegacyInProgressCompletesDocumentsWithoutMovingInventoryAgain() {
        stubLinkedChain("CONFIRMED", "PACKED");
        when(mapper.selectLegacyDispatchPlanHandoffReceiptId(
                340001L,
                "WDH-340001-1"
        )).thenReturn(390000L);
        when(mapper.shipPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderShipped(800001L, 307L, 307L)).thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(390001L);
        when(mapper.insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        )).thenReturn(1);

        var view = service.shipPackingList(access(), "830001");

        assertThat(view.status).isEqualTo("SHIPPED");
        verify(mapper).shipPackingList(830001L, 307L, 307L);
        verify(mapper).markOutboundOrderShipped(800001L, 307L, 307L);
        verify(mapper, never()).markDispatchPlanHandoffSuccess(
                anyLong(), anyLong(), anyString(), anyLong()
        );
        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
        verify(mapper).insertOperationLog(
                eq(390001L),
                eq(340001L),
                eq("INVENTORY_HANDOFF_COMPLETED"),
                eq(307L),
                eq("CONFIRMED"),
                eq("SHIPPED"),
                contains("LEGACY_HANDOFF_RECEIPT")
        );
    }

    private void stubLinkedChain(String packingStatus, String outboundStatus) {
        PackingListRecord packing = packingList();
        packing.status = packingStatus;
        packing.packedQuantity = 5;
        OutboundOrderRecord outbound = outboundOrder();
        outbound.status = outboundStatus;
        ShippingBatchRecord batch = shippingBatch();
        batch.dispatchPlanId = 340001L;
        DispatchPlanRecord plan = dispatchPlan();

        when(mapper.selectPackingListById(830001L)).thenReturn(packing);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outbound);
        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(plan);
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outbound);
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packing);

        OutboundOrderLineRecord line = outboundOrderLine();
        line.quantity = 5;
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listOutboundOrderLineSources(800001L))
                .thenReturn(List.of(outboundOrderLineSource()));
        when(mapper.listShippingBatchSources(700001L))
                .thenReturn(List.of(shippingBatchSource()));
        when(mapper.listDispatchLineSources(340001L))
                .thenReturn(List.of(dispatchSource()));
    }

    private DispatchPlanRecord dispatchPlan() {
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = 340001L;
        plan.ownerUserId = 307L;
        plan.status = "LOGISTICS_REQUESTED";
        plan.totalQuantity = 5;
        plan.handoffRequestNo = "WDH-340001-1";
        return plan;
    }

    private DispatchPlanLineSourceRecord dispatchSource() {
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.id = 360001L;
        source.dispatchPlanId = 340001L;
        source.ownerUserId = 307L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        return source;
    }
}
