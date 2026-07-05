package com.nuono.next.productselection;

public class ProductSelectionGroupCompetitorRow {

    private Long competitorId;
    private Long groupId;
    private String competitorUrl;
    private String note;
    private String fetchStatus;
    private String fetchedPayloadJson;
    private String fetchedAt;
    private Long createdBy;
    private Long updatedBy;

    public Long getCompetitorId() {
        return competitorId;
    }

    public void setCompetitorId(Long competitorId) {
        this.competitorId = competitorId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getCompetitorUrl() {
        return competitorUrl;
    }

    public void setCompetitorUrl(String competitorUrl) {
        this.competitorUrl = competitorUrl;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getFetchStatus() {
        return fetchStatus;
    }

    public void setFetchStatus(String fetchStatus) {
        this.fetchStatus = fetchStatus;
    }

    public String getFetchedPayloadJson() {
        return fetchedPayloadJson;
    }

    public void setFetchedPayloadJson(String fetchedPayloadJson) {
        this.fetchedPayloadJson = fetchedPayloadJson;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
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
}
