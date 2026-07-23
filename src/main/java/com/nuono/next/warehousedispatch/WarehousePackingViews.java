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

public class WarehousePackingViews extends WarehouseOutboundViews {

    public static class PackingListView {
        public String id;
        public Long outboundOrderId;
        public Long ownerUserId;
        public String packingNo;
        public String status;
        public Integer boxCount;
        public Integer packedQuantity;
        public String grossWeightKg;
        public String volumeCbm;
        public String remark;
        public String createdAt;
        public String updatedAt;
        public List<WarehouseDispatchViews.PackingBoxView> boxes = new ArrayList<>();

        public PackingListRecord toRecord() {
            PackingListRecord record = new PackingListRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.outboundOrderId = outboundOrderId;
            record.ownerUserId = ownerUserId;
            record.packingNo = packingNo;
            record.status = status;
            record.boxCount = boxCount;
            record.packedQuantity = packedQuantity;
            record.remark = remark;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class PackingBoxView {
        public String id;
        public Long packingListId;
        public Long outboundOrderId;
        public String boxNo;
        public String lengthCm;
        public String widthCm;
        public String heightCm;
        public String grossWeightKg;
        public String status;
        public Boolean isSealed;
        public String sealedAt;
        public Integer quantity;
        public List<WarehouseDispatchViews.PackingBoxItemView> items = new ArrayList<>();

        public PackingBoxRecord toRecord() {
            PackingBoxRecord record = new PackingBoxRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.packingListId = packingListId;
            record.outboundOrderId = outboundOrderId;
            record.boxNo = boxNo;
            record.quantity = quantity;
            return record;
        }
    }

    public static class PackingBoxItemView {
        public String id;
        public Long packingListId;
        public Long packingBoxId;
        public Long outboundOrderId;
        public Long outboundOrderLineId;
        public Long productVariantId;
        public String partnerSku;
        public String siteCode;
        public String actualTransportMode;
        public Integer quantity;

        public PackingBoxItemRecord toRecord() {
            PackingBoxItemRecord record = new PackingBoxItemRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.packingListId = packingListId;
            record.packingBoxId = packingBoxId;
            record.outboundOrderId = outboundOrderId;
            record.outboundOrderLineId = outboundOrderLineId;
            record.productVariantId = productVariantId;
            record.partnerSku = partnerSku;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.quantity = quantity;
            return record;
        }
    }
}
