package com.nuono.next.competitoranalysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CompetitorListingObservationCommand {
    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String noonProductCode;
    private String codeType;
    private LocalDate factDate;
    private String status;
    private String acquisitionMode;
    private String leaseToken;
    private String canonicalUrl;
    private String titleEn;
    private String titleAr;
    private String imageUrl;
    private BigDecimal priceAmount;
    private String currencyCode;
    private String tagsJson;
    private String sourceUrl;
    private String parserVersion;
    private Integer providerHttpStatus;
    private String responseHash;
    private LocalDateTime capturedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public LocalDate getFactDate() { return factDate; }
    public void setFactDate(LocalDate factDate) { this.factDate = factDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAcquisitionMode() { return acquisitionMode; }
    public void setAcquisitionMode(String acquisitionMode) { this.acquisitionMode = acquisitionMode; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getTitleAr() { return titleAr; }
    public void setTitleAr(String titleAr) { this.titleAr = titleAr; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public String getResponseHash() { return responseHash; }
    public void setResponseHash(String responseHash) { this.responseHash = responseHash; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
