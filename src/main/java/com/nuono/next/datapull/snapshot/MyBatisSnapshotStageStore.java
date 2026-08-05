package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Production stage adapter; locks the task fence before its snapshot staging header. */
public final class MyBatisSnapshotStageStore<T> implements SnapshotStageStore<T> {
    private static final String STALE_FENCE = "SNAPSHOT_STAGE_STALE_FENCE";

    private final CompleteSnapshotStageMapper mapper;
    private final SnapshotStageProofAssembler<T> proofAssembler;
    private final SnapshotMetadataProofReader metadataProofReader;
    private final SnapshotStageResetter resetter;
    private final SnapshotStageFence fence;
    private final SnapshotStagePageWriter<T> pageWriter;
    private final SnapshotTwoPassVerifier<T> verifier;

    public MyBatisSnapshotStageStore(
            CompleteSnapshotStageMapper mapper,
            SnapshotItemDescriptor<T> itemDescriptor,
            SnapshotPayloadCodec<T> payloadCodec
    ) {
        this(mapper, null, itemDescriptor, payloadCodec);
    }

    public MyBatisSnapshotStageStore(
            CompleteSnapshotStageMapper mapper,
            SnapshotTwoPassMapper twoPassMapper,
            SnapshotItemDescriptor<T> itemDescriptor,
            SnapshotPayloadCodec<T> payloadCodec
    ) {
        CompleteSnapshotStageMapper stageMapper = Objects.requireNonNull(mapper, "mapper");
        this.mapper = stageMapper;
        SnapshotItemDescriptor<T> descriptor = Objects.requireNonNull(
                itemDescriptor, "itemDescriptor"
        );
        SnapshotPayloadCodec<T> codec = Objects.requireNonNull(payloadCodec, "payloadCodec");
        this.fence = new SnapshotStageFence(stageMapper);
        this.pageWriter = new SnapshotStagePageWriter<>(
                stageMapper, twoPassMapper, descriptor, codec, fence
        );
        this.verifier = twoPassMapper == null ? null : new SnapshotTwoPassVerifier<>(
                stageMapper, twoPassMapper, descriptor, codec, fence
        );
        this.proofAssembler = new SnapshotStageProofAssembler<>(itemDescriptor, payloadCodec);
        this.metadataProofReader = new SnapshotMetadataProofReader(stageMapper);
        this.resetter = new SnapshotStageResetter(stageMapper, twoPassMapper);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public SnapshotStageResult stagePage(long taskId, long fenceEpoch, SnapshotPage<T> page) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        Objects.requireNonNull(page, "page");
        return pageWriter.stage(taskId, fenceEpoch, page);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public SnapshotVerificationResult verifyPage(
            long taskId,
            long fenceEpoch,
            SnapshotPage<T> page
    ) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        Objects.requireNonNull(page, "page");
        return verifier == null
                ? SnapshotVerificationResult.rejected("SNAPSHOT_TWO_PASS_UNSUPPORTED")
                : verifier.verify(taskId, fenceEpoch, page);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public SnapshotComparisonResult compareNext(long taskId, long fenceEpoch, int limit) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        return verifier == null
                ? SnapshotComparisonResult.rejected("SNAPSHOT_TWO_PASS_UNSUPPORTED")
                : verifier.compare(taskId, fenceEpoch, limit);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public SnapshotStageProof<T> proveComplete(long taskId, long fenceEpoch) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        if (!fence.ownsRunningTask(taskId, fenceEpoch)) {
            return SnapshotStageProof.incomplete(STALE_FENCE);
        }
        SnapshotStageAggregateRow aggregate = fence.lockAggregate(taskId, fenceEpoch, false);
        if (aggregate == null) {
            return SnapshotStageProof.incomplete("SNAPSHOT_NO_STAGED_PAGES");
        }
        if (aggregate.getPoisonCode() != null) {
            return SnapshotStageProof.incomplete(aggregate.getPoisonCode());
        }
        java.util.List<SnapshotStagePageRow> pages = mapper.selectPages(taskId);
        java.util.List<SnapshotStageItemRow> items = mapper.selectItems(taskId);
        return proofAssembler.assemble(taskId, aggregate, pages, items);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public SnapshotStageProof<T> proveCompleteMetadata(long taskId, long fenceEpoch) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        if (!fence.ownsRunningTask(taskId, fenceEpoch)) {
            return SnapshotStageProof.incomplete(STALE_FENCE);
        }
        SnapshotStageAggregateRow aggregate = fence.lockAggregate(taskId, fenceEpoch, false);
        if (aggregate == null) {
            return SnapshotStageProof.incomplete("SNAPSHOT_NO_STAGED_PAGES");
        }
        if (aggregate.getPoisonCode() != null) {
            return SnapshotStageProof.incomplete(aggregate.getPoisonCode());
        }
        return metadataProofReader.prove(taskId, fenceEpoch);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public boolean clear(long taskId, long fenceEpoch) {
        return clearBounded(taskId, fenceEpoch) == SnapshotStageClearResult.CLEARED;
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public SnapshotStageClearResult clearBounded(long taskId, long fenceEpoch) {
        requirePositive(taskId, "taskId");
        requirePositive(fenceEpoch, "fenceEpoch");
        return resetter.clear(taskId, fenceEpoch);
    }

    private static void requirePositive(long value, String name) {
        if (value < 1L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

}
