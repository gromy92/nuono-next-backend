package com.nuono.next.product;

public class ProductImageSuiteAssetRecord {
    private Long id;
    private Long suiteId;
    private ProductImageSuiteAssetRole imageRole;
    private String imageUrl;
    private Integer sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public ProductImageSuiteAssetRole getImageRole() {
        return imageRole;
    }

    public void setImageRole(ProductImageSuiteAssetRole imageRole) {
        this.imageRole = imageRole;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
