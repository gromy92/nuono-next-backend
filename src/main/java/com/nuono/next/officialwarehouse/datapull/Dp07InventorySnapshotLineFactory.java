package com.nuono.next.officialwarehouse.datapull;

import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.SnapshotApplyItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Maps provider-owned DP-07-A facts without consulting or persisting product projections. */
final class Dp07InventorySnapshotLineFactory {
    InventorySnapshotLineInsertRecord fresh(
            CompleteSnapshot<Dp07InventorySnapshotItem> snapshot,
            InventorySyncScopeRecord scope,
            InventorySyncBatchInsertRecord batch,
            SnapshotApplyItem<Dp07InventorySnapshotItem> staged,
            long lineId
    ) {
        Dp07InventorySnapshotItem item = staged.getValue();
        InventorySnapshotLineInsertRecord line = base(
                snapshot, scope, batch, staged.getStableIdentity(), lineId
        );
        line.partnerSku = trimToNull(item.getPartnerSku());
        line.noonSku = trimToNull(item.getNoonSku());
        line.pbarcode = item.getPbarcode();
        line.barcode = item.getBarcode();
        line.warehouseCode = item.getWarehouseCode();
        line.countryCode = item.getCountryCode();
        line.inventoryType = item.getInventoryType();
        line.reasonCode = item.getReasonCode();
        line.classificationCode = item.getClassificationCode();
        line.stockBucket = item.getStockBucket();
        line.quantity = item.getQuantity();
        line.inventorySnapshotAt = item.getInventorySnapshotAt();
        line.titleCache = trimToNull(item.getTitle());
        line.brandCache = trimToNull(item.getBrand());
        line.rawPayloadJson = item.getRawPayloadJson();
        return line;
    }

    InventorySnapshotLineInsertRecord carried(
            CompleteSnapshot<?> snapshot,
            InventorySyncScopeRecord scope,
            InventorySyncBatchInsertRecord batch,
            InventorySnapshotLineInsertRecord source,
            long lineId
    ) {
        InventorySnapshotLineInsertRecord line = base(
                snapshot, scope, batch, source.snapshotStableIdentity, lineId
        );
        line.partnerSku = source.partnerSku;
        line.noonSku = source.noonSku;
        line.pbarcode = source.pbarcode;
        line.barcode = source.barcode;
        line.warehouseCode = source.warehouseCode;
        line.countryCode = source.countryCode;
        line.inventoryType = source.inventoryType;
        line.reasonCode = source.reasonCode;
        line.classificationCode = source.classificationCode;
        line.stockBucket = source.stockBucket;
        line.quantity = source.quantity;
        line.inventorySnapshotAt = source.inventorySnapshotAt;
        line.titleCache = source.titleCache;
        line.brandCache = source.brandCache;
        line.rawPayloadJson = source.rawPayloadJson;
        return line;
    }

    void requireCarrySource(
            InventorySyncScopeRecord scope,
            String previous,
            InventorySnapshotLineInsertRecord source
    ) {
        if (source == null || !stable(source.snapshotStableIdentity)
                || !Objects.equals(source.ownerUserId, scope.ownerUserId)
                || !Objects.equals(source.logicalStoreId, scope.logicalStoreId)
                || !same(source.storeCode, scope.storeCode)
                || !sameIgnoreCase(source.siteCode, scope.siteCode)
                || !same(source.projectCode, scope.projectCode)
                || Objects.equals(previous, source.snapshotStableIdentity)) {
            throw new IllegalStateException("DP-07-A carried inventory fact is invalid");
        }
    }

    private InventorySnapshotLineInsertRecord base(
            CompleteSnapshot<?> snapshot,
            InventorySyncScopeRecord scope,
            InventorySyncBatchInsertRecord batch,
            String stableIdentity,
            long lineId
    ) {
        if (lineId < 1L || !stable(stableIdentity)) {
            throw new IllegalStateException("DP-07-A raw inventory identity is invalid");
        }
        InventorySnapshotLineInsertRecord line = new InventorySnapshotLineInsertRecord();
        line.id = lineId;
        line.syncBatchId = batch.id;
        line.snapshotStableIdentity = stableIdentity;
        line.ownerUserId = snapshot.getOwnerUserId();
        line.logicalStoreId = scope.logicalStoreId;
        line.storeCode = snapshot.getStoreCode();
        line.siteCode = snapshot.getSiteCode();
        line.projectCode = snapshot.getProjectCode();
        line.partnerId = batch.partnerId;
        line.matchStatus = "RAW_PROVIDER_FACT";
        line.operatorUserId = snapshot.getOwnerUserId();
        return line;
    }

    private boolean stable(String value) {
        return StringUtils.hasText(value) && value.equals(value.trim());
    }
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
    private boolean same(String left, String right) {
        return Objects.equals(trimToNull(left), trimToNull(right));
    }
    private boolean sameIgnoreCase(String left, String right) {
        String a = trimToNull(left); String b = trimToNull(right);
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
