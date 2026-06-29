package com.nuono.next.procurementorder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProcurementPurchaseOrderViews {

    private ProcurementPurchaseOrderViews() {
    }

    public static class PurchaseOrderView {
        public String id;
        public String orderNo;
        public String title;
        public String storeName;
        public String storeCode;
        public String ownerName;
        public String status;
        public String createdAt;
        public String updatedAt;
        public String remark;
        public List<String> siteCodes = new ArrayList<>();
        public List<PurchaseOrderItemView> items = new ArrayList<>();
        public PurchaseOrderLogisticsQuoteSummaryView logisticsQuoteSummary = new PurchaseOrderLogisticsQuoteSummaryView();
    }

    public static class PurchaseOrderAli1688HistoryView {
        public List<PurchaseOrderAli1688HistoryItemView> items = new ArrayList<>();
        public PaginationView pagination = new PaginationView();
        public Integer unlinkedAssignedLineCount = 0;
    }

    public static class PurchaseOrderAli1688HistoryItemView {
        public String storeCode;
        public String siteCode;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String productTitle;
        public Integer purchaseCount;
        public Integer totalQuantity;
        public BigDecimal totalCost;
        public BigDecimal averageUnitPrice;
        public BigDecimal recentUnitPrice;
        public String recentPurchaseTime;
        public List<PurchaseOrderAli1688HistorySourceView> history = new ArrayList<>();
        public List<PurchaseOrderAli1688PurchaseBatchView> purchaseBatches = new ArrayList<>();
    }

    public static class PurchaseOrderAli1688PurchaseBatchView {
        public Long id;
        public String label;
        public Integer batchSequence;
        public Integer countedQuantity;
        public BigDecimal countedCost;
        public BigDecimal unitPrice;
        public String note;
        public List<PurchaseOrderAli1688HistorySourceView> sources = new ArrayList<>();
    }

    public static class PurchaseOrderAli1688HistorySourceView {
        public Long allocationId;
        public Long orderId;
        public Long itemId;
        public Long assignmentId;
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

    public static class PaginationView {
        public Integer page;
        public Integer pageSize;
        public Integer total;
    }

    public static class PurchaseOrderLogisticsPlanView {
        public String id;
        public String planNo;
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public String storeName;
        public String storeCode;
        public String status;
        public String transportMode;
        public String generatedAt;
        public Integer itemCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer missingItemCount;
        public BigDecimal estimatedSeaVolumeCbm;
        public String estimatedSeaVolumeCbmText;
        public BigDecimal estimatedAirChargeableWeightKg;
        public String estimatedAirChargeableWeightKgText;
        public String recommendationStatus;
        public List<SiteQuantitySummaryView> siteSummaries = new ArrayList<>();
        public List<String> messages = new ArrayList<>();
        public List<PurchaseOrderLogisticsRecommendationView> recommendations = new ArrayList<>();
        public List<PurchaseOrderLogisticsPlanLineView> lines = new ArrayList<>();
    }

    public static class PurchaseOrderLogisticsQuoteSummaryView {
        public Integer totalLineCount = 0;
        public Integer pendingLineCount = 0;
        public Integer confirmedLineCount = 0;
        public Integer submittedLineCount = 0;
        public Integer newProductLineCount = 0;
        public String shippingSubmitStatus = "NOT_SUBMITTED";
    }

    public static class PurchaseOrderLogisticsQuoteOptionsView {
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public Integer pendingLineCount = 0;
        public Integer unsupportedChannelCount = 0;
        public List<PurchaseOrderLogisticsQuoteForwarderOptionView> forwarders = new ArrayList<>();
    }

    public static class PurchaseOrderLogisticsQuoteForwarderOptionView {
        public String forwarderCode;
        public String forwarderName;
        public String templateType;
        public String templateName;
        public List<PurchaseOrderLogisticsQuoteChannelOptionView> channels = new ArrayList<>();
    }

    public static class PurchaseOrderLogisticsQuoteChannelOptionView {
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public String siteCode;
        public String transportMode;
        public String transportModeLabel;
        public String country;
        public String targetPlatform;
        public String deliveryCity;
        public String destinationNode;
        public String transitTimeText;
        public String priceSummary;
        public Integer pendingLineCount = 0;
        public Integer newProductLineCount = 0;
    }

    public static class PurchaseOrderLogisticsQuoteReportExportView {
        public String filename;
        public String contentType;
        public byte[] content;
        public Integer rowCount = 0;
        public Integer pendingCount = 0;
        public Integer newProductCount = 0;
    }

    public static class PurchaseOrderLogisticsQuoteImportView {
        public Integer totalRows = 0;
        public Integer updatedRows = 0;
        public Integer skippedRows = 0;
        public List<PurchaseOrderLogisticsQuoteImportErrorView> errors = new ArrayList<>();
    }

    public static class PurchaseOrderLogisticsQuoteImportErrorView {
        public Integer rowNumber;
        public String message;
    }

    public static class PurchaseOrderShippingSubmitView {
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public String shippingSubmitStatus;
        public Integer submittedLineCount = 0;
    }

    public static class ShippingOrderView {
        public String id;
        public String shippingOrderNo;
        public String title;
        public String status;
        public Integer purchaseOrderCount = 0;
        public Integer lineCount = 0;
        public Integer skuCount = 0;
        public Integer totalQuantity = 0;
        public Integer missingYiteMaterialCount = 0;
        public String quoteStatus;
        public String shippingSubmitStatus;
        public String forwarderName;
        public String routeName;
        public String submittedAt;
        public String remark;
        public String createdAt;
        public String updatedAt;
        public List<String> warnings = new ArrayList<>();
        public List<ShippingOrderSegmentView> segments = new ArrayList<>();
        public List<ShippingOrderLineView> lines = new ArrayList<>();
    }

    public static class ShippingOrderSegmentView {
        public String id;
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
        public Integer lineCount = 0;
        public Integer skuCount = 0;
        public Integer totalQuantity = 0;
        public Integer missingYiteMaterialCount = 0;
        public String submittedAt;
    }

    public static class ShippingOrderLineView {
        public String id;
        public String shippingOrderSegmentId;
        public String shippingOrderSegmentNo;
        public String sourceStoreCode;
        public String sourceStoreName;
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public String purchaseOrderItemId;
        public String purchaseOrderItemSiteId;
        public String partnerSku;
        public String skuParent;
        public String barcode;
        public String productTitle;
        public String productTitleCn;
        public String productTitleEn;
        public String productImageUrl;
        public String siteCode;
        public String pskuCode;
        public String yiteMaterial;
        public String plannedTransportMode;
        public String quoteStatus;
        public String shippingSubmitStatus;
        public String fulfillmentType;
        public Integer quantity = 0;
    }

    public static class ShippingOrderSubmitView {
        public String shippingOrderId;
        public String shippingOrderNo;
        public String shippingSubmitStatus;
        public Integer submittedLineCount = 0;
    }

    public static class LogisticsBillView {
        public String id;
        public String expectedBillNo;
        public String shippingOrderId;
        public String shippingOrderNo;
        public String shippingOrderTitle;
        public String shippingOrderSegmentId;
        public String shippingOrderSegmentNo;
        public String forwarderCode;
        public String forwarderName;
        public String routeCode;
        public String routeName;
        public String serviceCode;
        public String serviceName;
        public String transportMode;
        public String currency;
        public BigDecimal expectedTotalAmount;
        public BigDecimal expectedTotalCny;
        public BigDecimal actualTotalCny;
        public BigDecimal diffAmountCny;
        public Integer componentCount = 0;
        public String billStatus;
        public String reconciliationStatus;
        public String createdAt;
        public String updatedAt;
        public List<LogisticsBillComponentView> components = new ArrayList<>();
    }

    public static class LogisticsBillComponentView {
        public String id;
        public String shippingOrderSegmentId;
        public String shippingOrderLineId;
        public String quoteLineId;
        public String barcode;
        public String pskuCode;
        public String siteCode;
        public String feeType;
        public BigDecimal quantity;
        public BigDecimal chargeQuantity;
        public String chargeUnit;
        public BigDecimal unitPrice;
        public String currency;
        public BigDecimal expectedAmount;
        public BigDecimal expectedAmountCny;
    }

    public static class PurchaseOrderLogisticsRecommendationView {
        public Integer rank;
        public boolean recommended;
        public String routeCode;
        public String routeName;
        public String forwarderCode;
        public String forwarderName;
        public String serviceCode;
        public String serviceName;
        public String transportMode;
        public String country;
        public String targetPlatform;
        public String deliveryCity;
        public String destinationNode;
        public String transitTimeText;
        public String priceSummary;
        public String cargoCategorySummary;
        public String estimateStatus;
        public String estimatedCostText;
        public BigDecimal estimatedTotalAmount;
        public String estimatedTotalCostText;
        public BigDecimal recurringAmountPerDay;
        public String recurringCostText;
        public List<PurchaseOrderLogisticsCostComponentView> costComponents = new ArrayList<>();
        public List<String> excludedCostNotes = new ArrayList<>();
        public List<String> reasons = new ArrayList<>();
        public List<String> risks = new ArrayList<>();
    }

    public static class PurchaseOrderLogisticsCostComponentView {
        public String componentType;
        public String componentName;
        public String currency;
        public BigDecimal unitPrice;
        public String billingUnit;
        public BigDecimal billableQuantity;
        public BigDecimal amount;
        public String amountText;
        public String amountStatus;
        public boolean includedInTotal;
        public String formulaText;
        public String sourceServiceCode;
        public Long sourceId;
        public String sourceFeeName;
        public String remark;
    }

    public static class SiteQuantitySummaryView {
        public String site;
        public String siteName;
        public String transportMode;
        public String transportModeLabel;
        public Integer quantity;
    }

    public static class PurchaseOrderLogisticsPlanLineView {
        public String itemId;
        public String partnerSku;
        public String productTitle;
        public String productImageUrl;
        public Integer totalQuantity;
        public List<SiteAllocationView> allocations = new ArrayList<>();
        public String productDimensionsText;
        public String productWeightText;
        public String cartonDimensionsText;
        public String cartonWeightText;
        public Integer cartonQuantity;
        public BigDecimal looseVolumeCbm;
        public String looseVolumeCbmText;
        public Integer seaQuantity;
        public BigDecimal seaLooseVolumeCbm;
        public String seaLooseVolumeCbmText;
        public Integer airQuantity;
        public BigDecimal airActualWeightKg;
        public String airActualWeightKgText;
        public BigDecimal airLooseVolumeCbm;
        public String airLooseVolumeCbmText;
        public String specSourceType;
        public List<String> missingFields = new ArrayList<>();
    }

    public static class PurchaseOrderItemView {
        public String id;
        public String sourceCollectionId;
        public String sourceCollectionNo;
        public String sourcePlatform;
        public String sourceTitle;
        public String sourceTitleCn;
        public String sourceImageUrl;
        public String variantId;
        public String skuParent;
        public String partnerSku;
        public String productFulltype;
        public String productTitle;
        public String productImageUrl;
        public String sourcingSpec;
        public String sourcingSize;
        public String sourcingColor;
        public String fulfillmentType;
        public String fulfillmentTypeLabel;
        public String fulfillmentSourceName;
        public Integer totalQuantity;
        public List<SiteAllocationView> allocations = new ArrayList<>();
        public String collectionStatus;
        public Integer progress;
        public String currentTaskNo;
        public Integer candidateCount;
        public Integer top5Count;
        public String failureMessage;
        public String lastCollectedAt;
    }

    public static class SiteAllocationView {
        public String site;
        public String siteName;
        public Long siteId;
        public String pskuCode;
        public String transportMode;
        public String transportModeLabel;
        public Integer quantity;
        public boolean enabled;
    }

    public static class ProductOptionView {
        public String variantId;
        public String skuParent;
        public String partnerSku;
        public String productTitle;
        public String productImageUrl;
        public List<String> availableSiteCodes = new ArrayList<>();
    }
}
