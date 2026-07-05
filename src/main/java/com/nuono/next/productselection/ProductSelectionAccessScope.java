package com.nuono.next.productselection;

import com.nuono.next.permission.access.BusinessAccessContext;

public class ProductSelectionAccessScope {

    private final BusinessAccessContext businessAccess;
    private final ProductSelectionStoreScope storeScope;

    public ProductSelectionAccessScope(
            BusinessAccessContext businessAccess,
            ProductSelectionStoreScope storeScope
    ) {
        this.businessAccess = businessAccess;
        this.storeScope = storeScope;
    }

    public BusinessAccessContext getBusinessAccess() {
        return businessAccess;
    }

    public ProductSelectionStoreScope getStoreScope() {
        return storeScope;
    }

    public Long getOperatorUserId() {
        return storeScope == null ? null : storeScope.getOperatorUserId();
    }

    public Long getOwnerUserId() {
        return storeScope == null ? null : storeScope.getOwnerUserId();
    }

    public Long getLogicalStoreId() {
        return storeScope == null ? null : storeScope.getLogicalStoreId();
    }

    public String getStoreCode() {
        return storeScope == null ? null : storeScope.getStoreCode();
    }
}
