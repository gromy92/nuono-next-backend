package com.nuono.next.warehousedispatch;

import java.math.BigDecimal;

public class WarehouseOutboundRecords extends WarehouseShippingOptionRecords {

    public static class OutboundOrderRecord {
        public Long id;
        public Long batchId;
        public Long optionId;
        public Long ownerUserId;
        public String outboundNo;
        public String status;
        public String originType;
        public String originName;
        public Integer skuCount;
        public Integer totalQuantity;
        public String siteSummaryJson;
        public String transportSummaryJson;
        public String remark;
        public String createdAt;
        public String updatedAt;
    }

    public static class OutboundOrderLineRecord {
        public Long id;
        public Long outboundOrderId;
        public Long batchId;
        public Long optionLineId;
        public Long ownerUserId;
        public Long productMasterId;
        public Long productVariantId;
        public Long logicalStoreId;
        public String storeCode;
        public String partnerSku;
        public String skuParent;
        public String titleCache;
        public String imageUrlCache;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public Integer quantity;
        public Integer packedQuantity;
    }

    public static class OutboundOrderLineSourceRecord {
        public Long id;
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
    }
}
