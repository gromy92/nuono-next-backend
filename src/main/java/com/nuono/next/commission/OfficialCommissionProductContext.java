package com.nuono.next.commission;

import java.math.BigDecimal;

public class OfficialCommissionProductContext {
    private Long variantId;
    private String skuId;
    private String partnerSku;
    private String childSku;
    private String skuParent;
    private String site;
    private String brand;
    private String productFulltype;
    private BigDecimal storedSalePrice;
    private String marketCurrency;

    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getChildSku() { return childSku; }
    public void setChildSku(String childSku) { this.childSku = childSku; }
    public String getSkuParent() { return skuParent; }
    public void setSkuParent(String skuParent) { this.skuParent = skuParent; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getProductFulltype() { return productFulltype; }
    public void setProductFulltype(String productFulltype) { this.productFulltype = productFulltype; }
    public BigDecimal getStoredSalePrice() { return storedSalePrice; }
    public void setStoredSalePrice(BigDecimal storedSalePrice) { this.storedSalePrice = storedSalePrice; }
    public String getMarketCurrency() { return marketCurrency; }
    public void setMarketCurrency(String marketCurrency) { this.marketCurrency = marketCurrency; }
}
