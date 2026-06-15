package com.nuono.next.productpublicdetail.noon;

import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class NoonPublicProductDetailResult {
    private ProductPublicDetailSyncStatus status;
    private String failureCode;
    private String failureMessage;
    private String noonProductCode;
    private String codeType;
    private String titleEn;
    private String titleAr;
    private String brand;
    private String categoryPath;
    private BigDecimal priceAmount;
    private String currencyCode;
    private BigDecimal rating;
    private Integer reviewCount;
    private String availabilityText;
    private String mainImageUrl;
    private String detailUrl;
    private String rawPayloadJson;
    private Integer providerHttpStatus;
    private String providerSourceUrl;
    private String providerResponseHash;
    private String providerParserVersion;
    private LocalDateTime fetchedAt;

    public ProductPublicDetailSyncStatus getStatus() { return status; }
    public void setStatus(ProductPublicDetailSyncStatus status) { this.status = status; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getTitleAr() { return titleAr; }
    public void setTitleAr(String titleAr) { this.titleAr = titleAr; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    public String getAvailabilityText() { return availabilityText; }
    public void setAvailabilityText(String availabilityText) { this.availabilityText = availabilityText; }
    public String getMainImageUrl() { return mainImageUrl; }
    public void setMainImageUrl(String mainImageUrl) { this.mainImageUrl = mainImageUrl; }
    public String getDetailUrl() { return detailUrl; }
    public void setDetailUrl(String detailUrl) { this.detailUrl = detailUrl; }
    public String getRawPayloadJson() { return rawPayloadJson; }
    public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public String getProviderSourceUrl() { return providerSourceUrl; }
    public void setProviderSourceUrl(String providerSourceUrl) { this.providerSourceUrl = providerSourceUrl; }
    public String getProviderResponseHash() { return providerResponseHash; }
    public void setProviderResponseHash(String providerResponseHash) { this.providerResponseHash = providerResponseHash; }
    public String getProviderParserVersion() { return providerParserVersion; }
    public void setProviderParserVersion(String providerParserVersion) { this.providerParserVersion = providerParserVersion; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
