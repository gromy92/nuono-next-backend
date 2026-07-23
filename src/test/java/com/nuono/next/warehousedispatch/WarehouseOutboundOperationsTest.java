package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionPreviewCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxItemCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseOutboundOperationsTest extends WarehouseDispatchServiceTestSupport {

@Test
    void createOutboundOrdersCarriesSourceStoreScopeForAppPacking() {
        when(mapper.selectShippingBatchById(700001L)).thenReturn(shippingBatch());
        when(mapper.selectShippingSuggestionOptionById(710001L)).thenReturn(selectedOption());
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(shippingBatchSource()));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(shippingSuggestionLine()));
        when(mapper.listShippingSuggestionLineSources(700001L)).thenReturn(List.of(shippingSuggestionLineSource()));
        when(mapper.nextOutboundOrderId()).thenReturn(800001L);
        when(mapper.nextOutboundOrderLineId()).thenReturn(820001L);
        when(mapper.nextOutboundOrderLineSourceId()).thenReturn(825001L);
        when(mapper.updateShippingBatchOutboundCreated(700001L, 307L, 307L)).thenReturn(1);

        var orders = service.createOutboundOrders(access(), "700001");

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).lines).hasSize(1);
        assertThat(orders.get(0).lines.get(0).storeCode).isEqualTo("STR69486-NSA");
        assertThat(orders.get(0).lines.get(0).partnerSku).isEqualTo("SGGR174");
        assertThat(orders.get(0).lines.get(0).sources).hasSize(1);
        assertThat(orders.get(0).lines.get(0).sources.get(0).sourceStoreCode).isEqualTo("STR69486-NSA");
        assertThat(orders.get(0).lines.get(0).sources.get(0).sourceStoreName).isEqualTo("SGGR");
        verify(mapper).insertOutboundOrderLineSource(org.mockito.ArgumentMatchers.argThat(row ->
                row.id.equals(825001L)
                        && row.outboundOrderLineId.equals(820001L)
                        && "STR69486-NSA".equals(row.sourceStoreCode)
                        && "SGGR".equals(row.sourceStoreName)
        ), eq(307L));
    }

    @Test
    void createOutboundOrdersKeepsDifferentStoresInOneBatchOrder() {
        ShippingBatchSourceRecord secondSource = shippingBatchSource();
        secondSource.id = 760002L;
        secondSource.sourcePartyName = "canman";
        secondSource.sourceStoreCode = "STR108065-NSA";
        ShippingSuggestionLineRecord secondLine = shippingSuggestionLine();
        secondLine.id = 720002L;
        secondLine.partnerSku = "PAPERSAYSB001";
        secondLine.sourcePartyName = "canman";
        ShippingSuggestionLineSourceRecord secondLineSource = shippingSuggestionLineSource();
        secondLineSource.id = 730002L;
        secondLineSource.lineId = 720002L;
        secondLineSource.batchSourceId = 760002L;
        when(mapper.selectShippingBatchById(700001L)).thenReturn(shippingBatch());
        when(mapper.selectShippingSuggestionOptionById(710001L)).thenReturn(selectedOption());
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(shippingBatchSource(), secondSource));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(shippingSuggestionLine(), secondLine));
        when(mapper.listShippingSuggestionLineSources(700001L))
                .thenReturn(List.of(shippingSuggestionLineSource(), secondLineSource));
        when(mapper.nextOutboundOrderId()).thenReturn(800001L);
        when(mapper.nextOutboundOrderLineId()).thenReturn(820001L, 820002L);
        when(mapper.nextOutboundOrderLineSourceId()).thenReturn(825001L, 825002L);
        when(mapper.updateShippingBatchOutboundCreated(700001L, 307L, 307L)).thenReturn(1);

        var orders = service.createOutboundOrders(access(), "700001");

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).originName).isEqualTo("多来源");
        assertThat(orders.get(0).lines).hasSize(2);
        verify(mapper).insertOutboundOrder(org.mockito.ArgumentMatchers.argThat(order ->
                order.skuCount == 2 && order.totalQuantity == 10
        ), eq(307L));
    }

@Test
    void listOutboundOrdersHydratesLineStoreScopeFromSources() {
        ShippingBatchRecord batch = shippingBatch();
        batch.status = "OUTBOUND_CREATED";
        OutboundOrderLineRecord outboundLine = outboundOrderLine();
        outboundLine.optionLineId = 720001L;
        ShippingSuggestionLineRecord suggestionLine = shippingSuggestionLine();
        suggestionLine.targetForwarderCode = "YT";
        suggestionLine.targetForwarderName = "义特物流";
        suggestionLine.routeCode = "YT-SAU-SEA-FBN-RUH";
        suggestionLine.routeName = "义特沙特海运";
        suggestionLine.warningJson = "{\"cargoCategoryCode\":\"GENERAL\",\"cargoCategoryName\":\"普货\","
                + "\"quoteCargoCategoryCode\":\"YT-CAT-020\",\"quoteCargoCategoryName\":\"普货报价\"}";
        OutboundOrderLineSourceRecord source = outboundOrderLineSource();
        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.listOutboundOrdersByBatch(700001L)).thenReturn(List.of(outboundOrder()));
        when(mapper.listOutboundOrderLineSources(800001L)).thenReturn(List.of(source));
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundLine));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(suggestionLine));

        var orders = service.listOutboundOrders(access(), "700001");

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).lines).hasSize(1);
        assertThat(orders.get(0).lines.get(0).logicalStoreId).isEqualTo(301L);
        assertThat(orders.get(0).lines.get(0).storeCode).isEqualTo("STR69486-NSA");
        assertThat(orders.get(0).lines.get(0).targetForwarderName).isEqualTo("义特物流");
        assertThat(orders.get(0).lines.get(0).routeCode).isEqualTo("YT-SAU-SEA-FBN-RUH");
        assertThat(orders.get(0).lines.get(0).quoteCargoCategoryCode).isEqualTo("YT-CAT-020");
        assertThat(orders.get(0).lines.get(0).packingGroupName).isEqualTo("义特物流 · 义特沙特海运 · 普货报价");
        assertThat(orders.get(0).lines.get(0).sources).hasSize(1);
        assertThat(orders.get(0).lines.get(0).sources.get(0).logicalStoreId).isEqualTo(301L);
        assertThat(orders.get(0).lines.get(0).sources.get(0).sourceStoreCode).isEqualTo("STR69486-NSA");
        assertThat(orders.get(0).lines.get(0).sources.get(0).purchaseOrderNo).isEqualTo("PO-200001");
    }

    @Test
    void shippingOriginKeySeparatesForwarderRouteAndQuoteCategory() {
        ShippingSuggestionLineRecord first = shippingSuggestionLine();
        first.targetForwarderCode = "YT";
        first.routeCode = "YT-SAU-SEA-FBN-RUH";
        first.warningJson = "{\"quoteCargoCategoryCode\":\"YT-CAT-020\"}";
        ShippingSuggestionLineRecord second = shippingSuggestionLine();
        second.targetForwarderCode = "ZD";
        second.routeCode = "ZD-SAU-SEA-FBN-RUH";
        second.warningJson = "{\"quoteCargoCategoryCode\":\"ZD-CAT-003\"}";

        assertThat(service.shippingOriginKey(first)).isNotEqualTo(service.shippingOriginKey(second));
    }
}
