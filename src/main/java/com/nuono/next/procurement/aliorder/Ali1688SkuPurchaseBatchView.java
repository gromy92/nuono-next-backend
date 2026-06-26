package com.nuono.next.procurement.aliorder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Ali1688SkuPurchaseBatchView {

    public static class SaveRequest {
        private String storeCode;
        private String siteCode;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private List<BatchRequest> batches = new ArrayList<>();

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

        public List<BatchRequest> getBatches() {
            return batches;
        }

        public void setBatches(List<BatchRequest> batches) {
            this.batches = batches;
        }
    }

    public static class BatchRequest {
        private String label;
        private String batchType;
        private Integer countedQuantity;
        private String countedQuantityUnit;
        private BigDecimal countedCost;
        private Integer componentCount;
        private Integer expectedComponentCount;
        private String note;
        private List<SourceRequest> sources = new ArrayList<>();

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
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

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public List<SourceRequest> getSources() {
            return sources;
        }

        public void setSources(List<SourceRequest> sources) {
            this.sources = sources;
        }
    }

    public static class SourceRequest {
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

    public static class SaveResult {
        private int savedBatchCount;
        private int savedSourceCount;

        public static SaveResult saved(int savedBatchCount, int savedSourceCount) {
            SaveResult result = new SaveResult();
            result.setSavedBatchCount(savedBatchCount);
            result.setSavedSourceCount(savedSourceCount);
            return result;
        }

        public int getSavedBatchCount() {
            return savedBatchCount;
        }

        public void setSavedBatchCount(int savedBatchCount) {
            this.savedBatchCount = savedBatchCount;
        }

        public int getSavedSourceCount() {
            return savedSourceCount;
        }

        public void setSavedSourceCount(int savedSourceCount) {
            this.savedSourceCount = savedSourceCount;
        }
    }
}
