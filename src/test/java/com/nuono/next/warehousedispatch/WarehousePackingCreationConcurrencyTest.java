package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePackingCreationConcurrencyTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void returnsTheActivePackingListAfterSerializingOnTheOutboundOrder() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        outboundOrder.status = "PACKING";
        PackingListRecord existing = packingList();
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.listPackingListsByOutboundOrder(800001L, access().getStoreOwnerUserIds()))
                .thenReturn(List.of(existing));

        PackingListView view = service.createPackingList(access(), "800001", null);

        assertThat(view.id).isEqualTo("830001");
        assertThat(view.status).isEqualTo("DRAFT");
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectOutboundOrderById(800001L);
        order.verify(mapper).selectOutboundOrderByIdForUpdate(800001L);
        order.verify(mapper).listPackingListsByOutboundOrder(
                800001L,
                access().getStoreOwnerUserIds()
        );
        verify(mapper, never()).nextPackingListId();
        verify(mapper, never()).insertPackingList(any(), anyLong());
        verify(mapper, never()).markOutboundOrderPacking(anyLong(), anyLong(), anyLong());
    }
}
