package com.nuono.next.warehousedispatch;

import java.math.BigDecimal;

public class WarehouseShippingBatchRecords extends WarehouseDispatchPlanRecords {

    public static class ShippingBatchRecord {
        public Long id;
        public Long ownerUserId;
        public String batchNo;
        public String status;
        public Long selectedOptionId;
        public Integer sourceCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer optionCount;
        public Integer packingListCount;
        public Integer boxCount;
        public Integer packedQuantity;
        public BigDecimal grossWeightKg;
        public BigDecimal volumeCbm;
        public String storeSummaryJson;
        public String siteSummaryJson;
        public String transportSummaryJson;
        public String originSummaryJson;
        public String remark;
        public String createdAt;
        public String updatedAt;
    }

    public static class ShippingBatchSourceRecord {
        public Long id;
        public Long batchId;
        public Long ownerUserId;
        public Long fulfillmentBalanceId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String titleCache;
        public String imageUrlCache;
        public String siteCode;
        public String plannedTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public String logisticsProfileStatus;
        public Boolean sensitiveFlag;
        public String sensitiveReasonJson;
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public Boolean logisticsQuoteBlocking;
        public Integer reservedQuantity;
    }
}
