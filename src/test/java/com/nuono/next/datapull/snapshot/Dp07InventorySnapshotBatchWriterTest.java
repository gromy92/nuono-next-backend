package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.InventorySnapshotRuntimeMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotBatchWriter;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotCodec;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotItem;
import com.nuono.next.officialwarehouse.datapull.InventorySnapshotIdBlock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Dp07InventorySnapshotBatchWriterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OfficialWarehouseStatisticsMapper warehouse;
    private InventorySnapshotRuntimeMapper runtime;
    private SnapshotFactApplyMapper apply;

    @BeforeEach
    void setUp() {
        warehouse = mock(OfficialWarehouseStatisticsMapper.class);
        runtime = mock(InventorySnapshotRuntimeMapper.class);
        apply = mock(SnapshotFactApplyMapper.class);
        when(warehouse.selectInventorySyncScope(307L, "STR108065-NSA", "SA"))
                .thenReturn(scope());
    }

    @Test
    void preparesOneChunkWithOneExactContiguousIdReservation() {
        CompleteSnapshot<Dp07InventorySnapshotItem> snapshot = snapshot(2L);
        bindNewBatch(snapshot, 7001L);
        when(warehouse.nextInventorySyncBatchId()).thenReturn(7001L);
        when(warehouse.insertInventorySyncBatch(any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<InventorySnapshotIdBlock>getArgument(0).setLastId(9002L);
            return null;
        }).when(runtime).reserveInventorySnapshotLineIds(any());
        when(runtime.insertStagedInventorySnapshotLine(any())).thenReturn(1);

        writer().prepare(snapshot, List.of(applyItem(0, 7), applyItem(1, 9)));

        ArgumentCaptor<InventorySnapshotIdBlock> block =
                ArgumentCaptor.forClass(InventorySnapshotIdBlock.class);
        verify(runtime).reserveInventorySnapshotLineIds(block.capture());
        assertThat(block.getValue().getBlockSize()).isEqualTo(2);
        assertThat(block.getValue().firstId()).isEqualTo(9001L);
        ArgumentCaptor<InventorySnapshotLineInsertRecord> rows =
                ArgumentCaptor.forClass(InventorySnapshotLineInsertRecord.class);
        verify(runtime, org.mockito.Mockito.times(2))
                .insertStagedInventorySnapshotLine(rows.capture());
        assertThat(rows.getAllValues()).extracting(row -> row.id)
                .containsExactly(9001L, 9002L);
        assertThat(rows.getAllValues()).extracting(row -> row.syncBatchId)
                .containsOnly(7001L);
        assertThat(rows.getAllValues()).extracting(row -> row.snapshotStableIdentity)
                .containsExactly("inventory-0", "inventory-1");
        assertThat(rows.getAllValues()).extracting(row -> row.productSiteOfferId)
                .containsOnlyNulls();
        verify(warehouse, never()).findInventoryLineProductMatch(
                anyLong(), any(), any(), any(), any()
        );
        verify(warehouse, never()).nextInventorySnapshotLineId();
    }

    @Test
    void rowFailureEscapesTheGuardTransactionBeforeProgressCanAdvance() {
        CompleteSnapshot<Dp07InventorySnapshotItem> snapshot = snapshot(2L);
        SnapshotApplyProgressRow guardProgress = progress(snapshot, null);
        guardProgress.setCursorPageNo(0);
        guardProgress.setCursorItemOrdinal(-1);
        guardProgress.setPreparedItemCount(0L);
        guardProgress.setAbsenceUnsafeItemCount(0L);
        when(apply.selectTaskForUpdate(snapshot.getTaskId())).thenReturn(liveTask(snapshot));
        when(apply.insertProgressIfAbsent(any(), any())).thenReturn(0);
        when(apply.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(
                guardProgress, progress(snapshot, null), progress(snapshot, 7001L)
        );
        Dp07InventorySnapshotCodec codec = new Dp07InventorySnapshotCodec(objectMapper);
        when(apply.selectCanonicalChunk(snapshot.getTaskId(), 0, -1, 20)).thenReturn(List.of(
                stagedRow(snapshot, codec, 0, 7), stagedRow(snapshot, codec, 1, 9)
        ));
        when(apply.bindTargetRef(anyLong(), anyLong(), any(), anyLong(), any())).thenReturn(1);
        when(warehouse.nextInventorySyncBatchId()).thenReturn(7001L);
        when(warehouse.insertInventorySyncBatch(any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<InventorySnapshotIdBlock>getArgument(0).setLastId(9002L);
            return null;
        }).when(runtime).reserveInventorySnapshotLineIds(any());
        when(runtime.insertStagedInventorySnapshotLine(any()))
                .thenReturn(1)
                .thenThrow(new IllegalStateException("insert failed"));

        SnapshotFactApplyGuard guard = new SnapshotFactApplyGuard(
                apply,
                mock(SnapshotCarryProgressMapper.class),
                Clock.fixed(Instant.parse("2026-08-03T06:31:00Z"), ZoneOffset.UTC)
        );
        assertThatThrownBy(() -> guard.advance(
                snapshot, codec, codec, rows -> writer().prepare(snapshot, rows),
                (source, mode, cursor, limit) -> SnapshotCarryForwardResult.complete(),
                ignored -> { }
        )).isInstanceOf(IllegalStateException.class).hasMessage("insert failed");
        verify(apply, never()).advanceProgress(any(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void sealOnlyMarksTheBoundBatchAndNeverFlipsLegacyCurrentRows() {
        CompleteSnapshot<Dp07InventorySnapshotItem> snapshot = snapshot(2L);
        SnapshotApplyProgressRow progress = progress(snapshot, 7001L);
        when(apply.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(progress);
        when(runtime.selectInventorySyncBatchForUpdate(7001L)).thenReturn(batch(snapshot, 7001L));
        when(runtime.markInventorySyncBatchImported(7001L, 307L, 2L)).thenReturn(1);

        writer().seal(snapshot, 2L);

        verify(runtime).markInventorySyncBatchImported(7001L, 307L, 2L);
        verify(warehouse, never()).deactivateCurrentInventorySnapshotLines(anyLong(), any(), any());
        verify(warehouse, never()).insertInventorySnapshotLine(any());
    }

    private Dp07InventorySnapshotBatchWriter writer() {
        return new Dp07InventorySnapshotBatchWriter(
                warehouse, runtime, objectMapper, new SnapshotApplyTargetStore(apply)
        );
    }

    private void bindNewBatch(CompleteSnapshot<?> snapshot, long batchId) {
        SnapshotApplyProgressRow unbound = progress(snapshot, null);
        SnapshotApplyProgressRow bound = progress(snapshot, batchId);
        when(apply.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(unbound, bound);
        when(apply.bindTargetRef(
                org.mockito.ArgumentMatchers.eq(snapshot.getTaskId()),
                org.mockito.ArgumentMatchers.eq(snapshot.getFenceEpoch()),
                org.mockito.ArgumentMatchers.eq("OFFICIAL_WAREHOUSE_INVENTORY_BATCH"),
                org.mockito.ArgumentMatchers.eq(batchId), any()
        )).thenReturn(1);
    }

    private SnapshotApplyProgressRow progress(CompleteSnapshot<?> snapshot, Long batchId) {
        SnapshotApplyProgressRow row = new SnapshotApplyProgressRow();
        row.setTaskId(snapshot.getTaskId());
        row.setActiveFenceEpoch(snapshot.getFenceEpoch());
        row.setState("PREPARING");
        row.setEffectiveItemCount(0L);
        row.setCarryMode(SnapshotCarryMode.NONE);
        if (batchId != null) {
            row.setTargetRefType("OFFICIAL_WAREHOUSE_INVENTORY_BATCH");
            row.setTargetRefId(batchId);
        }
        return row;
    }

    private InventorySyncScopeRecord scope() {
        InventorySyncScopeRecord row = new InventorySyncScopeRecord();
        row.ownerUserId = 307L;
        row.logicalStoreId = 108065L;
        row.storeCode = "STR108065-NSA";
        row.siteCode = "SA";
        row.projectCode = "PRJ108065";
        row.partnerId = "108065";
        return row;
    }

    private InventorySyncBatchInsertRecord batch(
            CompleteSnapshot<?> snapshot,
            long batchId
    ) {
        InventorySyncBatchInsertRecord row = new InventorySyncBatchInsertRecord();
        row.id = batchId;
        row.ownerUserId = snapshot.getOwnerUserId();
        row.logicalStoreId = snapshot.getLogicalStoreId();
        row.storeCode = snapshot.getStoreCode();
        row.siteCode = snapshot.getSiteCode();
        row.projectCode = snapshot.getProjectCode();
        row.sourceType = "FBN_INVENTORY_API";
        row.status = "STAGING";
        row.totalPages = snapshot.getLastPage();
        row.totalRows = Math.toIntExact(snapshot.getAppliedItemCount());
        row.validRows = row.totalRows;
        row.errorRows = Math.toIntExact(snapshot.getBusinessSkippedItemCount());
        return row;
    }

    private SnapshotApplyItem<Dp07InventorySnapshotItem> applyItem(int ordinal, int quantity) {
        SnapshotStageItemRow row = new SnapshotStageItemRow();
        row.setPageNo(1);
        row.setItemOrdinal(ordinal);
        row.setStableIdentity("inventory-" + ordinal);
        row.setContentFingerprint("a".repeat(64));
        row.setValidatedIdentityCandidate(true);
        row.setAbsenceReconciliationSafe(true);
        return new SnapshotApplyItem<>(row, item(ordinal, quantity));
    }

    private SnapshotStageItemRow stagedRow(
            CompleteSnapshot<?> snapshot,
            Dp07InventorySnapshotCodec codec,
            int ordinal,
            int quantity
    ) {
        Dp07InventorySnapshotItem item = item(ordinal, quantity);
        SnapshotStageItemRow row = new SnapshotStageItemRow();
        row.setTaskId(snapshot.getTaskId());
        row.setPageNo(1);
        row.setItemOrdinal(ordinal);
        row.setStableIdentity(codec.stableIdentity(item));
        row.setContentFingerprint(codec.stableContentFingerprint(item));
        row.setPayload(codec.encode(item));
        row.setValidatedIdentityCandidate(true);
        row.setAbsenceReconciliationSafe(true);
        return row;
    }

    private SnapshotApplyTaskRow liveTask(CompleteSnapshot<?> snapshot) {
        SnapshotApplyTaskRow row = new SnapshotApplyTaskRow();
        row.setTaskId(snapshot.getTaskId());
        row.setOperationCode(snapshot.getOperationCode());
        row.setScopeKey(snapshot.getScopeKey());
        row.setBusinessWindowKey(snapshot.getBusinessWindowKey());
        row.setFenceEpoch(snapshot.getFenceEpoch());
        row.setState("RUNNING");
        row.setLeaseOwner(snapshot.getLeaseOwner());
        row.setLeaseUntil(LocalDateTime.of(2026, 8, 3, 6, 40));
        return row;
    }

    private Dp07InventorySnapshotItem item(int ordinal, int quantity) {
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("warehouse_code", "RUH0" + ordinal);
        raw.put("qty", quantity);
        raw.put("inventory_type", "saleable");
        raw.put("partner_sku", "PAPERSAY-" + ordinal);
        raw.put("sku", "N-" + ordinal);
        InventoryItem row = InventoryItem.from(raw);
        return Dp07InventorySnapshotItem.fromProvider(row, objectMapper).orElseThrow();
    }

    private CompleteSnapshot<Dp07InventorySnapshotItem> snapshot(long itemCount) {
        DataPullTask task = DataPullTask.queued(
                4007L, OperationCode.DP07A, "NOON_FBN_INVENTORY", 307L, 108065L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA", "scope-7",
                LocalDateTime.of(2026, 8, 3, 6, 30), "snapshot:2026-08-03",
                "SNAPSHOT_APPLY", LocalDateTime.of(2026, 8, 3, 6, 30)
        );
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(5L);
        task.setLeaseOwner("worker-7");
        task.setLeaseUntil(LocalDateTime.of(2026, 8, 3, 6, 40));
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "dp07-generation", LocalDateTime.of(2026, 8, 3, 6, 29), itemCount
        );
        return CompleteSnapshot.from(task, SnapshotStageProof.completeMetadata(
                1, itemCount, 0, 0L, itemCount, authority
        ));
    }
}
