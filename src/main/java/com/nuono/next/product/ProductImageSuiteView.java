package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductImageSuiteView {
    private Long id;
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
    private String failureStage;
    private String failureReason;
    private String reviewedAt;
    private String publishedAt;
    private String adoptedAt;
    private String updatedAt;
    private List<ProductImageSuiteAssetView> assets = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(String reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

    public String getAdoptedAt() {
        return adoptedAt;
    }

    public void setAdoptedAt(String adoptedAt) {
        this.adoptedAt = adoptedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ProductImageSuiteAssetView> getAssets() {
        return assets;
    }

    public void setAssets(List<ProductImageSuiteAssetView> assets) {
        this.assets = assets == null ? new ArrayList<>() : assets;
    }
}
