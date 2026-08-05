package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import java.util.Objects;

/** One bounded reset transaction; the caller owns the transaction boundary. */
final class SnapshotStageResetter {
    private static final int ITEM_BATCH_SIZE = 200;
    private static final int PAGE_BATCH_SIZE = 50;

    private final CompleteSnapshotStageMapper mapper;
    private final SnapshotTwoPassMapper twoPassMapper;

    SnapshotStageResetter(CompleteSnapshotStageMapper mapper) {
        this(mapper, null);
    }

    SnapshotStageResetter(
            CompleteSnapshotStageMapper mapper,
            SnapshotTwoPassMapper twoPassMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.twoPassMapper = twoPassMapper;
    }

    SnapshotStageClearResult clear(long taskId, long fenceEpoch) {
        SnapshotStageTaskRow task = mapper.selectTaskForUpdate(taskId);
        if (!ownsRunningFence(task, taskId, fenceEpoch)) {
            return SnapshotStageClearResult.STALE_FENCE;
        }
        SnapshotStageAggregateRow aggregate = mapper.selectAggregateForUpdate(taskId);
        if (aggregate == null) {
            return SnapshotStageClearResult.CLEARED;
        }
        if (!validAggregate(aggregate, taskId)) {
            throw new IllegalStateException("snapshot stage aggregate row is invalid");
        }
        if (aggregate.getActiveFenceEpoch() > fenceEpoch) {
            return SnapshotStageClearResult.STALE_FENCE;
        }
        if (aggregate.getActiveFenceEpoch() < fenceEpoch) {
            if (mapper.adoptFence(taskId, fenceEpoch) != 1) {
                throw new IllegalStateException("snapshot stage reset fence adoption failed");
            }
            aggregate.setActiveFenceEpoch(fenceEpoch);
        }

        if (twoPassMapper != null) {
            int deletedVerifyPages = twoPassMapper.deleteVerifyPagesBounded(
                    taskId, PAGE_BATCH_SIZE
            );
            requireBounded(deletedVerifyPages, PAGE_BATCH_SIZE, "snapshot verify page reset");
            if (deletedVerifyPages == PAGE_BATCH_SIZE) {
                return SnapshotStageClearResult.MORE_WORK;
            }
            int deletedCounts = twoPassMapper.deleteFingerprintCountsBounded(
                    taskId, ITEM_BATCH_SIZE
            );
            requireBounded(deletedCounts, ITEM_BATCH_SIZE, "snapshot fingerprint reset");
            if (deletedCounts == ITEM_BATCH_SIZE) {
                return SnapshotStageClearResult.MORE_WORK;
            }
        }

        int deletedItems = mapper.deleteStageItemsBounded(taskId, ITEM_BATCH_SIZE);
        requireBounded(deletedItems, ITEM_BATCH_SIZE, "snapshot stage item reset");
        if (deletedItems == ITEM_BATCH_SIZE) {
            return SnapshotStageClearResult.MORE_WORK;
        }

        int deletedPages = mapper.deleteEmptyStagePagesBounded(taskId, PAGE_BATCH_SIZE);
        requireBounded(deletedPages, PAGE_BATCH_SIZE, "snapshot stage page reset");
        if (deletedPages == PAGE_BATCH_SIZE) {
            return SnapshotStageClearResult.MORE_WORK;
        }

        int deletedAggregate = mapper.deleteAggregate(taskId, fenceEpoch);
        if (deletedAggregate == 1) {
            return SnapshotStageClearResult.CLEARED;
        }
        if (deletedAggregate != 0) {
            throw new IllegalStateException("snapshot stage aggregate reset row count drift");
        }
        return SnapshotStageClearResult.APPLY_ALREADY_STARTED;
    }

    private boolean ownsRunningFence(
            SnapshotStageTaskRow task,
            long taskId,
            long fenceEpoch
    ) {
        if (task == null) {
            return false;
        }
        if (!Objects.equals(task.getTaskId(), taskId)
                || task.getFenceEpoch() == null
                || task.getFenceEpoch() < 1L
                || task.getState() == null
                || task.getLeaseValid() == null) {
            throw new IllegalStateException("snapshot task fence row is invalid");
        }
        return task.getFenceEpoch() == fenceEpoch
                && "RUNNING".equals(task.getState())
                && Boolean.TRUE.equals(task.getLeaseValid());
    }

    private boolean validAggregate(SnapshotStageAggregateRow aggregate, long taskId) {
        return Objects.equals(aggregate.getTaskId(), taskId)
                && aggregate.getActiveFenceEpoch() != null
                && aggregate.getActiveFenceEpoch() >= 1L;
    }

    private void requireBounded(int changed, int limit, String action) {
        if (changed < 0 || changed > limit) {
            throw new IllegalStateException(action + " affected an invalid row count: " + changed);
        }
    }
}
