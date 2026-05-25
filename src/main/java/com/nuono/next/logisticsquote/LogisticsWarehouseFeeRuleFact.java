package com.nuono.next.logisticsquote;

import java.math.BigDecimal;

public class LogisticsWarehouseFeeRuleFact {

    private final String naturalKey;
    private final String forwarderCode;
    private final String country;
    private final String warehouseNode;
    private final String serviceName;
    private final String serviceType;
    private final String processingScope;
    private final String feeType;
    private final String pricingModel;
    private final BigDecimal amount;
    private final BigDecimal rate;
    private final String currency;
    private final String billingUnit;
    private final String conditionText;
    private final String freeCondition;
    private final String status;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsWarehouseFeeRuleFact(
            String naturalKey,
            String forwarderCode,
            String country,
            String warehouseNode,
            String serviceName,
            String serviceType,
            String processingScope,
            String feeType,
            String pricingModel,
            BigDecimal amount,
            BigDecimal rate,
            String currency,
            String billingUnit,
            String conditionText,
            String freeCondition,
            String status,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.forwarderCode = forwarderCode;
        this.country = country;
        this.warehouseNode = warehouseNode;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.processingScope = processingScope;
        this.feeType = feeType;
        this.pricingModel = pricingModel;
        this.amount = amount;
        this.rate = rate;
        this.currency = currency;
        this.billingUnit = billingUnit;
        this.conditionText = conditionText;
        this.freeCondition = freeCondition;
        this.status = status;
        this.sourceLineage = sourceLineage;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getForwarderCode() {
        return forwarderCode;
    }

    public String getCountry() {
        return country;
    }

    public String getWarehouseNode() {
        return warehouseNode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getProcessingScope() {
        return processingScope;
    }

    public String getFeeType() {
        return feeType;
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

    public String getConditionText() {
        return conditionText;
    }

    public String getFreeCondition() {
        return freeCondition;
    }

    public String getStatus() {
        return status;
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
