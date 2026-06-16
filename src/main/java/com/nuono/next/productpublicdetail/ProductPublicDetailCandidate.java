package com.nuono.next.productpublicdetail;

public class ProductPublicDetailCandidate {
    private Long ownerUserId;
    private Long logicalStoreId;
    private String storeCode;
    private String siteCode;
    private Long productMasterId;
    private Long productVariantId;
    private Long productSiteOfferId;
    private String partnerSku;
    private String skuParent;
    private String noonProductCode;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long logicalStoreId) { this.logicalStoreId = logicalStoreId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getProductMasterId() { return productMasterId; }
    public void setProductMasterId(Long productMasterId) { this.productMasterId = productMasterId; }
    public Long getProductVariantId() { return productVariantId; }
    public void setProductVariantId(Long productVariantId) { this.productVariantId = productVariantId; }
    public Long getProductSiteOfferId() { return productSiteOfferId; }
    public void setProductSiteOfferId(Long productSiteOfferId) { this.productSiteOfferId = productSiteOfferId; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getSkuParent() { return skuParent; }
    public void setSkuParent(String skuParent) { this.skuParent = skuParent; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
}
