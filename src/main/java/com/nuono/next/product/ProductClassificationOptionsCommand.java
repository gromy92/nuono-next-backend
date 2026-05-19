package com.nuono.next.product;

public class ProductClassificationOptionsCommand {

    private Long ownerUserId;
    private String storeCode;
    private String brandQuery;
    private String fulltypeQuery;
    private Integer limit;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getBrandQuery() {
        return brandQuery;
    }

    public void setBrandQuery(String brandQuery) {
        this.brandQuery = brandQuery;
    }

    public String getFulltypeQuery() {
        return fulltypeQuery;
    }

    public void setFulltypeQuery(String fulltypeQuery) {
        this.fulltypeQuery = fulltypeQuery;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
