package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductImageSuiteRecord {
    private Long id;
    private Long profileId;
    private String suiteName;
    private Long skinId;
    private String skinName;
    private String generationTaskId;
    private String draftPackageJson;
    private String draftPromptText;
    private ProductImageSuiteStatus suiteStatus;
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
