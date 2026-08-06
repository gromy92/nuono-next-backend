package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import java.util.List;
import java.util.Objects;

/** Fenced pass-two page replay and bounded multiset comparison. */
final class SnapshotTwoPassVerifier<T> {
    private final CompleteSnapshotStageMapper stageMapper;
    private final SnapshotTwoPassMapper mapper;
    private final SnapshotItemDescriptor<T> descriptor;
    private final SnapshotPayloadCodec<T> codec;
    private final SnapshotStageFence fence;

    SnapshotTwoPassVerifier(
            CompleteSnapshotStageMapper stageMapper,
            SnapshotTwoPassMapper mapper,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> codec,
            SnapshotStageFence fence
    ) {
        this.stageMapper = Objects.requireNonNull(stageMapper, "stageMapper");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.fence = Objects.requireNonNull(fence, "fence");
    }

    SnapshotVerificationResult verify(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        SnapshotStagePageCandidate<T> candidate;
        try {
            candidate = SnapshotStagePageCandidate.from(page, descriptor, codec);
        } catch (RuntimeException invalid) {
            return reject(taskId, fenceEpoch, "SNAPSHOT_VERIFY_ITEM_ENCODING_INVALID", false);
        }
        if (!fence.ownsRunningTask(taskId, fenceEpoch)) {
            return SnapshotVerificationResult.rejected("SNAPSHOT_STAGE_STALE_FENCE");
        }
        SnapshotStageAggregateRow aggregate = fence.lockAggregate(taskId, fenceEpoch, false);
        if (aggregate == null) {
            return SnapshotVerificationResult.rejected("SNAPSHOT_STAGE_STALE_FENCE");
        }
        if (aggregate.getPoisonCode() != null) {
            return SnapshotVerificationResult.rejected(aggregate.getPoisonCode());
        }
        String envelopeError = validateEnvelope(aggregate, candidate);
        if (envelopeError != null) return reject(taskId, fenceEpoch, envelopeError, true);
        if ("PASS_ONE".equals(aggregate.getVerificationState())) {
            if (candidate.getPageNo() != 1 || mapper.beginVerification(
                    taskId, fenceEpoch, aggregate.getKnownLastPage()
            ) != 1) {
                return reject(taskId, fenceEpoch, "SNAPSHOT_VERIFY_START_INVALID", true);
            }
            aggregate = stageMapper.selectAggregateForUpdate(taskId);
        }

        String digest = SnapshotFingerprintMultiset.pageDigest(candidate);
        SnapshotVerifyPageRow observed = SnapshotVerifyPageRow.from(taskId, candidate, digest);
        SnapshotVerifyPageRow existing = mapper.selectVerifyPage(taskId, candidate.getPageNo());
        if (existing != null) {
            if (!observed.sameObservation(existing)) {
                return reject(taskId, fenceEpoch, "SNAPSHOT_VERIFY_PAGE_DRIFT", true);
            }
            return replay(aggregate);
        }
        if (!"VERIFYING".equals(aggregate.getVerificationState())
                || !Objects.equals(
                        aggregate.getVerificationNextPage(), candidate.getPageNo()
                )) {
            return reject(taskId, fenceEpoch, "SNAPSHOT_VERIFY_CURSOR_DRIFT", true);
        }
        SnapshotStageFence.requireOne(
                mapper.insertVerifyPage(observed), "snapshot verify page insert"
        );
        upsertPassTwo(taskId, candidate);
        Integer nextPage = candidate.getPageNo() == aggregate.getKnownLastPage()
                ? null : candidate.getPageNo() + 1;
        SnapshotStageFence.requireOne(mapper.advanceVerification(
                taskId, fenceEpoch, candidate.getPageNo(), nextPage,
                candidate.getSourceItemCount(), SnapshotFingerprintMultiset.initialChainDigest()
        ), "snapshot verify cursor advance");
        if (nextPage != null) return SnapshotVerificationResult.accepted(nextPage);

        SnapshotStageAggregateRow completed = stageMapper.selectAggregateForUpdate(taskId);
        if (!validCompletedPass(completed)) {
            return reject(taskId, fenceEpoch, "SNAPSHOT_VERIFY_EXTENT_DRIFT", true);
        }
        return SnapshotVerificationResult.complete();
    }

    SnapshotComparisonResult compare(long taskId, long fenceEpoch, int limit) {
        if (limit < 1 || limit > 256) {
            throw new IllegalArgumentException("snapshot compare limit must be between 1 and 256");
        }
        if (!fence.ownsRunningTask(taskId, fenceEpoch)) {
            return SnapshotComparisonResult.rejected("SNAPSHOT_STAGE_STALE_FENCE");
        }
        SnapshotStageAggregateRow aggregate = fence.lockAggregate(taskId, fenceEpoch, false);
        if (aggregate == null) {
            return SnapshotComparisonResult.rejected("SNAPSHOT_STAGE_STALE_FENCE");
        }
        if (aggregate.getPoisonCode() != null) {
            return SnapshotComparisonResult.rejected(aggregate.getPoisonCode());
        }
        if ("VERIFIED".equals(aggregate.getVerificationState())) {
            return SnapshotComparisonResult.verified();
        }
        if (!validComparisonState(aggregate)) {
            return rejectComparison(taskId, fenceEpoch, "SNAPSHOT_COMPARE_STATE_INVALID");
        }
        List<SnapshotFingerprintCountRow> rows = mapper.selectFingerprintCounts(
                taskId, aggregate.getComparisonAfterFingerprint(), limit
        );
        if (rows == null) {
            throw new IllegalStateException("snapshot comparison query returned null");
        }
        if (rows.isEmpty()) return finalizeComparison(taskId, fenceEpoch, aggregate);

        String digest = aggregate.getComparisonDigestSha256();
        long sourceDelta = 0L;
        String nextAfter = aggregate.getComparisonAfterFingerprint();
        try {
            for (SnapshotFingerprintCountRow row : rows) {
                if (nextAfter != null
                        && row.getContentFingerprint().compareTo(nextAfter) <= 0) {
                    throw new IllegalArgumentException("fingerprint keyset order drift");
                }
                sourceDelta = Math.addExact(
                        sourceDelta,
                        SnapshotFingerprintMultiset.requireEqualPositiveCounts(row)
                );
                digest = SnapshotFingerprintMultiset.extendChain(digest, row);
                nextAfter = row.getContentFingerprint();
            }
        } catch (RuntimeException drift) {
            return rejectComparison(taskId, fenceEpoch, "SNAPSHOT_MULTISET_DRIFT");
        }
        SnapshotStageFence.requireOne(mapper.advanceComparison(
                taskId, fenceEpoch, aggregate.getComparisonAfterFingerprint(), nextAfter,
                aggregate.getComparisonDigestSha256(), digest, rows.size(), sourceDelta
        ), "snapshot comparison cursor advance");
        return SnapshotComparisonResult.moreWork();
    }

    private String validateEnvelope(
            SnapshotStageAggregateRow aggregate,
            SnapshotStagePageCandidate<T> page
    ) {
        if (page.getAuthorityMode() != SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                || page.getAuthority() != null
                || !"TWO_PASS_REQUIRED".equals(aggregate.getCollectionMode())
                || aggregate.getKnownLastPage() == null
                || page.getPageNo() > aggregate.getKnownLastPage()) {
            return "SNAPSHOT_VERIFY_MODE_INVALID";
        }
        SnapshotStagePageRow passOne = stageMapper.selectPage(
                aggregate.getTaskId(), page.getPageNo()
        );
        if (passOne == null
                || !Objects.equals(passOne.getNextPage(), page.getNextPage())
                || !Objects.equals(passOne.getLastPage(), page.getLastPage())
                || !Objects.equals(passOne.getTotalPages(), page.getTotalPages())) {
            return "SNAPSHOT_VERIFY_PAGE_METADATA_DRIFT";
        }
        return null;
    }

    private void upsertPassTwo(long taskId, SnapshotStagePageCandidate<T> page) {
        List<SnapshotFingerprintCountRow> counts = SnapshotFingerprintMultiset.counts(page);
        if (counts.isEmpty()) return;
        int changed = mapper.upsertPassTwoCounts(taskId, counts);
        if (changed < counts.size() || changed > counts.size() * 2) {
            throw new IllegalStateException("snapshot pass-two count row drift");
        }
    }

    private SnapshotVerificationResult replay(SnapshotStageAggregateRow aggregate) {
        if ("COMPARING".equals(aggregate.getVerificationState())
                || "VERIFIED".equals(aggregate.getVerificationState())) {
            return SnapshotVerificationResult.complete();
        }
        return SnapshotVerificationResult.replayed(aggregate.getVerificationNextPage());
    }

    private boolean validCompletedPass(SnapshotStageAggregateRow row) {
        return row != null
                && "COMPARING".equals(row.getVerificationState())
                && row.getKnownLastPage().equals(row.getVerificationPageCount())
                && row.getPassOneSourceItemCount() != null
                && row.getPassOneSourceItemCount().equals(row.getVerificationSourceItemCount());
    }

    private boolean validComparisonState(SnapshotStageAggregateRow row) {
        return "COMPARING".equals(row.getVerificationState())
                && row.getComparisonDigestSha256() != null
                && row.getComparisonKeyCount() != null && row.getComparisonKeyCount() >= 0L
                && row.getComparisonSourceItemCount() != null
                && row.getComparisonSourceItemCount() >= 0L
                && row.getPassOneSourceItemCount() != null
                && row.getVerificationSourceItemCount() != null
                && row.getPassOneSourceItemCount().equals(row.getVerificationSourceItemCount());
    }

    private SnapshotComparisonResult finalizeComparison(
            long taskId,
            long fenceEpoch,
            SnapshotStageAggregateRow row
    ) {
        long sourceCount = row.getComparisonSourceItemCount();
        if (!Objects.equals(row.getPassOneSourceItemCount(), sourceCount)
                || mapper.finalizeComparison(
                        taskId, fenceEpoch, row.getComparisonAfterFingerprint(),
                        row.getComparisonDigestSha256(), sourceCount
                ) != 1) {
            return rejectComparison(taskId, fenceEpoch, "SNAPSHOT_COMPARE_EXTENT_DRIFT");
        }
        return SnapshotComparisonResult.verified();
    }

    private SnapshotVerificationResult reject(
            long taskId,
            long fenceEpoch,
            String code,
            boolean aggregateLocked
    ) {
        if (aggregateLocked) fence.poisonOnly(taskId, fenceEpoch, code);
        return SnapshotVerificationResult.rejected(code);
    }

    private SnapshotComparisonResult rejectComparison(
            long taskId,
            long fenceEpoch,
            String code
    ) {
        fence.poisonOnly(taskId, fenceEpoch, code);
        return SnapshotComparisonResult.rejected(code);
    }
}
