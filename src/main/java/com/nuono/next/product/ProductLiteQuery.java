package com.nuono.next.product;

public class ProductLiteQuery {
    private String siteCode;
    private String titleKeyword;
    private Integer limit;

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getTitleKeyword() {
        return titleKeyword;
    }

    public void setTitleKeyword(String titleKeyword) {
        this.titleKeyword = titleKeyword;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
