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

public class WarehouseDispatchPlanViews extends WarehouseProcurementViews {

    public static class DispatchPlanView {
        public String id;
        public Long ownerUserId;
        public String planNo;
        public String status;
        public Integer itemCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public String handoffRequestNo;
        public Integer handoffGenerationNo;
        public String handoffErrorMessage;
        public String createdAt;
        public String updatedAt;
        public List<WarehouseDispatchViews.DispatchPlanLineView> lines = new ArrayList<>();

        public DispatchPlanRecord toRecord() {
            DispatchPlanRecord record = new DispatchPlanRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.ownerUserId = ownerUserId;
            record.planNo = planNo;
            record.status = status;
            record.itemCount = itemCount;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.handoffRequestNo = handoffRequestNo;
            record.handoffGenerationNo = handoffGenerationNo;
            record.handoffErrorMessage = handoffErrorMessage;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class DispatchPlanLineView {
        public String id;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String specStatus;
        public Integer quantity;
        public List<WarehouseDispatchViews.DispatchPlanLineSourceView> sources = new ArrayList<>();

        public DispatchPlanLineRecord toRecord() {
            DispatchPlanLineRecord record = new DispatchPlanLineRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.specStatus = specStatus;
            record.quantity = quantity;
            record.sourceCount = sources == null ? 0 : sources.size();
            return record;
        }
    }

    public static class DispatchPlanLineSourceView {
        public String id;
        public Long dispatchPlanId;
        public Long dispatchPlanLineId;
        public Long fulfillmentBalanceId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public String plannedTransportMode;
        public String fulfillmentType;
        public Integer quantity;

        public DispatchPlanLineSourceRecord toRecord() {
            DispatchPlanLineSourceRecord record = new DispatchPlanLineSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.dispatchPlanId = dispatchPlanId;
            record.dispatchPlanLineId = dispatchPlanLineId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.sourceStoreCode = sourceStoreCode;
            record.sourceStoreName = sourceStoreName;
            record.purchaseOrderId = purchaseOrderId;
            record.purchaseOrderNo = purchaseOrderNo;
            record.purchaseOrderItemId = purchaseOrderItemId;
            record.purchaseOrderItemSiteId = purchaseOrderItemSiteId;
            record.plannedTransportMode = plannedTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.quantity = quantity;
            return record;
        }
    }

    public static class LogisticsHandoffView {
        public String dispatchPlanId;
        public String dispatchPlanNo;
        public String status;
        public Integer handoffGenerationNo;
        public String handoffRequestNo;
        public List<WarehouseDispatchViews.DispatchPlanLineView> lines = new ArrayList<>();
    }
}
