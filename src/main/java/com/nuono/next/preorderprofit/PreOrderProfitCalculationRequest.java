package com.nuono.next.preorderprofit;

import java.math.BigDecimal;

public class PreOrderProfitCalculationRequest {
    private String storeCode;
    private String siteCode;
    private String purchaseUrl;
    private BigDecimal purchasePriceRmb;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
    private BigDecimal actualWeightKg;
    private String categoryId;
    private String logisticsCarrierId;
    private BigDecimal salePrice;
    private BigDecimal targetMarginRate;

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getPurchaseUrl() {
        return purchaseUrl;
    }

    public void setPurchaseUrl(String purchaseUrl) {
        this.purchaseUrl = purchaseUrl;
    }

    public BigDecimal getPurchasePriceRmb() {
        return purchasePriceRmb;
    }

    public void setPurchasePriceRmb(BigDecimal purchasePriceRmb) {
        this.purchasePriceRmb = purchasePriceRmb;
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

    public BigDecimal getActualWeightKg() {
        return actualWeightKg;
    }

    public void setActualWeightKg(BigDecimal actualWeightKg) {
        this.actualWeightKg = actualWeightKg;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getLogisticsCarrierId() {
        return logisticsCarrierId;
    }

    public void setLogisticsCarrierId(String logisticsCarrierId) {
        this.logisticsCarrierId = logisticsCarrierId;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public BigDecimal getTargetMarginRate() {
        return targetMarginRate;
    }

    public void setTargetMarginRate(BigDecimal targetMarginRate) {
        this.targetMarginRate = targetMarginRate;
    }
}
