package com.nuono.next.sales;

import java.util.Objects;

public class ProductLifecycleJobQuery {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;

    public ProductLifecycleJobQuery(Long ownerUserId, String storeCode, String siteCode) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProductLifecycleJobQuery)) {
            return false;
        }
        ProductLifecycleJobQuery that = (ProductLifecycleJobQuery) o;
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, storeCode, siteCode);
    }
}
