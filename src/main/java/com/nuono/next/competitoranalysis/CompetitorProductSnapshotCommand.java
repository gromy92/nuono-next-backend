package com.nuono.next.competitoranalysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CompetitorProductSnapshotCommand {
    private Long id;
    private Long ownerUserId;
    private Long watchProductId;
    private Long competitorProductId;
    private String subjectType;
    private String siteCode;
    private String noonProductCode;
    private String codeType;
    private LocalDate factDate;
    private LocalDateTime capturedAt;
    private Long sourceRunId;
    private String detailUrl;
    private String titleEn;
    private String brand;
    private BigDecimal priceAmount;
    private String currencyCode;
    private BigDecimal rating;
    private Integer reviewCount;
    private String mainImageUrlRaw;
    private String mainImageUrlNormalized;
    private String mainImageAssetKey;
    private String snapshotHash;
    private String rawDetailJson;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long competitorProductId) { this.competitorProductId = competitorProductId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public LocalDate getFactDate() { return factDate; }
    public void setFactDate(LocalDate factDate) { this.factDate = factDate; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public Long getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(Long sourceRunId) { this.sourceRunId = sourceRunId; }
    public String getDetailUrl() { return detailUrl; }
    public void setDetailUrl(String detailUrl) { this.detailUrl = detailUrl; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    public String getMainImageUrlRaw() { return mainImageUrlRaw; }
    public void setMainImageUrlRaw(String mainImageUrlRaw) { this.mainImageUrlRaw = mainImageUrlRaw; }
    public String getMainImageUrlNormalized() { return mainImageUrlNormalized; }
    public void setMainImageUrlNormalized(String mainImageUrlNormalized) { this.mainImageUrlNormalized = mainImageUrlNormalized; }
    public String getMainImageAssetKey() { return mainImageAssetKey; }
    public void setMainImageAssetKey(String mainImageAssetKey) { this.mainImageAssetKey = mainImageAssetKey; }
    public String getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
    public String getRawDetailJson() { return rawDetailJson; }
    public void setRawDetailJson(String rawDetailJson) { this.rawDetailJson = rawDetailJson; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
