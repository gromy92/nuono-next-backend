package com.nuono.next.product;

public class ProductImageAssetRoleUpdateCommand {
    private Long assetId;
    private String imageUrl;
    private ProductImageRole imageRole;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public ProductImageRole getImageRole() {
        return imageRole;
    }

    public void setImageRole(ProductImageRole imageRole) {
        this.imageRole = imageRole;
    }
}
