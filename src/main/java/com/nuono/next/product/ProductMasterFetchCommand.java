package com.nuono.next.product;

public class ProductMasterFetchCommand {

    private Long ownerUserId;

    private String storeCode;

    private String skuParent;

    private String currentZCode;

    private String partnerSku;

    private String pskuCode;

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
        if (currentZCode == null || currentZCode.isBlank()) {
            this.currentZCode = skuParent;
        }
    }

    public String getCurrentZCode() {
        if (currentZCode != null && !currentZCode.isBlank()) {
            return currentZCode;
        }
        return skuParent;
    }

    public void setCurrentZCode(String currentZCode) {
        this.currentZCode = currentZCode;
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
}
