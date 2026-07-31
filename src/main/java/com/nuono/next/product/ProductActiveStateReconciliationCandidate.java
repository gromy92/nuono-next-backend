package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductActiveStateReconciliationCandidate {
    private Long ownerUserId;
    private Long logicalStoreId;
    private Long productMasterId;
    private Long variantId;
    private Long siteOfferId;
    private String storeCode;
    private String siteCode;
    private String skuParent;
    private String partnerSku;
    private String pskuCode;
    private LocalDateTime lastAttemptAt;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public void setLogicalStoreId(Long logicalStoreId) {
        this.logicalStoreId = logicalStoreId;
    }

    public Long getProductMasterId() {
        return productMasterId;
    }

    public void setProductMasterId(Long productMasterId) {
        this.productMasterId = productMasterId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public Long getSiteOfferId() {
        return siteOfferId;
    }

    public void setSiteOfferId(Long siteOfferId) {
        this.siteOfferId = siteOfferId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getPskuCode() {
        return pskuCode;
    }

    public void setPskuCode(String pskuCode) {
        this.pskuCode = pskuCode;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
}
