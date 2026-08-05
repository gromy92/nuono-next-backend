package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import java.util.Objects;

/** Shared task/aggregate fence rules for snapshot stage transactions. */
final class SnapshotStageFence {
    private final CompleteSnapshotStageMapper mapper;

    SnapshotStageFence(CompleteSnapshotStageMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    boolean ownsRunningTask(long taskId, long fenceEpoch) {
        SnapshotStageTaskRow task = mapper.selectTaskForUpdate(taskId);
        if (task == null) return false;
        if (!Objects.equals(task.getTaskId(), taskId)
                || task.getFenceEpoch() == null || task.getFenceEpoch() < 1L
                || task.getState() == null || task.getLeaseValid() == null) {
            throw new IllegalStateException("snapshot task fence row is invalid");
        }
        return task.getFenceEpoch() == fenceEpoch
                && "RUNNING".equals(task.getState())
                && Boolean.TRUE.equals(task.getLeaseValid());
    }

    SnapshotStageAggregateRow lockAggregate(
            long taskId,
            long fenceEpoch,
            boolean create
    ) {
        if (create) {
            requireAtMostOne(
                    mapper.insertAggregateIfAbsent(taskId, fenceEpoch),
                    "snapshot stage aggregate insert"
            );
        }
        SnapshotStageAggregateRow aggregate = mapper.selectAggregateForUpdate(taskId);
        if (aggregate == null) return null;
        if (!Objects.equals(aggregate.getTaskId(), taskId)
                || aggregate.getActiveFenceEpoch() == null
                || aggregate.getActiveFenceEpoch() < 1L) {
            throw new IllegalStateException("snapshot stage aggregate row is invalid");
        }
        if (aggregate.getActiveFenceEpoch() > fenceEpoch) return null;
        if (aggregate.getActiveFenceEpoch() < fenceEpoch) {
            requireOne(
                    mapper.adoptFence(taskId, fenceEpoch),
                    "snapshot stage fence adoption"
            );
            aggregate.setActiveFenceEpoch(fenceEpoch);
        }
        return aggregate;
    }

    SnapshotStageResult poison(long taskId, long fenceEpoch, String code) {
        requireOne(mapper.poison(taskId, fenceEpoch, code), "snapshot stage poison");
        return SnapshotStageResult.rejected(code);
    }

    void poisonOnly(long taskId, long fenceEpoch, String code) {
        requireOne(mapper.poison(taskId, fenceEpoch, code), "snapshot stage poison");
    }

    static void requireOne(int changed, String action) {
        if (changed != 1) {
            throw new IllegalStateException(action + " must affect exactly one row");
        }
    }

    private static void requireAtMostOne(int changed, String action) {
        if (changed < 0 || changed > 1) {
            throw new IllegalStateException(action + " affected an invalid row count: " + changed);
        }
    }
}
