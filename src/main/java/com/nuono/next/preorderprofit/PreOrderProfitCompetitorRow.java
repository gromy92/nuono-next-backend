package com.nuono.next.preorderprofit;

import java.time.LocalDateTime;

public class PreOrderProfitCompetitorRow extends PreOrderProfitCompetitorCommand {
    private Long id;
    private Long candidateId;
    private Long ownerUserId;
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

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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
