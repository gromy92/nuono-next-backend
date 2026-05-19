package com.nuono.next.product;

public class ProductMasterFetchCommand {

    private Long ownerUserId;

    private String storeCode;

    private String noonUser;

    private String noonPassword;

    private String skuParent;

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

    public String getNoonUser() {
        return noonUser;
    }

    public void setNoonUser(String noonUser) {
        this.noonUser = noonUser;
    }

    public String getNoonPassword() {
        return noonPassword;
    }

    public void setNoonPassword(String noonPassword) {
        this.noonPassword = noonPassword;
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
}
