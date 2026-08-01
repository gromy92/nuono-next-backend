package com.nuono.next.officialwarehouse;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class OfficialWarehouseBusinessScope {

    private final Long ownerUserId;
    private final List<String> storeCodes;
    private final Map<String, Long> storeOwnerUserIds;
    private final String requestedStoreCode;

    private OfficialWarehouseBusinessScope(
            Long ownerUserId,
            Map<String, Long> storeOwnerUserIds,
            String requestedStoreCode
    ) {
        this.ownerUserId = ownerUserId;
        this.storeOwnerUserIds = Collections.unmodifiableMap(new LinkedHashMap<>(storeOwnerUserIds));
        this.storeCodes = List.copyOf(this.storeOwnerUserIds.keySet());
        this.requestedStoreCode = requestedStoreCode;
    }

    static OfficialWarehouseBusinessScope resolve(BusinessAccessContext access, String storeCode) {
        requireAccess(access);
        String requestedStoreCode = normalizeStoreCode(storeCode);
        if (requestedStoreCode != null) {
            Long mappedOwnerUserId = access.resolveOwnerUserIdForStore(requestedStoreCode);
            if (mappedOwnerUserId == null
                    && access.getStoreOwnerUserIds().isEmpty()
                    && access.canAccessStore(requestedStoreCode)) {
                mappedOwnerUserId = requireCanonicalOwner(access);
            }
            if (mappedOwnerUserId == null || mappedOwnerUserId <= 0) {
                throw new IllegalArgumentException("当前账号不能访问该店铺。");
            }
            return new OfficialWarehouseBusinessScope(
                    mappedOwnerUserId,
                    Map.of(requestedStoreCode, mappedOwnerUserId),
                    requestedStoreCode
            );
        }
        Long ownerUserId = requireCanonicalOwner(access);
        return new OfficialWarehouseBusinessScope(
                ownerUserId,
                authorizedStoreOwnersForOwner(access, ownerUserId),
                null
        );
    }

    static OfficialWarehouseBusinessScope resolveObjectAccess(BusinessAccessContext access) {
        requireAccess(access);
        Long canonicalOwnerUserId = requireCanonicalOwner(access);
        return new OfficialWarehouseBusinessScope(
                canonicalOwnerUserId,
                allAuthorizedStoreOwners(access, canonicalOwnerUserId),
                null
        );
    }

    Long ownerUserId() {
        return ownerUserId;
    }

    List<String> storeCodes() {
        return storeCodes;
    }

    String requestedStoreCode() {
        return requestedStoreCode;
    }

    Map<String, Long> storeOwnerUserIds() {
        return storeOwnerUserIds;
    }

    boolean hasStores() {
        return !storeCodes.isEmpty();
    }

    void requireObjectAccess(Long recordOwnerUserId, String recordStoreCode, String message) {
        Long mappedOwnerUserId = storeOwnerUserIds.get(normalizeStoreCode(recordStoreCode));
        if (recordOwnerUserId == null || !recordOwnerUserId.equals(mappedOwnerUserId)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Map<String, Long> authorizedStoreOwnersForOwner(
            BusinessAccessContext access,
            Long ownerUserId
    ) {
        Map<String, Long> allStoreOwners = allAuthorizedStoreOwners(access, ownerUserId);
        Map<String, Long> result = new LinkedHashMap<>();
        allStoreOwners.forEach((storeCode, mappedOwnerUserId) -> {
            if (ownerUserId.equals(mappedOwnerUserId)) {
                result.put(storeCode, mappedOwnerUserId);
            }
        });
        return result;
    }

    private static Map<String, Long> allAuthorizedStoreOwners(
            BusinessAccessContext access,
            Long canonicalOwnerUserId
    ) {
        Map<String, Long> storeOwners = access.getStoreOwnerUserIds();
        if (storeOwners.isEmpty()) {
            Map<String, Long> legacyMappings = new LinkedHashMap<>();
            sortedStoreCodes(access.getStoreCodes()).forEach(
                    storeCode -> legacyMappings.put(storeCode, canonicalOwnerUserId)
            );
            return legacyMappings;
        }
        Map<String, Long> result = new LinkedHashMap<>();
        sortedStoreCodes(storeOwners.keySet()).forEach(storeCode -> {
            Long mappedOwnerUserId = storeOwners.get(storeCode);
            if (mappedOwnerUserId != null && mappedOwnerUserId > 0) {
                result.put(storeCode, mappedOwnerUserId);
            }
        });
        return result;
    }

    private static List<String> sortedStoreCodes(Iterable<String> source) {
        List<String> result = new ArrayList<>();
        source.forEach(result::add);
        result.sort(String::compareTo);
        return result;
    }

    private static void requireAccess(BusinessAccessContext access) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
    }

    private static Long requireCanonicalOwner(BusinessAccessContext access) {
        Long ownerUserId = access.getBusinessOwnerUserId();
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("无法识别当前业务老板账号。");
        }
        return ownerUserId;
    }

    private static String normalizeStoreCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
