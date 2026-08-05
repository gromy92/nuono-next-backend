package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.InventorySnapshotRuntimeMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotBatchWriter;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotItem;
import com.nuono.next.officialwarehouse.datapull.InventorySnapshotIdBlock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Dp07InventorySnapshotCarryTest {

    @Test
    void copiesOnlyRawProviderFactsInOneBoundedIdentitySlice() {
        OfficialWarehouseStatisticsMapper warehouse = mock(
                OfficialWarehouseStatisticsMapper.class
        );
        InventorySnapshotRuntimeMapper runtime = mock(InventorySnapshotRuntimeMapper.class);
        SnapshotFactApplyMapper apply = mock(SnapshotFactApplyMapper.class);
        CompleteSnapshot<Dp07InventorySnapshotItem> snapshot = snapshot();
        InventorySyncScopeRecord scope = scope();
        when(warehouse.selectInventorySyncScope(307L, "STR108065-NSA", "SA"))
                .thenReturn(scope);
        when(apply.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(progress(snapshot));
        when(runtime.selectInventorySyncBatchForUpdate(7001L)).thenReturn(batch(snapshot));
        InventorySnapshotLineInsertRecord source = source(scope);
        when(runtime.selectInventoryCarryChunk(4006L, 4007L, null, 20))
                .thenReturn(List.of(source));
        when(runtime.selectInventoryCarryChunk(4006L, 4007L, "inventory:old", 20))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<InventorySnapshotIdBlock>getArgument(0).setLastId(9001L);
            return null;
        }).when(runtime).reserveInventorySnapshotLineIds(any());
        when(runtime.insertStagedInventorySnapshotLine(any())).thenReturn(1);
        Dp07InventorySnapshotBatchWriter writer = new Dp07InventorySnapshotBatchWriter(
                warehouse, runtime, new ObjectMapper(), new SnapshotApplyTargetStore(apply)
        );

        SnapshotCarryForwardResult first = writer.carry(
                snapshot, 4006L, SnapshotCarryMode.FULL, null, 20
        );
        SnapshotCarryForwardResult done = writer.carry(
                snapshot, 4006L, SnapshotCarryMode.FULL,
                first.getLastStableIdentity(), 20
        );

        assertThat(first.getMaterializedItemCount()).isEqualTo(1);
        assertThat(first.getLastStableIdentity()).isEqualTo("inventory:old");
        assertThat(done.isComplete()).isTrue();
        ArgumentCaptor<InventorySnapshotLineInsertRecord> inserted =
                ArgumentCaptor.forClass(InventorySnapshotLineInsertRecord.class);
        verify(runtime).insertStagedInventorySnapshotLine(inserted.capture());
        assertThat(inserted.getValue().id).isEqualTo(9001L);
        assertThat(inserted.getValue().syncBatchId).isEqualTo(7001L);
        assertThat(inserted.getValue().snapshotStableIdentity).isEqualTo("inventory:old");
        assertThat(inserted.getValue().partnerSku).isEqualTo("PAPERSAY-OLD");
        assertThat(inserted.getValue().quantity).isEqualTo(7);
        assertThat(inserted.getValue().productMasterId).isNull();
        assertThat(inserted.getValue().productVariantId).isNull();
        assertThat(inserted.getValue().productSiteOfferId).isNull();
        assertThat(inserted.getValue().pskuCode).isNull();
        assertThat(inserted.getValue().matchStatus).isEqualTo("RAW_PROVIDER_FACT");
        verify(warehouse, never()).findInventoryLineProductMatch(
                anyLong(), any(), any(), any(), any()
        );
    }

    private SnapshotApplyProgressRow progress(CompleteSnapshot<?> snapshot) {
        SnapshotApplyProgressRow row = new SnapshotApplyProgressRow();
        row.setTaskId(snapshot.getTaskId());
        row.setActiveFenceEpoch(snapshot.getFenceEpoch());
        row.setTargetRefType("OFFICIAL_WAREHOUSE_INVENTORY_BATCH");
        row.setTargetRefId(7001L);
        row.setState("CARRYING");
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

    private InventorySyncBatchInsertRecord batch(CompleteSnapshot<?> snapshot) {
        InventorySyncBatchInsertRecord row = new InventorySyncBatchInsertRecord();
        row.id = 7001L;
        row.ownerUserId = snapshot.getOwnerUserId();
        row.logicalStoreId = snapshot.getLogicalStoreId();
        row.storeCode = snapshot.getStoreCode();
        row.siteCode = snapshot.getSiteCode();
        row.projectCode = snapshot.getProjectCode();
        row.sourceType = "FBN_INVENTORY_API";
        row.status = "STAGING";
        row.totalPages = 1;
        row.totalRows = 1;
        row.validRows = 1;
        row.errorRows = 0;
        return row;
    }

    private InventorySnapshotLineInsertRecord source(InventorySyncScopeRecord scope) {
        InventorySnapshotLineInsertRecord row = new InventorySnapshotLineInsertRecord();
        row.snapshotStableIdentity = "inventory:old";
        row.ownerUserId = scope.ownerUserId;
        row.logicalStoreId = scope.logicalStoreId;
        row.storeCode = scope.storeCode;
        row.siteCode = scope.siteCode;
        row.projectCode = scope.projectCode;
        row.partnerSku = "PAPERSAY-OLD";
        row.noonSku = "N-OLD";
        row.warehouseCode = "RUH01";
        row.stockBucket = "SELLABLE";
        row.quantity = 7;
        row.rawPayloadJson = "{\"qty\":7}";
        return row;
    }

    private CompleteSnapshot<Dp07InventorySnapshotItem> snapshot() {
        DataPullTask task = DataPullTask.queued(
                4007L, OperationCode.DP07A, "NOON_FBN_INVENTORY", 307L, 108065L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA", "scope-7",
                LocalDateTime.of(2026, 8, 3, 6, 30), "snapshot:2026-08-03",
                "SNAPSHOT_APPLY", LocalDateTime.of(2026, 8, 3, 6, 30)
        );
        task.setFenceEpoch(5L);
        task.setLeaseOwner("worker-7");
        task.setLeaseUntil(LocalDateTime.of(2026, 8, 3, 6, 40));
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "dp07-generation", LocalDateTime.of(2026, 8, 3, 6, 29), 1L
        );
        return CompleteSnapshot.from(task, SnapshotStageProof.completeMetadata(
                1, 1L, 0, 0L, 1L, authority
        ));
    }
}
