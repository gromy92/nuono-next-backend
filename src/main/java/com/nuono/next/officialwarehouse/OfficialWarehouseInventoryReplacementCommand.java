package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotItem;
import java.util.List;
import java.util.Objects;

/** Validated input contract for replacing one complete official-warehouse inventory scope. */
public final class OfficialWarehouseInventoryReplacementCommand {

    final long ownerUserId;
    final long logicalStoreId;
    final String projectCode;
    final String storeCode;
    final String siteCode;
    final long operatorUserId;
    final String sourceBatchReference;
    final int totalPages;
    final int skippedItemCount;
    final List<Dp07InventorySnapshotItem> items;

    public OfficialWarehouseInventoryReplacementCommand(
            long ownerUserId,
            long logicalStoreId,
            String projectCode,
            String storeCode,
            String siteCode,
            long operatorUserId,
            String sourceBatchReference,
            int totalPages,
            int skippedItemCount,
            List<Dp07InventorySnapshotItem> items
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.projectCode = projectCode;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.operatorUserId = operatorUserId;
        this.sourceBatchReference = sourceBatchReference;
        this.totalPages = totalPages;
        this.skippedItemCount = skippedItemCount;
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    OfficialWarehouseInventoryReplacementCommand validate() {
        if (ownerUserId < 1L || logicalStoreId < 1L || operatorUserId < 1L
                || totalPages < 1 || skippedItemCount < 0
                || !stable(projectCode) || !stable(storeCode) || !stable(siteCode)
                || !stable(sourceBatchReference)) {
            throw new IllegalArgumentException("inventory replacement command is invalid");
        }
        for (Dp07InventorySnapshotItem item : items) {
            Objects.requireNonNull(item, "inventory replacement item");
        }
        return this;
    }

    private boolean stable(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }
}
