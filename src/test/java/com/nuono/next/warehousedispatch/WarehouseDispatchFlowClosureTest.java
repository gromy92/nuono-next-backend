package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.IssueShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseDispatchFlowClosureTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void listsDispatchPlanWithItsCurrentShippingBatch() {
        DispatchPlanRecord plan = dispatchPlan("READY_FOR_LOGISTICS");
        ShippingBatchRecord batch = linkedBatch();
        batch.optionCount = 3;
        when(mapper.listDispatchPlans(anyMap())).thenReturn(List.of(plan));
        when(mapper.listLatestShippingBatchSummariesByDispatchPlanIds(
                eq(List.of(340001L)), anyMap()
        )).thenReturn(List.of(batch));

        var plans = service.listDispatchPlans(access());

        assertThat(plans).singleElement().satisfies(view -> {
            assertThat(view.currentShippingBatch).isNotNull();
            assertThat(view.currentShippingBatch.dispatchPlanId).isEqualTo("340001");
            assertThat(view.currentShippingBatch.optionCount).isEqualTo(3);
        });
    }

    @Test
    void createsOneLinkedShippingBatchWithoutMovingInventory() {
        DispatchPlanRecord plan = dispatchPlan("DRAFT");
        plan.handoffGenerationNo = 0;
        plan.handoffRequestNo = null;
        DispatchPlanLineRecord line = new DispatchPlanLineRecord();
        line.id = 350001L;
        line.dispatchPlanId = plan.id;
        line.ownerUserId = plan.ownerUserId;
        line.siteCode = "SA";
        line.actualTransportMode = "AIR";
        line.quantity = 5;
        DispatchPlanLineSourceRecord source = dispatchSource();
        var balance = balance("CONFIRMED", "SUBMITTED");
        balance.reservedQuantity = 5;

        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(plan);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of(line));
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextShippingBatchId()).thenReturn(700001L);
        when(mapper.nextShippingBatchSourceId()).thenReturn(760001L);
        when(mapper.nextShippingSuggestionOptionId())
                .thenReturn(710001L, 710002L, 710003L, 710004L, 710005L);
        when(mapper.nextShippingSuggestionLineId())
                .thenReturn(720001L, 720002L, 720003L, 720004L, 720005L);
        when(mapper.nextShippingSuggestionLineSourceId())
                .thenReturn(730001L, 730002L, 730003L, 730004L, 730005L);
        when(mapper.updateDispatchPlanReady(340001L, 307L, 1, "WDH-340001-1", 307L)).thenReturn(1);

        var view = service.createShippingBatchFromDispatchPlan(access(), "340001");

        assertThat(view.dispatchPlanId).isEqualTo("340001");
        verify(mapper).insertShippingBatch(
                org.mockito.ArgumentMatchers.argThat(batch ->
                        batch.dispatchPlanId.equals(340001L) && batch.totalQuantity == 5
                ),
                eq(307L)
        );
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
    }

    @Test
    void linkedPlanCannotReturnToDraft() {
        DispatchPlanRecord plan = dispatchPlan("READY_FOR_LOGISTICS");
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(plan);
        when(mapper.selectLatestShippingBatchByDispatchPlan(340001L))
                .thenReturn(linkedBatch());

        assertThatThrownBy(() -> service.reopenDraft(access(), "340001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessage("发运计划已生成物流批次，不能退回草稿。");

        verify(mapper, never()).reopenDispatchPlanDraft(
                anyLong(), anyLong(), anyLong()
        );
    }

    @Test
    void issuingCreatesOutboundAndPackingDocumentsWithoutMovingInventory() {
        ShippingBatchRecord batch = linkedBatch();
        batch.status = "OPTION_SELECTED";
        ShippingSuggestionOptionRecord option = selectedOption();
        ShippingBatchSourceRecord batchSource = shippingBatchSource();
        ShippingSuggestionLineRecord optionLine = shippingSuggestionLine();
        ShippingSuggestionLineSourceRecord optionSource = shippingSuggestionLineSource();
        OutboundOrderRecord outboundOrder = outboundOrder();
        DispatchPlanRecord plan = dispatchPlan("READY_FOR_LOGISTICS");
        IssueShippingBatchCommand command = new IssueShippingBatchCommand();
        command.optionId = String.valueOf(option.id);

        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectShippingSuggestionOptionById(option.id)).thenReturn(option);
        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.listOutboundOrdersByBatch(eq(700001L), anyMap())).thenReturn(List.of());
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(batchSource));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(optionLine));
        when(mapper.listShippingSuggestionLineSources(700001L)).thenReturn(List.of(optionSource));
        when(mapper.nextOutboundOrderId()).thenReturn(800001L);
        when(mapper.nextOutboundOrderLineId()).thenReturn(820001L);
        when(mapper.nextOutboundOrderLineSourceId()).thenReturn(825001L);
        when(mapper.updateShippingBatchOutboundCreated(700001L, 307L, 710001L, 307L))
                .thenReturn(1);
        when(mapper.listPackingListsByOutboundOrder(eq(800001L), anyMap()))
                .thenReturn(List.of());
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.nextPackingListId()).thenReturn(830001L);
        when(mapper.markOutboundOrderPacking(800001L, 307L, 307L)).thenReturn(1);
        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);

        var view = service.issueShippingBatch(access(), "700001", command);

        assertThat(view.outboundOrders).hasSize(1);
        assertThat(view.packingLists).hasSize(1);
        verify(mapper).insertOutboundOrder(any(OutboundOrderRecord.class), eq(307L));
        verify(mapper).insertPackingList(any(PackingListRecord.class), eq(307L));
        verify(mapper, never()).markDispatchPlanHandoffSuccess(
                anyLong(), anyLong(), anyString(), anyLong()
        );
        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
    }

    @Test
    void issuingShippingDocumentsNeverAdvancesPlanOrMovesInventory() {
        ShippingBatchRecord batch = linkedBatch();
        batch.status = "OUTBOUND_CREATED";
        ShippingSuggestionOptionRecord option = selectedOption();
        PackingListRecord packingList = packingList();
        DispatchPlanRecord plan = dispatchPlan("READY_FOR_LOGISTICS");
        IssueShippingBatchCommand command = new IssueShippingBatchCommand();
        command.optionId = String.valueOf(option.id);

        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectShippingSuggestionOptionById(option.id)).thenReturn(option);
        when(mapper.listOutboundOrdersByBatch(eq(700001L), anyMap()))
                .thenReturn(List.of(outboundOrder()));
        when(mapper.listPackingListsByOutboundOrder(eq(800001L), anyMap()))
                .thenReturn(List.of(packingList));
        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        var view = service.issueShippingBatch(access(), "700001", command);

        assertThat(view.outboundOrders).hasSize(1);
        assertThat(view.packingLists).hasSize(1);
        verify(mapper, never()).markDispatchPlanHandoffSuccess(
                anyLong(), anyLong(), anyString(), anyLong()
        );
        verify(mapper, never()).selectBalancesForUpdate(any());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
    }

    @Test
    void finalPackingHandoffAdvancesLinkedPlanAndMovesInventoryExactlyOnce() {
        PackingListRecord confirmed = packingList();
        confirmed.status = "CONFIRMED";
        confirmed.packedQuantity = 5;
        PackingListRecord shipped = packingList();
        shipped.status = "SHIPPED";
        shipped.packedQuantity = 5;
        OutboundOrderRecord packed = outboundOrder();
        packed.status = "PACKED";
        OutboundOrderRecord shippedOrder = outboundOrder();
        shippedOrder.status = "SHIPPED";
        ShippingBatchRecord batch = linkedBatch();
        DispatchPlanRecord ready = dispatchPlan("READY_FOR_LOGISTICS");
        DispatchPlanRecord requested = dispatchPlan("LOGISTICS_REQUESTED");
        OutboundOrderLineRecord outboundLine = outboundOrderLine();
        outboundLine.quantity = 5;
        OutboundOrderLineSourceRecord outboundSource = outboundOrderLineSource();
        ShippingBatchSourceRecord batchSource = shippingBatchSource();
        var balance = balance("CONFIRMED", "SUBMITTED");
        balance.reservedQuantity = 5;

        when(mapper.selectPackingListById(830001L)).thenReturn(confirmed, shipped);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(packed, shippedOrder);
        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(ready, requested);
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(packed, shippedOrder);
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(confirmed, shipped);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundLine));
        when(mapper.listOutboundOrderLineSources(800001L)).thenReturn(List.of(outboundSource));
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(batchSource));
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(dispatchSource()));
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.shipPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderShipped(800001L, 307L, 307L)).thenReturn(1);
        when(mapper.markDispatchPlanHandoffSuccess(
                340001L, 307L, "WDH-340001-1", 307L
        )).thenReturn(1);
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(390001L);
        when(mapper.insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        )).thenReturn(1);
        when(mapper.selectInventoryHandoffCompletionReceiptId(
                340001L, 700001L, 800001L, 830001L
        )).thenReturn(390001L);

        assertThat(service.shipPackingList(access(), "830001").status).isEqualTo("SHIPPED");
        assertThat(service.shipPackingList(access(), "830001").status).isEqualTo("SHIPPED");

        verify(mapper).shipPackingList(830001L, 307L, 307L);
        verify(mapper).markOutboundOrderShipped(800001L, 307L, 307L);
        verify(mapper).markDispatchPlanHandoffSuccess(
                340001L, 307L, "WDH-340001-1", 307L
        );
        verify(mapper).moveReservedToLogisticsHandoff(900001L, 5, 307L);
        verify(mapper).insertOperationLog(
                eq(390001L), eq(340001L), eq("INVENTORY_HANDOFF_COMPLETED"), eq(307L),
                eq("CONFIRMED"), eq("SHIPPED"), anyString()
        );
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).selectDispatchPlanByIdForUpdate(340001L);
        lockOrder.verify(mapper).selectShippingBatchByIdForUpdate(700001L);
        lockOrder.verify(mapper).selectOutboundOrderByIdForUpdate(800001L);
        lockOrder.verify(mapper).selectPackingListByIdForUpdate(830001L);
        lockOrder.verify(mapper).selectBalancesForUpdate(List.of(900001L));
    }

    private DispatchPlanRecord dispatchPlan(String status) {
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = 340001L;
        plan.ownerUserId = 307L;
        plan.planNo = "DP-340001";
        plan.status = status;
        plan.totalQuantity = 5;
        plan.handoffGenerationNo = 1;
        plan.handoffRequestNo = "WDH-340001-1";
        return plan;
    }

    private DispatchPlanLineSourceRecord dispatchSource() {
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.dispatchPlanId = 340001L;
        source.dispatchPlanLineId = 350001L;
        source.ownerUserId = 307L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        return source;
    }

    private ShippingBatchRecord linkedBatch() {
        ShippingBatchRecord batch = shippingBatch();
        batch.dispatchPlanId = 340001L;
        batch.totalQuantity = 5;
        return batch;
    }
}
