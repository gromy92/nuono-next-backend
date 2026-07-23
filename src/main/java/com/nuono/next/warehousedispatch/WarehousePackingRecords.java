package com.nuono.next.warehousedispatch;

import java.math.BigDecimal;

public class WarehousePackingRecords extends WarehouseOutboundRecords {

    public static class PackingListRecord {
        public Long id;
        public Long outboundOrderId;
        public Long ownerUserId;
        public String packingNo;
        public String status;
        public Integer boxCount;
        public Integer packedQuantity;
        public BigDecimal grossWeightKg;
        public BigDecimal volumeCbm;
        public String remark;
        public String createdAt;
        public String updatedAt;
    }

    public static class PackingBoxRecord {
        public Long id;
        public Long packingListId;
        public Long outboundOrderId;
        public Long ownerUserId;
        public String boxNo;
        public String status;
        public BigDecimal lengthCm;
        public BigDecimal widthCm;
        public BigDecimal heightCm;
        public BigDecimal grossWeightKg;
        public Integer quantity;
    }

    public static class PackingBoxItemRecord {
        public Long id;
        public Long packingListId;
        public Long packingBoxId;
        public Long outboundOrderId;
        public Long outboundOrderLineId;
        public Long ownerUserId;
        public Long productVariantId;
        public String partnerSku;
        public String siteCode;
        public String actualTransportMode;
        public Integer quantity;
    }
}
