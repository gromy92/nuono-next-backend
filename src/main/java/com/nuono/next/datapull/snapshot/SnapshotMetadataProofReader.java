package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.SnapshotStageProofMapper;
import java.util.Objects;

/** Builds a DP-04/07-A metadata-only proof after the caller has locked task and stage fences. */
final class SnapshotMetadataProofReader {
    private final SnapshotStageProofMapper mapper;

    SnapshotMetadataProofReader(SnapshotStageProofMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    <T> SnapshotStageProof<T> prove(long taskId, long fenceEpoch) {
        SnapshotStageManifestRow manifest = mapper.selectManifest(taskId);
        String invalid = validate(taskId, fenceEpoch, manifest);
        if (invalid != null) return SnapshotStageProof.incomplete(invalid);
        SnapshotCollectionAuthority authority;
        try {
            authority = SnapshotCollectionAuthority.fromPersistedDigest(
                    SnapshotCollectionAuthority.Kind.valueOf(manifest.getAuthorityKind()),
                    manifest.getAuthorityTokenSha256(), manifest.getSnapshotAsOfUtc(),
                    manifest.getDeclaredCollectionCount()
            );
        } catch (RuntimeException invalidAuthority) {
            return SnapshotStageProof.incomplete("SNAPSHOT_AUTHORITY_STATE_INVALID");
        }
        long duplicateCount = manifest.getStagedItemCount()
                - manifest.getCanonicalItemCount();
        if (duplicateCount > Integer.MAX_VALUE) {
            return SnapshotStageProof.incomplete("SNAPSHOT_IDENTITY_COUNT_OVERFLOW");
        }
        try {
            return SnapshotStageProof.completeMetadata(
                    manifest.getKnownLastPage(), manifest.getCanonicalItemCount(),
                    (int) duplicateCount, manifest.getBusinessSkippedItemCount(),
                    manifest.getSourceItemCount(), authority
            );
        } catch (RuntimeException invalidAccounting) {
            return SnapshotStageProof.incomplete("SNAPSHOT_STAGE_ACCOUNTING_INVALID");
        }
    }

    private String validate(long taskId, long fenceEpoch, SnapshotStageManifestRow row) {
        if (row == null
                || !Objects.equals(row.getTaskId(), taskId)
                || !Objects.equals(row.getActiveFenceEpoch(), fenceEpoch)
                || row.getKnownLastPage() == null || row.getKnownLastPage() < 1
                || row.getPageCount() == null
                || row.getPageCount() != row.getKnownLastPage().longValue()
                || !Objects.equals(row.getFirstPage(), 1)
                || !Objects.equals(row.getLastPage(), row.getKnownLastPage())
                || (row.getDeclaredTotalPages() != null
                        && !Objects.equals(row.getDeclaredTotalPages(), row.getKnownLastPage()))
                || invalidCounts(row)) {
            return "SNAPSHOT_STAGE_STATE_INVALID";
        }
        if (mapper.countInvalidPageShapes(taskId) != 0L) {
            return "SNAPSHOT_STAGE_PAGE_INVALID";
        }
        if (mapper.countInvalidItems(taskId) != 0L) {
            return "SNAPSHOT_STAGE_ITEM_INVALID";
        }
        if (row.getAuthorityKind() == null
                || row.getAuthorityTokenSha256() == null
                || row.getDeclaredCollectionCount() == null
                || !Objects.equals(
                        row.getDeclaredCollectionCount(), row.getSourceItemCount()
                )) {
            return "SNAPSHOT_AUTHORITY_EXTENT_DRIFT";
        }
        return null;
    }

    private boolean invalidCounts(SnapshotStageManifestRow row) {
        return row.getStagedItemCount() == null
                || row.getCanonicalItemCount() == null
                || row.getSourceItemCount() == null
                || row.getBusinessSkippedItemCount() == null
                || row.getStagedItemCount() < 0L
                || row.getCanonicalItemCount() < 0L
                || row.getCanonicalItemCount() > row.getStagedItemCount()
                || row.getBusinessSkippedItemCount() < 0L
                || row.getSourceItemCount() != row.getStagedItemCount()
                        + row.getBusinessSkippedItemCount();
    }
}
