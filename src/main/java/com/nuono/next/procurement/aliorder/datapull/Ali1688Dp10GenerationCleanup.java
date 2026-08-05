package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageCleanupMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Marker-governed, exact-generation, one-table-per-call DP-10 cleanup kernel. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Ali1688Dp10GenerationCleanup {
    private final Ali1688Dp10StageCleanupMapper mapper;
    private final int batchSize;

    public Ali1688Dp10GenerationCleanup(
            Ali1688Dp10StageCleanupMapper mapper,
            Ali1688Dp10StageCleanupProperties properties
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        Ali1688Dp10StageCleanupProperties limits = Objects.requireNonNull(
                properties, "properties");
        limits.validate();
        batchSize = limits.getBatchSize();
    }

    public Long oldestGenerationBefore(long taskId, long currentGenerationNo) {
        if (taskId <= 0L || currentGenerationNo <= 1L) return null;
        return mapper.selectOldestGenerationBefore(taskId, currentGenerationNo);
    }

    public Long markedGeneration(
            long taskId,
            Ali1688Dp10StageCleanupReason expectedReason
    ) {
        Ali1688Dp10StageCleanupMarker marker = lockTaskMarker(taskId);
        if (marker == null) return null;
        if (marker.getReason() != expectedReason) {
            throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_CONFLICT");
        }
        return marker.getGenerationNo();
    }

    public Ali1688Dp10StageCleanupMarker lockTaskMarker(long taskId) {
        if (taskId <= 0L) {
            throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_INVALID");
        }
        Ali1688Dp10StageCleanupMarker marker = mapper.selectTaskMarkerForUpdate(taskId);
        if (marker != null && (marker.getGenerationNo() == null
                || marker.getGenerationNo() <= 0L
                || marker.getFenceEpoch() == null || marker.getFenceEpoch() <= 0L
                || marker.getReason() == null)) {
            throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_CONFLICT");
        }
        return marker;
    }

    public void adoptForFailedRetention(
            long taskId,
            Ali1688Dp10StageCleanupMarker marker,
            long taskFenceEpoch,
            LocalDateTime nowUtc
    ) {
        if (marker == null || marker.getGenerationNo() == null
                || marker.getGenerationNo() <= 0L || marker.getReason() == null
                || marker.getFenceEpoch() == null || marker.getFenceEpoch() <= 0L
                || taskFenceEpoch <= 0L || nowUtc == null
                || marker.getFenceEpoch() > taskFenceEpoch) {
            throw new IllegalStateException("DP10_FAILED_RETENTION_MARKER_INVALID");
        }
        if (marker.getReason() == Ali1688Dp10StageCleanupReason.FAILED_RETENTION) return;
        if (mapper.adoptMarkerForFailedRetention(
                taskId,
                marker.getGenerationNo(),
                marker.getReason(),
                marker.getFenceEpoch(),
                taskFenceEpoch,
                nowUtc
        ) != 1) {
            throw new IllegalStateException("DP10_FAILED_RETENTION_MARKER_STALE");
        }
    }

    public Ali1688Dp10StageCleanupAdvance advance(
            long taskId,
            long generationNo,
            Ali1688Dp10StageCleanupReason reason,
            long fenceEpoch,
            LocalDateTime nowUtc
    ) {
        if (taskId <= 0L || generationNo <= 0L || fenceEpoch <= 0L
                || reason == null || nowUtc == null) {
            throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_INVALID");
        }
        ensureMarker(taskId, generationNo, reason, fenceEpoch, nowUtc);
        if (mapper.hasFingerprintCount(taskId, generationNo) != 0) {
            requireBounded(mapper.deleteFingerprintCountBatch(
                    taskId, generationNo, batchSize));
            return Ali1688Dp10StageCleanupAdvance.PROGRESSED;
        }
        if (mapper.hasIdentity(taskId, generationNo) != 0) {
            requireBounded(mapper.deleteIdentityBatch(taskId, generationNo, batchSize));
            return Ali1688Dp10StageCleanupAdvance.PROGRESSED;
        }
        if (mapper.hasItem(taskId, generationNo) != 0) {
            requireBounded(mapper.deleteItemBatch(taskId, generationNo, batchSize));
            return Ali1688Dp10StageCleanupAdvance.PROGRESSED;
        }
        if (mapper.hasPage(taskId, generationNo) != 0) {
            requireBounded(mapper.deletePageBatch(taskId, generationNo, batchSize));
            return Ali1688Dp10StageCleanupAdvance.PROGRESSED;
        }
        if (mapper.deleteMarker(taskId, generationNo, reason, fenceEpoch) != 1) {
            throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_STALE");
        }
        return Ali1688Dp10StageCleanupAdvance.COMPLETE;
    }

    private void ensureMarker(
            long taskId,
            long generationNo,
            Ali1688Dp10StageCleanupReason reason,
            long fenceEpoch,
            LocalDateTime nowUtc
    ) {
        Ali1688Dp10StageCleanupReason existing = mapper.selectMarkerReasonForUpdate(
                taskId, generationNo);
        if (existing == null) {
            if (mapper.insertMarker(taskId, generationNo, reason, fenceEpoch, nowUtc) != 1) {
                throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_CREATE_FAILED");
            }
            return;
        }
        if (existing != reason || mapper.refreshMarker(
                taskId, generationNo, reason, fenceEpoch, nowUtc) != 1) {
            throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_CONFLICT");
        }
    }

    private void requireBounded(int deleted) {
        if (deleted <= 0 || deleted > batchSize) {
            throw new IllegalStateException("DP10_STAGE_CLEANUP_DELETE_COUNT_INVALID");
        }
    }
}
