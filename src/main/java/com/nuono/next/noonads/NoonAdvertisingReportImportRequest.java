package com.nuono.next.noonads;

import java.util.List;

public class NoonAdvertisingReportImportRequest {
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private String dateFrom;
    private String dateTo;
    private String sourceName;
    private String sourceDigestSha256;
    private String notes;
    private List<NoonAdvertisingCampaignFact> campaignRows = List.of();
    private List<NoonAdvertisingQueryFact> queryRows = List.of();

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }
    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceDigestSha256() { return sourceDigestSha256; }
    public void setSourceDigestSha256(String sourceDigestSha256) { this.sourceDigestSha256 = sourceDigestSha256; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<NoonAdvertisingCampaignFact> getCampaignRows() { return campaignRows == null ? List.of() : campaignRows; }
    public void setCampaignRows(List<NoonAdvertisingCampaignFact> campaignRows) { this.campaignRows = campaignRows == null ? List.of() : campaignRows; }
    public List<NoonAdvertisingQueryFact> getQueryRows() { return queryRows == null ? List.of() : queryRows; }
    public void setQueryRows(List<NoonAdvertisingQueryFact> queryRows) { this.queryRows = queryRows == null ? List.of() : queryRows; }
}
