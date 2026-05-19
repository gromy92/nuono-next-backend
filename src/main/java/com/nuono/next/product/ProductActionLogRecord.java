package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductActionLogRecord {

    private Long id;
    private Long productMasterId;
    private Long baselineSnapshotId;
    private Long targetSiteId;
    private Long targetVariantId;
    private String actionType;
    private String stage;
    private String resultStatus;
    private String errorCode;
    private String pskuCode;
    private String offerCode;
    private Integer retryCount;
    private String summaryJson;
    private LocalDateTime occurredAt;

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

    public Long getTargetVariantId() {
        return targetVariantId;
    }

    public void setTargetVariantId(Long targetVariantId) {
        this.targetVariantId = targetVariantId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getPskuCode() {
        return pskuCode;
    }

    public void setPskuCode(String pskuCode) {
        this.pskuCode = pskuCode;
    }

    public String getOfferCode() {
        return offerCode;
    }

    public void setOfferCode(String offerCode) {
        this.offerCode = offerCode;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
