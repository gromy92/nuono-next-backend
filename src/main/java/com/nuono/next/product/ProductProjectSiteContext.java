package com.nuono.next.product;

class ProductProjectSiteContext {

    private final String storeCode;

    private String site;

    private String statusCode;

    ProductProjectSiteContext(String storeCode, String site, String statusCode) {
        this.storeCode = storeCode;
        this.site = site;
        this.statusCode = statusCode;
    }

    String getStoreCode() {
        return storeCode;
    }

    String getSite() {
        return site;
    }

    void setSite(String site) {
        this.site = site;
    }

    String getStatusCode() {
        return statusCode;
    }

    void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }
}
