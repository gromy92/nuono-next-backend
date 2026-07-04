package com.nuono.next.competitoranalysis;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CompetitorSearchResultInsertCommand {
    private Long id;
    private Long keywordRunId;
    private Integer resultPosition;
    private String noonProductCode;
    private String codeType;
    private String canonicalUrl;
    private String titleSnapshot;
    private String titleEnSnapshot;
    private String titleArSnapshot;
    private String brandSnapshot;
    private String imageUrlSnapshot;
    private BigDecimal priceAmount;
    private String currencyCode;
    private BigDecimal rating;
    private Integer reviewCount;
    private Boolean sponsored;
    private String tagsJson;
    private String rawResultJson;
    private LocalDateTime capturedAt;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKeywordRunId() { return keywordRunId; }
    public void setKeywordRunId(Long keywordRunId) { this.keywordRunId = keywordRunId; }
    public Integer getResultPosition() { return resultPosition; }
    public void setResultPosition(Integer resultPosition) { this.resultPosition = resultPosition; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
    public String getTitleSnapshot() { return titleSnapshot; }
    public void setTitleSnapshot(String titleSnapshot) { this.titleSnapshot = titleSnapshot; }
    public String getTitleEnSnapshot() { return titleEnSnapshot; }
    public void setTitleEnSnapshot(String titleEnSnapshot) { this.titleEnSnapshot = titleEnSnapshot; }
    public String getTitleArSnapshot() { return titleArSnapshot; }
    public void setTitleArSnapshot(String titleArSnapshot) { this.titleArSnapshot = titleArSnapshot; }
    public String getBrandSnapshot() { return brandSnapshot; }
    public void setBrandSnapshot(String brandSnapshot) { this.brandSnapshot = brandSnapshot; }
    public String getImageUrlSnapshot() { return imageUrlSnapshot; }
    public void setImageUrlSnapshot(String imageUrlSnapshot) { this.imageUrlSnapshot = imageUrlSnapshot; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    public Boolean getSponsored() { return sponsored; }
    public void setSponsored(Boolean sponsored) { this.sponsored = sponsored; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getRawResultJson() { return rawResultJson; }
    public void setRawResultJson(String rawResultJson) { this.rawResultJson = rawResultJson; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
