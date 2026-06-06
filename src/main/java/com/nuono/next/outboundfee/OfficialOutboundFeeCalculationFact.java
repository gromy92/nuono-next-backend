package com.nuono.next.outboundfee;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OfficialOutboundFeeCalculationFact {
    private Long ownerUserId;
    private String storeCode;
    private String site;
    private String country;
    private String platform;
    private String fulfillmentType;
    private Long variantId;
    private String skuId;
    private String partnerSku;
    private String childSku;
    private Long effectiveSourceId;
    private String effectiveSourceType;
    private BigDecimal productLengthCm;
    private BigDecimal productWidthCm;
    private BigDecimal productHeightCm;
    private BigDecimal productWeightG;
    private BigDecimal salePrice;
    private String marketCurrency;
    private LocalDate calculationDate;
    private BigDecimal feeAmount;
    private String currency;
    private BigDecimal taxMultiplier;
    private BigDecimal taxIncludedFeeAmount;
    private String matchedClassificationName;
    private String matchedSlabNaturalKey;
    private Long sourceVersionId;
    private String evidenceJson;
    private String status;
    private String failureCode;
    private String message;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getFulfillmentType() {
        return fulfillmentType;
    }

    public void setFulfillmentType(String fulfillmentType) {
        this.fulfillmentType = fulfillmentType;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getChildSku() {
        return childSku;
    }

    public void setChildSku(String childSku) {
        this.childSku = childSku;
    }

    public Long getEffectiveSourceId() {
        return effectiveSourceId;
    }

    public void setEffectiveSourceId(Long effectiveSourceId) {
        this.effectiveSourceId = effectiveSourceId;
    }

    public String getEffectiveSourceType() {
        return effectiveSourceType;
    }

    public void setEffectiveSourceType(String effectiveSourceType) {
        this.effectiveSourceType = effectiveSourceType;
    }

    public BigDecimal getProductLengthCm() {
        return productLengthCm;
    }

    public void setProductLengthCm(BigDecimal productLengthCm) {
        this.productLengthCm = productLengthCm;
    }

    public BigDecimal getProductWidthCm() {
        return productWidthCm;
    }

    public void setProductWidthCm(BigDecimal productWidthCm) {
        this.productWidthCm = productWidthCm;
    }

    public BigDecimal getProductHeightCm() {
        return productHeightCm;
    }

    public void setProductHeightCm(BigDecimal productHeightCm) {
        this.productHeightCm = productHeightCm;
    }

    public BigDecimal getProductWeightG() {
        return productWeightG;
    }

    public void setProductWeightG(BigDecimal productWeightG) {
        this.productWeightG = productWeightG;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public String getMarketCurrency() {
        return marketCurrency;
    }

    public void setMarketCurrency(String marketCurrency) {
        this.marketCurrency = marketCurrency;
    }

    public LocalDate getCalculationDate() {
        return calculationDate;
    }

    public void setCalculationDate(LocalDate calculationDate) {
        this.calculationDate = calculationDate;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTaxMultiplier() {
        return taxMultiplier;
    }

    public void setTaxMultiplier(BigDecimal taxMultiplier) {
        this.taxMultiplier = taxMultiplier;
    }

    public BigDecimal getTaxIncludedFeeAmount() {
        return taxIncludedFeeAmount;
    }

    public void setTaxIncludedFeeAmount(BigDecimal taxIncludedFeeAmount) {
        this.taxIncludedFeeAmount = taxIncludedFeeAmount;
    }

    public String getMatchedClassificationName() {
        return matchedClassificationName;
    }

    public void setMatchedClassificationName(String matchedClassificationName) {
        this.matchedClassificationName = matchedClassificationName;
    }

    public String getMatchedSlabNaturalKey() {
        return matchedSlabNaturalKey;
    }

    public void setMatchedSlabNaturalKey(String matchedSlabNaturalKey) {
        this.matchedSlabNaturalKey = matchedSlabNaturalKey;
    }

    public Long getSourceVersionId() {
        return sourceVersionId;
    }

    public void setSourceVersionId(Long sourceVersionId) {
        this.sourceVersionId = sourceVersionId;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
