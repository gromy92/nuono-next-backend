package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductVariantSpecDetailView {
    private boolean ready;
    private Long ownerUserId;
    private String storeCode;
    private Long variantId;
    private String partnerSku;
    private String childSku;
    private String skuParent;
    private String title;
    private String imageUrl;
    private Long effectiveSourceId;
    private String effectiveSourceType;
    private ProductVariantSpecView effectiveSpec;
    private ProductVariantLogisticsProfileView logisticsProfile;
    private List<ProductVariantSpecSourceView> sources = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
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

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getChildSku() {
        return childSku;
    }

    public void setChildSku(String childSku) {
        this.childSku = childSku;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Long getEffectiveSourceId() {
        return effectiveSourceId;
    }

    public void setEffectiveSourceId(Long effectiveSourceId) {
        this.effectiveSourceId = effectiveSourceId;
    }

    public String getEffectiveSourceType() {
        return effectiveSourceType;
    }

    public void setEffectiveSourceType(String effectiveSourceType) {
        this.effectiveSourceType = effectiveSourceType;
    }

    public ProductVariantSpecView getEffectiveSpec() {
        return effectiveSpec;
    }

    public void setEffectiveSpec(ProductVariantSpecView effectiveSpec) {
        this.effectiveSpec = effectiveSpec;
    }

    public ProductVariantLogisticsProfileView getLogisticsProfile() {
        return logisticsProfile;
    }

    public void setLogisticsProfile(ProductVariantLogisticsProfileView logisticsProfile) {
        this.logisticsProfile = logisticsProfile;
    }

    public List<ProductVariantSpecSourceView> getSources() {
        return sources;
    }

    public void setSources(List<ProductVariantSpecSourceView> sources) {
        this.sources = sources;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
