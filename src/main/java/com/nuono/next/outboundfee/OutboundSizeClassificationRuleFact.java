package com.nuono.next.outboundfee;

import java.math.BigDecimal;

public class OutboundSizeClassificationRuleFact {

    private final String naturalKey;
    private final String country;
    private final String platform;
    private final String fulfillmentType;
    private final String classificationName;
    private final BigDecimal longestSideMaxCm;
    private final BigDecimal medianSideMaxCm;
    private final BigDecimal shortestSideMaxCm;
    private final BigDecimal maxShippingWeightGrams;
    private final BigDecimal packagingWeightGrams;
    private final Integer priority;
    private final String dimensionUnit;
    private final String weightUnit;
    private final String effectiveFrom;
    private String status;
    private final OfficialOutboundFeeSourceLineage sourceLineage;

    public OutboundSizeClassificationRuleFact(
            String naturalKey,
            String country,
            String platform,
            String fulfillmentType,
            String classificationName,
            BigDecimal longestSideMaxCm,
            BigDecimal medianSideMaxCm,
            BigDecimal shortestSideMaxCm,
            BigDecimal maxShippingWeightGrams,
            BigDecimal packagingWeightGrams,
            Integer priority,
            String dimensionUnit,
            String weightUnit,
            String effectiveFrom,
            String status,
            OfficialOutboundFeeSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.country = country;
        this.platform = platform;
        this.fulfillmentType = fulfillmentType;
        this.classificationName = classificationName;
        this.longestSideMaxCm = longestSideMaxCm;
        this.medianSideMaxCm = medianSideMaxCm;
        this.shortestSideMaxCm = shortestSideMaxCm;
        this.maxShippingWeightGrams = maxShippingWeightGrams;
        this.packagingWeightGrams = packagingWeightGrams;
        this.priority = priority;
        this.dimensionUnit = dimensionUnit;
        this.weightUnit = weightUnit;
        this.effectiveFrom = effectiveFrom;
        this.status = status;
        this.sourceLineage = sourceLineage;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getCountry() {
        return country;
    }

    public String getPlatform() {
        return platform;
    }

    public String getFulfillmentType() {
        return fulfillmentType;
    }

    public String getClassificationName() {
        return classificationName;
    }

    public BigDecimal getLongestSideMaxCm() {
        return longestSideMaxCm;
    }

    public BigDecimal getMedianSideMaxCm() {
        return medianSideMaxCm;
    }

    public BigDecimal getShortestSideMaxCm() {
        return shortestSideMaxCm;
    }

    public BigDecimal getMaxShippingWeightGrams() {
        return maxShippingWeightGrams;
    }

    public BigDecimal getPackagingWeightGrams() {
        return packagingWeightGrams;
    }

    public Integer getPriority() {
        return priority;
    }

    public String getDimensionUnit() {
        return dimensionUnit;
    }

    public String getWeightUnit() {
        return weightUnit;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OfficialOutboundFeeSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
