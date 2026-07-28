package com.nuono.next.product;

import com.nuono.next.store.LocalDbStoreInitializationService;
import java.util.Locale;
import java.util.Map;

final class ProductListStatusResolver {

    private ProductListStatusResolver() {
    }

    static String resolve(
            LocalDbStoreInitializationService.StoreInitializationProductListItemView item
    ) {
        if (item == null) {
            return "synced";
        }
        Map<String, Object> listingTask = item.getListingPublishTask();
        String listingStatus = normalize(listingTask == null ? null : listingTask.get("status"));
        if ("failed".equals(listingStatus) || "rejected".equals(listingStatus)) {
            return "failed";
        }
        String syncStatus = normalize(item.getSyncStatus());
        return syncStatus.isEmpty() ? "synced" : syncStatus;
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }
}
