package com.nuono.next.commission;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class OfficialCommissionCalculationView {
    private boolean ready;
    private String status;
    private String failureCode;
    private String message;
    private Long ownerUserId;
    private String storeCode;
    private String skuId;
    private Long variantId;
    private String partnerSku;
    private String childSku;
    private String site;
    private String country;
    private String platform = "NOON";
    private String fulfillmentType = "FBN";
    private BigDecimal salePrice;
    private String marketCurrency;
    private String brand;
    private String productFulltype;
    private String categoryPath;
    private String categoryName;
    private String brandRestriction;
    private String amountRangeLabel;
    private BigDecimal amountMin;
    private BigDecimal amountMax;
    private String amountCurrency;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private String currency;
    private BigDecimal taxMultiplier;
    private BigDecimal taxIncludedCommissionAmount;
    private String matchedRuleNaturalKey;
    private Long sourceVersionId;
    private Long calculationFactId;
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private String evidenceJson;

    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }
    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getChildSku() { return childSku; }
    public void setChildSku(String childSku) { this.childSku = childSku; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(String fulfillmentType) { this.fulfillmentType = fulfillmentType; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public String getMarketCurrency() { return marketCurrency; }
    public void setMarketCurrency(String marketCurrency) { this.marketCurrency = marketCurrency; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getProductFulltype() { return productFulltype; }
    public void setProductFulltype(String productFulltype) { this.productFulltype = productFulltype; }
    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getBrandRestriction() { return brandRestriction; }
    public void setBrandRestriction(String brandRestriction) { this.brandRestriction = brandRestriction; }
    public String getAmountRangeLabel() { return amountRangeLabel; }
    public void setAmountRangeLabel(String amountRangeLabel) { this.amountRangeLabel = amountRangeLabel; }
    public BigDecimal getAmountMin() { return amountMin; }
    public void setAmountMin(BigDecimal amountMin) { this.amountMin = amountMin; }
    public BigDecimal getAmountMax() { return amountMax; }
    public void setAmountMax(BigDecimal amountMax) { this.amountMax = amountMax; }
    public String getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(String amountCurrency) { this.amountCurrency = amountCurrency; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getTaxMultiplier() { return taxMultiplier; }
    public void setTaxMultiplier(BigDecimal taxMultiplier) { this.taxMultiplier = taxMultiplier; }
    public BigDecimal getTaxIncludedCommissionAmount() { return taxIncludedCommissionAmount; }
    public void setTaxIncludedCommissionAmount(BigDecimal taxIncludedCommissionAmount) { this.taxIncludedCommissionAmount = taxIncludedCommissionAmount; }
    public String getMatchedRuleNaturalKey() { return matchedRuleNaturalKey; }
    public void setMatchedRuleNaturalKey(String matchedRuleNaturalKey) { this.matchedRuleNaturalKey = matchedRuleNaturalKey; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public void setSourceVersionId(Long sourceVersionId) { this.sourceVersionId = sourceVersionId; }
    public Long getCalculationFactId() { return calculationFactId; }
    public void setCalculationFactId(Long calculationFactId) { this.calculationFactId = calculationFactId; }
    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence == null ? new LinkedHashMap<>() : evidence; }
    @JsonIgnore
    public String getEvidenceJson() { return evidenceJson; }
    @JsonIgnore
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
}
