package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseLogisticsPartitionInvariantTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void createDispatchPlanRejectsMixedTransportBeforeReservingInventory() {
        FulfillmentBalanceRecord air = balance("CONFIRMED", "SUBMITTED");
        FulfillmentBalanceRecord sea = balance("CONFIRMED", "SUBMITTED");
        sea.id = 900002L;
        sea.plannedTransportMode = "SEA";
        when(mapper.selectBalancesForUpdate(List.of(900001L, 900002L))).thenReturn(List.of(air, sea));

        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.sources = List.of(dispatchSource(900001L, "SA", "AIR"), dispatchSource(900002L, "SA", "SEA"));

        assertThatThrownBy(() -> service.createDispatchPlan(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同一物流分区");
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createShippingBatchRejectsMixedSiteBeforeReservingInventory() {
        FulfillmentBalanceRecord sa = balance("CONFIRMED", "SUBMITTED");
        FulfillmentBalanceRecord ae = balance("CONFIRMED", "SUBMITTED");
        ae.id = 900002L;
        ae.siteCode = "AE";
        when(mapper.selectBalancesForUpdate(List.of(900001L, 900002L))).thenReturn(List.of(sa, ae));

        CreateShippingBatchCommand command = new CreateShippingBatchCommand();
        command.sources = List.of(shippingSource(900001L), shippingSource(900002L));

        assertThatThrownBy(() -> service.createShippingBatch(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同一物流分区");
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createOutboundOrdersSplitHistoricalMixedBatchByPartition() {
        ShippingBatchSourceRecord airSource = shippingBatchSource();
        ShippingBatchSourceRecord seaSource = shippingBatchSource();
        seaSource.id = 760002L;
        seaSource.fulfillmentBalanceId = 900002L;
        seaSource.plannedTransportMode = "SEA";
        ShippingSuggestionLineRecord airLine = shippingSuggestionLine();
        ShippingSuggestionLineRecord seaLine = shippingSuggestionLine();
        seaLine.id = 720002L;
        seaLine.actualTransportMode = "SEA";
        ShippingSuggestionLineSourceRecord airLineSource = shippingSuggestionLineSource();
        ShippingSuggestionLineSourceRecord seaLineSource = shippingSuggestionLineSource();
        seaLineSource.id = 730002L;
        seaLineSource.lineId = 720002L;
        seaLineSource.batchSourceId = 760002L;
        seaLineSource.fulfillmentBalanceId = 900002L;
        seaLineSource.plannedTransportMode = "SEA";

        when(mapper.selectShippingBatchById(700001L)).thenReturn(shippingBatch());
        when(mapper.selectShippingSuggestionOptionById(710001L)).thenReturn(selectedOption());
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(airSource, seaSource));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(airLine, seaLine));
        when(mapper.listShippingSuggestionLineSources(700001L)).thenReturn(List.of(airLineSource, seaLineSource));
        when(mapper.nextOutboundOrderId()).thenReturn(800001L, 800002L);
        when(mapper.nextOutboundOrderLineId()).thenReturn(820001L, 820002L);
        when(mapper.nextOutboundOrderLineSourceId()).thenReturn(825001L, 825002L);
        when(mapper.updateShippingBatchOutboundCreated(700001L, 307L, 307L)).thenReturn(1);

        var orders = service.createOutboundOrders(access(), "700001");

        assertThat(orders).hasSize(2);
        assertThat(orders).allSatisfy(order -> assertThat(order.lines).hasSize(1));
        assertThat(orders).extracting(order -> order.lines.get(0).actualTransportMode)
                .containsExactly("AIR", "SEA");
    }

    private DispatchPlanSourceCommand dispatchSource(Long balanceId, String siteCode, String transportMode) {
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = balanceId;
        source.quantity = 5;
        source.targetSiteCode = siteCode;
        source.actualTransportMode = transportMode;
        return source;
    }

    private ShippingBatchSourceCommand shippingSource(Long balanceId) {
        ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
        source.fulfillmentBalanceId = balanceId;
        source.quantity = 5;
        return source;
    }
}
