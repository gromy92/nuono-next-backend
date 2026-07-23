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
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
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
class LocalDbWarehouseDispatchServiceTest extends WarehouseDispatchServiceTestSupport {

@Test
    void createDispatchPlanRejectsBalanceBeforeLogisticsQuoteIsConfirmedAndShippingSubmitted() {
        FulfillmentBalanceRecord balance = balance("PENDING_QUOTE", "NOT_SUBMITTED");
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));

        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        source.actualTransportMode = "AIR";
        command.sources = List.of(source);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流报价未确认");
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

@Test
    void createDispatchPlanMergesRebuiltVariantRowsByStoreAndPartnerSku() {
        FulfillmentBalanceRecord first = balance("CONFIRMED", "SUBMITTED");
        FulfillmentBalanceRecord rebuilt = balance("CONFIRMED", "SUBMITTED");
        rebuilt.id = 900002L;
        rebuilt.purchaseOrderItemId = 210002L;
        rebuilt.purchaseOrderItemSiteId = 220003L;
        rebuilt.productVariantId = 329999L;
        rebuilt.availableQuantity = 20;
        when(mapper.selectBalancesForUpdate(List.of(900001L, 900002L))).thenReturn(List.of(first, rebuilt));
        when(mapper.reserveBalance(900001L, 5, 307L)).thenReturn(1);
        when(mapper.reserveBalance(900002L, 7, 307L)).thenReturn(1);
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L, 360002L);
        DispatchPlanLineRecord persistedLine = new DispatchPlanLineRecord();
        persistedLine.id = 350001L;
        persistedLine.dispatchPlanId = 340001L;
        persistedLine.productVariantId = 320001L;
        persistedLine.partnerSku = "SGGRB115";
        persistedLine.siteCode = "SA";
        persistedLine.actualTransportMode = "AIR";
        persistedLine.fulfillmentType = "WAREHOUSE_RECEIPT";
        persistedLine.specStatus = "READY";
        persistedLine.quantity = 12;
        persistedLine.sourceCount = 2;
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of(persistedLine));
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of());

        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        DispatchPlanSourceCommand firstSource = new DispatchPlanSourceCommand();
        firstSource.fulfillmentBalanceId = 900001L;
        firstSource.quantity = 5;
        firstSource.actualTransportMode = "AIR";
        DispatchPlanSourceCommand rebuiltSource = new DispatchPlanSourceCommand();
        rebuiltSource.fulfillmentBalanceId = 900002L;
        rebuiltSource.quantity = 7;
        rebuiltSource.actualTransportMode = "AIR";
        command.sources = List.of(firstSource, rebuiltSource);

        var view = service.createDispatchPlan(access(), command);

        assertThat(view.lines).hasSize(1);
        assertThat(view.skuCount).isEqualTo(1);
        assertThat(view.totalQuantity).isEqualTo(12);
        verify(mapper).insertDispatchPlanLine(org.mockito.ArgumentMatchers.argThat(row ->
                row.productVariantId.equals(320001L)
                        && "SGGRB115".equals(row.partnerSku)
                        && row.quantity == 12
                        && row.sourceCount == 2
        ), eq(307L));
        verify(mapper).insertDispatchPlan(org.mockito.ArgumentMatchers.argThat(row ->
                row.itemCount == 1
                        && row.skuCount == 1
                        && row.totalQuantity == 12
        ), eq(307L));
    }

@Test
    void previewMobileShippingDecisionDoesNotBlockOnProcurementQuoteSubmitStatus() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "NOT_SUBMITTED");
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));

        MobileShippingDecisionPreviewCommand command = new MobileShippingDecisionPreviewCommand();
        command.siteCode = "SA";
        command.transportMode = "AIR";
        ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        command.sources = List.of(source);

        assertThat(service.previewMobileShippingDecision(access(), command).blockers)
                .noneMatch(reason -> reason.contains("物流报价未确认"));
    }
}
