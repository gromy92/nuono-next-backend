package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePackingConcurrencyGuardTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void replaceLocksPackingListBeforeReadingOrReplacingDetails() {
        PackingListRecord packingList = packingList();
        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        command.boxes = List.of();
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.updatePackingListTotals(
                anyLong(), anyLong(), anyInt(), anyInt(), eq(BigDecimal.ZERO),
                eq(new BigDecimal("0.0000")), eq((String) null), anyLong()
        )).thenReturn(1);

        service.replacePackingBoxes(access(), "830001", command);

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectPackingListByIdForUpdate(830001L);
        order.verify(mapper).softDeletePackingBoxItems(830001L, 307L);
        order.verify(mapper).softDeletePackingBoxes(830001L, 307L);
    }

    @Test
    void replaceRejectsLostDraftTotalsUpdate() {
        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        command.boxes = List.of();
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList());
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.updatePackingListTotals(
                anyLong(), anyLong(), anyInt(), anyInt(), eq(BigDecimal.ZERO),
                eq(new BigDecimal("0.0000")), eq((String) null), anyLong()
        )).thenReturn(0);

        assertThatThrownBy(() -> service.replacePackingBoxes(access(), "830001", command))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessage("装箱单状态已变化，请刷新后重试。");
    }

    @Test
    void saveRejectsConfirmedListBeforeReadingStoredDetails() {
        PackingListRecord packingList = packingList();
        packingList.status = "CONFIRMED";
        PackingBoxCommand command = new PackingBoxCommand();
        command.boxNo = "箱1";
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);

        assertThatThrownBy(() -> service.savePackingBox(access(), "830001", "箱1", command))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessage("只有草稿装箱单可以修改箱明细。");

        verify(mapper, never()).listPackingBoxes(830001L);
        verify(mapper, never()).listPackingBoxItems(830001L);
    }

    @Test
    void confirmLocksOutboundBeforePackingAndReadingStoredDetails() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        when(mapper.selectPackingListById(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(packingBox(null)));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(packingBoxItem()));
        when(mapper.confirmPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderPacked(800001L, 307L, 307L)).thenReturn(1);

        service.confirmPackingList(access(), "830001");

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectPackingListById(830001L);
        order.verify(mapper).selectOutboundOrderByIdForUpdate(800001L);
        order.verify(mapper).selectPackingListByIdForUpdate(830001L);
        order.verify(mapper).listPackingBoxes(830001L);
        order.verify(mapper).listPackingBoxItems(830001L);
        order.verify(mapper).confirmPackingList(830001L, 307L, 307L);
    }
}
