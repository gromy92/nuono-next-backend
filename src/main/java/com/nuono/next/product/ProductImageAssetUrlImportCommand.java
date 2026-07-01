package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductImageAssetUrlImportCommand {
    private List<String> imageUrls = new ArrayList<>();
    private ProductImageRole imageRole;

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls == null ? new ArrayList<>() : imageUrls;
    }

    public ProductImageRole getImageRole() {
        return imageRole;
    }

    public void setImageRole(ProductImageRole imageRole) {
        this.imageRole = imageRole;
    }
}
