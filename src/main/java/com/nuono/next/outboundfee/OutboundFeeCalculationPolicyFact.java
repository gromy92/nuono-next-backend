package com.nuono.next.outboundfee;

import java.math.BigDecimal;

public class OutboundFeeCalculationPolicyFact {

    private final String naturalKey;
    private final String country;
    private final String platform;
    private final String fulfillmentType;
    private final String policyName;
    private final String shippingWeightFormula;
    private final String dimensionSortRule;
    private final String weightBoundaryRule;
    private final String roundingRule;
    private final BigDecimal salesPriceThresholdAmount;
    private final String thresholdCurrency;
    private final String dimensionUnit;
    private final String weightUnit;
    private final String effectiveFrom;
    private String status;
    private final OfficialOutboundFeeSourceLineage sourceLineage;

    public OutboundFeeCalculationPolicyFact(
            String naturalKey,
            String country,
            String platform,
            String fulfillmentType,
            String policyName,
            String shippingWeightFormula,
            String dimensionSortRule,
            String weightBoundaryRule,
            String roundingRule,
            BigDecimal salesPriceThresholdAmount,
            String thresholdCurrency,
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
        this.policyName = policyName;
        this.shippingWeightFormula = shippingWeightFormula;
        this.dimensionSortRule = dimensionSortRule;
        this.weightBoundaryRule = weightBoundaryRule;
        this.roundingRule = roundingRule;
        this.salesPriceThresholdAmount = salesPriceThresholdAmount;
        this.thresholdCurrency = thresholdCurrency;
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

    public String getPolicyName() {
        return policyName;
    }

    public String getShippingWeightFormula() {
        return shippingWeightFormula;
    }

    public String getDimensionSortRule() {
        return dimensionSortRule;
    }

    public String getWeightBoundaryRule() {
        return weightBoundaryRule;
    }

    public String getRoundingRule() {
        return roundingRule;
    }

    public BigDecimal getSalesPriceThresholdAmount() {
        return salesPriceThresholdAmount;
    }

    public String getThresholdCurrency() {
        return thresholdCurrency;
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
