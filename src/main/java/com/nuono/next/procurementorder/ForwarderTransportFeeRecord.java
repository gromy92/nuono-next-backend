package com.nuono.next.procurementorder;

import java.math.BigDecimal;

public class ForwarderTransportFeeRecord {
    public Long id;
    public String serviceCode;
    public String feeName;
    public String feeType;
    public String targetPlatform;
    public String deliveryCity;
    public String triggerCondition;
    public String pricingModel;
    public String currency;
    public BigDecimal amount;
    public BigDecimal rate;
    public String billingUnit;
    public String billingBasis;
    public BigDecimal minCharge;
    public BigDecimal minBillableUnit;
    public String roundingRule;
    public Boolean includedInBasePrice;
}
