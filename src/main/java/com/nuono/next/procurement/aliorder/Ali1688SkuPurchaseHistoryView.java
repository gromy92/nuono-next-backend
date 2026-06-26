package com.nuono.next.procurement.aliorder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Ali1688SkuPurchaseHistoryView {

    private List<ItemView> items = new ArrayList<>();
    private PaginationView pagination = new PaginationView();
    private int unlinkedAssignedLineCount;

    public static Ali1688SkuPurchaseHistoryView of(
            List<ItemView> items,
            int page,
            int pageSize,
            int total
    ) {
        Ali1688SkuPurchaseHistoryView view = new Ali1688SkuPurchaseHistoryView();
        view.setItems(items == null ? new ArrayList<>() : items);
        PaginationView pagination = new PaginationView();
        pagination.setPage(page);
        pagination.setPageSize(pageSize);
        pagination.setTotal(total);
        view.setPagination(pagination);
        return view;
    }

    public List<ItemView> getItems() {
        return items;
    }

    public void setItems(List<ItemView> items) {
        this.items = items;
    }

    public PaginationView getPagination() {
        return pagination;
    }

    public void setPagination(PaginationView pagination) {
        this.pagination = pagination;
    }

    public int getUnlinkedAssignedLineCount() {
        return unlinkedAssignedLineCount;
    }

    public void setUnlinkedAssignedLineCount(int unlinkedAssignedLineCount) {
        this.unlinkedAssignedLineCount = unlinkedAssignedLineCount;
    }

    public static class ItemView {
        private String storeCode;
        private String siteCode;
        private String linkStatus;
        private Long assignmentId;
        private Long orderId;
        private Long itemId;
        private String orderNo;
        private String orderTime;
        private String supplierName;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String productTitle;
        private String productTitleCn;
        private String productImageUrl;
        private String sourceOfferId;
        private String sourceSkuId;
        private String sourceProductCode;
        private String sourceSingleProductCode;
        private int purchaseCount;
        private int totalQuantity;
        private BigDecimal totalCost;
        private BigDecimal averageUnitPrice;
        private BigDecimal recentUnitPrice;
        private String recentPurchaseTime;
        private BigDecimal lowestUnitPrice;
        private BigDecimal highestUnitPrice;
        private int priceAnomalyCount;
        private BigDecimal stableAverageUnitPrice;
        private String amountBasis;
        private List<String> dataQualityFlags = new ArrayList<>();
        private List<HistoryView> history = new ArrayList<>();
        private List<PurchaseBatchView> purchaseBatches = new ArrayList<>();

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public String getLinkStatus() {
            return linkStatus;
        }

        public void setLinkStatus(String linkStatus) {
            this.linkStatus = linkStatus;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getOrderTime() {
            return orderTime;
        }

        public void setOrderTime(String orderTime) {
            this.orderTime = orderTime;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getProductTitle() {
            return productTitle;
        }

        public void setProductTitle(String productTitle) {
            this.productTitle = productTitle;
        }

        public String getProductTitleCn() {
            return productTitleCn;
        }

        public void setProductTitleCn(String productTitleCn) {
            this.productTitleCn = productTitleCn;
        }

        public String getProductImageUrl() {
            return productImageUrl;
        }

        public void setProductImageUrl(String productImageUrl) {
            this.productImageUrl = Ali1688HistoricalOrderProductLinkView.normalizeNoonImageUrl(productImageUrl);
        }

        public String getSourceOfferId() {
            return sourceOfferId;
        }

        public void setSourceOfferId(String sourceOfferId) {
            this.sourceOfferId = sourceOfferId;
        }

        public String getSourceSkuId() {
            return sourceSkuId;
        }

        public void setSourceSkuId(String sourceSkuId) {
            this.sourceSkuId = sourceSkuId;
        }

        public String getSourceProductCode() {
            return sourceProductCode;
        }

        public void setSourceProductCode(String sourceProductCode) {
            this.sourceProductCode = sourceProductCode;
        }

        public String getSourceSingleProductCode() {
            return sourceSingleProductCode;
        }

        public void setSourceSingleProductCode(String sourceSingleProductCode) {
            this.sourceSingleProductCode = sourceSingleProductCode;
        }

        public int getPurchaseCount() {
            return purchaseCount;
        }

        public void setPurchaseCount(int purchaseCount) {
            this.purchaseCount = purchaseCount;
        }

        public int getTotalQuantity() {
            return totalQuantity;
        }

        public void setTotalQuantity(int totalQuantity) {
            this.totalQuantity = totalQuantity;
        }

        public BigDecimal getTotalCost() {
            return totalCost;
        }

        public void setTotalCost(BigDecimal totalCost) {
            this.totalCost = totalCost;
        }

        public BigDecimal getAverageUnitPrice() {
            return averageUnitPrice;
        }

        public void setAverageUnitPrice(BigDecimal averageUnitPrice) {
            this.averageUnitPrice = averageUnitPrice;
        }

        public BigDecimal getRecentUnitPrice() {
            return recentUnitPrice;
        }

        public void setRecentUnitPrice(BigDecimal recentUnitPrice) {
            this.recentUnitPrice = recentUnitPrice;
        }

        public String getRecentPurchaseTime() {
            return recentPurchaseTime;
        }

        public void setRecentPurchaseTime(String recentPurchaseTime) {
            this.recentPurchaseTime = recentPurchaseTime;
        }

        public BigDecimal getLowestUnitPrice() {
            return lowestUnitPrice;
        }

        public void setLowestUnitPrice(BigDecimal lowestUnitPrice) {
            this.lowestUnitPrice = lowestUnitPrice;
        }

        public BigDecimal getHighestUnitPrice() {
            return highestUnitPrice;
        }

        public void setHighestUnitPrice(BigDecimal highestUnitPrice) {
            this.highestUnitPrice = highestUnitPrice;
        }

        public int getPriceAnomalyCount() {
            return priceAnomalyCount;
        }

        public void setPriceAnomalyCount(int priceAnomalyCount) {
            this.priceAnomalyCount = priceAnomalyCount;
        }

        public BigDecimal getStableAverageUnitPrice() {
            return stableAverageUnitPrice;
        }

        public void setStableAverageUnitPrice(BigDecimal stableAverageUnitPrice) {
            this.stableAverageUnitPrice = stableAverageUnitPrice;
        }

        public String getAmountBasis() {
            return amountBasis;
        }

        public void setAmountBasis(String amountBasis) {
            this.amountBasis = amountBasis;
        }

        public List<String> getDataQualityFlags() {
            return dataQualityFlags;
        }

        public void setDataQualityFlags(List<String> dataQualityFlags) {
            this.dataQualityFlags = dataQualityFlags;
        }

        public List<HistoryView> getHistory() {
            return history;
        }

        public void setHistory(List<HistoryView> history) {
            this.history = history;
        }

        public List<PurchaseBatchView> getPurchaseBatches() {
            return purchaseBatches;
        }

        public void setPurchaseBatches(List<PurchaseBatchView> purchaseBatches) {
            this.purchaseBatches = purchaseBatches;
        }
    }

    public static class HistoryView {
        private Long orderId;
        private Long itemId;
        private Long assignmentId;
        private String orderNo;
        private String orderTime;
        private String supplierName;
        private Integer assignedQuantity;
        private BigDecimal allocatedCost;
        private BigDecimal unitPrice;
        private String amountBasis;
        private String priceQuality;

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getOrderTime() {
            return orderTime;
        }

        public void setOrderTime(String orderTime) {
            this.orderTime = orderTime;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public Integer getAssignedQuantity() {
            return assignedQuantity;
        }

        public void setAssignedQuantity(Integer assignedQuantity) {
            this.assignedQuantity = assignedQuantity;
        }

        public BigDecimal getAllocatedCost() {
            return allocatedCost;
        }

        public void setAllocatedCost(BigDecimal allocatedCost) {
            this.allocatedCost = allocatedCost;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getAmountBasis() {
            return amountBasis;
        }

        public void setAmountBasis(String amountBasis) {
            this.amountBasis = amountBasis;
        }

        public String getPriceQuality() {
            return priceQuality;
        }

        public void setPriceQuality(String priceQuality) {
            this.priceQuality = priceQuality;
        }
    }

    public static class PaginationView {
        private int page;
        private int pageSize;
        private int total;

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    public static class PurchaseBatchView {
        private Long id;
        private String label;
        private Integer batchSequence;
        private String batchType;
        private Integer countedQuantity;
        private String countedQuantityUnit;
        private BigDecimal countedCost;
        private Integer componentCount;
        private Integer expectedComponentCount;
        private BigDecimal unitPrice;
        private String note;
        private List<PurchaseBatchSourceView> sources = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Integer getBatchSequence() {
            return batchSequence;
        }

        public void setBatchSequence(Integer batchSequence) {
            this.batchSequence = batchSequence;
        }

        public String getBatchType() {
            return batchType;
        }

        public void setBatchType(String batchType) {
            this.batchType = batchType;
        }

        public Integer getCountedQuantity() {
            return countedQuantity;
        }

        public void setCountedQuantity(Integer countedQuantity) {
            this.countedQuantity = countedQuantity;
        }

        public String getCountedQuantityUnit() {
            return countedQuantityUnit;
        }

        public void setCountedQuantityUnit(String countedQuantityUnit) {
            this.countedQuantityUnit = countedQuantityUnit;
        }

        public BigDecimal getCountedCost() {
            return countedCost;
        }

        public void setCountedCost(BigDecimal countedCost) {
            this.countedCost = countedCost;
        }

        public Integer getComponentCount() {
            return componentCount;
        }

        public void setComponentCount(Integer componentCount) {
            this.componentCount = componentCount;
        }

        public Integer getExpectedComponentCount() {
            return expectedComponentCount;
        }

        public void setExpectedComponentCount(Integer expectedComponentCount) {
            this.expectedComponentCount = expectedComponentCount;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public List<PurchaseBatchSourceView> getSources() {
            return sources;
        }

        public void setSources(List<PurchaseBatchSourceView> sources) {
            this.sources = sources;
        }
    }

    public static class PurchaseBatchSourceView {
        private Long orderId;
        private Long itemId;
        private Long assignmentId;
        private Integer componentSequence;
        private String componentRole;
        private String orderNo;
        private String orderTime;
        private String supplierName;
        private String sourceOfferId;
        private String sourceSkuId;
        private String sourceTitle;
        private String sourceSpec;
        private BigDecimal sourceQuantity;
        private String sourceUnit;
        private BigDecimal sourceUnitPrice;
        private BigDecimal sourceAmount;
        private BigDecimal sourceQuantityPerCountedUnit;

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public Integer getComponentSequence() {
            return componentSequence;
        }

        public void setComponentSequence(Integer componentSequence) {
            this.componentSequence = componentSequence;
        }

        public String getComponentRole() {
            return componentRole;
        }

        public void setComponentRole(String componentRole) {
            this.componentRole = componentRole;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getOrderTime() {
            return orderTime;
        }

        public void setOrderTime(String orderTime) {
            this.orderTime = orderTime;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getSourceOfferId() {
            return sourceOfferId;
        }

        public void setSourceOfferId(String sourceOfferId) {
            this.sourceOfferId = sourceOfferId;
        }

        public String getSourceSkuId() {
            return sourceSkuId;
        }

        public void setSourceSkuId(String sourceSkuId) {
            this.sourceSkuId = sourceSkuId;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public String getSourceSpec() {
            return sourceSpec;
        }

        public void setSourceSpec(String sourceSpec) {
            this.sourceSpec = sourceSpec;
        }

        public BigDecimal getSourceQuantity() {
            return sourceQuantity;
        }

        public void setSourceQuantity(BigDecimal sourceQuantity) {
            this.sourceQuantity = sourceQuantity;
        }

        public String getSourceUnit() {
            return sourceUnit;
        }

        public void setSourceUnit(String sourceUnit) {
            this.sourceUnit = sourceUnit;
        }

        public BigDecimal getSourceUnitPrice() {
            return sourceUnitPrice;
        }

        public void setSourceUnitPrice(BigDecimal sourceUnitPrice) {
            this.sourceUnitPrice = sourceUnitPrice;
        }

        public BigDecimal getSourceAmount() {
            return sourceAmount;
        }

        public void setSourceAmount(BigDecimal sourceAmount) {
            this.sourceAmount = sourceAmount;
        }

        public BigDecimal getSourceQuantityPerCountedUnit() {
            return sourceQuantityPerCountedUnit;
        }

        public void setSourceQuantityPerCountedUnit(BigDecimal sourceQuantityPerCountedUnit) {
            this.sourceQuantityPerCountedUnit = sourceQuantityPerCountedUnit;
        }
    }
}
