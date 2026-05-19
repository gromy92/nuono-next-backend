package com.nuono.next.product;

import com.nuono.next.store.LocalDbStoreInitializationService;
import java.util.ArrayList;
import java.util.List;

public class ProductGroupCandidatesView {

    private boolean ready;
    private String source;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private Long ownerUserId;
    private String storeCode;
    private String skuParent;
    private List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items = new ArrayList<>();

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
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

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> getItems() {
        return items;
    }

    public void setItems(List<LocalDbStoreInitializationService.StoreInitializationProductListItemView> items) {
        this.items = items;
    }
}
