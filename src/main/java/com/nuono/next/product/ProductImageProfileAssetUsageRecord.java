package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductImageProfileAssetUsageRecord {
    private Long id;
    private Long profileId;
    private Long assetId;
    private ProductImageRole imageRole;
    private Integer sortOrder;
    private String processingNote;
    private ProductImageProcessingStatus processingStatus;
    private Long processedBy;
    private LocalDateTime processedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public ProductImageRole getImageRole() { return imageRole; }
    public void setImageRole(ProductImageRole imageRole) { this.imageRole = imageRole; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getProcessingNote() { return processingNote; }
    public void setProcessingNote(String processingNote) { this.processingNote = processingNote; }
    public ProductImageProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProductImageProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
    public Long getProcessedBy() { return processedBy; }
    public void setProcessedBy(Long processedBy) { this.processedBy = processedBy; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
