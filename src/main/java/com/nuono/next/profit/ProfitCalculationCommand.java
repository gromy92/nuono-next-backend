package com.nuono.next.profit;

import java.math.BigDecimal;

public class ProfitCalculationCommand {

    private String title;

    private String site;

    private BigDecimal salePrice;

    private BigDecimal purchasePrice;

    private BigDecimal lengthCm;

    private BigDecimal widthCm;

    private BigDecimal heightCm;

    private BigDecimal weightGrams;

    private BigDecimal vatRate;

    private BigDecimal exchangeRate;

    private BigDecimal domesticShippingFee;

    private BigDecimal warehouseDeliveryUnitPrice;

    private BigDecimal airFreightUnitPrice;

    private BigDecimal oceanFreightUnitPrice;

    private BigDecimal airFreightDimFactor;

    private BigDecimal fbnCommissionRate;

    private BigDecimal fbpCommissionRate;

    private BigDecimal fbnOutboundFee;

    private Boolean manualFbnOutboundFeeOverride;

    private BigDecimal fbpDirectShipFee;

    private BigDecimal fulfillmentFee;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
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

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(BigDecimal vatRate) {
        this.vatRate = vatRate;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public BigDecimal getDomesticShippingFee() {
        return domesticShippingFee;
    }

    public void setDomesticShippingFee(BigDecimal domesticShippingFee) {
        this.domesticShippingFee = domesticShippingFee;
    }

    public BigDecimal getWarehouseDeliveryUnitPrice() {
        return warehouseDeliveryUnitPrice;
    }

    public void setWarehouseDeliveryUnitPrice(BigDecimal warehouseDeliveryUnitPrice) {
        this.warehouseDeliveryUnitPrice = warehouseDeliveryUnitPrice;
    }

    public BigDecimal getAirFreightUnitPrice() {
        return airFreightUnitPrice;
    }

    public void setAirFreightUnitPrice(BigDecimal airFreightUnitPrice) {
        this.airFreightUnitPrice = airFreightUnitPrice;
    }

    public BigDecimal getOceanFreightUnitPrice() {
        return oceanFreightUnitPrice;
    }

    public void setOceanFreightUnitPrice(BigDecimal oceanFreightUnitPrice) {
        this.oceanFreightUnitPrice = oceanFreightUnitPrice;
    }

    public BigDecimal getAirFreightDimFactor() {
        return airFreightDimFactor;
    }

    public void setAirFreightDimFactor(BigDecimal airFreightDimFactor) {
        this.airFreightDimFactor = airFreightDimFactor;
    }

    public BigDecimal getFbnCommissionRate() {
        return fbnCommissionRate;
    }

    public void setFbnCommissionRate(BigDecimal fbnCommissionRate) {
        this.fbnCommissionRate = fbnCommissionRate;
    }

    public BigDecimal getFbpCommissionRate() {
        return fbpCommissionRate;
    }

    public void setFbpCommissionRate(BigDecimal fbpCommissionRate) {
        this.fbpCommissionRate = fbpCommissionRate;
    }

    public BigDecimal getFbnOutboundFee() {
        return fbnOutboundFee;
    }

    public void setFbnOutboundFee(BigDecimal fbnOutboundFee) {
        this.fbnOutboundFee = fbnOutboundFee;
    }

    public Boolean getManualFbnOutboundFeeOverride() {
        return manualFbnOutboundFeeOverride;
    }

    public void setManualFbnOutboundFeeOverride(Boolean manualFbnOutboundFeeOverride) {
        this.manualFbnOutboundFeeOverride = manualFbnOutboundFeeOverride;
    }

    public BigDecimal getFbpDirectShipFee() {
        return fbpDirectShipFee;
    }

    public void setFbpDirectShipFee(BigDecimal fbpDirectShipFee) {
        this.fbpDirectShipFee = fbpDirectShipFee;
    }

    public BigDecimal getFulfillmentFee() {
        return fulfillmentFee;
    }

    public void setFulfillmentFee(BigDecimal fulfillmentFee) {
        this.fulfillmentFee = fulfillmentFee;
    }
}
