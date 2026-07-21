package com.nuono.next.permission.access;

public final class BusinessStoreAccessTestFixture {

    private BusinessStoreAccessTestFixture() {
    }

    public static BusinessStoreAccess access(long ownerUserId, String storeCode) {
        return new BusinessStoreAccess(ownerUserId, storeCode);
    }
}
