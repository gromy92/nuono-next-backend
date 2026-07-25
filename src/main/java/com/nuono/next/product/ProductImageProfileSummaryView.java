package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductImageProfileSummaryView {
    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String pskuCode;
    private String productIdentityKey;
    private Long productMasterId;
    private String productTitle;
    private String brand;
    private String titleAr;
    private String titleEn;
    private String specSummary;
    private String coverImageUrl;
    private Integer assetCount;
    private Integer suiteCount;
    private Integer activeSuiteCount;
    private Boolean hasAdoptedSuite;
    private ProductImageProfileReadinessStatus profileReadinessStatus;
    private List<ProductImageProfileMissingField> missingProfileFields = new ArrayList<>();
    private ProductImageSummaryStatus imageStatus;
    private String updatedAt;

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

    public String getPskuCode() {
        return pskuCode;
    }

    public void setPskuCode(String pskuCode) {
        this.pskuCode = pskuCode;
    }

    public String getProductIdentityKey() {
        return productIdentityKey;
    }

    public void setProductIdentityKey(String productIdentityKey) {
        this.productIdentityKey = productIdentityKey;
    }

    public Long getProductMasterId() {
        return productMasterId;
    }

    public void setProductMasterId(Long productMasterId) {
        this.productMasterId = productMasterId;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getTitleAr() {
        return titleAr;
    }

    public void setTitleAr(String titleAr) {
        this.titleAr = titleAr;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getSpecSummary() {
        return specSummary;
    }

    public void setSpecSummary(String specSummary) {
        this.specSummary = specSummary;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public Integer getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(Integer assetCount) {
        this.assetCount = assetCount;
    }

    public Integer getSuiteCount() {
        return suiteCount;
    }

    public void setSuiteCount(Integer suiteCount) {
        this.suiteCount = suiteCount;
    }

    public Integer getActiveSuiteCount() {
        return activeSuiteCount;
    }

    public void setActiveSuiteCount(Integer activeSuiteCount) {
        this.activeSuiteCount = activeSuiteCount;
    }

    public Boolean getHasAdoptedSuite() {
        return hasAdoptedSuite;
    }

    public void setHasAdoptedSuite(Boolean hasAdoptedSuite) {
        this.hasAdoptedSuite = hasAdoptedSuite;
    }

    public ProductImageProfileReadinessStatus getProfileReadinessStatus() {
        return profileReadinessStatus;
    }

    public void setProfileReadinessStatus(ProductImageProfileReadinessStatus profileReadinessStatus) {
        this.profileReadinessStatus = profileReadinessStatus;
    }

    public List<ProductImageProfileMissingField> getMissingProfileFields() {
        return missingProfileFields;
    }

    public void setMissingProfileFields(List<ProductImageProfileMissingField> missingProfileFields) {
        this.missingProfileFields = missingProfileFields == null ? new ArrayList<>() : new ArrayList<>(missingProfileFields);
    }

    public ProductImageSummaryStatus getImageStatus() {
        return imageStatus;
    }

    public void setImageStatus(ProductImageSummaryStatus imageStatus) {
        this.imageStatus = imageStatus;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
