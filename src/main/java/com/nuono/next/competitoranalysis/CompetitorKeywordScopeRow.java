package com.nuono.next.competitoranalysis;

public class CompetitorKeywordScopeRow {
    private Long keywordId;
    private Long productKeywordId;
    private Long watchProductId;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String partnerSku;
    private String status;

    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public Long getProductKeywordId() { return productKeywordId; }
    public void setProductKeywordId(Long productKeywordId) { this.productKeywordId = productKeywordId; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
