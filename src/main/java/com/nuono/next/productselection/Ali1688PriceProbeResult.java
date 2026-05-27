package com.nuono.next.productselection;

import java.math.BigDecimal;

public class Ali1688PriceProbeResult {

    public String status;
    public String source;
    public String skuText;
    public Integer quantity;
    public BigDecimal unitPrice;
    public BigDecimal freightPrice;
    public BigDecimal discountPrice;
    public BigDecimal totalPrice;
    public String currency;
    public BigDecimal rmbTotalPrice;
    public BigDecimal exchangeRateToRmb;
    public String regionText;
    public String addressContextJson;
    public String failureCode;
    public String failureMessage;
    public String rawSnapshotJson;
    public String safetyMode;
    public String sideEffectPolicy;

    public static Ali1688PriceProbeResult confirmed(
            String source,
            String skuText,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal freightPrice,
            BigDecimal discountPrice,
            BigDecimal totalPrice,
            String currency,
            BigDecimal rmbTotalPrice,
            BigDecimal exchangeRateToRmb,
            String regionText
    ) {
        Ali1688PriceProbeResult result = new Ali1688PriceProbeResult();
        result.status = "confirmed";
        result.source = source;
        result.skuText = skuText;
        result.quantity = quantity;
        result.unitPrice = unitPrice;
        result.freightPrice = freightPrice;
        result.discountPrice = discountPrice;
        result.totalPrice = totalPrice;
        result.currency = currency;
        result.rmbTotalPrice = rmbTotalPrice;
        result.exchangeRateToRmb = exchangeRateToRmb;
        result.regionText = regionText;
        result.safetyMode = "preview_only";
        result.sideEffectPolicy = "no_payment_no_order_no_message";
        return result;
    }

    public static Ali1688PriceProbeResult failed(String failureCode, String failureMessage) {
        Ali1688PriceProbeResult result = new Ali1688PriceProbeResult();
        result.status = "failed";
        result.source = "order_preview";
        result.failureCode = failureCode;
        result.failureMessage = failureMessage;
        result.safetyMode = "preview_only";
        result.sideEffectPolicy = "no_payment_no_order_no_message";
        return result;
    }
}
