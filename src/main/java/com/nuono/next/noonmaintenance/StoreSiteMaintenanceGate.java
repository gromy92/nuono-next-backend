package com.nuono.next.noonmaintenance;

@FunctionalInterface
public interface StoreSiteMaintenanceGate {
    boolean isUnderMaintenance(Long ownerUserId, String storeCode, String siteCode);

    static StoreSiteMaintenanceGate allowAll() {
        return (ownerUserId, storeCode, siteCode) -> false;
    }
}
