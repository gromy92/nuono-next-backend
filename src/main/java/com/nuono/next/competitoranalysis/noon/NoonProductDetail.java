package com.nuono.next.competitoranalysis.noon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class NoonProductDetail {
    private String noonProductCode;
    private String codeType;
    private String detailUrl;
    private String titleEn;
    private String titleAr;
    private String brand;
    private String sellerName;
    private BigDecimal priceAmount;
    private String currencyCode;
    private BigDecimal rating;
    private Integer reviewCount;
    private String mainImageUrlRaw;
    private String mainImageUrlNormalized;
    private String mainImageAssetKey;
    private Boolean supermallEnabled;
    private String soldRecentlyText;
    private String logisticsTagsJson;
    private String badgesJson;
    private String availabilityStatus;
    private String snapshotHash;
    private String rawDetailJson;
    private Integer providerHttpStatus;
    private LocalDateTime capturedAt;

    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getDetailUrl() { return detailUrl; }
    public void setDetailUrl(String detailUrl) { this.detailUrl = detailUrl; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getTitleAr() { return titleAr; }
    public void setTitleAr(String titleAr) { this.titleAr = titleAr; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
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
    public Boolean getSupermallEnabled() { return supermallEnabled; }
    public void setSupermallEnabled(Boolean supermallEnabled) { this.supermallEnabled = supermallEnabled; }
    public String getSoldRecentlyText() { return soldRecentlyText; }
    public void setSoldRecentlyText(String soldRecentlyText) { this.soldRecentlyText = soldRecentlyText; }
    public String getLogisticsTagsJson() { return logisticsTagsJson; }
    public void setLogisticsTagsJson(String logisticsTagsJson) { this.logisticsTagsJson = logisticsTagsJson; }
    public String getBadgesJson() { return badgesJson; }
    public void setBadgesJson(String badgesJson) { this.badgesJson = badgesJson; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public String getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
    public String getRawDetailJson() { return rawDetailJson; }
    public void setRawDetailJson(String rawDetailJson) { this.rawDetailJson = rawDetailJson; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
}
