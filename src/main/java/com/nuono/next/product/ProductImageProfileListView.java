package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductImageProfileListView {
    private Long ownerUserId;
    private String storeCode;
    private List<ProductImageProfileDetailView> items = new ArrayList<>();

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

    public List<ProductImageProfileDetailView> getItems() {
        return items;
    }

    public void setItems(List<ProductImageProfileDetailView> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
