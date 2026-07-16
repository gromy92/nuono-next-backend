package com.nuono.next.product;

import java.math.BigDecimal;

public class ProductImageAssetCreateCommand {
    private Long ownerUserId;
    private String storeCode;
    private Long profileId;
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
    private Long operatorUserId;

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

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

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

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
