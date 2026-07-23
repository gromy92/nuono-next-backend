package com.nuono.next.warehousedispatch;

import java.math.BigDecimal;

public class WarehouseProcurementRecords {

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
}
