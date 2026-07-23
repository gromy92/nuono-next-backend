package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class WarehouseOutboundViews extends WarehouseLogisticsComparisonViews {

    public static class OutboundOrderView {
        public String id;
        public Long batchId;
        public Long optionId;
        public Long ownerUserId;
        public String outboundNo;
        public String status;
        public String originType;
        public String originName;
        public Integer skuCount;
        public Integer totalQuantity;
        public String remark;
        public String createdAt;
        public String updatedAt;
        public List<WarehouseDispatchViews.OutboundOrderLineView> lines = new ArrayList<>();

        public OutboundOrderRecord toRecord() {
            OutboundOrderRecord record = new OutboundOrderRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.batchId = batchId;
            record.optionId = optionId;
            record.ownerUserId = ownerUserId;
            record.outboundNo = outboundNo;
            record.status = status;
            record.originType = originType;
            record.originName = originName;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.remark = remark;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class OutboundOrderLineView {
        public String id;
        public Long outboundOrderId;
        public Long batchId;
        public Long optionLineId;
        public Long productVariantId;
        public Long logicalStoreId;
        public String storeCode;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public String targetForwarderCode;
        public String targetForwarderName;
        public String routeCode;
        public String routeName;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String quoteCargoCategoryCode;
        public String quoteCargoCategoryName;
        public String packingGroupCode;
        public String packingGroupName;
        public Integer quantity;
        public Integer packedQuantity;
        public List<WarehouseDispatchViews.OutboundOrderLineSourceView> sources = new ArrayList<>();

        public OutboundOrderLineRecord toRecord() {
            OutboundOrderLineRecord record = new OutboundOrderLineRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.outboundOrderId = outboundOrderId;
            record.batchId = batchId;
            record.optionLineId = optionLineId;
            record.productVariantId = productVariantId;
            record.logicalStoreId = logicalStoreId;
            record.storeCode = storeCode;
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.sourcePartyName = sourcePartyName;
            record.specStatus = specStatus;
            record.quantity = quantity;
            record.packedQuantity = packedQuantity;
            return record;
        }
    }

    public static class OutboundOrderLineSourceView {
        public String id;
        public Long outboundOrderId;
        public Long outboundOrderLineId;
        public Long batchSourceId;
        public Long fulfillmentBalanceId;
        public Long logicalStoreId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public String plannedTransportMode;
        public Integer quantity;

        public OutboundOrderLineSourceRecord toRecord() {
            OutboundOrderLineSourceRecord record = new OutboundOrderLineSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.outboundOrderId = outboundOrderId;
            record.outboundOrderLineId = outboundOrderLineId;
            record.batchSourceId = batchSourceId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.logicalStoreId = logicalStoreId;
            record.sourceStoreCode = sourceStoreCode;
            record.sourceStoreName = sourceStoreName;
            record.purchaseOrderId = purchaseOrderId;
            record.purchaseOrderNo = purchaseOrderNo;
            record.purchaseOrderTitle = purchaseOrderTitle;
            record.purchaseOrderItemId = purchaseOrderItemId;
            record.purchaseOrderItemSiteId = purchaseOrderItemSiteId;
            record.plannedTransportMode = plannedTransportMode;
            record.quantity = quantity;
            return record;
        }
    }
}
