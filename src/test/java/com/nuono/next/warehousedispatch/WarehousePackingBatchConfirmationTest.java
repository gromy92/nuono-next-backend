package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePackingBatchConfirmationTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void confirmsEveryPackingListInOneBatch() {
        PackingListRecord firstList = packingList();
        PackingListRecord secondList = packingList();
        secondList.id = 830002L;
        secondList.outboundOrderId = 800002L;
        secondList.packingNo = "PK-830002";

        OutboundOrderRecord firstOrder = outboundOrder();
        OutboundOrderRecord secondOrder = outboundOrder();
        secondOrder.id = 800002L;
        secondOrder.outboundNo = "OB-800002";

        OutboundOrderLineRecord firstLine = outboundOrderLine();
        OutboundOrderLineRecord secondLine = outboundOrderLine();
        secondLine.id = 820002L;
        secondLine.outboundOrderId = 800002L;

        PackingBoxRecord firstBox = packingBox(null);
        PackingBoxRecord secondBox = packingBox(null);
        secondBox.id = 840002L;
        secondBox.packingListId = 830002L;
        secondBox.outboundOrderId = 800002L;
        secondBox.boxNo = "箱2";

        PackingBoxItemRecord firstItem = packingBoxItem();
        PackingBoxItemRecord secondItem = packingBoxItem();
        secondItem.id = 850002L;
        secondItem.packingListId = 830002L;
        secondItem.packingBoxId = 840002L;
        secondItem.outboundOrderId = 800002L;
        secondItem.outboundOrderLineId = 820002L;

        when(mapper.selectPackingListById(830001L)).thenReturn(firstList);
        when(mapper.selectPackingListById(830002L)).thenReturn(secondList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(firstOrder);
        when(mapper.selectOutboundOrderById(800002L)).thenReturn(secondOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(firstLine));
        when(mapper.listOutboundOrderLines(800002L)).thenReturn(List.of(secondLine));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(firstBox));
        when(mapper.listPackingBoxes(830002L)).thenReturn(List.of(secondBox));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(firstItem));
        when(mapper.listPackingBoxItems(830002L)).thenReturn(List.of(secondItem));
        when(mapper.confirmPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.confirmPackingList(830002L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderPacked(800001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderPacked(800002L, 307L, 307L)).thenReturn(1);

        List<PackingListView> views = service.confirmPackingLists(
                access(),
                List.of("830001", "830002", "830001")
        );

        assertThat(views).extracting(view -> view.id).containsExactly("830001", "830002");
        assertThat(views).allMatch(view -> "CONFIRMED".equals(view.status));
        verify(mapper).confirmPackingList(830001L, 307L, 307L);
        verify(mapper).confirmPackingList(830002L, 307L, 307L);
    }
}
