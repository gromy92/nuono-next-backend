package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDateTime;

/** MyBatis projection for one active watch-product keyword. */
public class Dp08KeywordScopeRow {
    private Long ownerUserId;
    private Long logicalStoreId;
    private Long watchProductId;
    private Long keywordId;
    private String storeCode;
    private String siteCode;
    private String keyword;
    private String locale;
    private String trackedProductType;
    private Long competitorProductId;
    private String trackedNoonProductCode;
    private LocalDateTime sourceUpdatedAtUtc;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long logicalStoreId) { this.logicalStoreId = logicalStoreId; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getTrackedProductType() { return trackedProductType; }
    public void setTrackedProductType(String value) { trackedProductType = value; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long value) { competitorProductId = value; }
    public String getTrackedNoonProductCode() { return trackedNoonProductCode; }
    public void setTrackedNoonProductCode(String value) { trackedNoonProductCode = value; }
    public LocalDateTime getSourceUpdatedAtUtc() { return sourceUpdatedAtUtc; }
    public void setSourceUpdatedAtUtc(LocalDateTime value) { sourceUpdatedAtUtc = value; }
}
