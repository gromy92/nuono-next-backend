package com.nuono.next.competitoranalysis;

public class CompetitorKeywordScopeRow {
    private Long keywordId;
    private Long watchProductId;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String status;

    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
