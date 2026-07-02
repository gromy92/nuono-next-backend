package com.nuono.next.noonads;

import java.time.LocalDate;

public class NoonAdvertisingQueryFact extends NoonAdvertisingQueryRow {
    private Long id;
    private Long batchId;
    private String sourceSystem;
    private Long ownerUserId;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private LocalDate reportDateFrom;
    private LocalDate reportDateTo;
    private String queryHash;
    private String rawPayloadJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
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
    public String getQueryHash() { return queryHash; }
    public void setQueryHash(String queryHash) { this.queryHash = queryHash; }
    public String getRawPayloadJson() { return rawPayloadJson; }
    public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
}
