package com.nuono.next.officialwarehouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OfficialWarehouseRecords {

    private OfficialWarehouseRecords() {
    }

    public static class StoreSiteRecord {
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long logicalStoreSiteId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
    }

    public static class ProductCandidateRecord {
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long logicalStoreSiteId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String skuParent;
        public String partnerSku;
        public String childSku;
        public String pskuCode;
        public String noonSku;
        public String titleCache;
        public String titleEn;
        public String brandCache;
        public String imageUrlCache;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public BigDecimal cartonLengthCm;
        public BigDecimal cartonWidthCm;
        public BigDecimal cartonHeightCm;
        public BigDecimal cartonWeightKg;
        public Integer cartonQuantity;
        public String storageTypeCode;
        public String logisticsProfileStatus;
        public String batteryElectricType;
        public String magneticType;
        public String liquidType;
        public String powderType;
        public String woodenMaterialType;
        public String bladeWeaponType;
        public Boolean manualConfirmRequired;
    }

    public static class AsnInsertRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String localAsnNo;
        public String sourceType;
        public String status;
        public Integer productCount;
        public Integer totalQuantity;
        public Long operatorUserId;
    }

    public static class AsnLineInsertRecord {
        public Long id;
        public Long asnId;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String skuParent;
        public String partnerSku;
        public String childSku;
        public String pskuCode;
        public String noonSku;
        public String titleCache;
        public String titleEn;
        public String brandCache;
        public String imageUrlCache;
        public Integer quantity;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public BigDecimal cubicFeet;
        public String storageTypeCode;
        public String lineStatus;
        public Long operatorUserId;
    }

    public static class ShippingBatchCandidateRecord {
        public Long id;
        public String sourceKind;
        public String batchNo;
        public String trackingNo;
        public String externalShipmentNo;
        public String forwarderName;
        public String transportMode;
        public String status;
        public String latestNodeStatus;
        public Long selectedOptionId;
        public Integer totalQuantity;
        public Integer storeSiteQuantity;
        public Integer linkedQuantity;
        public Integer remainingQuantity;
        public Integer scheduledAppointmentQuantity;
        public Boolean alreadyAppointed;
        public Boolean batchUsedByAsn;
        public String batchUsageLabel;
        public Integer skuCount;
        public Integer purchaseOrderCount;
        public String storeSummaryJson;
        public String siteSummaryJson;
        public String transportSummaryJson;
        public String updatedAt;
    }

    public static class ShippingBatchSourceAllocationRecord {
        public Long shippingBatchId;
        public String shippingBatchNo;
        public String status;
        public Long selectedOptionId;
        public Long shippingBatchSourceId;
        public Long inTransitBatchId;
        public String batchReferenceNo;
        public String trackingNo;
        public String externalShipmentNo;
        public String forwarderName;
        public String transportMode;
        public String latestNodeStatus;
        public Long inTransitGoodsLineId;
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
        public Integer quantity;
    }

    public static class AsnShippingBatchLinkInsertRecord {
        public Long id;
        public Long asnId;
        public Long asnLineId;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public Long shippingBatchId;
        public String shippingBatchNo;
        public Long shippingBatchSourceId;
        public Long inTransitBatchId;
        public String batchReferenceNo;
        public String trackingNo;
        public String externalShipmentNo;
        public String forwarderName;
        public String transportMode;
        public String latestNodeStatus;
        public Long inTransitGoodsLineId;
        public Long fulfillmentBalanceId;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String pskuCode;
        public Integer quantity;
        public String relationStatus;
        public String relationBasis;
        public Long operatorUserId;
    }

    public static class AsnShippingBatchLinkRecord {
        public Long id;
        public Long asnId;
        public Long asnLineId;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public Long shippingBatchId;
        public String shippingBatchNo;
        public Long shippingBatchSourceId;
        public Long inTransitBatchId;
        public String batchReferenceNo;
        public String trackingNo;
        public String externalShipmentNo;
        public String forwarderName;
        public String transportMode;
        public String latestNodeStatus;
        public Long inTransitGoodsLineId;
        public Long fulfillmentBalanceId;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String pskuCode;
        public Integer quantity;
        public String relationStatus;
        public String relationBasis;
        public String createdAt;
    }

    public static class AsnRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String localAsnNo;
        public String sourceType;
        public String status;
        public String noonAsnNr;
        public Long noonPartnerAsnId;
        public Integer noonTotalQty;
        public String noonAsnStatus;
        public LocalDateTime noonUpdatedAt;
        public String routingResponseJson;
        public Boolean routingIsTransfer;
        public String selectedWarehousePartnerCode;
        public String selectedWarehouseCode;
        public String selectedWarehouseName;
        public Integer productCount;
        public Integer totalQuantity;
        public String errorStage;
        public String failureType;
        public String errorMessage;
        public String submittedAt;
        public String finishedAt;
        public String createdAt;
        public String updatedAt;
    }

    public static class AsnNoonListSyncRecord {
        public Long id;
        public Long ownerUserId;
        public String projectCode;
        public String partnerId;
        public String status;
        public String noonAsnNr;
        public Long noonPartnerAsnId;
        public Integer noonTotalQty;
        public String noonAsnStatus;
        public LocalDateTime noonUpdatedAt;
        public String warehouseToPartnerCode;
        public String warehouseToCode;
        public String warehouseName;
        public String failureType;
        public String errorMessage;
        public Long operatorUserId;
    }

    public static class AsnLineRecord {
        public Long id;
        public Long asnId;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String skuParent;
        public String partnerSku;
        public String childSku;
        public String pskuCode;
        public String noonSku;
        public String titleCache;
        public String titleEn;
        public String brandCache;
        public String imageUrlCache;
        public Integer qty;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public BigDecimal cubicFeet;
        public String storageTypeCode;
        public Long noonPartnerAsnLineId;
        public Integer noonIdCluster;
        public Integer noonIdStorageType;
        public String noonClusterCode;
        public String noonAsnStatus;
        public String noonCountryCode;
        public Boolean labeled;
        public Boolean replToolAsn;
        public String lineStatus;
        public String errorMessage;
    }

    public static class AsnInboundReceiptRecord {
        public Long asnId;
        public Long asnLineId;
        public Long importId;
        public Long reportRowId;
        public String noonAsnNr;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String pbarcodeCanonical;
        public String partnerWarehouse;
        public String noonWarehouse;
        public Integer qtyExpected;
        public Integer receivedQty;
        public Integer qcFailedQty;
        public Integer unidentifiedQty;
        public String qcFailedReason;
        public String receiptStatus;
        public String matchStatus;
        public String asnCompletedAt;
        public String importedAt;
    }

    public static class AppointmentInsertRecord {
        public Long id;
        public Long asnId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String localAsnNo;
        public String noonAsnNr;
        public Integer totalUnits;
        public String warehouseFrom;
        public String warehouseToPartnerCode;
        public String warehouseToCode;
        public LocalDate apStartDate;
        public LocalDate apEndDate;
        public String apTimeRange;
        public Boolean availableToday;
        public String status;
        public String gate;
        public String docks;
        public Long operatorUserId;
    }

    public static class AppointmentRecord {
        public Long id;
        public Long asnId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String localAsnNo;
        public String noonAsnNr;
        public Integer totalUnits;
        public String warehouseFrom;
        public String warehouseToPartnerCode;
        public String warehouseToCode;
        public LocalDate apStartDateValue;
        public LocalDate apEndDateValue;
        public String apStartDate;
        public String apEndDate;
        public String apTimeRange;
        public Boolean availableToday;
        public String status;
        public String appointmentDate;
        public Integer appointmentSlotId;
        public String appointmentTime;
        public String gate;
        public String docks;
        public Integer attemptCount;
        public String lastAttemptAt;
        public String nextAttemptAt;
        public String apSuccessTime;
        public String errorStage;
        public String failureType;
        public String errorMessage;
        public String createdAt;
        public String updatedAt;
    }
}
