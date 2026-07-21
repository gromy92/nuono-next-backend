package com.nuono.next.permission.access;

public final class BusinessStoreAccess {

    private final long ownerUserId;
    private final String storeCode;

    BusinessStoreAccess(long ownerUserId, String storeCode) {
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        String normalizedStoreCode = BusinessAccessContext.normalizeStoreCode(storeCode);
        if (normalizedStoreCode == null) {
            throw new IllegalArgumentException("storeCode must not be blank");
        }
        this.ownerUserId = ownerUserId;
        this.storeCode = normalizedStoreCode;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }
}
