package com.nuono.next.product;

public class ProductImageProductCandidateRecord {
    private Long productMasterId;
    private String pskuCode;
    private String productIdentityKey;
    private String productTitle;
    private String brand;
    private String coverImageUrl;

    public Long getProductMasterId() {
        return productMasterId;
    }

    public void setProductMasterId(Long productMasterId) {
        this.productMasterId = productMasterId;
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

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }
}
