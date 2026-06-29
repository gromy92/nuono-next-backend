package com.nuono.next.officialwarehouse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class OfficialWarehouseViews {

    private OfficialWarehouseViews() {
    }

    public static class ProductCandidateView {
        public String productVariantId;
        public String productSiteOfferId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String skuParent;
        public String partnerSku;
        public String childSku;
        public String pskuCode;
        public String noonSku;
        public String title;
        public String titleEn;
        public String brand;
        public String imageUrl;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public BigDecimal cubicFeet;
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
        public Integer batchAvailableQuantity;
        public List<String> missingTags = new ArrayList<>();
    }

    public static class ShippingBatchCandidateView {
        public String id;
        public String sourceKind;
        public String batchNo;
        public String trackingNo;
        public String externalShipmentNo;
        public String forwarderName;
        public String transportMode;
        public String status;
        public String latestNodeStatus;
        public String selectedOptionId;
        public Integer totalQuantity;
        public Integer storeSiteQuantity;
        public Integer linkedQuantity;
        public Integer remainingQuantity;
        public Integer skuCount;
        public Integer purchaseOrderCount;
        public String updatedAt;
    }

    public static class AsnView {
        public String id;
        public String inboundNo;
        public String localAsnNo;
        public String sourceType;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String status;
        public String asnNo;
        public String noonAsnNr;
        public String noonAsnStatus;
        public String noonUser;
        public String noonPartnerAsnId;
        public Integer productCount;
        public Integer totalQuantity;
        public String selectedWarehouseCode;
        public String selectedWarehousePartnerCode;
        public String selectedWarehouseName;
        public Boolean routingIsTransfer;
        public String errorStage;
        public String failureType;
        public String errorMessage;
        public String submittedAt;
        public String finishedAt;
        public String createdAt;
        public String updatedAt;
        public List<RoutingWarehouseView> routingWarehouses = new ArrayList<>();
        public List<AsnLineView> lines = new ArrayList<>();
        public List<AsnShippingBatchLinkView> shippingBatchLinks = new ArrayList<>();
        public AppointmentView appointment;
    }

    public static class AsnLineView {
        public String id;
        public String productVariantId;
        public String productSiteOfferId;
        public String skuParent;
        public String partnerSku;
        public String childSku;
        public String pskuCode;
        public String noonSku;
        public String title;
        public String titleEn;
        public String brand;
        public String imageUrl;
        public Integer quantity;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public BigDecimal cubicFeet;
        public String storageTypeCode;
        public String noonPartnerAsnLineId;
        public String noonClusterCode;
        public String noonAsnStatus;
        public String noonCountryCode;
        public Boolean labeled;
        public Boolean replToolAsn;
        public String lineStatus;
        public String errorMessage;
        public List<AsnShippingBatchLinkView> shippingBatchLinks = new ArrayList<>();
    }

    public static class AsnShippingBatchLinkView {
        public String id;
        public String asnId;
        public String asnLineId;
        public String shippingBatchId;
        public String shippingBatchNo;
        public String shippingBatchSourceId;
        public String inTransitBatchId;
        public String batchReferenceNo;
        public String trackingNo;
        public String externalShipmentNo;
        public String forwarderName;
        public String transportMode;
        public String latestNodeStatus;
        public String inTransitGoodsLineId;
        public String fulfillmentBalanceId;
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderItemId;
        public String purchaseOrderItemSiteId;
        public String productMasterId;
        public String productVariantId;
        public String partnerSku;
        public String pskuCode;
        public Integer quantity;
        public String relationStatus;
        public String relationBasis;
        public String createdAt;
    }

    public static class RoutingWarehouseView {
        public String partnerCode;
        public String code;
        public Long lat;
        public Long lng;
    }

    public static class AsnListSyncView {
        public int fetched;
        public int created;
        public int updated;
        public int scheduled;
        public int corrected;
        public int failed;
        public int skipped;
        public int pages;
    }

    public static class AppointmentView {
        public String id;
        public String asnId;
        public String localAsnNo;
        public String noonAsnNr;
        public String storeCode;
        public String siteCode;
        public String status;
        public String warehouseFrom;
        public String warehouseToPartnerCode;
        public String warehouseToCode;
        public String apStartDate;
        public String apEndDate;
        public String apTimeRange;
        public Boolean availableToday;
        public String appointmentDate;
        public Integer appointmentSlotId;
        public String appointmentTime;
        public String gate;
        public String docks;
        public Integer attemptCount;
        public String lastAttemptAt;
        public String nextAttemptAt;
        public String apSuccessTime;
        public String failureType;
        public String errorStage;
        public String errorMessage;
        public String createdAt;
        public String updatedAt;
    }

    public static class AppointmentAvailabilityView {
        public String date;
        public Integer slotId;
        public String time;
        public String warehouseFrom;
        public String warehouseFromCode;
        public String label;
    }
}
