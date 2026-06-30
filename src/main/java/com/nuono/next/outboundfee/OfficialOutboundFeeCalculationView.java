package com.nuono.next.outboundfee;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class OfficialOutboundFeeCalculationView {

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

    private Long effectiveSourceId;

    private String specSourceType;

    private BigDecimal lengthCm;

    private BigDecimal widthCm;

    private BigDecimal heightCm;

    private BigDecimal weightGrams;

    private BigDecimal feeAmount;

    private String currency;

    private BigDecimal taxMultiplier;

    private BigDecimal taxIncludedFeeAmount;

    private String matchedClassificationName;

    private String matchedSlabNaturalKey;

    private Long sourceVersionId;

    private Long calculationFactId;

    private Map<String, Object> evidence = new LinkedHashMap<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
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

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
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

    public Long getEffectiveSourceId() {
        return effectiveSourceId;
    }

    public void setEffectiveSourceId(Long effectiveSourceId) {
        this.effectiveSourceId = effectiveSourceId;
    }

    public String getSpecSourceType() {
        return specSourceType;
    }

    public void setSpecSourceType(String specSourceType) {
        this.specSourceType = specSourceType;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(BigDecimal widthCm) {
        this.widthCm = widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(BigDecimal weightGrams) {
        this.weightGrams = weightGrams;
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

    public Long getCalculationFactId() {
        return calculationFactId;
    }

    public void setCalculationFactId(Long calculationFactId) {
        this.calculationFactId = calculationFactId;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence);
    }
}
