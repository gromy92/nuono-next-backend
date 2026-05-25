package com.nuono.next.outboundfee;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OfficialOutboundFeeCalculationRequest {

    private final String country;
    private final String platform;
    private final String fulfillmentType;
    private final String currency;
    private final BigDecimal salePriceAmount;
    private final BigDecimal productWeightGrams;
    private final BigDecimal lengthCm;
    private final BigDecimal widthCm;
    private final BigDecimal heightCm;
    private final LocalDate calculationDate;

    public OfficialOutboundFeeCalculationRequest(
            String country,
            String platform,
            String fulfillmentType,
            String currency,
            BigDecimal salePriceAmount,
            BigDecimal productWeightGrams,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            LocalDate calculationDate
    ) {
        this.country = country;
        this.platform = platform;
        this.fulfillmentType = fulfillmentType;
        this.currency = currency;
        this.salePriceAmount = salePriceAmount;
        this.productWeightGrams = productWeightGrams;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.calculationDate = calculationDate;
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

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSalePriceAmount() {
        return salePriceAmount;
    }

    public BigDecimal getProductWeightGrams() {
        return productWeightGrams;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public LocalDate getCalculationDate() {
        return calculationDate;
    }
}
