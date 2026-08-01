package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePackingHandoffOperationsTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void shipPackingListMovesAggregatedOutboundInventoryBeforeLoggingSuccess() {
        PackingListRecord packingList = confirmedPackingList();
        OutboundOrderRecord outboundOrder = packedOutboundOrder();
        packingList.packedQuantity = 7;
        outboundOrder.totalQuantity = 7;
        OutboundOrderLineSourceRecord first = source(825001L, 900002L, 2);
        OutboundOrderLineSourceRecord second = source(825002L, 900001L, 3);
        OutboundOrderLineSourceRecord third = source(825003L, 900001L, 2);
        OutboundOrderLineRecord line = outboundOrderLine();
        line.quantity = 7;

        stubHandoffDocuments(packingList, outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listOutboundOrderLineSources(800001L)).thenReturn(List.of(first, second, third));
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(
                batchSource(760001L, 900001L, 5),
                batchSource(760002L, 900002L, 2)
        ));
        when(mapper.selectBalancesForUpdate(List.of(900001L, 900002L))).thenReturn(List.of(
                balanceWithReserved(900001L, 5),
                balanceWithReserved(900002L, 2)
        ));
        when(mapper.shipPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderShipped(800001L, 307L, 307L)).thenReturn(1);
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(1);
        when(mapper.moveReservedToLogisticsHandoff(900002L, 2, 307L)).thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(390001L);
        when(mapper.insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        )).thenReturn(1);

        var view = service.shipPackingList(access(), "830001");

        assertThat(view.status).isEqualTo("SHIPPED");
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectPackingListById(830001L);
        order.verify(mapper).selectOutboundOrderById(800001L);
        order.verify(mapper).selectShippingBatchById(700001L);
        order.verify(mapper).selectShippingBatchByIdForUpdate(700001L);
        order.verify(mapper).selectOutboundOrderByIdForUpdate(800001L);
        order.verify(mapper).selectPackingListByIdForUpdate(830001L);
        order.verify(mapper).listOutboundOrderLines(800001L);
        order.verify(mapper).listOutboundOrderLineSources(800001L);
        order.verify(mapper).listShippingBatchSources(700001L);
        order.verify(mapper).selectBalancesForUpdate(List.of(900001L, 900002L));
        order.verify(mapper).shipPackingList(830001L, 307L, 307L);
        order.verify(mapper).markOutboundOrderShipped(800001L, 307L, 307L);
        order.verify(mapper).moveReservedToLogisticsHandoff(900001L, 5, 307L);
        order.verify(mapper).moveReservedToLogisticsHandoff(900002L, 2, 307L);
        order.verify(mapper).insertOperationLog(
                eq(390001L),
                eq((Long) null),
                eq("INVENTORY_HANDOFF_COMPLETED"),
                eq(307L),
                eq("CONFIRMED"),
                eq("SHIPPED"),
                anyString()
        );
    }

    @Test
    void shipPackingListRejectsMissingOutboundSourcesBeforeChangingState() {
        stubHandoffDocuments(confirmedPackingList(), packedOutboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.listOutboundOrderLineSources(800001L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.shipPackingList(access(), "830001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("交接库存状态已变化");

        verify(mapper, never()).shipPackingList(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).markOutboundOrderShipped(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shipPackingListRejectsUnmovedReservedQuantityWithoutSuccessAudit() {
        OutboundOrderLineSourceRecord source = source(825001L, 900001L, 5);
        stubHandoffDocuments(confirmedPackingList(), packedOutboundOrder());
        OutboundOrderLineRecord line = outboundOrderLine();
        line.quantity = 5;
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listOutboundOrderLineSources(800001L)).thenReturn(List.of(source));
        when(mapper.listShippingBatchSources(700001L))
                .thenReturn(List.of(batchSource(760001L, 900001L, 5)));
        when(mapper.selectBalancesForUpdate(List.of(900001L)))
                .thenReturn(List.of(balanceWithReserved(900001L, 5)));
        when(mapper.shipPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderShipped(800001L, 307L, 307L)).thenReturn(1);
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(0);

        assertThatThrownBy(() -> service.shipPackingList(access(), "830001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("交接库存状态已变化");

        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shippedReplayWithCurrentReceiptReturnsWithoutMovingInventoryOrAuditingAgain() {
        PackingListRecord packingList = confirmedPackingList();
        packingList.status = "SHIPPED";
        OutboundOrderRecord outboundOrder = packedOutboundOrder();
        outboundOrder.status = "SHIPPED";
        stubHandoffDocuments(packingList, outboundOrder);
        stubValidHandoffSnapshot();
        when(mapper.selectInventoryHandoffCompletionReceiptId(
                null, 700001L, 800001L, 830001L
        )).thenReturn(390001L);

        var view = service.shipPackingList(access(), "830001");

        assertThat(view.status).isEqualTo("SHIPPED");
        verify(mapper).listOutboundOrderLineSources(800001L);
        verify(mapper).listShippingBatchSources(700001L);
        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shippedStandaloneWithoutCurrentReceiptFailsClosed() {
        PackingListRecord packingList = confirmedPackingList();
        packingList.status = "SHIPPED";
        OutboundOrderRecord outboundOrder = packedOutboundOrder();
        outboundOrder.status = "SHIPPED";
        stubHandoffDocuments(packingList, outboundOrder);
        stubValidHandoffSnapshot();

        assertThatThrownBy(() -> service.shipPackingList(access(), "830001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("库存交接凭证");

        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
    }

    @Test
    void shippedReplayRejectsChangedSourceSnapshotWithoutMovingInventory() {
        PackingListRecord packingList = confirmedPackingList();
        packingList.status = "SHIPPED";
        OutboundOrderRecord outboundOrder = packedOutboundOrder();
        outboundOrder.status = "SHIPPED";
        stubHandoffDocuments(packingList, outboundOrder);
        OutboundOrderLineRecord line = outboundOrderLine();
        line.quantity = 5;
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listOutboundOrderLineSources(800001L))
                .thenReturn(List.of(source(825001L, 900001L, 5)));
        when(mapper.listShippingBatchSources(700001L))
                .thenReturn(List.of(batchSource(760001L, 900001L, 4)));

        assertThatThrownBy(() -> service.shipPackingList(access(), "830001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("交接库存状态已变化");

        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shippedReplayRejectsInconsistentOutboundStateWithoutMovingInventory() {
        PackingListRecord packingList = confirmedPackingList();
        packingList.status = "SHIPPED";
        stubHandoffDocuments(packingList, packedOutboundOrder());

        assertThatThrownBy(() -> service.shipPackingList(access(), "830001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("状态不一致");

        verify(mapper, never()).listOutboundOrderLineSources(anyLong());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
    }

    private PackingListRecord confirmedPackingList() {
        PackingListRecord packingList = packingList();
        packingList.status = "CONFIRMED";
        packingList.packedQuantity = 5;
        return packingList;
    }

    private OutboundOrderRecord packedOutboundOrder() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        outboundOrder.status = "PACKED";
        return outboundOrder;
    }

    private OutboundOrderLineSourceRecord source(Long id, Long balanceId, int quantity) {
        OutboundOrderLineSourceRecord source = outboundOrderLineSource();
        source.id = id;
        source.fulfillmentBalanceId = balanceId;
        source.quantity = quantity;
        return source;
    }

    private FulfillmentBalanceRecord balanceWithReserved(Long id, int reservedQuantity) {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.id = id;
        balance.reservedQuantity = reservedQuantity;
        return balance;
    }

    private void stubHandoffDocuments(
            PackingListRecord packingList,
            OutboundOrderRecord outboundOrder
    ) {
        var batch = shippingBatch();
        batch.totalQuantity = outboundOrder.totalQuantity;
        when(mapper.selectPackingListById(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
    }

    private void stubValidHandoffSnapshot() {
        OutboundOrderLineRecord line = outboundOrderLine();
        line.quantity = 5;
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listOutboundOrderLineSources(800001L))
                .thenReturn(List.of(source(825001L, 900001L, 5)));
        when(mapper.listShippingBatchSources(700001L))
                .thenReturn(List.of(batchSource(760001L, 900001L, 5)));
    }

    private ShippingBatchSourceRecord batchSource(Long id, Long balanceId, int quantity) {
        ShippingBatchSourceRecord source = shippingBatchSource();
        source.id = id;
        source.fulfillmentBalanceId = balanceId;
        source.reservedQuantity = quantity;
        return source;
    }
}
