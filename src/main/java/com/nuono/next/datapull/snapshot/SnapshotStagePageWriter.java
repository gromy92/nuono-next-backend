package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Atomic pass-one page writer, including optional two-pass multiplicities. */
final class SnapshotStagePageWriter<T> {
    private static final String STALE_FENCE = "SNAPSHOT_STAGE_STALE_FENCE";
    private final CompleteSnapshotStageMapper mapper;
    private final SnapshotTwoPassMapper twoPassMapper;
    private final SnapshotItemDescriptor<T> descriptor;
    private final SnapshotPayloadCodec<T> codec;
    private final SnapshotStageFence fence;

    SnapshotStagePageWriter(
            CompleteSnapshotStageMapper mapper,
            SnapshotTwoPassMapper twoPassMapper,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> codec,
            SnapshotStageFence fence
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.twoPassMapper = twoPassMapper;
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.fence = Objects.requireNonNull(fence, "fence");
    }

    SnapshotStageResult stage(long taskId, long fenceEpoch, SnapshotPage<T> page) {
        SnapshotStagePageCandidate<T> candidate;
        try {
            candidate = SnapshotStagePageCandidate.from(page, descriptor, codec);
        } catch (RuntimeException invalidItem) {
            candidate = null;
        }
        if (!fence.ownsRunningTask(taskId, fenceEpoch)) {
            return SnapshotStageResult.rejected(STALE_FENCE);
        }
        SnapshotStageAggregateRow aggregate = fence.lockAggregate(taskId, fenceEpoch, true);
        if (aggregate == null) return SnapshotStageResult.rejected(STALE_FENCE);
        if (aggregate.getPoisonCode() != null) {
            return SnapshotStageResult.rejected(aggregate.getPoisonCode());
        }
        if (candidate == null) {
            return fence.poison(taskId, fenceEpoch, "SNAPSHOT_ITEM_ENCODING_INVALID");
        }
        if (candidate.getAuthorityMode() == SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                && twoPassMapper == null) {
            return fence.poison(taskId, fenceEpoch, "SNAPSHOT_TWO_PASS_UNSUPPORTED");
        }

        Integer maxPage = mapper.selectMaxPageNo(taskId);
        if (twoPassMapper != null) {
            String modeError;
            try {
                modeError = SnapshotStageCollectionMode.validate(aggregate, candidate, maxPage);
            } catch (RuntimeException invalidMode) {
                modeError = "SNAPSHOT_AUTHORITY_MODE_STATE_INVALID";
            }
            if (modeError != null) return fence.poison(taskId, fenceEpoch, modeError);
        }
        SnapshotStageAuthority.Decision authority = SnapshotStageAuthority.merge(
                aggregate, candidate, maxPage
        );
        if (!authority.isAccepted()) {
            return fence.poison(taskId, fenceEpoch, authority.getRejectionCode());
        }
        SnapshotStagePageRow existing = mapper.selectPage(taskId, candidate.getPageNo());
        if (existing != null) {
            return replay(taskId, fenceEpoch, aggregate, candidate, existing);
        }

        SnapshotStageMetadata.Decision metadata = SnapshotStageMetadata.merge(
                aggregate, candidate, maxPage
        );
        if (!metadata.isAccepted()) {
            return fence.poison(taskId, fenceEpoch, metadata.getRejectionCode());
        }
        SnapshotCollectionAuthority bound = authority.getAuthority();
        updateMetadata(taskId, fenceEpoch, candidate, metadata, bound);
        SnapshotStageFence.requireOne(
                mapper.insertPage(SnapshotStagePageRow.from(taskId, candidate)),
                "snapshot stage page insert"
        );
        insertItems(taskId, candidate);
        recordPassOne(taskId, fenceEpoch, candidate);
        return SnapshotStageResult.staged(
                metadata.getNextPage(), metadata.getKnownLastPage()
        );
    }

    SnapshotStageResult appendVerified(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        SnapshotStagePageCandidate<T> candidate;
        try {
            candidate = SnapshotStagePageCandidate.from(page, descriptor, codec);
        } catch (RuntimeException invalidItem) {
            candidate = null;
        }
        if (!fence.ownsRunningTask(taskId, fenceEpoch)) {
            return SnapshotStageResult.rejected(STALE_FENCE);
        }
        SnapshotStageAggregateRow aggregate = fence.lockAggregate(taskId, fenceEpoch, false);
        if (aggregate == null) return SnapshotStageResult.rejected(STALE_FENCE);
        if (aggregate.getPoisonCode() != null) {
            return SnapshotStageResult.rejected(aggregate.getPoisonCode());
        }
        if (candidate == null) {
            return fence.poison(taskId, fenceEpoch, "SNAPSHOT_ITEM_ENCODING_INVALID");
        }
        Integer sourcePages = aggregate.getPassOnePageCount();
        if (!"TWO_PASS_REQUIRED".equals(aggregate.getCollectionMode())
                || !"VERIFIED".equals(aggregate.getVerificationState())
                || sourcePages == null || sourcePages < 1
                || candidate.getPageNo() <= sourcePages
                || candidate.getAuthorityMode()
                        != SnapshotPage.AuthorityMode.PROVIDER_AUTHORITY) {
            return fence.poison(taskId, fenceEpoch, "SNAPSHOT_TRAILING_PAGE_STATE_INVALID");
        }
        Integer maxPage = mapper.selectMaxPageNo(taskId);
        SnapshotStageAuthority.Decision authority = SnapshotStageAuthority.merge(
                aggregate, candidate, maxPage
        );
        if (!authority.isAccepted()) {
            return fence.poison(taskId, fenceEpoch, authority.getRejectionCode());
        }
        SnapshotStagePageRow existing = mapper.selectPage(taskId, candidate.getPageNo());
        if (existing != null) {
            return replay(taskId, fenceEpoch, aggregate, candidate, existing);
        }
        SnapshotStageMetadata.Decision metadata = SnapshotStageMetadata.merge(
                aggregate, candidate, maxPage
        );
        if (!metadata.isAccepted() || maxPage == null
                || candidate.getPageNo() != maxPage + 1) {
            return fence.poison(taskId, fenceEpoch, metadata.isAccepted()
                    ? "SNAPSHOT_NON_CONTIGUOUS_TRAILING_PAGE"
                    : metadata.getRejectionCode());
        }
        SnapshotStageFence.requireOne(
                mapper.insertPage(SnapshotStagePageRow.from(taskId, candidate)),
                "snapshot trailing page insert"
        );
        insertItems(taskId, candidate);
        return SnapshotStageResult.staged(
                metadata.getNextPage(), metadata.getKnownLastPage()
        );
    }

    private void updateMetadata(
            long taskId,
            long fenceEpoch,
            SnapshotStagePageCandidate<T> page,
            SnapshotStageMetadata.Decision metadata,
            SnapshotCollectionAuthority authority
    ) {
        if (twoPassMapper != null) {
            SnapshotStageFence.requireOne(twoPassMapper.updateMetadataAndMode(
                    taskId, fenceEpoch, metadata.getDeclaredTotalPages(),
                    metadata.getKnownLastPage(),
                    authority == null ? null : authority.getKind().name(),
                    authority == null ? null : authority.getGenerationTokenSha256(),
                    authority == null ? null : authority.getProviderAsOfUtc(),
                    authority == null ? null : authority.getDeclaredCollectionCount(),
                    page.getAuthorityMode().name()
            ), "snapshot authority mode bind");
            return;
        }
        SnapshotStageFence.requireOne(mapper.updateMetadata(
                taskId, fenceEpoch, metadata.getDeclaredTotalPages(),
                metadata.getKnownLastPage(),
                authority == null ? null : authority.getKind().name(),
                authority == null ? null : authority.getGenerationTokenSha256(),
                authority == null ? null : authority.getProviderAsOfUtc(),
                authority == null ? null : authority.getDeclaredCollectionCount()
        ), "snapshot stage metadata update");
    }

    private void insertItems(long taskId, SnapshotStagePageCandidate<T> page) {
        List<SnapshotStageItemRow> rows = new ArrayList<>(page.getItems().size());
        int ordinal = 0;
        for (SnapshotStagePageCandidate.EncodedItem<T> item : page.getItems()) {
            rows.add(SnapshotStageItemRow.from(taskId, page.getPageNo(), ordinal++, item));
        }
        if (!rows.isEmpty() && mapper.insertItems(rows) != rows.size()) {
            throw new IllegalStateException("snapshot stage item insert row count drift");
        }
    }

    private void recordPassOne(
            long taskId,
            long fenceEpoch,
            SnapshotStagePageCandidate<T> page
    ) {
        if (page.getAuthorityMode() != SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED) return;
        List<SnapshotFingerprintCountRow> counts = SnapshotFingerprintMultiset.counts(page);
        if (!counts.isEmpty()) {
            int changed = twoPassMapper.upsertPassOneCounts(taskId, counts);
            if (changed < counts.size() || changed > counts.size() * 2) {
                throw new IllegalStateException("snapshot pass-one count row drift");
            }
        }
        SnapshotStageFence.requireOne(twoPassMapper.recordPassOnePage(
                taskId, fenceEpoch, page.getSourceItemCount()
        ), "snapshot pass-one source accounting");
    }

    private SnapshotStageResult replay(
            long taskId,
            long fenceEpoch,
            SnapshotStageAggregateRow aggregate,
            SnapshotStagePageCandidate<T> candidate,
            SnapshotStagePageRow existing
    ) {
        if (!Objects.equals(existing.getTaskId(), taskId)
                || !candidate.sameMetadata(existing)) {
            return fence.poison(taskId, fenceEpoch, "SNAPSHOT_PAGE_METADATA_DRIFT");
        }
        List<SnapshotStageItemRow> rows = mapper.selectPageItems(taskId, candidate.getPageNo());
        if (!ownsItems(taskId, candidate.getPageNo(), rows)
                || !candidate.sameContent(rows)) {
            return fence.poison(taskId, fenceEpoch, "SNAPSHOT_PAGE_CONTENT_DRIFT");
        }
        SnapshotStageMetadata.Decision routing = SnapshotStageMetadata.routeExisting(
                aggregate, existing
        );
        return routing.isAccepted()
                ? SnapshotStageResult.idempotentReplay(
                        routing.getNextPage(), routing.getKnownLastPage()
                )
                : fence.poison(taskId, fenceEpoch, routing.getRejectionCode());
    }

    private boolean ownsItems(long taskId, int pageNo, List<SnapshotStageItemRow> rows) {
        if (rows == null) return false;
        return rows.stream().allMatch(row -> row != null
                && Objects.equals(row.getTaskId(), taskId)
                && Objects.equals(row.getPageNo(), pageNo));
    }
}
