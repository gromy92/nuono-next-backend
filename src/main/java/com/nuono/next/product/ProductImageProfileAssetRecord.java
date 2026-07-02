package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductImageProfileAssetRecord {
    private Long id;
    private Long profileId;
    private String imageUrl;
    private String contentType;
    private Long sizeBytes;
    private Integer widthPx;
    private Integer heightPx;
    private String sourceStoreCode;
    private String sourceSiteCode;
    private Long sourceSnapshotId;
    private String sourceField;
    private String sourceKind;
    private ProductImageRole imageRole;
    private Integer sortOrder;
    private ProductImageAssetStatus assetStatus;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getSourceStoreCode() {
        return sourceStoreCode;
    }

    public void setSourceStoreCode(String sourceStoreCode) {
        this.sourceStoreCode = sourceStoreCode;
    }

    public String getSourceSiteCode() {
        return sourceSiteCode;
    }

    public void setSourceSiteCode(String sourceSiteCode) {
        this.sourceSiteCode = sourceSiteCode;
    }

    public Long getSourceSnapshotId() {
        return sourceSnapshotId;
    }

    public void setSourceSnapshotId(Long sourceSnapshotId) {
        this.sourceSnapshotId = sourceSnapshotId;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = sourceKind;
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

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
