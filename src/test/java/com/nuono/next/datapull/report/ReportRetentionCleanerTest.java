package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkRetentionMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportLocatorMapper;
import com.nuono.next.infrastructure.mapper.ReportStageRetentionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ReportRetentionCleanerTest {

    @Test
    void cleanupHasNoOuterTransactionAroundItsBoundedStatements() throws Exception {
        assertThat(ReportRetentionCleaner.class.getAnnotation(Transactional.class)).isNull();
        assertThat(ReportRetentionCleaner.class.getDeclaredMethod("run", Instant.class)
                .getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void cleanupUsesUtcGraceOneBatchAndTheConfiguredRunInterval() {
        List<String> calls = new ArrayList<>();
        RecordingStageMapper stages = new RecordingStageMapper(calls);
        RecordingChunkMapper chunks = new RecordingChunkMapper(calls);
        RecordingArtifactMapper artifacts = new RecordingArtifactMapper(calls);
        RecordingLocatorMapper locators = new RecordingLocatorMapper(calls);
        ReportRetentionProperties properties = new ReportRetentionProperties();
        properties.setGraceSeconds(3_600L);
        properties.setFailedGraceSeconds(7_200L);
        properties.setRunIntervalSeconds(600L);
        properties.setBatchSize(100);
        ReportRetentionCleaner cleaner = new ReportRetentionCleaner(
                stages,
                chunks,
                artifacts,
                locators,
                properties
        );

        cleaner.run(Instant.parse("2026-08-02T02:00:00Z"));
        cleaner.run(Instant.parse("2026-08-02T02:09:59Z"));
        cleaner.run(Instant.parse("2026-08-02T02:10:00Z"));

        assertThat(artifacts.cutoffs).containsExactly(
                LocalDateTime.parse("2026-08-02T01:00:00"),
                LocalDateTime.parse("2026-08-02T01:10:00")
        );
        assertThat(artifacts.abandonedCutoffs).containsExactly(
                LocalDateTime.parse("2026-08-02T00:00:00"),
                LocalDateTime.parse("2026-08-02T00:10:00")
        );
        assertThat(stages.rowCutoffs).containsExactlyElementsOf(artifacts.cutoffs);
        assertThat(stages.stageCutoffs).containsExactlyElementsOf(artifacts.cutoffs);
        assertThat(locators.cutoffs).containsExactlyElementsOf(artifacts.cutoffs);
        assertThat(stages.rowBatchSizes).containsExactly(100, 100);
        assertThat(stages.stageBatchSizes).containsExactly(100, 100);
        assertThat(artifacts.batchSizes).containsExactly(100, 100);
        assertThat(chunks.batchSizes).containsExactly(8, 8);
        assertThat(locators.batchSizes).containsExactly(100, 100);
        assertThat(calls).containsExactly(
                "stage-rows", "abandoned-stage-rows", "stage-header",
                "abandoned-stage-header", "artifact-chunks", "abandoned-artifact-chunks",
                "artifact", "abandoned-artifact",
                "locator", "abandoned-locator",
                "stage-rows", "abandoned-stage-rows", "stage-header",
                "abandoned-stage-header", "artifact-chunks", "abandoned-artifact-chunks",
                "artifact", "abandoned-artifact",
                "locator", "abandoned-locator"
        );
    }

    @Test
    void backlogUsesCatchUpCadenceButEveryInvocationKeepsAStatementCap() {
        List<String> calls = new ArrayList<>();
        ReportRetentionProperties properties = properties();
        properties.setMaximumStatementsPerRun(20);
        ReportRetentionCleaner cleaner = new ReportRetentionCleaner(
                new RecordingStageMapper(calls, true),
                new RecordingChunkMapper(calls, true),
                new RecordingArtifactMapper(calls, true),
                new RecordingLocatorMapper(calls, true),
                properties
        );
        Instant started = Instant.parse("2026-08-02T02:00:00Z");

        cleaner.run(started);
        cleaner.run(started.plusSeconds(14));
        assertThat(calls).hasSize(20);
        cleaner.run(started.plusSeconds(15));
        assertThat(calls).hasSize(40);
    }

    @Test
    void monotonicDeadlineStopsBeforeTheStatementCapWhenWorkIsSlow() {
        List<String> calls = new ArrayList<>();
        ReportRetentionProperties properties = properties();
        properties.setMaximumStatementsPerRun(100);
        properties.setMaximumRunMillis(5L);
        AtomicLong nanos = new AtomicLong();
        ReportRetentionCleaner cleaner = new ReportRetentionCleaner(
                new RecordingStageMapper(calls, true),
                new RecordingChunkMapper(calls, true),
                new RecordingArtifactMapper(calls, true),
                new RecordingLocatorMapper(calls, true),
                properties,
                () -> nanos.addAndGet(1_000_000L)
        );

        cleaner.run(Instant.parse("2026-08-02T02:00:00Z"));

        assertThat(calls).hasSize(3);
    }

    private ReportRetentionProperties properties() {
        ReportRetentionProperties properties = new ReportRetentionProperties();
        properties.setGraceSeconds(3_600L);
        properties.setFailedGraceSeconds(7_200L);
        properties.setRunIntervalSeconds(600L);
        properties.setCatchUpIntervalSeconds(15L);
        properties.setBatchSize(100);
        return properties;
    }

    private static final class RecordingChunkMapper
            implements DataPullReportArtifactChunkRetentionMapper {
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<String> calls;
        private final boolean backlogged;

        private RecordingChunkMapper(List<String> calls) { this(calls, false); }

        private RecordingChunkMapper(List<String> calls, boolean backlogged) {
            this.calls = calls;
            this.backlogged = backlogged;
        }

        @Override
        public int deleteTerminalBatch(LocalDateTime cutoffUtc, int batchSize) {
            batchSizes.add(batchSize);
            calls.add("artifact-chunks");
            return backlogged ? batchSize : 0;
        }

        @Override
        public int deleteAbandonedBatch(LocalDateTime cutoffUtc, int batchSize) {
            calls.add("abandoned-artifact-chunks");
            return backlogged ? batchSize : 0;
        }
    }

    private static final class RecordingStageMapper implements ReportStageRetentionMapper {
        private final List<LocalDateTime> rowCutoffs = new ArrayList<>();
        private final List<LocalDateTime> stageCutoffs = new ArrayList<>();
        private final List<Integer> rowBatchSizes = new ArrayList<>();
        private final List<Integer> stageBatchSizes = new ArrayList<>();
        private final List<String> calls;
        private final boolean backlogged;

        private RecordingStageMapper(List<String> calls) {
            this(calls, false);
        }

        private RecordingStageMapper(List<String> calls, boolean backlogged) {
            this.calls = calls;
            this.backlogged = backlogged;
        }

        @Override
        public int deleteTerminalRowsBatch(LocalDateTime cutoffUtc, int batchSize) {
            rowCutoffs.add(cutoffUtc);
            rowBatchSizes.add(batchSize);
            calls.add("stage-rows");
            return backlogged ? batchSize : 0;
        }

        @Override
        public int deleteAbandonedRowsBatch(LocalDateTime cutoffUtc, int batchSize) {
            calls.add("abandoned-stage-rows");
            return backlogged ? batchSize : 0;
        }

        @Override
        public int deleteTerminalStagesBatch(LocalDateTime cutoffUtc, int batchSize) {
            stageCutoffs.add(cutoffUtc);
            stageBatchSizes.add(batchSize);
            calls.add("stage-header");
            return backlogged ? batchSize : 0;
        }

        @Override
        public int deleteAbandonedStagesBatch(LocalDateTime cutoffUtc, int batchSize) {
            calls.add("abandoned-stage-header");
            return backlogged ? batchSize : 0;
        }
    }

    private static final class RecordingArtifactMapper
            implements DataPullReportArtifactMapper {
        private final List<LocalDateTime> cutoffs = new ArrayList<>();
        private final List<LocalDateTime> abandonedCutoffs = new ArrayList<>();
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<String> calls;
        private final boolean backlogged;

        private RecordingArtifactMapper(List<String> calls) {
            this(calls, false);
        }

        private RecordingArtifactMapper(List<String> calls, boolean backlogged) {
            this.calls = calls;
            this.backlogged = backlogged;
        }

        @Override public int insertIfAbsent(ReportArtifactRecord row) { return 0; }
        @Override public ReportArtifactRecord selectByKey(String artifactKey) { return null; }
        @Override public ReportArtifactRecord selectMetadataByKey(String artifactKey) {
            return null;
        }
        @Override public ReportArtifactRecord selectVerifiedMetadataByKey(String artifactKey) {
            return null;
        }
        @Override public byte[] selectContentSlice(String artifactKey, long offset, int maxBytes) {
            return null;
        }

        @Override
        public int deleteTerminalBatch(LocalDateTime cutoffUtc, int batchSize) {
            cutoffs.add(cutoffUtc);
            batchSizes.add(batchSize);
            calls.add("artifact");
            return backlogged ? batchSize : 0;
        }

        @Override
        public int deleteAbandonedBatch(LocalDateTime cutoffUtc, int batchSize) {
            abandonedCutoffs.add(cutoffUtc);
            calls.add("abandoned-artifact");
            return backlogged ? batchSize : 0;
        }
    }

    private static final class RecordingLocatorMapper
            implements DataPullReportLocatorMapper {
        private final List<LocalDateTime> cutoffs = new ArrayList<>();
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<String> calls;
        private final boolean backlogged;

        private RecordingLocatorMapper(List<String> calls) {
            this(calls, false);
        }

        private RecordingLocatorMapper(List<String> calls, boolean backlogged) {
            this.calls = calls;
            this.backlogged = backlogged;
        }

        @Override public int insert(ReportDownloadLocatorRecord row) { return 0; }
        @Override public ReportDownloadLocatorRecord selectByReference(String reference) {
            return null;
        }

        @Override
        public int deleteTerminalBatch(LocalDateTime cutoffUtc, int batchSize) {
            cutoffs.add(cutoffUtc);
            batchSizes.add(batchSize);
            calls.add("locator");
            return backlogged ? batchSize : 0;
        }

        @Override
        public int deleteAbandonedBatch(LocalDateTime cutoffUtc, int batchSize) {
            calls.add("abandoned-locator");
            return backlogged ? batchSize : 0;
        }
    }
}
