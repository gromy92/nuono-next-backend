package com.nuono.next.procurementorder;

import java.math.BigDecimal;

public final class ProcurementPurchaseOrderRecords {

    private ProcurementPurchaseOrderRecords() {
    }

    public static class StoreScopeRecord {
        public Long ownerUserId;
        public Long logicalStoreId;
        public String projectCode;
        public String projectName;
        public String anchorStoreCode;
        public String anchorSite;
    }

    public static class StoreSiteRecord {
        public Long siteId;
        public String siteCode;
        public String storeCode;
    }

    public static class ProductArchiveRecord {
        public Long productMasterId;
        public Long productVariantId;
        public String skuParent;
        public String partnerSku;
        public String childSku;
        public String sizeEn;
        public String sizeAr;
        public String title;
        public String imageUrl;
        public String availableSiteCodesCsv;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public BigDecimal cartonLengthCm;
        public BigDecimal cartonWidthCm;
        public BigDecimal cartonHeightCm;
        public BigDecimal cartonWeightKg;
        public Integer cartonQuantity;
        public String specSourceType;
    }

    public static class ProductOfferRecord {
        public Long siteId;
        public String siteCode;
        public Long productSiteOfferId;
        public String pskuCode;
        public String offerCode;
    }

    public static class ForwarderSeaRecommendationRecord {
        public String forwarderCode;
        public String forwarderName;
        public String serviceCode;
        public String serviceName;
        public String country;
        public String targetPlatform;
        public String deliveryCity;
        public String destinationNode;
        public String transportMode;
        public String deliveryScope;
        public String transitTimeText;
        public Integer transitDaysMin;
        public Integer transitDaysMax;
        public Integer priceRuleCount;
        public String currency;
        public BigDecimal minUnitPrice;
        public String billingUnit;
        public String billingBasis;
        public BigDecimal minBillableUnit;
        public String minBillableUnitType;
        public BigDecimal minCharge;
        public BigDecimal volumeDivisor;
        public String cargoCategoryNamesCsv;
        public String priceStatusesCsv;
        public BigDecimal cbmMinUnitPrice;
        public BigDecimal kgMinUnitPrice;
    }

    public static class ForwarderRouteRecommendationRecord {
        public String routeCode;
        public String routeName;
        public String forwarderCode;
        public String forwarderName;
        public String serviceCode;
        public String serviceName;
        public String quoteVersionCode;
        public String country;
        public String siteCode;
        public String targetPlatform;
        public String deliveryCity;
        public String destinationNode;
        public String transportMode;
        public String transitTimeText;
        public Integer transitDaysMin;
        public Integer transitDaysMax;
        public Integer priceRuleCount;
        public String currency;
        public BigDecimal minUnitPrice;
        public String billingUnit;
        public String billingBasis;
        public BigDecimal minBillableUnit;
        public String minBillableUnitType;
        public BigDecimal minCharge;
        public BigDecimal volumeDivisor;
        public String cargoCategoryNamesCsv;
        public String priceStatusesCsv;
        public BigDecimal cbmMinUnitPrice;
        public BigDecimal kgMinUnitPrice;
    }

    public static class ForwarderRouteSegmentRecord {
        public String routeCode;
        public Integer segmentNo;
        public String segmentRole;
        public String serviceCode;
        public String costPolicy;
        public Boolean required;
        public String displayName;
        public String remark;
    }

    public static class ForwarderBasePriceRecord {
        public Long id;
        public String serviceCode;
        public String priceRuleCode;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String pricingModel;
        public String currency;
        public BigDecimal unitPrice;
        public String billingUnit;
        public String billingBasis;
        public BigDecimal volumeDivisor;
        public BigDecimal minBillableUnit;
        public String minBillableUnitType;
        public BigDecimal minCharge;
        public String targetPlatform;
        public String deliveryCity;
        public String priceStatus;
    }

    public static class ForwarderWarehouseProcessingFeeRecord {
        public Long id;
        public String serviceCode;
        public String feeName;
        public String feeType;
        public String processingScope;
        public String pricingModel;
        public String currency;
        public BigDecimal amount;
        public String billingUnit;
        public String conditionText;
        public BigDecimal minCharge;
        public String targetPlatform;
    }

    public static class ForwarderTransportFeeRecord {
        public Long id;
        public String serviceCode;
        public String feeName;
        public String feeType;
        public String targetPlatform;
        public String deliveryCity;
        public String triggerCondition;
        public String pricingModel;
        public String currency;
        public BigDecimal amount;
        public BigDecimal rate;
        public String billingUnit;
        public String billingBasis;
        public BigDecimal minCharge;
        public BigDecimal minBillableUnit;
        public String roundingRule;
        public Boolean includedInBasePrice;
    }

    public static class LogisticsRecommendationInsertRecord {
        public Long id;
        public Long logisticsPlanId;
        public Long purchaseOrderId;
        public String routeCode;
        public String forwarderCode;
        public String serviceCode;
        public String transportMode;
        public Integer rankNo;
        public Boolean recommended;
        public String estimateStatus;
        public String currency;
        public BigDecimal estimatedTotalAmount;
        public BigDecimal recurringAmountPerDay;
        public String snapshotJson;
    }

    public static class LogisticsCostComponentInsertRecord {
        public Long id;
        public Long recommendationId;
        public Long logisticsPlanId;
        public String componentType;
        public String componentName;
        public String sourceTable;
        public Long sourceId;
        public String currency;
        public BigDecimal unitPrice;
        public String billingUnit;
        public BigDecimal billableQuantity;
        public BigDecimal amount;
        public String amountStatus;
        public Boolean includedInTotal;
        public String formulaText;
        public String remark;
    }

    public static class PurchaseOrderLogisticsQuoteLineRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long shippingOrderId;
        public String shippingOrderNo;
        public Long shippingOrderSegmentId;
        public String shippingOrderSegmentNo;
        public Long shippingOrderLineId;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public Long productMasterId;
        public Long productVariantId;
        public String skuParent;
        public String partnerSku;
        public String barcode;
        public String titleCache;
        public String titleEn;
        public String imageUrlCache;
        public String brandName;
        public String siteCode;
        public String pskuCode;
        public String yiteMaterial;
        public String plannedTransportMode;
        public Integer quantity;
        public String fulfillmentType;
        public Boolean isNewProduct;
        public String quoteStatus;
        public String shippingSubmitStatus;
        public String forwarderCode;
        public String forwarderName;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public String currency;
        public BigDecimal unitPrice;
        public String billingUnit;
        public BigDecimal estimatedAmount;
        public String remark;
        public BigDecimal productLengthCm;
        public BigDecimal productWidthCm;
        public BigDecimal productHeightCm;
        public BigDecimal productWeightG;
        public BigDecimal cartonLengthCm;
        public BigDecimal cartonWidthCm;
        public BigDecimal cartonHeightCm;
        public BigDecimal cartonWeightKg;
        public Integer cartonQuantity;
        public String exportedAt;
        public String confirmedAt;
        public String shippingSubmittedAt;
    }

    public static class ProductForwarderChannelQuoteRecord {
        public Long id;
        public Long ownerUserId;
        public Long productMasterId;
        public Long productVariantId;
        public Long logicalStoreId;
        public String sourceStoreCode;
        public String partnerSku;
        public String barcode;
        public String forwarderCode;
        public String forwarderName;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public String siteCode;
        public String transportMode;
        public String targetPlatform;
        public String deliveryCity;
        public String currency;
        public BigDecimal unitPrice;
        public String billingUnit;
        public BigDecimal estimatedAmount;
        public String sourceType;
        public Long sourceShippingOrderId;
        public Long sourceShippingOrderLineId;
        public Long sourceQuoteLineId;
        public Long sourceActualBillId;
        public Long sourceActualComponentId;
        public String sourceFilename;
        public String effectiveStatus;
        public String rawSnapshotJson;
    }

    public static class LogisticsExpectedBillRecord {
        public Long id;
        public Long ownerUserId;
        public String expectedBillNo;
        public Long shippingOrderId;
        public String shippingOrderNo;
        public String shippingOrderTitle;
        public Long shippingOrderSegmentId;
        public String shippingOrderSegmentNo;
        public String forwarderCode;
        public String forwarderName;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public String transportMode;
        public String currency;
        public BigDecimal exchangeRateToCny;
        public BigDecimal expectedTotalAmount;
        public BigDecimal expectedTotalCny;
        public Integer componentCount;
        public String billStatus;
        public String generatedFrom;
        public String reconciliationStatus;
        public BigDecimal actualTotalCny;
        public BigDecimal diffAmountCny;
        public String createdAt;
        public String updatedAt;
        public String rawSnapshotJson;
    }

    public static class LogisticsExpectedBillComponentRecord {
        public Long id;
        public Long ownerUserId;
        public Long expectedBillId;
        public Long shippingOrderId;
        public Long shippingOrderSegmentId;
        public Long shippingOrderLineId;
        public Long quoteLineId;
        public Long productMasterId;
        public Long productVariantId;
        public String barcode;
        public String pskuCode;
        public String siteCode;
        public String boxNo;
        public String feeType;
        public String rawFeeName;
        public BigDecimal quantity;
        public BigDecimal chargeQuantity;
        public String chargeUnit;
        public BigDecimal unitPrice;
        public String currency;
        public BigDecimal exchangeRateToCny;
        public BigDecimal expectedAmount;
        public BigDecimal expectedAmountCny;
        public String allocationBasis;
        public String rawSnapshotJson;
    }

    public static class LogisticsBillReconciliationRecord {
        public Long id;
        public Long ownerUserId;
        public Long shippingOrderId;
        public Long shippingOrderSegmentId;
        public Long expectedBillId;
        public Long actualBillId;
        public String reconciliationNo;
        public String reconciliationStatus;
        public BigDecimal expectedTotalCny;
        public BigDecimal actualTotalCny;
        public BigDecimal diffAmountCny;
        public BigDecimal diffRate;
        public Integer matchedComponentCount;
        public Integer unmatchedExpectedCount;
        public Integer unmatchedActualCount;
        public String summaryJson;
    }

    public static class ShippingOrderRecord {
        public Long id;
        public Long ownerUserId;
        public String shippingOrderNo;
        public String title;
        public String status;
        public Integer purchaseOrderCount;
        public Integer lineCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer missingYiteMaterialCount;
        public String storeSummaryJson;
        public String siteSummaryJson;
        public String transportSummaryJson;
        public String quoteStatus;
        public String shippingSubmitStatus;
        public String forwarderCode;
        public String forwarderName;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public String submittedAt;
        public String remark;
        public String createdAt;
        public String updatedAt;
    }

    public static class ShippingOrderSegmentRecord {
        public Long id;
        public Long shippingOrderId;
        public Long ownerUserId;
        public String segmentNo;
        public String siteCode;
        public String transportMode;
        public String forwarderCode;
        public String forwarderName;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public String quoteStatus;
        public String shippingSubmitStatus;
        public Integer lineCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer missingYiteMaterialCount;
        public String submittedAt;
        public String createdAt;
        public String updatedAt;
    }

    public static class ShippingOrderLineRecord {
        public Long id;
        public Long shippingOrderId;
        public Long shippingOrderSegmentId;
        public String shippingOrderSegmentNo;
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
        public String skuParent;
        public String partnerSku;
        public String barcode;
        public String titleCache;
        public String titleEn;
        public String imageUrlCache;
        public String siteCode;
        public String pskuCode;
        public String yiteMaterial;
        public String plannedTransportMode;
        public Integer quantity;
        public String fulfillmentType;
        public Long quoteLineId;
        public BigDecimal unitPrice;
        public String currency;
        public String billingUnit;
        public String quoteStatus;
        public String shippingSubmitStatus;
    }

    public static class ProductForwarderDeclarationAttributeRecord {
        public Long id;
        public Long ownerUserId;
        public Long productMasterId;
        public Long productVariantId;
        public Long logicalStoreId;
        public String sourceStoreCode;
        public String partnerSku;
        public String barcode;
        public String forwarderCode;
        public String attributeCode;
        public String attributeValue;
        public Long sourceShippingOrderId;
        public Long sourceShippingOrderLineId;
        public String createdAt;
        public String updatedAt;
    }

    public static class PurchaseOrderAli1688HistoryRow {
        public Long allocationId;
        public Long orderId;
        public Long itemId;
        public Long assignmentId;
        public String storeCode;
        public String siteCode;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String productTitle;
        public String orderNo;
        public String orderTime;
        public String supplierName;
        public Integer assignedQuantity;
        public BigDecimal allocatedCost;
        public BigDecimal unitPrice;
        public String sourceLineLabel;
        public String allocationBasis;
        public String evidenceText;
    }

    public static class PurchaseOrderAli1688PurchaseBatchRow {
        public Long id;
        public String storeCode;
        public String siteCode;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String batchLabel;
        public Integer batchSequence;
        public Integer countedQuantity;
        public BigDecimal countedCost;
        public String note;
        public Long sourceOrderId;
        public Long sourceItemId;
        public Long sourceAssignmentId;
        public String orderNo;
        public String orderTime;
        public String supplierName;
    }

    public static class PurchaseOrderRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String orderNo;
        public String title;
        public String remark;
        public String status;
        public String collectionStatus;
        public Integer progressPercent;
        public String siteCodesJson;
        public String projectCodeCache;
        public String projectNameCache;
        public String anchorStoreCodeCache;
        public Integer itemCount;
        public Integer totalQuantity;
        public Integer collectingItemCount;
        public Integer abnormalItemCount;
        public Long createdBy;
        public Long updatedBy;
        public String createdAt;
        public String updatedAt;
    }

    public static class PurchaseOrderItemRecord {
        public Long id;
        public Long purchaseOrderId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long productMasterId;
        public Long productVariantId;
        public String skuParent;
        public String partnerSku;
        public String childSku;
        public String titleCache;
        public String imageUrlCache;
        public String productFulltypeCache;
        public String sourceType;
        public String fulfillmentType;
        public String fulfillmentSourceName;
        public Long manualSelectionSourceCollectionId;
        public String sourcingSpecText;
        public String sourcingSizeText;
        public String sourcingColorText;
        public Integer totalQuantity;
        public Integer siteCount;
        public String collectionStatus;
        public Integer progressPercent;
        public Integer candidateCount;
        public Integer recommendedCount;
        public String failureCode;
        public String failureMessage;
        public Long latestCollectionLinkId;
        public String lastCollectedAt;
        public Long createdBy;
        public Long updatedBy;
        public Long sourceCollectionId;
        public String sourceCollectionNo;
        public String sourcePlatform;
        public String aliTaskNo;
        public String aliStatus;
        public Integer aliProgressPercent;
        public Integer aliCandidateCount;
        public Integer aliRecommendedCount;
        public String aliFailureCode;
        public String aliFailureMessage;
        public String aliFinishedAt;
    }

    public static class PurchaseOrderItemSiteRecord {
        public Long id;
        public Long purchaseOrderId;
        public Long purchaseOrderItemId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public Long siteId;
        public String siteCode;
        public Long productSiteOfferId;
        public String pskuCode;
        public String offerCode;
        public String transportMode;
        public Integer quantity;
        public String status;
        public Long createdBy;
        public Long updatedBy;
    }

    public static class PurchaseOrderDuplicateItemSiteRecord {
        public Long purchaseOrderId;
        public String orderNo;
        public String title;
        public String partnerSku;
        public String siteCode;
        public String transportMode;
    }
}
