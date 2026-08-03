package com.nuono.next.warehousedispatch;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class WarehouseBusinessScope {

    private final Map<String, Long> storeOwnerUserIds;
    private final Set<Long> ownerUserIds;

    private WarehouseBusinessScope(Map<String, Long> storeOwnerUserIds) {
        this.storeOwnerUserIds = Collections.unmodifiableMap(new LinkedHashMap<>(storeOwnerUserIds));
        this.ownerUserIds = Collections.unmodifiableSet(new LinkedHashSet<>(storeOwnerUserIds.values()));
    }

    static WarehouseBusinessScope from(BusinessAccessContext access) {
        if (access == null || access.getStoreCodes().isEmpty()) {
            return new WarehouseBusinessScope(Map.of());
        }
        Long fallbackOwnerUserId = access.getBusinessOwnerUserId() == null
                ? access.getSessionUserId()
                : access.getBusinessOwnerUserId();
        boolean legacyContextWithoutStoreOwners = access.getStoreOwnerUserIds().isEmpty();
        Map<String, Long> result = new LinkedHashMap<>();
        for (String storeCode : access.getStoreCodes()) {
            String normalizedStoreCode = normalizeStoreCode(storeCode);
            Long ownerUserId = access.resolveOwnerUserIdForStore(normalizedStoreCode);
            if (ownerUserId == null && legacyContextWithoutStoreOwners) {
                ownerUserId = fallbackOwnerUserId;
            }
            if (normalizedStoreCode != null && ownerUserId != null && ownerUserId > 0) {
                result.put(normalizedStoreCode, ownerUserId);
            }
        }
        return new WarehouseBusinessScope(result);
    }

    Map<String, Long> storeOwnerUserIds() {
        return storeOwnerUserIds;
    }

    Set<Long> ownerUserIds() {
        return ownerUserIds;
    }

    boolean allowsOwner(Long ownerUserId) {
        return ownerUserId != null && ownerUserIds.contains(ownerUserId);
    }

    boolean allows(Long ownerUserId, String storeCode) {
        String normalizedStoreCode = normalizeStoreCode(storeCode);
        return normalizedStoreCode != null
                && ownerUserId != null
                && ownerUserId.equals(storeOwnerUserIds.get(normalizedStoreCode));
    }

    boolean allowsAll(Long ownerUserId, String commaSeparatedStoreCodes) {
        if (!StringUtils.hasText(commaSeparatedStoreCodes)) {
            return false;
        }
        for (String storeCode : commaSeparatedStoreCodes.split(",")) {
            if (!allows(ownerUserId, storeCode)) {
                return false;
            }
        }
        return true;
    }

    Long requireSingleBalanceOwner(Collection<FulfillmentBalanceRecord> balances) {
        if (balances == null || balances.isEmpty()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }
        Long aggregateOwnerUserId = null;
        for (FulfillmentBalanceRecord balance : balances) {
            if (balance == null || !allows(balance.ownerUserId, balance.sourceStoreCode)) {
                throw new IllegalArgumentException("当前账号不能发运所选来源。");
            }
            if (aggregateOwnerUserId == null) {
                aggregateOwnerUserId = balance.ownerUserId;
            } else if (!aggregateOwnerUserId.equals(balance.ownerUserId)) {
                throw new IllegalArgumentException("不同业务归属人的库存不能合并到同一发运单，请分别创建。");
            }
        }
        return aggregateOwnerUserId;
    }

    private static String normalizeStoreCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
