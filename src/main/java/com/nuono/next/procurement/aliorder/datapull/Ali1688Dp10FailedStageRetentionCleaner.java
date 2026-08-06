package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullRuntimeMaintenance;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10FailedStageRetentionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Single-runtime, one-table-per-run retention for permanently FAILED DP-10 stages. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Ali1688Dp10FailedStageRetentionCleaner
        implements DataPullRuntimeMaintenance {
    private final Ali1688Dp10FailedStageRetentionMapper mapper;
    private final Ali1688Dp10GenerationCleanup generationCleanup;
    private final Ali1688Dp10StageCleanupProperties properties;
    private final Ali1688Dp10CheckpointCodec checkpointCodec;
    private Instant nextRunUtc = Instant.MIN;

    public Ali1688Dp10FailedStageRetentionCleaner(
            Ali1688Dp10FailedStageRetentionMapper mapper,
            Ali1688Dp10GenerationCleanup generationCleanup,
            Ali1688Dp10StageCleanupProperties properties,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.generationCleanup = Objects.requireNonNull(
                generationCleanup, "generationCleanup");
        this.properties = Objects.requireNonNull(properties, "properties");
        checkpointCodec = new Ali1688Dp10CheckpointCodec(
                Objects.requireNonNull(objectMapper, "objectMapper"));
        properties.validate();
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public synchronized void run(Instant nowUtc) {
        Instant now = Objects.requireNonNull(nowUtc, "nowUtc");
        if (now.isBefore(nextRunUtc)) return;
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                now.minus(properties.failedTaskGrace()), ZoneOffset.UTC);
        Ali1688Dp10FailedStageCandidate candidate = mapper.selectOldestEligibleMarker(cutoff);
        if (candidate == null) candidate = mapper.selectOldestEligibleGeneration(cutoff);
        if (candidate != null) {
            if (candidate.getTaskId() == null || candidate.getTaskId() <= 0L
                    || candidate.getGenerationNo() == null
                    || candidate.getGenerationNo() <= 0L
                    || candidate.getMarkerCandidate() == null) {
                throw new IllegalStateException("DP10_FAILED_RETENTION_CANDIDATE_INVALID");
            }
            Ali1688Dp10FailedTaskFence taskFence = mapper.lockEligibleTaskFence(
                    candidate.getTaskId(), cutoff);
            if (taskFence == null || taskFence.getTaskId() == null
                    || !candidate.getTaskId().equals(taskFence.getTaskId())
                    || taskFence.getFenceEpoch() == null || taskFence.getFenceEpoch() <= 0L) {
                throw new IllegalStateException("DP10_FAILED_RETENTION_TASK_STALE");
            }
            Ali1688Dp10StageCleanupMarker marker = generationCleanup.lockTaskMarker(
                    candidate.getTaskId());
            if (Boolean.TRUE.equals(candidate.getMarkerCandidate())
                    && (marker == null
                    || !candidate.getGenerationNo().equals(marker.getGenerationNo()))) {
                throw new IllegalStateException("DP10_FAILED_RETENTION_CANDIDATE_STALE");
            }
            long generationNo = candidate.getGenerationNo();
            if (marker != null) {
                validateTerminalMarker(taskFence, marker);
                generationNo = marker.getGenerationNo();
                generationCleanup.adoptForFailedRetention(
                        candidate.getTaskId(), marker, taskFence.getFenceEpoch(),
                        LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            }
            generationCleanup.advance(
                    candidate.getTaskId(),
                    generationNo,
                    Ali1688Dp10StageCleanupReason.FAILED_RETENTION,
                    taskFence.getFenceEpoch(),
                    LocalDateTime.ofInstant(now, ZoneOffset.UTC)
            );
        }
        nextRunUtc = now.plus(properties.retentionRunInterval());
    }

    private void validateTerminalMarker(
            Ali1688Dp10FailedTaskFence task,
            Ali1688Dp10StageCleanupMarker marker
    ) {
        if (marker.getFenceEpoch() > task.getFenceEpoch()) {
            throw new IllegalStateException("DP10_FAILED_RETENTION_MARKER_INVALID");
        }
        if (marker.getReason() == Ali1688Dp10StageCleanupReason.FAILED_RETENTION) return;
        long checkpointGeneration;
        try {
            checkpointGeneration = checkpointCodec.decode(
                    task.getCheckpoint()).getGenerationNo();
        } catch (RuntimeException invalidCheckpoint) {
            throw new IllegalStateException(
                    "DP10_FAILED_RETENTION_MARKER_INVALID", invalidCheckpoint);
        }
        boolean current = marker.getReason()
                == Ali1688Dp10StageCleanupReason.CURRENT_GENERATION
                && Ali1688Dp10Job.CLEANUP_STEP.equals(task.getStepCode())
                && marker.getGenerationNo() == checkpointGeneration;
        boolean older = marker.getReason()
                == Ali1688Dp10StageCleanupReason.OLDER_GENERATION
                && Ali1688Dp10Job.LIST_STEP.equals(task.getStepCode())
                && marker.getGenerationNo() < checkpointGeneration;
        if (!current && !older) {
            throw new IllegalStateException("DP10_FAILED_RETENTION_MARKER_INVALID");
        }
    }
}
