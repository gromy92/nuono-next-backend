package com.nuono.next.procurement.aliorder;

public class Ali1688HistoricalOrderProductLinkAuditRow {

    private Long id;
    private Long ownerUserId;
    private Long assignmentId;
    private Long oldLinkId;
    private Long newLinkId;
    private String actionType;
    private String oldSkuParent;
    private String oldPartnerSku;
    private String oldPskuCode;
    private String oldProductTitle;
    private String newSkuParent;
    private String newPartnerSku;
    private String newPskuCode;
    private String newProductTitle;
    private String remark;
    private Long createdBy;
    private String createdAt;

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

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Long getOldLinkId() {
        return oldLinkId;
    }

    public void setOldLinkId(Long oldLinkId) {
        this.oldLinkId = oldLinkId;
    }

    public Long getNewLinkId() {
        return newLinkId;
    }

    public void setNewLinkId(Long newLinkId) {
        this.newLinkId = newLinkId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getOldSkuParent() {
        return oldSkuParent;
    }

    public void setOldSkuParent(String oldSkuParent) {
        this.oldSkuParent = oldSkuParent;
    }

    public String getOldPartnerSku() {
        return oldPartnerSku;
    }

    public void setOldPartnerSku(String oldPartnerSku) {
        this.oldPartnerSku = oldPartnerSku;
    }

    public String getOldPskuCode() {
        return oldPskuCode;
    }

    public void setOldPskuCode(String oldPskuCode) {
        this.oldPskuCode = oldPskuCode;
    }

    public String getOldProductTitle() {
        return oldProductTitle;
    }

    public void setOldProductTitle(String oldProductTitle) {
        this.oldProductTitle = oldProductTitle;
    }

    public String getNewSkuParent() {
        return newSkuParent;
    }

    public void setNewSkuParent(String newSkuParent) {
        this.newSkuParent = newSkuParent;
    }

    public String getNewPartnerSku() {
        return newPartnerSku;
    }

    public void setNewPartnerSku(String newPartnerSku) {
        this.newPartnerSku = newPartnerSku;
    }

    public String getNewPskuCode() {
        return newPskuCode;
    }

    public void setNewPskuCode(String newPskuCode) {
        this.newPskuCode = newPskuCode;
    }

    public String getNewProductTitle() {
        return newProductTitle;
    }

    public void setNewProductTitle(String newProductTitle) {
        this.newProductTitle = newProductTitle;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
