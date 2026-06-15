package com.nuono.next.product;

import java.math.BigDecimal;

public class ProductVariantSpecCommand {
    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String skuParent;
    private Long variantId;
    private String partnerSku;
    private String childSku;
    private BigDecimal productLengthCm;
    private BigDecimal productWidthCm;
    private BigDecimal productHeightCm;
    private BigDecimal productWeightG;
    private BigDecimal cartonLengthCm;
    private BigDecimal cartonWidthCm;
    private BigDecimal cartonHeightCm;
    private BigDecimal cartonWeightKg;
    private Integer cartonQuantity;
    private String batteryMagneticType;
    private String liquidPowderType;
    private Long operatorUserId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getChildSku() {
        return childSku;
    }

    public void setChildSku(String childSku) {
        this.childSku = childSku;
    }

    public BigDecimal getProductLengthCm() {
        return productLengthCm;
    }

    public void setProductLengthCm(BigDecimal productLengthCm) {
        this.productLengthCm = productLengthCm;
    }

    public BigDecimal getProductWidthCm() {
        return productWidthCm;
    }

    public void setProductWidthCm(BigDecimal productWidthCm) {
        this.productWidthCm = productWidthCm;
    }

    public BigDecimal getProductHeightCm() {
        return productHeightCm;
    }

    public void setProductHeightCm(BigDecimal productHeightCm) {
        this.productHeightCm = productHeightCm;
    }

    public BigDecimal getProductWeightG() {
        return productWeightG;
    }

    public void setProductWeightG(BigDecimal productWeightG) {
        this.productWeightG = productWeightG;
    }

    public BigDecimal getCartonLengthCm() {
        return cartonLengthCm;
    }

    public void setCartonLengthCm(BigDecimal cartonLengthCm) {
        this.cartonLengthCm = cartonLengthCm;
    }

    public BigDecimal getCartonWidthCm() {
        return cartonWidthCm;
    }

    public void setCartonWidthCm(BigDecimal cartonWidthCm) {
        this.cartonWidthCm = cartonWidthCm;
    }

    public BigDecimal getCartonHeightCm() {
        return cartonHeightCm;
    }

    public void setCartonHeightCm(BigDecimal cartonHeightCm) {
        this.cartonHeightCm = cartonHeightCm;
    }

    public BigDecimal getCartonWeightKg() {
        return cartonWeightKg;
    }

    public void setCartonWeightKg(BigDecimal cartonWeightKg) {
        this.cartonWeightKg = cartonWeightKg;
    }

    public Integer getCartonQuantity() {
        return cartonQuantity;
    }

    public void setCartonQuantity(Integer cartonQuantity) {
        this.cartonQuantity = cartonQuantity;
    }

    public String getBatteryMagneticType() {
        return batteryMagneticType;
    }

    public void setBatteryMagneticType(String batteryMagneticType) {
        this.batteryMagneticType = batteryMagneticType;
    }

    public String getLiquidPowderType() {
        return liquidPowderType;
    }

    public void setLiquidPowderType(String liquidPowderType) {
        this.liquidPowderType = liquidPowderType;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
