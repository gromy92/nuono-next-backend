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
