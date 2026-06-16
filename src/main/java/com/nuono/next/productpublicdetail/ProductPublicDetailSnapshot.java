package com.nuono.next.productpublicdetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductPublicDetailSnapshot {
    private Long id;
    private Long ownerUserId;
    private Long logicalStoreId;
    private String storeCode;
    private String siteCode;
    private Long productMasterId;
    private Long productVariantId;
    private Long productSiteOfferId;
    private String partnerSku;
    private String skuParent;
    private String noonProductCode;
    private String codeType;
    private String sourcePlatform;
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
    private String snapshotHash;
    private Integer providerHttpStatus;
    private String providerSourceUrl;
    private String providerResponseHash;
    private String providerParserVersion;
    private ProductPublicDetailSyncStatus syncStatus;
    private String failureCode;
    private String failureMessage;
    private LocalDate factDate;
    private LocalDateTime fetchedAt;
    private Boolean latest;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long logicalStoreId) { this.logicalStoreId = logicalStoreId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getProductMasterId() { return productMasterId; }
    public void setProductMasterId(Long productMasterId) { this.productMasterId = productMasterId; }
    public Long getProductVariantId() { return productVariantId; }
    public void setProductVariantId(Long productVariantId) { this.productVariantId = productVariantId; }
    public Long getProductSiteOfferId() { return productSiteOfferId; }
    public void setProductSiteOfferId(Long productSiteOfferId) { this.productSiteOfferId = productSiteOfferId; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getSkuParent() { return skuParent; }
    public void setSkuParent(String skuParent) { this.skuParent = skuParent; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getSourcePlatform() { return sourcePlatform; }
    public void setSourcePlatform(String sourcePlatform) { this.sourcePlatform = sourcePlatform; }
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
    public String getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public String getProviderSourceUrl() { return providerSourceUrl; }
    public void setProviderSourceUrl(String providerSourceUrl) { this.providerSourceUrl = providerSourceUrl; }
    public String getProviderResponseHash() { return providerResponseHash; }
    public void setProviderResponseHash(String providerResponseHash) { this.providerResponseHash = providerResponseHash; }
    public String getProviderParserVersion() { return providerParserVersion; }
    public void setProviderParserVersion(String providerParserVersion) { this.providerParserVersion = providerParserVersion; }
    public ProductPublicDetailSyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(ProductPublicDetailSyncStatus syncStatus) { this.syncStatus = syncStatus; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public LocalDate getFactDate() { return factDate; }
    public void setFactDate(LocalDate factDate) { this.factDate = factDate; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
    public Boolean getLatest() { return latest; }
    public void setLatest(Boolean latest) { this.latest = latest; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
