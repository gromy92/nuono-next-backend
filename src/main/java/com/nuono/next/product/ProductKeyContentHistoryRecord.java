package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductKeyContentHistoryRecord {

    private Long id;
    private Long productMasterId;
    private Long baselineSnapshotId;
    private Long targetSiteId;
    private String sourceActionType;
    private String visibilityStatus;
    private String changeTypesJson;
    private String summaryJson;
    private LocalDateTime publishedAt;
    private LocalDateTime visibleAfter;
    private LocalDateTime visibleAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductMasterId() {
        return productMasterId;
    }

    public void setProductMasterId(Long productMasterId) {
        this.productMasterId = productMasterId;
    }

    public Long getBaselineSnapshotId() {
        return baselineSnapshotId;
    }

    public void setBaselineSnapshotId(Long baselineSnapshotId) {
        this.baselineSnapshotId = baselineSnapshotId;
    }

    public Long getTargetSiteId() {
        return targetSiteId;
    }

    public void setTargetSiteId(Long targetSiteId) {
        this.targetSiteId = targetSiteId;
    }

    public String getSourceActionType() {
        return sourceActionType;
    }

    public void setSourceActionType(String sourceActionType) {
        this.sourceActionType = sourceActionType;
    }

    public String getVisibilityStatus() {
        return visibilityStatus;
    }

    public void setVisibilityStatus(String visibilityStatus) {
        this.visibilityStatus = visibilityStatus;
    }

    public String getChangeTypesJson() {
        return changeTypesJson;
    }

    public void setChangeTypesJson(String changeTypesJson) {
        this.changeTypesJson = changeTypesJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getVisibleAfter() {
        return visibleAfter;
    }

    public void setVisibleAfter(LocalDateTime visibleAfter) {
        this.visibleAfter = visibleAfter;
    }

    public LocalDateTime getVisibleAt() {
        return visibleAt;
    }

    public void setVisibleAt(LocalDateTime visibleAt) {
        this.visibleAt = visibleAt;
    }
}
