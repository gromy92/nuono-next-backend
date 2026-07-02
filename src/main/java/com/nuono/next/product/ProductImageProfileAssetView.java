package com.nuono.next.product;

public class ProductImageProfileAssetView {
    private Long id;
    private String imageUrl;
    private String contentType;
    private Long sizeBytes;
    private Integer widthPx;
    private Integer heightPx;
    private ProductImageRole imageRole;
    private Integer sortOrder;
    private ProductImageAssetStatus assetStatus;
    private Boolean removable;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
