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

public class WarehouseShippingBatchViews extends WarehouseShippingSuggestionViews {

    public static class ShippingBatchView {
        public String id;
        public Long ownerUserId;
        public String batchNo;
        public String status;
        public String selectedOptionId;
        public Integer sourceCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer optionCount;
        public Integer packingListCount;
        public Integer boxCount;
        public Integer packedQuantity;
        public BigDecimal grossWeightKg;
        public BigDecimal volumeCbm;
        public String remark;
        public String createdAt;
        public String updatedAt;
        public List<WarehouseDispatchViews.ShippingBatchSourceView> sources = new ArrayList<>();
        public List<WarehouseDispatchViews.ShippingSuggestionOptionView> options = new ArrayList<>();

        public ShippingBatchRecord toRecord() {
            ShippingBatchRecord record = new ShippingBatchRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.ownerUserId = ownerUserId;
            record.batchNo = batchNo;
            record.status = status;
            record.selectedOptionId = selectedOptionId == null ? null : Long.valueOf(selectedOptionId);
            record.sourceCount = sourceCount;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.optionCount = optionCount;
            record.packingListCount = packingListCount;
            record.boxCount = boxCount;
            record.packedQuantity = packedQuantity;
            record.grossWeightKg = grossWeightKg;
            record.volumeCbm = volumeCbm;
            record.remark = remark;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class ShippingBatchSourceView {
        public String id;
        public Long batchId;
        public Long fulfillmentBalanceId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String plannedTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public String productLengthCm;
        public String productWidthCm;
        public String productHeightCm;
        public String productWeightG;
        public String logisticsProfileStatus;
        public Boolean sensitiveFlag;
        public List<String> sensitiveReasons = new ArrayList<>();
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public Boolean logisticsQuoteBlocking;
        public Integer reservedQuantity;

        public ShippingBatchSourceRecord toRecord() {
            ShippingBatchSourceRecord record = new ShippingBatchSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.batchId = batchId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.sourceStoreCode = sourceStoreCode;
            record.sourceStoreName = sourceStoreName;
            record.purchaseOrderId = purchaseOrderId;
            record.purchaseOrderNo = purchaseOrderNo;
            record.purchaseOrderTitle = purchaseOrderTitle;
            record.purchaseOrderItemId = purchaseOrderItemId;
            record.purchaseOrderItemSiteId = purchaseOrderItemSiteId;
            record.productVariantId = productVariantId;
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.plannedTransportMode = plannedTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.sourcePartyName = sourcePartyName;
            record.specStatus = specStatus;
            record.reservedQuantity = reservedQuantity;
            return record;
        }
    }
}
