package com.nuono.next.productpublicdetail;

public class ProductPublicDetailScope {
    private Long ownerUserId;
    private Long logicalStoreId;
    private String storeCode;
    private String siteCode;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long logicalStoreId) { this.logicalStoreId = logicalStoreId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
}
