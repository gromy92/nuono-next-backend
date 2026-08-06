package com.nuono.next.datapull.snapshot;

/** Durable staging Seam for complete-snapshot operations. */
public interface SnapshotStageStore<T> {
    SnapshotStageResult stagePage(long taskId, long fenceEpoch, SnapshotPage<T> page);

    SnapshotStageProof<T> proveComplete(long taskId, long fenceEpoch);

    /**
     * Completeness proof without loading all item payloads. Production complete-snapshot engines
     * override this with an aggregate SQL proof; small in-memory stores may reuse the full proof.
     */
    default SnapshotStageProof<T> proveCompleteMetadata(long taskId, long fenceEpoch) {
        return proveComplete(taskId, fenceEpoch);
    }

    /** Persists exactly one pass-two page or replays its page digest fence. */
    default SnapshotVerificationResult verifyPage(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        return SnapshotVerificationResult.rejected("SNAPSHOT_TWO_PASS_UNSUPPORTED");
    }

    /** Compares at most {@code limit} fingerprint rows without a provider call. */
    default SnapshotComparisonResult compareNext(
            long taskId,
            long fenceEpoch,
            int limit
    ) {
        return SnapshotComparisonResult.rejected("SNAPSHOT_TWO_PASS_UNSUPPORTED");
    }

    /** Promotes a verified pass into immutable authority and reserves trailing logical pages. */
    default SnapshotStagePromotionResult promoteVerifiedTwoPass(
            long taskId,
            long fenceEpoch,
            int trailingPageCount
    ) {
        return SnapshotStagePromotionResult.rejected("SNAPSHOT_TWO_PASS_PROMOTION_UNSUPPORTED");
    }

    /** Appends one trailing page without changing the already verified source observation. */
    default SnapshotStageResult stageVerifiedTrailingPage(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        return SnapshotStageResult.rejected("SNAPSHOT_TWO_PASS_PROMOTION_UNSUPPORTED");
    }

    boolean clear(long taskId, long fenceEpoch);

    /**
     * Performs at most one bounded reset transaction. In-memory and other small stores may clear
     * atomically; production stores override this so a large poisoned container cannot create an
     * unbounded cascade delete.
     */
    default SnapshotStageClearResult clearBounded(long taskId, long fenceEpoch) {
        return clear(taskId, fenceEpoch)
                ? SnapshotStageClearResult.CLEARED
                : SnapshotStageClearResult.STALE_FENCE;
    }
}
