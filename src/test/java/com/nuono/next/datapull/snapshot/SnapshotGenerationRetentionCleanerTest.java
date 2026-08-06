package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InventorySnapshotRuntimeMapper;
import com.nuono.next.infrastructure.mapper.SnapshotStageRetentionMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassRetentionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SnapshotGenerationRetentionCleanerTest {

    @Test
    void removesTwoPassChildrenBeforeTheirStageRows() {
        SnapshotStageRetentionMapper stage = mock(SnapshotStageRetentionMapper.class);
        InventorySnapshotRuntimeMapper inventory = mock(InventorySnapshotRuntimeMapper.class);
        SnapshotTwoPassRetentionMapper twoPass = mock(SnapshotTwoPassRetentionMapper.class);
        SnapshotGenerationRetentionCleaner cleaner =
                new SnapshotGenerationRetentionCleaner(stage, inventory, twoPass);
        LocalDateTime cutoff = LocalDateTime.parse("2026-08-03T00:00:00");

        cleaner.run(Instant.parse("2026-08-10T00:00:00Z"));

        InOrder order = inOrder(twoPass, stage, inventory);
        order.verify(twoPass).deleteSupersededVerifyPages(cutoff, 20);
        order.verify(twoPass).deleteAbandonedVerifyPages(cutoff, 20);
        order.verify(twoPass).deleteSupersededFingerprintCounts(cutoff, 100);
        order.verify(twoPass).deleteAbandonedFingerprintCounts(cutoff, 100);
        order.verify(stage).deleteSupersededEffectiveItemsBatch(cutoff, 100);
    }

    @Test
    void retiresOnlyBoundedTerminalRowsInForeignKeyOrderAtTheMinuteCadence() {
        SnapshotStageRetentionMapper stage = mock(SnapshotStageRetentionMapper.class);
        InventorySnapshotRuntimeMapper inventory = mock(InventorySnapshotRuntimeMapper.class);
        when(stage.deleteSupersededEffectiveItemsBatch(any(), anyInt())).thenReturn(100);
        when(stage.deleteAbandonedEffectiveItemsBatch(any(), anyInt())).thenReturn(100);
        when(stage.deleteSupersededItemsBatch(any(), anyInt())).thenReturn(100);
        when(stage.deleteAbandonedItemsBatch(any(), anyInt())).thenReturn(100);
        when(stage.deleteSupersededPagesBatch(any(), anyInt())).thenReturn(20);
        when(stage.deleteAbandonedPagesBatch(any(), anyInt())).thenReturn(20);
        when(stage.deleteSupersededStagesBatch(any(), anyInt())).thenReturn(1);
        when(stage.deleteAbandonedStagesBatch(any(), anyInt())).thenReturn(1);
        when(inventory.retireSupersededInventoryLinesBatch(any(), anyInt()))
                .thenReturn(100);
        when(inventory.retireAbandonedInventoryLinesBatch(any(), anyInt()))
                .thenReturn(100);
        when(inventory.retireSupersededInventoryBatchesBatch(any(), anyInt()))
                .thenReturn(1);
        when(inventory.retireAbandonedInventoryBatchesBatch(any(), anyInt()))
                .thenReturn(1);
        SnapshotGenerationRetentionCleaner cleaner =
                new SnapshotGenerationRetentionCleaner(stage, inventory);
        Instant now = Instant.parse("2026-08-10T00:00:00Z");

        cleaner.run(now);
        cleaner.run(now.plusSeconds(59));

        LocalDateTime cutoff = LocalDateTime.parse("2026-08-03T00:00:00");
        InOrder order = inOrder(stage, inventory);
        order.verify(stage).deleteSupersededEffectiveItemsBatch(cutoff, 100);
        order.verify(stage).deleteAbandonedEffectiveItemsBatch(cutoff, 100);
        order.verify(stage).deleteSupersededItemsBatch(cutoff, 100);
        order.verify(stage).deleteAbandonedItemsBatch(cutoff, 100);
        order.verify(stage).deleteSupersededPagesBatch(cutoff, 20);
        order.verify(stage).deleteAbandonedPagesBatch(cutoff, 20);
        order.verify(stage).deleteSupersededStagesBatch(cutoff, 1);
        order.verify(stage).deleteAbandonedStagesBatch(cutoff, 1);
        order.verify(inventory).retireSupersededInventoryLinesBatch(cutoff, 100);
        order.verify(inventory).retireAbandonedInventoryLinesBatch(cutoff, 100);
        order.verify(inventory).retireSupersededInventoryBatchesBatch(cutoff, 1);
        order.verify(inventory).retireAbandonedInventoryBatchesBatch(cutoff, 1);

        cleaner.run(now.plusSeconds(60));
        verify(stage, times(2)).deleteSupersededItemsBatch(any(), anyInt());
    }

    @Test
    void rejectsAnyMapperCountAboveItsDeclaredBound() {
        SnapshotStageRetentionMapper stage = mock(SnapshotStageRetentionMapper.class);
        InventorySnapshotRuntimeMapper inventory = mock(InventorySnapshotRuntimeMapper.class);
        when(stage.deleteSupersededItemsBatch(any(), anyInt())).thenReturn(101);
        SnapshotGenerationRetentionCleaner cleaner =
                new SnapshotGenerationRetentionCleaner(stage, inventory);

        assertThatThrownBy(() -> cleaner.run(Instant.parse("2026-08-10T00:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SNAPSHOT_RETENTION_DELETE_COUNT_INVALID");
    }
}
