package com.nuono.next.product;

public class ProductImageSuiteAssetView {
    private Long id;
    private ProductImageSuiteAssetRole imageRole;
    private String imageUrl;
    private Integer sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
