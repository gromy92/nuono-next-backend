package com.nuono.next.logisticsquote;

import java.math.BigDecimal;

public class LogisticsSurchargeRuleFact {

    private final String naturalKey;
    private final String forwarderCode;
    private final String serviceLineKey;
    private final String surchargeName;
    private final String surchargeType;
    private final String triggerCondition;
    private final String pricingModel;
    private final BigDecimal amount;
    private final BigDecimal rate;
    private final String currency;
    private final String billingUnit;
    private final BigDecimal minimumCharge;
    private final boolean includedInBasePrice;
    private final String status;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsSurchargeRuleFact(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String surchargeName,
            String surchargeType,
            String triggerCondition,
            String pricingModel,
            BigDecimal amount,
            BigDecimal rate,
            String currency,
            String billingUnit,
            BigDecimal minimumCharge,
            boolean includedInBasePrice,
            String status,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.forwarderCode = forwarderCode;
        this.serviceLineKey = serviceLineKey;
        this.surchargeName = surchargeName;
        this.surchargeType = surchargeType;
        this.triggerCondition = triggerCondition;
        this.pricingModel = pricingModel;
        this.amount = amount;
        this.rate = rate;
        this.currency = currency;
        this.billingUnit = billingUnit;
        this.minimumCharge = minimumCharge;
        this.includedInBasePrice = includedInBasePrice;
        this.status = status;
        this.sourceLineage = sourceLineage;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getForwarderCode() {
        return forwarderCode;
    }

    public String getServiceLineKey() {
        return serviceLineKey;
    }

    public String getSurchargeName() {
        return surchargeName;
    }

    public String getSurchargeType() {
        return surchargeType;
    }

    public String getTriggerCondition() {
        return triggerCondition;
    }

    public String getPricingModel() {
        return pricingModel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBillingUnit() {
        return billingUnit;
    }

    public BigDecimal getMinimumCharge() {
        return minimumCharge;
    }

    public boolean isIncludedInBasePrice() {
        return includedInBasePrice;
    }

    public String getStatus() {
        return status;
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
