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
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbWarehouseDispatchServiceTest {

    @Mock
    private WarehouseDispatchMapper mapper;

    private LocalDbWarehouseDispatchService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbWarehouseDispatchService(mapper, new ObjectMapper());
    }

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
    void createShippingBatchRejectsBalanceBeforeLogisticsQuoteIsConfirmedAndShippingSubmitted() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "NOT_SUBMITTED");
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));

        CreateShippingBatchCommand command = new CreateShippingBatchCommand();
        ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        command.sources = List.of(source);

        assertThatThrownBy(() -> service.createShippingBatch(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流报价未确认");
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createPackingListRejectsOutboundOrderWithBlockingLogisticsQuotes() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.countBlockingOutboundOrderLogisticsQuotes(800001L)).thenReturn(1);

        assertThatThrownBy(() -> service.createPackingList(access(), "800001", new CreatePackingListCommand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流报价未确认");
        verify(mapper, never()).nextPackingListId();
    }

    @Test
    void createPackingListAllowsOutboundOrderAfterLogisticsQuoteIsSubmitted() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        CreatePackingListCommand command = new CreatePackingListCommand();
        command.remark = "仓库装箱";

        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.countBlockingOutboundOrderLogisticsQuotes(800001L)).thenReturn(0);
        when(mapper.nextPackingListId()).thenReturn(830001L);
        when(mapper.markOutboundOrderPacking(800001L, 307L, 307L)).thenReturn(1);

        PackingListView view = service.createPackingList(access(), "800001", command);

        assertThat(view.id).isEqualTo("830001");
        assertThat(view.status).isEqualTo("DRAFT");
        verify(mapper).insertPackingList(org.mockito.ArgumentMatchers.argThat(row ->
                row.id.equals(830001L)
                        && row.outboundOrderId.equals(800001L)
                        && "仓库装箱".equals(row.remark)
        ), eq(307L));
    }

    @Test
    void logisticsHandoffSuccessOnlyMovesReservedBalance() {
        DispatchPlanRecord plan = dispatchPlan();
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;

        when(mapper.selectDispatchPlanByHandoffRequest("WDH-340001-1")).thenReturn(plan);
        when(mapper.markDispatchPlanHandoffSuccess("WDH-340001-1", 307L)).thenReturn(1);
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));

        service.markLogisticsHandoffSuccess(access(), "WDH-340001-1");

        verify(mapper).moveReservedToLogisticsHandoff(900001L, 5, 307L);
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
    }

    private FulfillmentBalanceRecord balance(String quoteStatus, String shippingSubmitStatus) {
        FulfillmentBalanceRecord record = new FulfillmentBalanceRecord();
        record.id = 900001L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.sourceStoreCode = "STR69486-NSA";
        record.sourceStoreName = "SGGR";
        record.purchaseOrderId = 200001L;
        record.purchaseOrderNo = "PO-200001";
        record.purchaseOrderTitle = "SGGR-0607";
        record.purchaseOrderItemId = 210001L;
        record.purchaseOrderItemSiteId = 220002L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.partnerSku = "SGGRB115";
        record.skuParent = "SGGR";
        record.titleCache = "测试商品";
        record.siteCode = "SA";
        record.plannedTransportMode = "AIR";
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.availableQuantity = 20;
        record.specStatus = "READY";
        record.logisticsQuoteStatus = quoteStatus;
        record.logisticsShippingSubmitStatus = shippingSubmitStatus;
        return record;
    }

    private DispatchPlanRecord dispatchPlan() {
        DispatchPlanRecord record = new DispatchPlanRecord();
        record.id = 340001L;
        record.ownerUserId = 307L;
        record.planNo = "DP-340001";
        record.status = "READY_FOR_LOGISTICS";
        record.handoffRequestNo = "WDH-340001-1";
        return record;
    }

    private OutboundOrderRecord outboundOrder() {
        OutboundOrderRecord record = new OutboundOrderRecord();
        record.id = 800001L;
        record.batchId = 700001L;
        record.ownerUserId = 307L;
        record.outboundNo = "OB-800001";
        record.status = "DRAFT";
        record.skuCount = 1;
        record.totalQuantity = 5;
        return record;
    }
}
