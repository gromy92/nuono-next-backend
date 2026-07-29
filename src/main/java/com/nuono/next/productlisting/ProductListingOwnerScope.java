package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.LinkedHashSet;
import java.util.Set;

final class ProductListingOwnerScope {

    private ProductListingOwnerScope() {
    }

    static Long resolve(
            BusinessAccessContext context,
            String storeCode
    ) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null
                ? context.getBusinessOwnerUserId()
                : ownerUserId;
    }

    static Set<Long> accessible(BusinessAccessContext context) {
        Set<Long> ownerUserIds = new LinkedHashSet<>();
        if (context.getBusinessOwnerUserId() != null) {
            ownerUserIds.add(context.getBusinessOwnerUserId());
        }
        if (context.getStoreOwnerUserIds() != null) {
            context.getStoreOwnerUserIds().values().stream()
                    .filter(ownerUserId ->
                            ownerUserId != null && ownerUserId > 0)
                    .forEach(ownerUserIds::add);
        }
        if (ownerUserIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Business owner user ID is required.");
        }
        return ownerUserIds;
    }
}
