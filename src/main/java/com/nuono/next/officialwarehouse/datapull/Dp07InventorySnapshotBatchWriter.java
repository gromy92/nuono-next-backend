package com.nuono.next.officialwarehouse.datapull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.SnapshotApplyItem;
import com.nuono.next.datapull.snapshot.SnapshotApplyTargetStore;
import com.nuono.next.datapull.snapshot.SnapshotCarryForwardResult;
import com.nuono.next.datapull.snapshot.SnapshotCarryMode;
import com.nuono.next.infrastructure.mapper.InventorySnapshotRuntimeMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Prepares invisible DP-07-A rows in bounded chunks and seals them with set-based updates. */
public final class Dp07InventorySnapshotBatchWriter {
    private static final String TARGET_TYPE = "OFFICIAL_WAREHOUSE_INVENTORY_BATCH";
    private static final String SOURCE_TYPE = "FBN_INVENTORY_API";

    private final OfficialWarehouseStatisticsMapper mapper;
    private final InventorySnapshotRuntimeMapper runtimeMapper;
    private final ObjectMapper objectMapper;
    private final SnapshotApplyTargetStore targets;
    private final Dp07InventorySnapshotLineFactory lineFactory;

    public Dp07InventorySnapshotBatchWriter(
            OfficialWarehouseStatisticsMapper mapper,
            InventorySnapshotRuntimeMapper runtimeMapper,
            ObjectMapper objectMapper,
            SnapshotApplyTargetStore targets
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.runtimeMapper = Objects.requireNonNull(runtimeMapper, "runtimeMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.lineFactory = new Dp07InventorySnapshotLineFactory();
    }

    public int prepare(
            CompleteSnapshot<Dp07InventorySnapshotItem> snapshot,
            List<SnapshotApplyItem<Dp07InventorySnapshotItem>> items
    ) {
        CompleteSnapshot<Dp07InventorySnapshotItem> value = Objects.requireNonNull(
                snapshot, "snapshot"
        );
        if (items == null || items.isEmpty() || items.size() > 20) {
            throw new IllegalArgumentException("DP-07-A preparation chunk is invalid");
        }
        InventorySyncScopeRecord scope = requireScope(value);
        InventorySyncBatchInsertRecord batch = requireBatch(value, scope);
        InventorySnapshotIdBlock ids = new InventorySnapshotIdBlock(items.size());
        runtimeMapper.reserveInventorySnapshotLineIds(ids);
        long nextId = ids.firstId();
        for (SnapshotApplyItem<Dp07InventorySnapshotItem> staged : items) {
            InventorySnapshotLineInsertRecord line = lineFactory.fresh(
                    value, scope, batch, Objects.requireNonNull(staged, "staged item"),
                    nextId
            );
            requireSingleRow(
                    runtimeMapper.insertStagedInventorySnapshotLine(line),
                    "inventory staged line insert"
            );
            nextId = Math.addExact(nextId, 1L);
        }
        return items.size();
    }

    public SnapshotCarryForwardResult carry(
            CompleteSnapshot<Dp07InventorySnapshotItem> snapshot,
            long sourceTaskId,
            SnapshotCarryMode mode,
            String afterStableIdentity,
            int limit
    ) {
        CompleteSnapshot<Dp07InventorySnapshotItem> value = Objects.requireNonNull(
                snapshot, "snapshot"
        );
        if (mode != SnapshotCarryMode.FULL || sourceTaskId < 1L
                || sourceTaskId >= value.getTaskId() || limit < 1 || limit > 20) {
            throw new IllegalArgumentException("DP-07-A inventory carry is invalid");
        }
        InventorySyncScopeRecord scope = requireScope(value);
        InventorySyncBatchInsertRecord batch = requireBatch(value, scope);
        List<InventorySnapshotLineInsertRecord> rows = runtimeMapper.selectInventoryCarryChunk(
                sourceTaskId, value.getTaskId(), afterStableIdentity, limit
        );
        if (rows == null || rows.size() > limit) {
            throw new IllegalStateException("DP-07-A inventory carry chunk is invalid");
        }
        if (rows.isEmpty()) return SnapshotCarryForwardResult.complete();
        InventorySnapshotIdBlock ids = new InventorySnapshotIdBlock(rows.size());
        runtimeMapper.reserveInventorySnapshotLineIds(ids);
        long nextId = ids.firstId();
        String previous = afterStableIdentity;
        for (InventorySnapshotLineInsertRecord source : rows) {
            lineFactory.requireCarrySource(scope, previous, source);
            InventorySnapshotLineInsertRecord target = lineFactory.carried(
                    value, scope, batch, source, nextId
            );
            requireSingleRow(
                    runtimeMapper.insertStagedInventorySnapshotLine(target),
                    "inventory carried line insert"
            );
            previous = source.snapshotStableIdentity;
            nextId = Math.addExact(nextId, 1L);
        }
        return SnapshotCarryForwardResult.advanced(previous, rows.size());
    }

    public void seal(
            CompleteSnapshot<Dp07InventorySnapshotItem> snapshot,
            long effectiveItemCount
    ) {
        CompleteSnapshot<Dp07InventorySnapshotItem> value = Objects.requireNonNull(
                snapshot, "snapshot"
        );
        InventorySyncScopeRecord scope = requireScope(value);
        InventorySyncBatchInsertRecord batch = requireBatch(value, scope);
        requireSingleRow(
                runtimeMapper.markInventorySyncBatchImported(
                        batch.id, value.getOwnerUserId(), effectiveItemCount
                ),
                "inventory batch seal"
        );
    }

    private InventorySyncBatchInsertRecord requireBatch(
            CompleteSnapshot<Dp07InventorySnapshotItem> snapshot,
            InventorySyncScopeRecord scope
    ) {
        long batchId = targets.resolve(
                snapshot, TARGET_TYPE, () -> requireId(mapper.nextInventorySyncBatchId())
        );
        InventorySyncBatchInsertRecord existing =
                runtimeMapper.selectInventorySyncBatchForUpdate(batchId);
        if (existing == null) {
            InventorySyncBatchInsertRecord created = batch(snapshot, scope, batchId);
            requireSingleRow(mapper.insertInventorySyncBatch(created), "inventory staging batch insert");
            return created;
        }
        if (!Objects.equals(existing.id, batchId)
                || !Objects.equals(existing.ownerUserId, snapshot.getOwnerUserId())
                || !Objects.equals(existing.logicalStoreId, snapshot.getLogicalStoreId())
                || !same(existing.storeCode, snapshot.getStoreCode())
                || !sameIgnoreCase(existing.siteCode, snapshot.getSiteCode())
                || !same(existing.projectCode, snapshot.getProjectCode())
                || !SOURCE_TYPE.equals(existing.sourceType)
                || !"STAGING".equals(existing.status)
                || !Objects.equals(existing.totalPages, snapshot.getLastPage())
                || !Objects.equals(existing.totalRows, Math.toIntExact(snapshot.getAppliedItemCount()))
                || !Objects.equals(existing.validRows, Math.toIntExact(snapshot.getAppliedItemCount()))
                || !Objects.equals(existing.errorRows,
                        Math.toIntExact(snapshot.getBusinessSkippedItemCount()))) {
            throw new IllegalStateException("DP-07-A staging batch identity drift");
        }
        return existing;
    }

    private InventorySyncBatchInsertRecord batch(
            CompleteSnapshot<Dp07InventorySnapshotItem> snapshot,
            InventorySyncScopeRecord scope,
            long batchId
    ) {
        InventorySyncBatchInsertRecord row = new InventorySyncBatchInsertRecord();
        row.id = batchId;
        row.ownerUserId = snapshot.getOwnerUserId();
        row.logicalStoreId = scope.logicalStoreId;
        row.storeCode = snapshot.getStoreCode();
        row.siteCode = snapshot.getSiteCode();
        row.projectCode = snapshot.getProjectCode();
        row.partnerId = firstNonBlank(scope.partnerId, derivePartnerId(row.projectCode));
        row.sourceType = SOURCE_TYPE;
        row.requestSummaryJson = summary(snapshot, "PREPARED");
        row.responseSummaryJson = summary(snapshot, "SEALED_ONLY_AFTER_ALL_ROWS");
        row.status = "STAGING";
        row.totalPages = snapshot.getLastPage();
        row.totalRows = Math.toIntExact(snapshot.getAppliedItemCount());
        row.validRows = row.totalRows;
        row.errorRows = Math.toIntExact(snapshot.getBusinessSkippedItemCount());
        row.operatorUserId = snapshot.getOwnerUserId();
        return row;
    }

    private InventorySyncScopeRecord requireScope(CompleteSnapshot<?> snapshot) {
        InventorySyncScopeRecord scope = mapper.selectInventorySyncScope(
                snapshot.getOwnerUserId(), snapshot.getStoreCode(), snapshot.getSiteCode()
        );
        if (scope == null
                || !Objects.equals(scope.ownerUserId, snapshot.getOwnerUserId())
                || !Objects.equals(scope.logicalStoreId, snapshot.getLogicalStoreId())
                || !same(scope.storeCode, snapshot.getStoreCode())
                || !sameIgnoreCase(scope.siteCode, snapshot.getSiteCode())
                || !same(scope.projectCode, snapshot.getProjectCode())) {
            throw new IllegalStateException("DP-07-A inventory scope changed before apply");
        }
        return scope;
    }

    private String summary(CompleteSnapshot<?> snapshot, String state) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("source_type", SOURCE_TYPE);
            root.put("task_id", snapshot.getTaskId());
            root.put("generation_sha256", snapshot.getAuthority().getGenerationTokenSha256());
            root.put("row_count", snapshot.getAppliedItemCount());
            root.put("state", state);
            return objectMapper.writeValueAsString(root);
        } catch (Exception failure) {
            throw new IllegalStateException("DP-07-A batch summary cannot be encoded", failure);
        }
    }

    private long requireId(Long value) {
        if (value == null || value < 1L) throw new IllegalStateException("inventory id is invalid");
        return value;
    }
    private String derivePartnerId(String value) {
        String text = trimToNull(value);
        return text != null && text.toUpperCase(Locale.ROOT).startsWith("PRJ")
                ? text.substring(3) : text;
    }
    private String firstNonBlank(String... values) {
        for (String value : values) if (trimToNull(value) != null) return trimToNull(value);
        return null;
    }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private boolean same(String left, String right) { return Objects.equals(trimToNull(left), trimToNull(right)); }
    private boolean sameIgnoreCase(String left, String right) {
        String a = trimToNull(left); String b = trimToNull(right);
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
    private void requireSingleRow(int changed, String action) {
        if (changed != 1) throw new IllegalStateException(action + " must affect exactly one row");
    }
}
