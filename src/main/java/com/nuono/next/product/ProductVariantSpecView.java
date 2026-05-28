package com.nuono.next.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProductVariantSpecView {
    private Long variantId;
    private String partnerSku;
    private String childSku;
    private String sizeEn;
    private String sizeAr;
    private BigDecimal productLengthCm;
    private BigDecimal productWidthCm;
    private BigDecimal productHeightCm;
    private BigDecimal productWeightG;
    private BigDecimal cartonLengthCm;
    private BigDecimal cartonWidthCm;
    private BigDecimal cartonHeightCm;
    private BigDecimal cartonWeightKg;
    private Integer cartonQuantity;
    private String batteryMagneticType = ProductVariantSpecLogisticsType.UNKNOWN;
    private String liquidPowderType = ProductVariantSpecLogisticsType.UNKNOWN;
    private String completenessStatus = "not_found";
    private List<String> missingFields = new ArrayList<>();
    private LocalDateTime confirmedAt;
    private Long confirmedBy;

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

    public String getSizeEn() {
        return sizeEn;
    }

    public void setSizeEn(String sizeEn) {
        this.sizeEn = sizeEn;
    }

    public String getSizeAr() {
        return sizeAr;
    }

    public void setSizeAr(String sizeAr) {
        this.sizeAr = sizeAr;
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

    public String getCompletenessStatus() {
        return completenessStatus;
    }

    public void setCompletenessStatus(String completenessStatus) {
        this.completenessStatus = completenessStatus;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Long getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(Long confirmedBy) {
        this.confirmedBy = confirmedBy;
    }
}
