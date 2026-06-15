package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductVariantSpecOverviewView {
    private boolean ready;
    private String source = "local";
    private Long ownerUserId;
    private String storeCode;
    private List<String> warnings = new ArrayList<>();
    private List<ProductVariantSpecView> items = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<ProductVariantSpecView> getItems() {
        return items;
    }

    public void setItems(List<ProductVariantSpecView> items) {
        this.items = items;
    }
}
