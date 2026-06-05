package com.nuono.next.procurement.aliorder;

public class Ali1688HistoricalOrderProductLinkCandidateRow {
    private String storeCode;
    private String siteCode;
    private String skuParent;
    private String partnerSku;
    private String pskuCode;
    private String offerCode;
    private String productTitle;
    private String productImageUrl;
    private String linkStatus;
    private Integer linkedAssignmentCount;

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

    public String getOfferCode() {
        return offerCode;
    }

    public void setOfferCode(String offerCode) {
        this.offerCode = offerCode;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getProductImageUrl() {
        return productImageUrl;
    }

    public void setProductImageUrl(String productImageUrl) {
        this.productImageUrl = productImageUrl;
    }

    public String getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(String linkStatus) {
        this.linkStatus = linkStatus;
    }

    public Integer getLinkedAssignmentCount() {
        return linkedAssignmentCount;
    }

    public void setLinkedAssignmentCount(Integer linkedAssignmentCount) {
        this.linkedAssignmentCount = linkedAssignmentCount;
    }
}
