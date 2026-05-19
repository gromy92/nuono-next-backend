package com.nuono.next.product;

public class ProductGroupMemberProjectionRecord {

    private String skuParent;
    private String memberSku;
    private String childSku;
    private String partnerSku;
    private String axisValuesJson;
    private Integer sortIx;
    private String title;
    private String imageUrl;

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public String getMemberSku() {
        return memberSku;
    }

    public void setMemberSku(String memberSku) {
        this.memberSku = memberSku;
    }

    public String getChildSku() {
        return childSku;
    }

    public void setChildSku(String childSku) {
        this.childSku = childSku;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getAxisValuesJson() {
        return axisValuesJson;
    }

    public void setAxisValuesJson(String axisValuesJson) {
        this.axisValuesJson = axisValuesJson;
    }

    public Integer getSortIx() {
        return sortIx;
    }

    public void setSortIx(Integer sortIx) {
        this.sortIx = sortIx;
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
}
