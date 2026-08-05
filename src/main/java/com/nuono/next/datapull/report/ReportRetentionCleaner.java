package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.DataPullRuntimeMaintenance;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkRetentionMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportLocatorMapper;
import com.nuono.next.infrastructure.mapper.ReportStageRetentionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Child-first bounded retention with short catch-up cycles whenever one run cannot drain. */
public final class ReportRetentionCleaner implements DataPullRuntimeMaintenance {
    static final int MAXIMUM_CHUNK_DELETE_BATCH = 8;
    private final ReportStageRetentionMapper stages;
    private final DataPullReportArtifactChunkRetentionMapper chunks;
    private final DataPullReportArtifactMapper artifacts;
    private final DataPullReportLocatorMapper locators;
    private final ReportRetentionProperties properties;
    private final LongSupplier nanoTime;

    private Instant nextRunUtc = Instant.MIN;

    public ReportRetentionCleaner(
            ReportStageRetentionMapper stages,
            DataPullReportArtifactChunkRetentionMapper chunks,
            DataPullReportArtifactMapper artifacts,
            DataPullReportLocatorMapper locators,
            ReportRetentionProperties properties
    ) {
        this(stages, chunks, artifacts, locators, properties, System::nanoTime);
    }

    ReportRetentionCleaner(
            ReportStageRetentionMapper stages,
            DataPullReportArtifactChunkRetentionMapper chunks,
            DataPullReportArtifactMapper artifacts,
            DataPullReportLocatorMapper locators,
            ReportRetentionProperties properties,
            LongSupplier nanoTime
    ) {
        this.stages = Objects.requireNonNull(stages, "stages");
        this.chunks = Objects.requireNonNull(chunks, "chunks");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.locators = Objects.requireNonNull(locators, "locators");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        properties.validate();
    }

    @Override
    public synchronized void run(Instant nowUtc) {
        Instant now = Objects.requireNonNull(nowUtc, "nowUtc");
        if (now.isBefore(nextRunUtc)) {
            return;
        }
        LocalDateTime cutoffUtc = LocalDateTime.ofInstant(
                now.minus(properties.grace()), ZoneOffset.UTC
        );
        LocalDateTime failedCutoffUtc = LocalDateTime.ofInstant(
                now.minus(properties.failedGrace()), ZoneOffset.UTC
        );
        int batchSize = properties.getBatchSize();
        int chunkBatchSize = Math.min(batchSize, MAXIMUM_CHUNK_DELETE_BATCH);
        List<DeleteStep> childFirstRound = List.of(
                step(batchSize, () -> stages.deleteTerminalRowsBatch(cutoffUtc, batchSize)),
                step(batchSize, () -> stages.deleteAbandonedRowsBatch(
                        failedCutoffUtc, batchSize
                )),
                step(batchSize, () -> stages.deleteTerminalStagesBatch(cutoffUtc, batchSize)),
                step(batchSize, () -> stages.deleteAbandonedStagesBatch(
                        failedCutoffUtc, batchSize
                )),
                step(chunkBatchSize, () -> chunks.deleteTerminalBatch(
                        cutoffUtc, chunkBatchSize
                )),
                step(chunkBatchSize, () -> chunks.deleteAbandonedBatch(
                        failedCutoffUtc, chunkBatchSize
                )),
                step(batchSize, () -> artifacts.deleteTerminalBatch(cutoffUtc, batchSize)),
                step(batchSize, () -> artifacts.deleteAbandonedBatch(
                        failedCutoffUtc, batchSize
                )),
                step(batchSize, () -> locators.deleteTerminalBatch(cutoffUtc, batchSize)),
                step(batchSize, () -> locators.deleteAbandonedBatch(
                        failedCutoffUtc, batchSize
                ))
        );

        RunBudget budget = new RunBudget(
                properties.getMaximumStatementsPerRun(),
                properties.getMaximumRunMillis(),
                nanoTime
        );
        boolean drained = false;
        while (budget.hasCapacity()) {
            boolean deletedInRound = false;
            boolean completeRound = true;
            for (DeleteStep deletion : childFirstRound) {
                if (!budget.hasCapacity()) {
                    completeRound = false;
                    break;
                }
                int deleted = deletion.execute();
                budget.statementCompleted();
                requireBounded(deleted, deletion.maximumRows);
                deletedInRound |= deleted > 0;
            }
            if (completeRound && !deletedInRound) {
                drained = true;
                break;
            }
            if (!completeRound) {
                break;
            }
        }
        nextRunUtc = now.plus(
                drained ? properties.runInterval() : properties.catchUpInterval()
        );
    }

    private DeleteStep step(int maximumRows, IntSupplier deletion) {
        return new DeleteStep(maximumRows, deletion);
    }

    private void requireBounded(int deleted, int batchSize) {
        if (deleted < 0 || deleted > batchSize) {
            throw new IllegalStateException("REPORT_RETENTION_DELETE_COUNT_INVALID");
        }
    }

    private static final class DeleteStep {
        private final int maximumRows;
        private final IntSupplier deletion;

        private DeleteStep(int maximumRows, IntSupplier deletion) {
            this.maximumRows = maximumRows;
            this.deletion = deletion;
        }

        private int execute() {
            return deletion.getAsInt();
        }
    }

    private static final class RunBudget {
        private final int maximumStatements;
        private final long maximumNanos;
        private final LongSupplier nanoTime;
        private final long startedNanos;
        private int completedStatements;

        private RunBudget(
                int maximumStatements,
                long maximumMillis,
                LongSupplier nanoTime
        ) {
            this.maximumStatements = maximumStatements;
            this.maximumNanos = Math.multiplyExact(maximumMillis, 1_000_000L);
            this.nanoTime = nanoTime;
            this.startedNanos = nanoTime.getAsLong();
        }

        private boolean hasCapacity() {
            return completedStatements < maximumStatements
                    && nanoTime.getAsLong() - startedNanos < maximumNanos;
        }

        private void statementCompleted() {
            completedStatements++;
        }
    }
}
