package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10TerminalSemanticsTest extends Ali1688Dp10JobTestSupport {

    @Test
    void malformedSealContainerBacksOffWithTheExactCheckpointAndZeroFactWrites() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(order("ORDER-1", NEWEST, true)), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        provider.pages.add(page(List.of(order("ORDER-1", NEWEST, true)), 1, 1, 1));
        provider.pages.add(page(List.of(), 1, 1, 0));
        Ali1688Dp10InMemoryStageStore stage = new Ali1688Dp10InMemoryStageStore();
        RecordingWriter writer = new RecordingWriter();
        DataPullTask task = task(authorization);
        Ali1688Dp10Job job = job(
                scopeSource(authorization), provider, stage, writer,
                progress(false, null, 0L));
        continueTask(task, job.advance(context(task)));
        for (int page = 0; page < 4; page++) {
            continueTask(task, job.advance(context(task)));
        }
        assertEquals(Ali1688Dp10Job.SEAL_STEP, task.getStepCode());

        Ali1688Dp10CheckpointCodec codec = new Ali1688Dp10CheckpointCodec(new ObjectMapper());
        Ali1688Dp10Checkpoint checkpoint = codec.decode(task.getCheckpoint());
        Ali1688Dp10PageStageStore invalidSeal = mock(Ali1688Dp10PageStageStore.class);
        when(invalidSeal.readSealBatch(
                task,
                checkpoint.getGenerationNo(),
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                null,
                NOW)).thenThrow(new Ali1688Dp10PageContractException(
                        "DP10_SEAL_BATCH_CURSOR_INVALID"));
        Ali1688Dp10ListScanStep step = new Ali1688Dp10ListScanStep(
                scopeSource(authorization),
                provider,
                invalidSeal,
                stage,
                new Ali1688Dp10FailurePolicy(new ProviderWaitTransition(new BackoffPolicy(
                        Duration.ofMinutes(1), Duration.ofHours(1), 0.0d))),
                codec);

        AdvanceResult result = step.advance(task, NOW, checkpoint);

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals(Ali1688Dp10Job.SEAL_STEP, result.getStepCode());
        assertEquals("DP10_SEAL_BATCH_CURSOR_INVALID", result.getSanitizedCode());
        assertEquals(task.getCheckpoint(), result.getCheckpoint());
        assertTrue(provider.detailRequests.isEmpty());
        assertTrue(writer.commands.isEmpty());
    }
}
