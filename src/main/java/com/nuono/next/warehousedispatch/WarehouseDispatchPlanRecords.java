package com.nuono.next.warehousedispatch;

import java.math.BigDecimal;

public class WarehouseDispatchPlanRecords extends WarehouseProcurementRecords {

    public static class DispatchPlanRecord {
        public Long id;
        public Long ownerUserId;
        public String planNo;
        public String status;
        public Integer itemCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public String siteSummaryJson;
        public String transportSummaryJson;
        public String remark;
        public Integer handoffGenerationNo;
        public String handoffRequestNo;
        public String handoffErrorMessage;
        public String createdAt;
        public String updatedAt;
    }

    public static class DispatchPlanLineRecord {
        public Long id;
        public Long dispatchPlanId;
        public Long ownerUserId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String titleCache;
        public String imageUrlCache;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String specStatus;
        public Integer quantity;
        public Integer sourceCount;
    }

    public static class DispatchPlanLineSourceRecord {
        public Long id;
        public Long dispatchPlanId;
        public Long dispatchPlanLineId;
        public Long ownerUserId;
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
    }
}
