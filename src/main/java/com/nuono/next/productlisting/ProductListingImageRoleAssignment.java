package com.nuono.next.productlisting;

import com.nuono.next.product.ProductImageRole;

public class ProductListingImageRoleAssignment {

    private String imageUrl;
    private ProductImageRole imageRole;
    private Integer sortOrder;

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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
