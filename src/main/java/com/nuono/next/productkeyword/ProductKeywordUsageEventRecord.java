package com.nuono.next.productkeyword;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductKeywordUsageEventRecord {
    private Long id;
    private Long keywordId;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String partnerSku;
    private String keyword;
    private String keywordNorm;
    private String sourceType;
    private String sourceRefType;
    private Long sourceRefId;
    private String sourceRefKey;
    private String eventNaturalKey;
    private String eventStatus;
    private LocalDateTime occurredAt;
    private LocalDate factDateFrom;
    private LocalDate factDateTo;
    private String payloadJson;
    private String metricsJson;
    private Long createdBy;
    private Long updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKeywordId() {
        return keywordId;
    }

    public void setKeywordId(Long keywordId) {
        this.keywordId = keywordId;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRefType() {
        return sourceRefType;
    }

    public void setSourceRefType(String sourceRefType) {
        this.sourceRefType = sourceRefType;
    }

    public Long getSourceRefId() {
        return sourceRefId;
    }

    public void setSourceRefId(Long sourceRefId) {
        this.sourceRefId = sourceRefId;
    }

    public String getSourceRefKey() {
        return sourceRefKey;
    }

    public void setSourceRefKey(String sourceRefKey) {
        this.sourceRefKey = sourceRefKey;
    }

    public String getEventNaturalKey() {
        return eventNaturalKey;
    }

    public void setEventNaturalKey(String eventNaturalKey) {
        this.eventNaturalKey = eventNaturalKey;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDate getFactDateFrom() {
        return factDateFrom;
    }

    public void setFactDateFrom(LocalDate factDateFrom) {
        this.factDateFrom = factDateFrom;
    }

    public LocalDate getFactDateTo() {
        return factDateTo;
    }

    public void setFactDateTo(LocalDate factDateTo) {
        this.factDateTo = factDateTo;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
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
