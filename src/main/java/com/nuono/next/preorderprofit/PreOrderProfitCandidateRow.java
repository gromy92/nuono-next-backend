package com.nuono.next.preorderprofit;

import java.time.LocalDateTime;

public class PreOrderProfitCandidateRow extends PreOrderProfitCandidateCommand {
    private Long id;
    private Long ownerUserId;
    private String latestCalculationStatus;
    private String latestCalculationJson;
    private Long competitorCount;
    private Long purchaseOrderCount;
    private Long createdBy;
    private Long updatedBy;
    private boolean deleted;
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

    public String getLatestCalculationStatus() {
        return latestCalculationStatus;
    }

    public void setLatestCalculationStatus(String latestCalculationStatus) {
        this.latestCalculationStatus = latestCalculationStatus;
    }

    public String getLatestCalculationJson() {
        return latestCalculationJson;
    }

    public void setLatestCalculationJson(String latestCalculationJson) {
        this.latestCalculationJson = latestCalculationJson;
    }

    public Long getCompetitorCount() {
        return competitorCount;
    }

    public void setCompetitorCount(Long competitorCount) {
        this.competitorCount = competitorCount;
    }

    public Long getPurchaseOrderCount() {
        return purchaseOrderCount;
    }

    public void setPurchaseOrderCount(Long purchaseOrderCount) {
        this.purchaseOrderCount = purchaseOrderCount;
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
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
