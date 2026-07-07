package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PostSaleProfitBatchRowView {
    private Long batchId;
    private String sourceId;
    private String skuParent;
    private String partnerSku;
    private String productTitle;
    private String productImageUrl;
    private LocalDateTime purchaseBatchTime;
    private BigDecimal purchaseQuantity;
    private BigDecimal purchaseUnitCostCny;
    private BigDecimal purchaseCostCny;
    private String shippingSourceType;
    private String shippingSourceId;
    private String shippingBatchNo;
    private String inTransitBatchId;
    private String inTransitReferenceNo;
    private LocalDateTime availableAt;
    private String availableAtSource;
    private String headhaulCostSourceType;
    private BigDecimal headhaulUnitCostCny;
    private BigDecimal headhaulCostCny;
    private BigDecimal soldQuantity;
    private BigDecimal autoQuantity;
    private BigDecimal lockedQuantity;
    private BigDecimal netProceedsLcy;
    private BigDecimal referralFeeLcy;
    private BigDecimal fulfillmentFeeLcy;
    private BigDecimal otherFeeNetLcy;
    private BigDecimal averageSalePriceLcy;
    private BigDecimal gmvLcy;
    private Integer salePriceFactCount;
    private String currency;
    private BigDecimal fxRateToCny;
    private BigDecimal profitCny;
    private BigDecimal profitRate;
    private List<String> qualityStatuses = List.of();
    private String evidenceJson;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSkuParent() { return skuParent; }
    public void setSkuParent(String skuParent) { this.skuParent = skuParent; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    public String getProductImageUrl() { return productImageUrl; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
    public LocalDateTime getPurchaseBatchTime() { return purchaseBatchTime; }
    public void setPurchaseBatchTime(LocalDateTime purchaseBatchTime) { this.purchaseBatchTime = purchaseBatchTime; }
    public BigDecimal getPurchaseQuantity() { return purchaseQuantity; }
    public void setPurchaseQuantity(BigDecimal purchaseQuantity) { this.purchaseQuantity = purchaseQuantity; }
    public BigDecimal getPurchaseUnitCostCny() { return purchaseUnitCostCny; }
    public void setPurchaseUnitCostCny(BigDecimal purchaseUnitCostCny) { this.purchaseUnitCostCny = purchaseUnitCostCny; }
    public BigDecimal getPurchaseCostCny() { return purchaseCostCny; }
    public void setPurchaseCostCny(BigDecimal purchaseCostCny) { this.purchaseCostCny = purchaseCostCny; }
    public String getShippingSourceType() { return shippingSourceType; }
    public void setShippingSourceType(String shippingSourceType) { this.shippingSourceType = shippingSourceType; }
    public String getShippingSourceId() { return shippingSourceId; }
    public void setShippingSourceId(String shippingSourceId) { this.shippingSourceId = shippingSourceId; }
    public String getShippingBatchNo() { return shippingBatchNo; }
    public void setShippingBatchNo(String shippingBatchNo) { this.shippingBatchNo = shippingBatchNo; }
    public String getInTransitBatchId() { return inTransitBatchId; }
    public void setInTransitBatchId(String inTransitBatchId) { this.inTransitBatchId = inTransitBatchId; }
    public String getInTransitReferenceNo() { return inTransitReferenceNo; }
    public void setInTransitReferenceNo(String inTransitReferenceNo) { this.inTransitReferenceNo = inTransitReferenceNo; }
    public LocalDateTime getAvailableAt() { return availableAt; }
    public void setAvailableAt(LocalDateTime availableAt) { this.availableAt = availableAt; }
    public String getAvailableAtSource() { return availableAtSource; }
    public void setAvailableAtSource(String availableAtSource) { this.availableAtSource = availableAtSource; }
    public String getHeadhaulCostSourceType() { return headhaulCostSourceType; }
    public void setHeadhaulCostSourceType(String headhaulCostSourceType) { this.headhaulCostSourceType = headhaulCostSourceType; }
    public BigDecimal getHeadhaulUnitCostCny() { return headhaulUnitCostCny; }
    public void setHeadhaulUnitCostCny(BigDecimal headhaulUnitCostCny) { this.headhaulUnitCostCny = headhaulUnitCostCny; }
    public BigDecimal getHeadhaulCostCny() { return headhaulCostCny; }
    public void setHeadhaulCostCny(BigDecimal headhaulCostCny) { this.headhaulCostCny = headhaulCostCny; }
    public BigDecimal getSoldQuantity() { return soldQuantity; }
    public void setSoldQuantity(BigDecimal soldQuantity) { this.soldQuantity = soldQuantity; }
    public BigDecimal getAutoQuantity() { return autoQuantity; }
    public void setAutoQuantity(BigDecimal autoQuantity) { this.autoQuantity = autoQuantity; }
    public BigDecimal getLockedQuantity() { return lockedQuantity; }
    public void setLockedQuantity(BigDecimal lockedQuantity) { this.lockedQuantity = lockedQuantity; }
    public BigDecimal getNetProceedsLcy() { return netProceedsLcy; }
    public void setNetProceedsLcy(BigDecimal netProceedsLcy) { this.netProceedsLcy = netProceedsLcy; }
    public BigDecimal getReferralFeeLcy() { return referralFeeLcy; }
    public void setReferralFeeLcy(BigDecimal referralFeeLcy) { this.referralFeeLcy = referralFeeLcy; }
    public BigDecimal getFulfillmentFeeLcy() { return fulfillmentFeeLcy; }
    public void setFulfillmentFeeLcy(BigDecimal fulfillmentFeeLcy) { this.fulfillmentFeeLcy = fulfillmentFeeLcy; }
    public BigDecimal getOtherFeeNetLcy() { return otherFeeNetLcy; }
    public void setOtherFeeNetLcy(BigDecimal otherFeeNetLcy) { this.otherFeeNetLcy = otherFeeNetLcy; }
    public BigDecimal getAverageSalePriceLcy() { return averageSalePriceLcy; }
    public void setAverageSalePriceLcy(BigDecimal averageSalePriceLcy) { this.averageSalePriceLcy = averageSalePriceLcy; }
    public BigDecimal getGmvLcy() { return gmvLcy; }
    public void setGmvLcy(BigDecimal gmvLcy) { this.gmvLcy = gmvLcy; }
    public Integer getSalePriceFactCount() { return salePriceFactCount; }
    public void setSalePriceFactCount(Integer salePriceFactCount) { this.salePriceFactCount = salePriceFactCount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getFxRateToCny() { return fxRateToCny; }
    public void setFxRateToCny(BigDecimal fxRateToCny) { this.fxRateToCny = fxRateToCny; }
    public BigDecimal getProfitCny() { return profitCny; }
    public void setProfitCny(BigDecimal profitCny) { this.profitCny = profitCny; }
    public BigDecimal getProfitRate() { return profitRate; }
    public void setProfitRate(BigDecimal profitRate) { this.profitRate = profitRate; }
    public List<String> getQualityStatuses() { return qualityStatuses; }
    public void setQualityStatuses(List<String> qualityStatuses) {
        this.qualityStatuses = qualityStatuses == null ? List.of() : List.copyOf(qualityStatuses);
    }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
}
