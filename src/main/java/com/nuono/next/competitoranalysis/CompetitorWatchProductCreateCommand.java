package com.nuono.next.competitoranalysis;

public class CompetitorWatchProductCreateCommand {
    private String storeCode;
    private String siteCode;
    private Long productSiteOfferId;
    private String partnerSku;
    private String selfNoonProductCode;

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

    public Long getProductSiteOfferId() {
        return productSiteOfferId;
    }

    public void setProductSiteOfferId(Long productSiteOfferId) {
        this.productSiteOfferId = productSiteOfferId;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getSelfNoonProductCode() {
        return selfNoonProductCode;
    }

    public void setSelfNoonProductCode(String selfNoonProductCode) {
        this.selfNoonProductCode = selfNoonProductCode;
    }
}
