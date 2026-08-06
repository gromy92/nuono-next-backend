package com.nuono.next.datapull.snapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Test-only in-memory Adapter.
 *
 * <p>The store owns task routing and synchronization. Each task aggregate owns its fenced page
 * state machine, so a newer worker epoch can resume pages while stale epochs cannot mutate them.</p>
 */
public final class InMemorySnapshotStageStore<T> implements SnapshotStageStore<T> {
    private final SnapshotItemDescriptor<T> itemDescriptor;
    private final Map<Long, InMemorySnapshotAggregate<T>> aggregates = new LinkedHashMap<>();

    public InMemorySnapshotStageStore(SnapshotItemDescriptor<T> itemDescriptor) {
        this.itemDescriptor = Objects.requireNonNull(itemDescriptor, "itemDescriptor");
    }

    @Override
    public synchronized SnapshotStageResult stagePage(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        Objects.requireNonNull(page, "page");
        InMemorySnapshotAggregate<T> aggregate = aggregates.computeIfAbsent(
                taskId,
                ignored -> new InMemorySnapshotAggregate<>(itemDescriptor)
        );
        return aggregate.stagePage(fenceEpoch, page);
    }

    @Override
    public synchronized SnapshotStageProof<T> proveComplete(long taskId, long fenceEpoch) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        InMemorySnapshotAggregate<T> aggregate = aggregates.get(taskId);
        if (aggregate == null) {
            return SnapshotStageProof.incomplete("SNAPSHOT_NO_STAGED_PAGES");
        }
        return aggregate.proveComplete(fenceEpoch);
    }

    @Override
    public synchronized SnapshotVerificationResult verifyPage(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        InMemorySnapshotAggregate<T> aggregate = aggregates.get(taskId);
        return aggregate == null
                ? SnapshotVerificationResult.rejected("SNAPSHOT_NO_STAGED_PAGES")
                : aggregate.verifyPage(fenceEpoch, page);
    }

    @Override
    public synchronized SnapshotComparisonResult compareNext(
            long taskId,
            long fenceEpoch,
            int limit
    ) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        InMemorySnapshotAggregate<T> aggregate = aggregates.get(taskId);
        return aggregate == null
                ? SnapshotComparisonResult.rejected("SNAPSHOT_NO_STAGED_PAGES")
                : aggregate.compareNext(fenceEpoch, limit);
    }

    @Override
    public synchronized SnapshotStagePromotionResult promoteVerifiedTwoPass(
            long taskId,
            long fenceEpoch,
            int trailingPageCount
    ) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        InMemorySnapshotAggregate<T> aggregate = aggregates.get(taskId);
        return aggregate == null
                ? SnapshotStagePromotionResult.rejected("SNAPSHOT_NO_STAGED_PAGES")
                : aggregate.promoteVerifiedTwoPass(fenceEpoch, trailingPageCount);
    }

    @Override
    public synchronized SnapshotStageResult stageVerifiedTrailingPage(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        InMemorySnapshotAggregate<T> aggregate = aggregates.get(taskId);
        return aggregate == null
                ? SnapshotStageResult.rejected("SNAPSHOT_NO_STAGED_PAGES")
                : aggregate.stageVerifiedTrailingPage(fenceEpoch, page);
    }

    @Override
    public synchronized boolean clear(long taskId, long fenceEpoch) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        InMemorySnapshotAggregate<T> aggregate = aggregates.get(taskId);
        if (aggregate == null) {
            return true;
        }
        if (!aggregate.canClear(fenceEpoch)) {
            return false;
        }
        aggregates.remove(taskId);
        return true;
    }

    private static void requirePositive(long value, String name) {
        if (value < 1L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
