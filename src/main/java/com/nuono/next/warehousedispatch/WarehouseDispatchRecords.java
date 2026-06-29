package com.nuono.next.warehousedispatch;

import java.math.BigDecimal;

public final class WarehouseDispatchRecords {

    private WarehouseDispatchRecords() {
    }

    public static class IdSequenceCommand {
        private final String sequenceName;
        private final long initialValue;
        private Long allocatedId;

        public IdSequenceCommand(String sequenceName, long initialValue) {
            this.sequenceName = sequenceName;
            this.initialValue = initialValue;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public long getInitialValue() {
            return initialValue;
        }

        public Long getAllocatedId() {
            return allocatedId;
        }

        public void setAllocatedId(Long allocatedId) {
            this.allocatedId = allocatedId;
        }
    }

    public static class PurchaseOrderAccessRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String orderNo;
        public String title;
        public String anchorStoreCodeCache;
        public String projectCodeCache;
        public String projectNameCache;
    }

    public static class PurchaseOrderItemRecord {
        public Long id;
        public Long purchaseOrderId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String titleCache;
        public String imageUrlCache;
        public String fulfillmentType;
        public String fulfillmentSourceName;
        public Integer totalQuantity;
    }

    public static class PurchaseOrderItemSiteRecord {
        public Long id;
        public Long purchaseOrderId;
        public Long purchaseOrderItemId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String siteCode;
        public String transportMode;
        public Integer quantity;
    }

    public static class FulfillmentBalanceRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
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
        public Boolean isNewProduct;
        public String plannedTransportMode;
        public String fulfillmentType;
        public Integer plannedQuantity;
        public Integer confirmedQuantity;
        public Integer abnormalQuantity;
        public Integer reservedQuantity;
        public Integer logisticsHandoffQuantity;
        public Integer availableQuantity;
        public String specStatus;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public String logisticsProfileStatus;
        public String batteryType;
        public String magneticType;
        public String liquidPowderType;
        public String electricType;
        public String bladeWeaponType;
        public String sensitiveTagsJson;
        public Boolean manualConfirmRequired;
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public Boolean logisticsQuoteBlocking;
        public String status;
    }

    public static class PurchaseReceiptRow {
        public Long receiptSourceId;
        public String receiptSourceNo;
        public String receiptSourceTitle;
        public String receiptSourceStoreName;
        public String receiptSourceStoreCode;
        public String receiptSourceCreatedAt;
        public Long orderId;
        public String orderNo;
        public String orderTitle;
        public String storeName;
        public String sourceStoreCode;
        public String createdAt;
        public Long itemId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String titleCache;
        public String imageUrlCache;
        public String siteCode;
        public String transportMode;
        public Integer expectedQuantity;
        public Integer receivedQuantity;
        public Integer plannedQuantity;
        public String specStatus;
        public String fulfillmentType;
        public String fulfillmentSourceName;
    }

    public static class FulfillmentConfirmationInsertRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long purchaseOrderId;
        public String confirmationNo;
        public String confirmationType;
        public String status;
        public String sourcePartyName;
        public Long relatedConfirmationId;
        public String relationType;
        public Long operatorUserId;
        public Integer expectedQuantity;
        public Integer confirmedQuantityDelta;
        public Integer abnormalQuantityDelta;
        public String remark;
    }

    public static class FulfillmentConfirmationLineInsertRecord {
        public Long id;
        public Long confirmationId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long purchaseOrderId;
        public Long purchaseOrderItemId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String titleCache;
        public String imageUrlCache;
        public String fulfillmentType;
        public Integer expectedQuantity;
        public Integer confirmedQuantityDelta;
        public Integer abnormalQuantityDelta;
        public Long relatedConfirmationLineId;
        public String exceptionReason;
        public String snapshotJson;
        public Long operatorUserId;
    }

    public static class BalanceQuantityDelta {
        public Long balanceId;
        public Integer confirmedDelta;
        public Integer abnormalDelta;
        public Long operatorUserId;

        public BalanceQuantityDelta() {
        }

        public BalanceQuantityDelta(Long balanceId, Integer confirmedDelta, Integer abnormalDelta, Long operatorUserId) {
            this.balanceId = balanceId;
            this.confirmedDelta = confirmedDelta;
            this.abnormalDelta = abnormalDelta;
            this.operatorUserId = operatorUserId;
        }
    }

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

    public static class ShippingBatchRecord {
        public Long id;
        public Long ownerUserId;
        public String batchNo;
        public String status;
        public Long selectedOptionId;
        public Integer sourceCount;
        public Integer skuCount;
        public Integer totalQuantity;
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

    public static class ShippingSuggestionOptionRecord {
        public Long id;
        public Long batchId;
        public Long ownerUserId;
        public String optionType;
        public String optionName;
        public String status;
        public Boolean selectedFlag;
        public Integer score;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer airQuantity;
        public Integer seaQuantity;
        public Integer specMissingCount;
        public Integer warningCount;
        public String forwarderPlanType;
        public Boolean autoRecommended;
        public String targetForwarderCodesJson;
        public String targetForwarderNamesJson;
        public String routeCodesJson;
        public String evaluationStatus;
        public String blockedReasonsJson;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedTotalAmount;
        public BigDecimal avgUnitAmount;
        public String currency;
        public String costSnapshotJson;
        public String summaryJson;
        public String createdAt;
        public String updatedAt;
    }

    public static class ShippingSuggestionLineRecord {
        public Long id;
        public Long optionId;
        public Long batchId;
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
        public String sourcePartyName;
        public String specStatus;
        public String targetForwarderCode;
        public String targetForwarderName;
        public String routeCode;
        public String routeName;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedAmount;
        public String currency;
        public Integer quantity;
        public Integer sourceCount;
        public String warningJson;
    }

    public static class ForwarderRouteQuoteRecord {
        public String routeCode;
        public String routeName;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String currency;
        public BigDecimal minUnitPrice;
        public String billingUnit;
        public BigDecimal minBillableUnit;
        public String minBillableUnitType;
        public BigDecimal minCharge;
        public BigDecimal volumeDivisor;
    }

    public static class ShippingSuggestionLineSourceRecord {
        public Long id;
        public Long optionId;
        public Long lineId;
        public Long batchId;
        public Long batchSourceId;
        public Long fulfillmentBalanceId;
        public String plannedTransportMode;
        public Integer quantity;
    }

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
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public String plannedTransportMode;
        public Integer quantity;
    }

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
