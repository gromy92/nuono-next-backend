package com.nuono.next.product;

import java.util.List;

public class ProductImageAssetUsageCreateCommand {
    private Long assetId;
    private String imageUrl;
    private ProductImageRole sourceRole;
    private List<ProductImageRole> imageRoles;

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public ProductImageRole getSourceRole() { return sourceRole; }
    public void setSourceRole(ProductImageRole sourceRole) { this.sourceRole = sourceRole; }
    public List<ProductImageRole> getImageRoles() { return imageRoles; }
    public void setImageRoles(List<ProductImageRole> imageRoles) { this.imageRoles = imageRoles; }
}
