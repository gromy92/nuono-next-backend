package com.nuono.next.outboundfee;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OfficialOutboundFeeProductSpecSourceView {
    private Long sourceId;
    private Long variantId;
    private String sourceType;
    private BigDecimal productLengthCm;
    private BigDecimal productWidthCm;
    private BigDecimal productHeightCm;
    private BigDecimal productWeightG;
    private LocalDateTime sourceRecordedAt;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
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

    public LocalDateTime getSourceRecordedAt() {
        return sourceRecordedAt;
    }

    public void setSourceRecordedAt(LocalDateTime sourceRecordedAt) {
        this.sourceRecordedAt = sourceRecordedAt;
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
