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

abstract class WarehouseDispatchServiceTestSupport {

    @Mock
    protected WarehouseDispatchMapper mapper;

    protected LocalDbWarehouseDispatchService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbWarehouseDispatchService(mapper, new ObjectMapper());
    }

protected BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
    }

protected FulfillmentBalanceRecord balance(String quoteStatus, String shippingSubmitStatus) {
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

protected OutboundOrderRecord outboundOrder() {
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

protected OutboundOrderLineRecord outboundOrderLine() {
        OutboundOrderLineRecord record = new OutboundOrderLineRecord();
        record.id = 820001L;
        record.outboundOrderId = 800001L;
        record.batchId = 700001L;
        record.ownerUserId = 307L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.partnerSku = "SGGR174";
        record.skuParent = "SGGR";
        record.siteCode = "SA";
        record.actualTransportMode = "SEA";
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.quantity = 1;
        return record;
    }

protected OutboundOrderLineSourceRecord outboundOrderLineSource() {
        OutboundOrderLineSourceRecord record = new OutboundOrderLineSourceRecord();
        record.id = 825001L;
        record.outboundOrderId = 800001L;
        record.outboundOrderLineId = 820001L;
        record.batchSourceId = 760001L;
        record.fulfillmentBalanceId = 900001L;
        record.logicalStoreId = 301L;
        record.sourceStoreCode = "STR69486-NSA";
        record.sourceStoreName = "SGGR";
        record.purchaseOrderId = 200001L;
        record.purchaseOrderNo = "PO-200001";
        record.purchaseOrderTitle = "SGGR-0607";
        record.purchaseOrderItemId = 210001L;
        record.purchaseOrderItemSiteId = 220002L;
        record.plannedTransportMode = "AIR";
        record.quantity = 5;
        return record;
    }

protected ShippingBatchRecord shippingBatch() {
        ShippingBatchRecord record = new ShippingBatchRecord();
        record.id = 700001L;
        record.ownerUserId = 307L;
        record.batchNo = "WB-700001";
        record.status = "OPTION_SELECTED";
        record.selectedOptionId = 710001L;
        record.sourceCount = 1;
        record.skuCount = 1;
        record.totalQuantity = 5;
        return record;
    }

protected ShippingSuggestionOptionRecord selectedOption() {
        ShippingSuggestionOptionRecord record = new ShippingSuggestionOptionRecord();
        record.id = 710001L;
        record.batchId = 700001L;
        record.ownerUserId = 307L;
        record.optionType = "FORWARDER_ZD";
        record.optionName = "众鸫单货代";
        record.status = "SELECTED";
        record.selectedFlag = true;
        return record;
    }

protected ShippingBatchSourceRecord shippingBatchSource() {
        ShippingBatchSourceRecord record = new ShippingBatchSourceRecord();
        record.id = 760001L;
        record.batchId = 700001L;
        record.ownerUserId = 307L;
        record.fulfillmentBalanceId = 900001L;
        record.sourceStoreCode = "STR69486-NSA";
        record.sourceStoreName = "SGGR";
        record.purchaseOrderId = 200001L;
        record.purchaseOrderNo = "PO-200001";
        record.purchaseOrderTitle = "SGGR-0607";
        record.purchaseOrderItemId = 210001L;
        record.purchaseOrderItemSiteId = 220002L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.partnerSku = "SGGR174";
        record.skuParent = "SGGR";
        record.titleCache = "测试商品";
        record.siteCode = "SA";
        record.plannedTransportMode = "AIR";
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.sourcePartyName = "SGGR";
        record.specStatus = "READY";
        record.reservedQuantity = 5;
        return record;
    }

protected ShippingSuggestionLineRecord shippingSuggestionLine() {
        ShippingSuggestionLineRecord record = new ShippingSuggestionLineRecord();
        record.id = 720001L;
        record.optionId = 710001L;
        record.batchId = 700001L;
        record.ownerUserId = 307L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.partnerSku = "SGGR174";
        record.skuParent = "SGGR";
        record.titleCache = "测试商品";
        record.siteCode = "SA";
        record.actualTransportMode = "AIR";
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.sourcePartyName = "SGGR";
        record.specStatus = "READY";
        record.quantity = 5;
        record.sourceCount = 1;
        return record;
    }

protected ShippingSuggestionLineSourceRecord shippingSuggestionLineSource() {
        ShippingSuggestionLineSourceRecord record = new ShippingSuggestionLineSourceRecord();
        record.id = 730001L;
        record.optionId = 710001L;
        record.lineId = 720001L;
        record.batchId = 700001L;
        record.batchSourceId = 760001L;
        record.fulfillmentBalanceId = 900001L;
        record.plannedTransportMode = "AIR";
        record.quantity = 5;
        return record;
    }

protected PackingListRecord packingList() {
        PackingListRecord record = new PackingListRecord();
        record.id = 830001L;
        record.outboundOrderId = 800001L;
        record.ownerUserId = 307L;
        record.packingNo = "PK-830001";
        record.status = "DRAFT";
        record.boxCount = 0;
        record.packedQuantity = 0;
        return record;
    }

protected PackingBoxRecord packingBox(BigDecimal grossWeightKg) {
        PackingBoxRecord record = new PackingBoxRecord();
        record.id = 840001L;
        record.packingListId = 830001L;
        record.outboundOrderId = 800001L;
        record.ownerUserId = 307L;
        record.boxNo = "箱1";
        record.status = "DRAFT";
        record.lengthCm = new BigDecimal("60");
        record.widthCm = new BigDecimal("40");
        record.heightCm = new BigDecimal("30");
        record.grossWeightKg = grossWeightKg;
        record.quantity = 1;
        return record;
    }

protected PackingBoxItemRecord packingBoxItem() {
        PackingBoxItemRecord record = new PackingBoxItemRecord();
        record.id = 850001L;
        record.packingListId = 830001L;
        record.packingBoxId = 840001L;
        record.outboundOrderId = 800001L;
        record.outboundOrderLineId = 820001L;
        record.ownerUserId = 307L;
        record.productVariantId = 320001L;
        record.partnerSku = "SGGR174";
        record.siteCode = "SA";
        record.actualTransportMode = "SEA";
        record.quantity = 1;
        return record;
    }
}
