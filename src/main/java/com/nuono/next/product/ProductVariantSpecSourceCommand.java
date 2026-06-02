package com.nuono.next.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductVariantSpecSourceCommand {
    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private Long variantId;
    private String sourceType;
    private BigDecimal productLengthCm;
    private BigDecimal productWidthCm;
    private BigDecimal productHeightCm;
    private BigDecimal productWeightG;
    private BigDecimal cartonLengthCm;
    private BigDecimal cartonWidthCm;
    private BigDecimal cartonHeightCm;
    private BigDecimal cartonWeightKg;
    private Integer cartonQuantity;
    private String cartonSourceType;
    private String batteryMagneticType;
    private String liquidPowderType;
    private LocalDateTime sourceRecordedAt;
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

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
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

    public String getCartonSourceType() {
        return cartonSourceType;
    }

    public void setCartonSourceType(String cartonSourceType) {
        this.cartonSourceType = cartonSourceType;
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

    public LocalDateTime getSourceRecordedAt() {
        return sourceRecordedAt;
    }

    public void setSourceRecordedAt(LocalDateTime sourceRecordedAt) {
        this.sourceRecordedAt = sourceRecordedAt;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
