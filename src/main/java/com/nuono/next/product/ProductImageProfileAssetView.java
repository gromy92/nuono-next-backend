package com.nuono.next.product;

import java.math.BigDecimal;

public class ProductImageProfileAssetView {
    private Long id;
    private Long usageId;
    private String imageUrl;
    private String contentType;
    private Long sizeBytes;
    private Integer widthPx;
    private Integer heightPx;
    private BigDecimal horizontalPpi;
    private BigDecimal verticalPpi;
    private String colorSpace;
    private ProductImageRole imageRole;
    private Integer sortOrder;
    private ProductImageAssetStatus assetStatus;
    private Boolean removable;
    private String processingNote;
    private ProductImageProcessingStatus processingStatus;
    private String processedAt;
    private NoonImageTechnicalComplianceView noonTechnicalCompliance;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsageId() { return usageId; }
    public void setUsageId(Long usageId) { this.usageId = usageId; }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

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

    public ProductImageRole getImageRole() {
        return imageRole;
    }

    public void setImageRole(ProductImageRole imageRole) {
        this.imageRole = imageRole;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public ProductImageAssetStatus getAssetStatus() {
        return assetStatus;
    }

    public void setAssetStatus(ProductImageAssetStatus assetStatus) {
        this.assetStatus = assetStatus;
    }

    public Boolean getRemovable() {
        return removable;
    }

    public void setRemovable(Boolean removable) {
        this.removable = removable;
    }

    public String getProcessingNote() { return processingNote; }
    public void setProcessingNote(String processingNote) { this.processingNote = processingNote; }
    public ProductImageProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProductImageProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
    public String getProcessedAt() { return processedAt; }
    public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }
    public NoonImageTechnicalComplianceView getNoonTechnicalCompliance() { return noonTechnicalCompliance; }
    public void setNoonTechnicalCompliance(NoonImageTechnicalComplianceView noonTechnicalCompliance) { this.noonTechnicalCompliance = noonTechnicalCompliance; }
}
