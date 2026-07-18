package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductImageProfileDetailView {
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
    private String productFactText;
    private List<String> heroSellingPoints = new ArrayList<>();
    private String updatedAt;
    private List<ProductImageProfileAssetView> assets = new ArrayList<>();
    private List<ProductImageSectionView> sections = new ArrayList<>();
    private List<ProductImageSuiteView> suites = new ArrayList<>();

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

    public String getProductFactText() {
        return productFactText;
    }

    public void setProductFactText(String productFactText) {
        this.productFactText = productFactText;
    }

    public List<String> getHeroSellingPoints() {
        return heroSellingPoints;
    }

    public void setHeroSellingPoints(List<String> heroSellingPoints) {
        this.heroSellingPoints = heroSellingPoints == null ? new ArrayList<>() : heroSellingPoints;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ProductImageProfileAssetView> getAssets() {
        return assets;
    }

    public void setAssets(List<ProductImageProfileAssetView> assets) {
        this.assets = assets == null ? new ArrayList<>() : assets;
    }

    public List<ProductImageSectionView> getSections() {
        return sections;
    }

    public void setSections(List<ProductImageSectionView> sections) {
        this.sections = sections == null ? new ArrayList<>() : sections;
    }

    public List<ProductImageSuiteView> getSuites() {
        return suites;
    }

    public void setSuites(List<ProductImageSuiteView> suites) {
        this.suites = suites == null ? new ArrayList<>() : suites;
    }
}
