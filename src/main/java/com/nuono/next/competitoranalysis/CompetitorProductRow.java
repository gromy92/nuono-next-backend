package com.nuono.next.competitoranalysis;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CompetitorProductRow {
    private Long id;
    private Long watchProductId;
    private String noonProductCode;
    private String codeType;
    private String canonicalUrl;
    private String titleSnapshot;
    private String titleEnSnapshot;
    private String titleArSnapshot;
    private String brandSnapshot;
    private String imageUrlSnapshot;
    private BigDecimal priceAmountSnapshot;
    private String currencyCodeSnapshot;
    private BigDecimal ratingSnapshot;
    private Integer reviewCountSnapshot;
    private String tagsSnapshotJson;
    private String sourceType;
    private String reviewStatus;
    private Boolean ownedByCurrentStore;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
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
    public BigDecimal getPriceAmountSnapshot() { return priceAmountSnapshot; }
    public void setPriceAmountSnapshot(BigDecimal priceAmountSnapshot) { this.priceAmountSnapshot = priceAmountSnapshot; }
    public String getCurrencyCodeSnapshot() { return currencyCodeSnapshot; }
    public void setCurrencyCodeSnapshot(String currencyCodeSnapshot) { this.currencyCodeSnapshot = currencyCodeSnapshot; }
    public BigDecimal getRatingSnapshot() { return ratingSnapshot; }
    public void setRatingSnapshot(BigDecimal ratingSnapshot) { this.ratingSnapshot = ratingSnapshot; }
    public Integer getReviewCountSnapshot() { return reviewCountSnapshot; }
    public void setReviewCountSnapshot(Integer reviewCountSnapshot) { this.reviewCountSnapshot = reviewCountSnapshot; }
    public String getTagsSnapshotJson() { return tagsSnapshotJson; }
    public void setTagsSnapshotJson(String tagsSnapshotJson) { this.tagsSnapshotJson = tagsSnapshotJson; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Boolean getOwnedByCurrentStore() { return ownedByCurrentStore; }
    public void setOwnedByCurrentStore(Boolean ownedByCurrentStore) { this.ownedByCurrentStore = ownedByCurrentStore; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
