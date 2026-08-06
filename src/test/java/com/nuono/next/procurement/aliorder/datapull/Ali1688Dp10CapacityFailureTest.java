package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Ali1688Dp10CapacityFailureTest extends Ali1688Dp10JobTestSupport {

    @Test
    void unpersistableLargeOrderBacksOffWithoutAdvancingOfficialHighWater() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        Ali1688HistoricalOrderProvider.OrderSnapshot large = order("LARGE", NEWEST, true);
        large.setRawSnapshotJson("大".repeat(80));
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(large), 1, 1, 1));
        CapacityBoundStageStore stage = new CapacityBoundStageStore(128);
        RecordingWriter writer = new RecordingWriter();
        MutableProgressStore progress = progress(false, null, 0L);
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = new Ali1688Dp10Job(
                scopeSource(authorization), provider, stage, stage, writer, progress,
                new ProviderWaitTransition(new BackoffPolicy(
                        Duration.ofMinutes(1), Duration.ofHours(1), 0.0d)),
                new ObjectMapper()
        );

        continueTask(task, job.advance(context(task)));
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("DP10_STAGE_PAYLOAD_TOO_LARGE", result.getSanitizedCode());
        assertEquals(0, stage.persistedPages);
        assertTrue(writer.commands.isEmpty());
        assertFalse(progress.current.isInitialFullCompleted());
        assertNull(progress.current.getOfficialModifiedHighWaterUtc());
    }

    private static final class CapacityBoundStageStore
            implements Ali1688Dp10PageStageStore, Ali1688Dp10StageCleanup {
        private final Ali1688Dp10StageAssembler assembler;
        private int persistedPages;

        private CapacityBoundStageStore(int maximumPayloadBytes) {
            assembler = new Ali1688Dp10StageAssembler(
                    null, new ObjectMapper().findAndRegisterModules(), maximumPayloadBytes);
        }

        @Override
        public Ali1688Dp10StagedPage stageList(
                DataPullTask task,
                long generationNo,
                int scanPass,
                Ali1688Dp10ValidatedPage page,
                LocalDateTime nowUtc
        ) {
            assembler.itemRows(task, generationNo, scanPass, page);
            persistedPages++;
            throw new AssertionError("test does not persist successful pages");
        }

        @Override
        public Optional<Ali1688Dp10StagedPage> load(
                DataPullTask task,
                long generationNo,
                int scanPass,
                Ali1688HistoricalOrderProvider.Partition partition,
                int pageNo,
                LocalDateTime nowUtc
        ) {
            return Optional.empty();
        }

        @Override
        public Ali1688Dp10StageCleanupAdvance cleanupOlderGenerations(
                DataPullTask task,
                long currentGenerationNo,
                LocalDateTime nowUtc
        ) {
            return Ali1688Dp10StageCleanupAdvance.COMPLETE;
        }

        @Override
        public Ali1688Dp10StageCleanupAdvance cleanupCurrentGeneration(
                DataPullTask task,
                long currentGenerationNo,
                LocalDateTime nowUtc
        ) {
            return Ali1688Dp10StageCleanupAdvance.COMPLETE;
        }

        @Override
        public Ali1688Dp10SealBatch readSealBatch(
                DataPullTask task,
                long generationNo,
                Ali1688HistoricalOrderProvider.Partition partition,
                String afterFingerprint,
                LocalDateTime nowUtc
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Ali1688Dp10PendingItem> nextPendingDetail(
                DataPullTask task,
                long generationNo,
                LocalDateTime nowUtc
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Ali1688Dp10StagedPage recordDetail(
                DataPullTask task,
                Ali1688Dp10PendingItem item,
                Ali1688Dp10DetailDecision decision,
                LocalDateTime nowUtc
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
