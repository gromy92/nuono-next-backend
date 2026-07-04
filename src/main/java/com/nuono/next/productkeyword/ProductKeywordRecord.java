package com.nuono.next.productkeyword;

import java.time.LocalDateTime;

public class ProductKeywordRecord {
    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String partnerSku;
    private String keyword;
    private String keywordNorm;
    private String locale;
    private String status;
    private String intentTagsJson;
    private String sourceSummaryJson;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Long createdBy;
    private Long updatedBy;

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

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getKeywordNorm() {
        return keywordNorm;
    }

    public void setKeywordNorm(String keywordNorm) {
        this.keywordNorm = keywordNorm;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIntentTagsJson() {
        return intentTagsJson;
    }

    public void setIntentTagsJson(String intentTagsJson) {
        this.intentTagsJson = intentTagsJson;
    }

    public String getSourceSummaryJson() {
        return sourceSummaryJson;
    }

    public void setSourceSummaryJson(String sourceSummaryJson) {
        this.sourceSummaryJson = sourceSummaryJson;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(LocalDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
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
