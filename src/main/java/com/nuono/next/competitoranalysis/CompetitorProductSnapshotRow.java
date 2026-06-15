package com.nuono.next.competitoranalysis;

import java.math.BigDecimal;

public class CompetitorProductSnapshotRow {
    private Long id;
    private String titleEn;
    private String brand;
    private BigDecimal priceAmount;
    private String currencyCode;
    private BigDecimal rating;
    private Integer reviewCount;
    private String mainImageUrlNormalized;
    private String mainImageAssetKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getMainImageUrlNormalized() { return mainImageUrlNormalized; }
    public void setMainImageUrlNormalized(String mainImageUrlNormalized) { this.mainImageUrlNormalized = mainImageUrlNormalized; }
    public String getMainImageAssetKey() { return mainImageAssetKey; }
    public void setMainImageAssetKey(String mainImageAssetKey) { this.mainImageAssetKey = mainImageAssetKey; }
}
