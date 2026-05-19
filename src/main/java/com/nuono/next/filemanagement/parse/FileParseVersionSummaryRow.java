package com.nuono.next.filemanagement.parse;

import java.time.LocalDateTime;

public class FileParseVersionSummaryRow {

    private Long id;
    private String versionNo;
    private Long targetPlanId;
    private Long sourceTaskId;
    private Long sourceResultId;
    private Long standardVersionId;
    private Long baseVersionId;
    private String dataScopeType;
    private String dataScopeKey;
    private String versionStatus;
    private LocalDateTime publishedAt;
    private Long publishedBy;
    private String summaryJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
    }

    public Long getSourceTaskId() {
        return sourceTaskId;
    }

    public void setSourceTaskId(Long sourceTaskId) {
        this.sourceTaskId = sourceTaskId;
    }

    public Long getSourceResultId() {
        return sourceResultId;
    }

    public void setSourceResultId(Long sourceResultId) {
        this.sourceResultId = sourceResultId;
    }

    public Long getStandardVersionId() {
        return standardVersionId;
    }

    public void setStandardVersionId(Long standardVersionId) {
        this.standardVersionId = standardVersionId;
    }

    public Long getBaseVersionId() {
        return baseVersionId;
    }

    public void setBaseVersionId(Long baseVersionId) {
        this.baseVersionId = baseVersionId;
    }

    public String getDataScopeType() {
        return dataScopeType;
    }

    public void setDataScopeType(String dataScopeType) {
        this.dataScopeType = dataScopeType;
    }

    public String getDataScopeKey() {
        return dataScopeKey;
    }

    public void setDataScopeKey(String dataScopeKey) {
        this.dataScopeKey = dataScopeKey;
    }

    public String getVersionStatus() {
        return versionStatus;
    }

    public void setVersionStatus(String versionStatus) {
        this.versionStatus = versionStatus;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(Long publishedBy) {
        this.publishedBy = publishedBy;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }
}
