package com.nuono.next.noonads;

import java.time.LocalDate;

public class NoonAdvertisingReportBatch {
    private Long id;
    private String sourceSystem;
    private String sourceName;
    private String sourceDigestSha256;
    private Long ownerUserId;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private LocalDate reportDateFrom;
    private LocalDate reportDateTo;
    private String status;
    private int campaignRowCount;
    private int queryRowCount;
    private String notes;
    private Long createdBy;
    private Long updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceDigestSha256() { return sourceDigestSha256; }
    public void setSourceDigestSha256(String sourceDigestSha256) { this.sourceDigestSha256 = sourceDigestSha256; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public LocalDate getReportDateFrom() { return reportDateFrom; }
    public void setReportDateFrom(LocalDate reportDateFrom) { this.reportDateFrom = reportDateFrom; }
    public LocalDate getReportDateTo() { return reportDateTo; }
    public void setReportDateTo(LocalDate reportDateTo) { this.reportDateTo = reportDateTo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getCampaignRowCount() { return campaignRowCount; }
    public void setCampaignRowCount(int campaignRowCount) { this.campaignRowCount = campaignRowCount; }
    public int getQueryRowCount() { return queryRowCount; }
    public void setQueryRowCount(int queryRowCount) { this.queryRowCount = queryRowCount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
