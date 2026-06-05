package com.nuono.next.productlisting;

import java.time.LocalDateTime;

public class ProductListingDraftRecord {

    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String draftNo;
    private String sourceType;
    private Long sourceRefId;
    private Long optionalPurchaseOrderId;
    private String status;
    private String draftJson;
    private String validationJson;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtUpdated;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getDraftNo() {
        return draftNo;
    }

    public void setDraftNo(String draftNo) {
        this.draftNo = draftNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceRefId() {
        return sourceRefId;
    }

    public void setSourceRefId(Long sourceRefId) {
        this.sourceRefId = sourceRefId;
    }

    public Long getOptionalPurchaseOrderId() {
        return optionalPurchaseOrderId;
    }

    public void setOptionalPurchaseOrderId(Long optionalPurchaseOrderId) {
        this.optionalPurchaseOrderId = optionalPurchaseOrderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDraftJson() {
        return draftJson;
    }

    public void setDraftJson(String draftJson) {
        this.draftJson = draftJson;
    }

    public String getValidationJson() {
        return validationJson;
    }

    public void setValidationJson(String validationJson) {
        this.validationJson = validationJson;
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

    public LocalDateTime getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(LocalDateTime gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    public LocalDateTime getGmtUpdated() {
        return gmtUpdated;
    }

    public void setGmtUpdated(LocalDateTime gmtUpdated) {
        this.gmtUpdated = gmtUpdated;
    }
}
