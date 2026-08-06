package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeMaintenance;
import com.nuono.next.infrastructure.mapper.InventorySnapshotRuntimeMapper;
import com.nuono.next.infrastructure.mapper.SnapshotStageRetentionMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassRetentionMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Bounded retention for superseded and quiescent abandoned DP-04/07-A generations. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class SnapshotGenerationRetentionCleaner implements DataPullRuntimeMaintenance {
    static final int ITEM_BATCH_SIZE = 100;
    static final int PAGE_BATCH_SIZE = 20;
    static final int STAGE_BATCH_SIZE = 1;
    static final Duration TERMINAL_GRACE = Duration.ofDays(7);
    static final Duration RUN_INTERVAL = Duration.ofMinutes(1);

    private final SnapshotStageRetentionMapper mapper;
    private final InventorySnapshotRuntimeMapper inventory;
    private final SnapshotTwoPassRetentionMapper twoPass;
    private Instant nextRunUtc = Instant.MIN;

    @Autowired
    public SnapshotGenerationRetentionCleaner(
            SnapshotStageRetentionMapper mapper,
            InventorySnapshotRuntimeMapper inventory,
            SnapshotTwoPassRetentionMapper twoPass
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.twoPass = Objects.requireNonNull(twoPass, "twoPass");
    }

    SnapshotGenerationRetentionCleaner(
            SnapshotStageRetentionMapper mapper,
            InventorySnapshotRuntimeMapper inventory
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.twoPass = null;
    }

    @Override
    public synchronized void run(Instant nowUtc) {
        Instant now = Objects.requireNonNull(nowUtc, "nowUtc");
        if (now.isBefore(nextRunUtc)) {
            return;
        }
        LocalDateTime cutoffUtc = LocalDateTime.ofInstant(
                now.minus(TERMINAL_GRACE), ZoneOffset.UTC
        );
        if (twoPass != null) {
            requireBounded(twoPass.deleteSupersededVerifyPages(
                    cutoffUtc, PAGE_BATCH_SIZE
            ), PAGE_BATCH_SIZE);
            requireBounded(twoPass.deleteAbandonedVerifyPages(
                    cutoffUtc, PAGE_BATCH_SIZE
            ), PAGE_BATCH_SIZE);
            requireBounded(twoPass.deleteSupersededFingerprintCounts(
                    cutoffUtc, ITEM_BATCH_SIZE
            ), ITEM_BATCH_SIZE);
            requireBounded(twoPass.deleteAbandonedFingerprintCounts(
                    cutoffUtc, ITEM_BATCH_SIZE
            ), ITEM_BATCH_SIZE);
        }
        requireBounded(
                mapper.deleteSupersededEffectiveItemsBatch(cutoffUtc, ITEM_BATCH_SIZE),
                ITEM_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteAbandonedEffectiveItemsBatch(cutoffUtc, ITEM_BATCH_SIZE),
                ITEM_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteSupersededItemsBatch(cutoffUtc, ITEM_BATCH_SIZE),
                ITEM_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteAbandonedItemsBatch(cutoffUtc, ITEM_BATCH_SIZE),
                ITEM_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteSupersededPagesBatch(cutoffUtc, PAGE_BATCH_SIZE),
                PAGE_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteAbandonedPagesBatch(cutoffUtc, PAGE_BATCH_SIZE),
                PAGE_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteSupersededStagesBatch(cutoffUtc, STAGE_BATCH_SIZE),
                STAGE_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteAbandonedStagesBatch(cutoffUtc, STAGE_BATCH_SIZE),
                STAGE_BATCH_SIZE
        );
        requireBounded(
                inventory.retireSupersededInventoryLinesBatch(cutoffUtc, ITEM_BATCH_SIZE),
                ITEM_BATCH_SIZE
        );
        requireBounded(
                inventory.retireAbandonedInventoryLinesBatch(cutoffUtc, ITEM_BATCH_SIZE),
                ITEM_BATCH_SIZE
        );
        requireBounded(
                inventory.retireSupersededInventoryBatchesBatch(cutoffUtc, STAGE_BATCH_SIZE),
                STAGE_BATCH_SIZE
        );
        requireBounded(
                inventory.retireAbandonedInventoryBatchesBatch(cutoffUtc, STAGE_BATCH_SIZE),
                STAGE_BATCH_SIZE
        );
        nextRunUtc = now.plus(RUN_INTERVAL);
    }

    private void requireBounded(int deleted, int bound) {
        if (deleted < 0 || deleted > bound) {
            throw new IllegalStateException("SNAPSHOT_RETENTION_DELETE_COUNT_INVALID");
        }
    }
}
