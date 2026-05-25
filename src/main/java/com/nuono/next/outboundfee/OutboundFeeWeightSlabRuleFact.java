package com.nuono.next.outboundfee;

import java.math.BigDecimal;

public class OutboundFeeWeightSlabRuleFact {

    private final String naturalKey;
    private final String country;
    private final String platform;
    private final String fulfillmentType;
    private final String classificationName;
    private final BigDecimal weightMinGrams;
    private final Boolean weightMinInclusive;
    private final BigDecimal weightMaxGrams;
    private final Boolean weightMaxInclusive;
    private final BigDecimal standardFeeAmount;
    private final BigDecimal highAspFeeAmount;
    private final BigDecimal salesPriceThresholdAmount;
    private final String thresholdCurrency;
    private final BigDecimal extraWeightStepGrams;
    private final BigDecimal extraFeeAmount;
    private final String currency;
    private final String effectiveFrom;
    private String status;
    private final OfficialOutboundFeeSourceLineage sourceLineage;

    public OutboundFeeWeightSlabRuleFact(
            String naturalKey,
            String country,
            String platform,
            String fulfillmentType,
            String classificationName,
            BigDecimal weightMinGrams,
            Boolean weightMinInclusive,
            BigDecimal weightMaxGrams,
            Boolean weightMaxInclusive,
            BigDecimal standardFeeAmount,
            BigDecimal highAspFeeAmount,
            BigDecimal salesPriceThresholdAmount,
            String thresholdCurrency,
            BigDecimal extraWeightStepGrams,
            BigDecimal extraFeeAmount,
            String currency,
            String effectiveFrom,
            String status,
            OfficialOutboundFeeSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.country = country;
        this.platform = platform;
        this.fulfillmentType = fulfillmentType;
        this.classificationName = classificationName;
        this.weightMinGrams = weightMinGrams;
        this.weightMinInclusive = weightMinInclusive;
        this.weightMaxGrams = weightMaxGrams;
        this.weightMaxInclusive = weightMaxInclusive;
        this.standardFeeAmount = standardFeeAmount;
        this.highAspFeeAmount = highAspFeeAmount;
        this.salesPriceThresholdAmount = salesPriceThresholdAmount;
        this.thresholdCurrency = thresholdCurrency;
        this.extraWeightStepGrams = extraWeightStepGrams;
        this.extraFeeAmount = extraFeeAmount;
        this.currency = currency;
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

    public BigDecimal getWeightMinGrams() {
        return weightMinGrams;
    }

    public Boolean getWeightMinInclusive() {
        return weightMinInclusive;
    }

    public BigDecimal getWeightMaxGrams() {
        return weightMaxGrams;
    }

    public Boolean getWeightMaxInclusive() {
        return weightMaxInclusive;
    }

    public BigDecimal getStandardFeeAmount() {
        return standardFeeAmount;
    }

    public BigDecimal getHighAspFeeAmount() {
        return highAspFeeAmount;
    }

    public BigDecimal getSalesPriceThresholdAmount() {
        return salesPriceThresholdAmount;
    }

    public String getThresholdCurrency() {
        return thresholdCurrency;
    }

    public BigDecimal getExtraWeightStepGrams() {
        return extraWeightStepGrams;
    }

    public BigDecimal getExtraFeeAmount() {
        return extraFeeAmount;
    }

    public String getCurrency() {
        return currency;
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
