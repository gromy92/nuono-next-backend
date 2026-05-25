package com.nuono.next.logisticsquote;

import java.math.BigDecimal;

public class LogisticsPriceRuleFact {

    private final String naturalKey;
    private final String forwarderCode;
    private final String serviceLineKey;
    private final String cargoCategoryKey;
    private final BigDecimal unitPrice;
    private final String currency;
    private final String billingUnit;
    private final String pricingModel;
    private final BigDecimal minimumBillableUnit;
    private final String minimumBillableUnitType;
    private final BigDecimal minimumCharge;
    private final BigDecimal volumeDivisor;
    private final String seaWeightRatio;
    private final String roundingRule;
    private final String priceStatus;
    private final String effectiveFrom;
    private final String status;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsPriceRuleFact(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String cargoCategoryKey,
            BigDecimal unitPrice,
            String currency,
            String billingUnit,
            String pricingModel,
            BigDecimal minimumBillableUnit,
            String minimumBillableUnitType,
            BigDecimal minimumCharge,
            BigDecimal volumeDivisor,
            String seaWeightRatio,
            String roundingRule,
            String priceStatus,
            String effectiveFrom,
            String status,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.forwarderCode = forwarderCode;
        this.serviceLineKey = serviceLineKey;
        this.cargoCategoryKey = cargoCategoryKey;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.billingUnit = billingUnit;
        this.pricingModel = pricingModel;
        this.minimumBillableUnit = minimumBillableUnit;
        this.minimumBillableUnitType = minimumBillableUnitType;
        this.minimumCharge = minimumCharge;
        this.volumeDivisor = volumeDivisor;
        this.seaWeightRatio = seaWeightRatio;
        this.roundingRule = roundingRule;
        this.priceStatus = priceStatus;
        this.effectiveFrom = effectiveFrom;
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

    public String getCargoCategoryKey() {
        return cargoCategoryKey;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBillingUnit() {
        return billingUnit;
    }

    public String getPricingModel() {
        return pricingModel;
    }

    public BigDecimal getMinimumBillableUnit() {
        return minimumBillableUnit;
    }

    public String getMinimumBillableUnitType() {
        return minimumBillableUnitType;
    }

    public BigDecimal getMinimumCharge() {
        return minimumCharge;
    }

    public BigDecimal getVolumeDivisor() {
        return volumeDivisor;
    }

    public String getSeaWeightRatio() {
        return seaWeightRatio;
    }

    public String getRoundingRule() {
        return roundingRule;
    }

    public String getPriceStatus() {
        return priceStatus;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getStatus() {
        return status;
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }

    public boolean isComparable() {
        return LogisticsQuoteFactStatus.ACTIVE.value().equals(status)
                && "NORMAL".equalsIgnoreCase(priceStatus)
                && unitPrice != null;
    }
}
