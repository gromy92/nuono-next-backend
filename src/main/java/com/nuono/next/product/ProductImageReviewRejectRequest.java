package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductImageReviewRejectRequest {
    private String comment;
    private boolean wholeSuite;
    private List<Long> assetIds = new ArrayList<>();

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isWholeSuite() {
        return wholeSuite;
    }

    public void setWholeSuite(boolean wholeSuite) {
        this.wholeSuite = wholeSuite;
    }

    public List<Long> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds == null ? new ArrayList<>() : assetIds;
    }
}
