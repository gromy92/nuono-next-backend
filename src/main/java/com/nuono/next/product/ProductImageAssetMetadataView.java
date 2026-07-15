package com.nuono.next.product;

import java.math.BigDecimal;

public class ProductImageAssetMetadataView {
    private String contentType;
    private Long sizeBytes;
    private Integer widthPx;
    private Integer heightPx;
    private BigDecimal horizontalPpi;
    private BigDecimal verticalPpi;
    private String colorSpace;

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getWidthPx() {
        return widthPx;
    }

    public void setWidthPx(Integer widthPx) {
        this.widthPx = widthPx;
    }

    public Integer getHeightPx() {
        return heightPx;
    }

    public void setHeightPx(Integer heightPx) {
        this.heightPx = heightPx;
    }

    public BigDecimal getHorizontalPpi() { return horizontalPpi; }
    public void setHorizontalPpi(BigDecimal horizontalPpi) { this.horizontalPpi = horizontalPpi; }
    public BigDecimal getVerticalPpi() { return verticalPpi; }
    public void setVerticalPpi(BigDecimal verticalPpi) { this.verticalPpi = verticalPpi; }
    public String getColorSpace() { return colorSpace; }
    public void setColorSpace(String colorSpace) { this.colorSpace = colorSpace; }
}
