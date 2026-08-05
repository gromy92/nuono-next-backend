package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes one fenced fingerprint/identity/item/page batch in FK-safe order per advance. */
@Service
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Ali1688Dp10MyBatisStageCleanup implements Ali1688Dp10StageCleanup {
    private final Ali1688Dp10GenerationCleanup generationCleanup;
    private final Ali1688Dp10RuntimeMapper runtimeMapper;

    public Ali1688Dp10MyBatisStageCleanup(
            Ali1688Dp10GenerationCleanup generationCleanup,
            Ali1688Dp10RuntimeMapper runtimeMapper
    ) {
        this.generationCleanup = Objects.requireNonNull(
                generationCleanup, "generationCleanup");
        this.runtimeMapper = Objects.requireNonNull(runtimeMapper, "runtimeMapper");
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Ali1688Dp10StageCleanupAdvance cleanupOlderGenerations(
            DataPullTask task,
            long currentGenerationNo,
            LocalDateTime nowUtc
    ) {
        return cleanup(task, currentGenerationNo, nowUtc, true);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Ali1688Dp10StageCleanupAdvance cleanupCurrentGeneration(
            DataPullTask task,
            long currentGenerationNo,
            LocalDateTime nowUtc
    ) {
        return cleanup(task, currentGenerationNo, nowUtc, false);
    }

    private Ali1688Dp10StageCleanupAdvance cleanup(
            DataPullTask task,
            long generationNo,
            LocalDateTime nowUtc,
            boolean older
    ) {
        if (task == null || task.getId() == null || task.getFenceEpoch() == null
                || generationNo < 1L) {
            throw new IllegalStateException("DP10_TASK_FENCE_STALE");
        }
        Ali1688Dp10FenceGuard.requireLive(task, runtimeMapper.lockTask(task.getId()), nowUtc);
        long exactGeneration = generationNo;
        Ali1688Dp10StageCleanupReason reason = Ali1688Dp10StageCleanupReason.CURRENT_GENERATION;
        if (older) {
            Long oldest = generationCleanup.markedGeneration(
                    task.getId(), Ali1688Dp10StageCleanupReason.OLDER_GENERATION);
            if (oldest == null) {
                oldest = generationCleanup.oldestGenerationBefore(task.getId(), generationNo);
            }
            if (oldest == null) return Ali1688Dp10StageCleanupAdvance.COMPLETE;
            if (oldest <= 0L || oldest >= generationNo) {
                throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_CONFLICT");
            }
            exactGeneration = oldest;
            reason = Ali1688Dp10StageCleanupReason.OLDER_GENERATION;
        } else {
            Long marked = generationCleanup.markedGeneration(
                    task.getId(), Ali1688Dp10StageCleanupReason.CURRENT_GENERATION);
            if (marked != null && marked.longValue() != generationNo) {
                throw new IllegalStateException("DP10_STAGE_CLEANUP_MARKER_CONFLICT");
            }
        }
        return generationCleanup.advance(
                task.getId(), exactGeneration, reason, task.getFenceEpoch(), nowUtc);
    }
}
