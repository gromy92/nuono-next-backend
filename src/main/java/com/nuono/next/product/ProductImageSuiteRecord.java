package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductImageSuiteRecord {
    private Long id;
    private Long profileId;
    private Long parentSuiteId;
    private Integer revisionNo;
    private String suiteName;
    private Long skinId;
    private String skinName;
    private String generationTaskId;
    private String draftPackageJson;
    private String draftPromptText;
    private ProductImageSuiteStatus suiteStatus;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String failureStage;
    private String failureReason;
    private LocalDateTime publishedAt;
    private String publishManifestJson;
    private LocalDateTime adoptedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;

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

    public Long getParentSuiteId() { return parentSuiteId; }
    public void setParentSuiteId(Long parentSuiteId) { this.parentSuiteId = parentSuiteId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public Long getSkinId() {
        return skinId;
    }

    public void setSkinId(Long skinId) {
        this.skinId = skinId;
    }

    public String getSkinName() {
        return skinName;
    }

    public void setSkinName(String skinName) {
        this.skinName = skinName;
    }

    public String getGenerationTaskId() {
        return generationTaskId;
    }

    public void setGenerationTaskId(String generationTaskId) {
        this.generationTaskId = generationTaskId;
    }

    public String getDraftPackageJson() {
        return draftPackageJson;
    }

    public void setDraftPackageJson(String draftPackageJson) {
        this.draftPackageJson = draftPackageJson;
    }

    public String getDraftPromptText() {
        return draftPromptText;
    }

    public void setDraftPromptText(String draftPromptText) {
        this.draftPromptText = draftPromptText;
    }

    public ProductImageSuiteStatus getSuiteStatus() {
        return suiteStatus;
    }

    public void setSuiteStatus(ProductImageSuiteStatus suiteStatus) {
        this.suiteStatus = suiteStatus;
    }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishManifestJson() { return publishManifestJson; }
    public void setPublishManifestJson(String publishManifestJson) { this.publishManifestJson = publishManifestJson; }

    public LocalDateTime getAdoptedAt() {
        return adoptedAt;
    }

    public void setAdoptedAt(LocalDateTime adoptedAt) {
        this.adoptedAt = adoptedAt;
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

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
