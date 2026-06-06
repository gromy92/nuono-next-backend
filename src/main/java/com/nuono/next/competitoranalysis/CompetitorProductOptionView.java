package com.nuono.next.competitoranalysis;

public class CompetitorProductOptionView {
    private Long productSiteOfferId;
    private Long productMasterId;
    private Long productVariantId;
    private String storeCode;
    private String siteCode;
    private String skuParent;
    private String partnerSku;
    private String childSku;
    private String noonProductCode;
    private String codeType;
    private String title;
    private String brand;
    private String imageUrl;
    private String productFulltype;

    public static CompetitorProductOptionView fromRow(
            CompetitorProductOptionRow row,
            String noonProductCode,
            String codeType
    ) {
        CompetitorProductOptionView view = new CompetitorProductOptionView();
        view.setProductSiteOfferId(row.getProductSiteOfferId());
        view.setProductMasterId(row.getProductMasterId());
        view.setProductVariantId(row.getProductVariantId());
        view.setStoreCode(row.getStoreCode());
        view.setSiteCode(row.getSiteCode());
        view.setSkuParent(row.getSkuParent());
        view.setPartnerSku(row.getPartnerSku());
        view.setChildSku(row.getChildSku());
        view.setNoonProductCode(noonProductCode);
        view.setCodeType(codeType);
        view.setTitle(row.getTitle());
        view.setBrand(row.getBrand());
        view.setImageUrl(row.getImageUrl());
        view.setProductFulltype(row.getProductFulltype());
        return view;
    }

    public Long getProductSiteOfferId() { return productSiteOfferId; }
    public void setProductSiteOfferId(Long productSiteOfferId) { this.productSiteOfferId = productSiteOfferId; }
    public Long getProductMasterId() { return productMasterId; }
    public void setProductMasterId(Long productMasterId) { this.productMasterId = productMasterId; }
    public Long getProductVariantId() { return productVariantId; }
    public void setProductVariantId(Long productVariantId) { this.productVariantId = productVariantId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getSkuParent() { return skuParent; }
    public void setSkuParent(String skuParent) { this.skuParent = skuParent; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getChildSku() { return childSku; }
    public void setChildSku(String childSku) { this.childSku = childSku; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getProductFulltype() { return productFulltype; }
    public void setProductFulltype(String productFulltype) { this.productFulltype = productFulltype; }
}
