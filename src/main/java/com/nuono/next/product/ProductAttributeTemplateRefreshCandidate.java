package com.nuono.next.product;

public class ProductAttributeTemplateRefreshCandidate {

    private Long ownerUserId;
    private String projectCode;
    private String storeCode;
    private String productFulltype;
    private String authProjectCode;
    private String authStoreCode;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public void setProductFulltype(String productFulltype) {
        this.productFulltype = productFulltype;
    }

    public String getAuthProjectCode() {
        return authProjectCode;
    }

    public void setAuthProjectCode(String authProjectCode) {
        this.authProjectCode = authProjectCode;
    }

    public String getAuthStoreCode() {
        return authStoreCode;
    }

    public void setAuthStoreCode(String authStoreCode) {
        this.authStoreCode = authStoreCode;
    }
}
