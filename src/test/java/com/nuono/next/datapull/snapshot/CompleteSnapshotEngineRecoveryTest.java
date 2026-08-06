package com.nuono.next.datapull.snapshot;

import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.CODEC;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.continueAsNextClaim;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.engine;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.item;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.page;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.stageStore;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.task;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.values;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompleteSnapshotEngineRecoveryTest {

    @Test
    void restartReplaysAnAlreadyStagedPageIdempotentlyThenContinues() {
        SnapshotPage<CompleteSnapshotEngineFixture.Item> firstPage = page(
                1, 2, false, 2, item("A", "one")
        );
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> stageStore = stageStore();
        stageStore.stagePage(102L, 1L, firstPage);
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1, ProviderOutcome.success(firstPage));
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> restartedEngine = engine(
                OperationCode.DP04,
                provider,
                stageStore,
                writer
        );

        AdvanceResult result = restartedEngine.advance(task(102L, OperationCode.DP04, 2L, null));

        SnapshotCheckpoint checkpoint = CODEC.decode(result.getCheckpoint());
        assertEquals(TaskState.QUEUED, result.getNextState());
        assertEquals(SnapshotCheckpoint.Phase.FETCH, checkpoint.getPhase());
        assertEquals(2, checkpoint.getNextPage());
        assertEquals(2, checkpoint.getKnownLastPage().orElseThrow());
        assertEquals(List.of(1), provider.calls);
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void missingMiddlePageQueuesASeparateResetThenBacksOffAtPageOne() {
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> stageStore = stageStore();
        stageStore.stagePage(103L, 1L, page(1, 2, false, 3, item("A", "one")));
        stageStore.stagePage(103L, 2L, page(3, null, true, 3, item("C", "one")));
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP07A,
                request -> ProviderOutcome.contractError("UNEXPECTED_FETCH"),
                stageStore,
                writer
        );
        DataPullTask task = task(
                103L,
                OperationCode.DP07A,
                3L,
                CODEC.encode(SnapshotCheckpoint.apply(3))
        );

        AdvanceResult reset = engine.advance(task);
        continueAsNextClaim(task, reset);
        AdvanceResult result = engine.advance(task);

        assertEquals(TaskState.QUEUED, reset.getNextState());
        assertEquals(SnapshotCheckpoint.Phase.RESET, CODEC.decode(
                reset.getCheckpoint()
        ).getPhase());
        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("SNAPSHOT_CONTAINER_RESTARTED", result.getSanitizedCode());
        assertEquals(1, CODEC.decode(result.getCheckpoint()).getNextPage());
        assertEquals(
                "SNAPSHOT_NO_STAGED_PAGES",
                stageStore.proveComplete(103L, 3L).getSanitizedCode()
        );
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void laterIdentityConflictIsSkippedAndCannotOverwriteTheFirstItem() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1, ProviderOutcome.success(page(
                1, 2, false, 2, item("A", "first"), item("B", "one")
        )));
        provider.add(2, ProviderOutcome.success(page(
                2, null, true, 2, item("A", "later"), item("C", "one")
        )));
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04,
                provider,
                stageStore(),
                writer
        );
        DataPullTask task = task(104L, OperationCode.DP04, 1L, null);

        continueAsNextClaim(task, engine.advance(task));
        continueAsNextClaim(task, engine.advance(task));
        AdvanceResult applied = engine.advance(task);

        CompleteSnapshot<CompleteSnapshotEngineFixture.Item> snapshot = writer.snapshot(104L);
        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(List.of("A:first", "B:one", "C:one"), values(snapshot.getItems()));
        assertEquals(1, snapshot.getSkippedIdentityCount());
    }

    @Test
    void changedPageAfterCrashResetsBeforeRetryingAndNeverAppliesDriftedContent() {
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> stageStore = stageStore();
        stageStore.stagePage(105L, 1L, page(
                1, null, true, 1, item("A", "before")
        ));
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1, ProviderOutcome.success(page(
                1, null, true, 1, item("A", "after")
        )));
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04,
                provider,
                stageStore,
                writer
        );

        DataPullTask task = task(105L, OperationCode.DP04, 2L, null);
        AdvanceResult reset = engine.advance(task);
        continueAsNextClaim(task, reset);
        AdvanceResult result = engine.advance(task);

        assertEquals(TaskState.QUEUED, reset.getNextState());
        assertEquals(SnapshotCheckpoint.Phase.RESET, CODEC.decode(
                reset.getCheckpoint()
        ).getPhase());
        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("SNAPSHOT_CONTAINER_RESTARTED", result.getSanitizedCode());
        assertEquals(1, CODEC.decode(result.getCheckpoint()).getNextPage());
        assertEquals(
                "SNAPSHOT_NO_STAGED_PAGES",
                stageStore.proveComplete(105L, 2L).getSanitizedCode()
        );
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void explicitLastPageTransitionsToApplyAndTheWriterAppliesATaskOnlyOnce() {
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> stageStore = stageStore();
        stageStore.stagePage(109L, 1L, page(1, null, true, 1, item("A", "one")));
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP07A,
                request -> ProviderOutcome.contractError("UNEXPECTED_FETCH"),
                stageStore,
                writer
        );
        String applyCheckpoint = CODEC.encode(SnapshotCheckpoint.apply(1));

        AdvanceResult firstApply = engine.advance(task(
                109L, OperationCode.DP07A, 2L, applyCheckpoint
        ));
        assertTrue(stageStore.proveComplete(109L, 2L).isComplete());
        stageStore.stagePage(109L, 3L, page(1, null, true, 1, item("A", "one")));
        AdvanceResult replayedApply = engine.advance(task(
                109L, OperationCode.DP07A, 3L, applyCheckpoint
        ));

        assertEquals(TaskState.SUCCEEDED, firstApply.getNextState());
        assertEquals(TaskState.SUCCEEDED, replayedApply.getNextState());
        assertEquals(2, writer.replaceCalls);
        assertEquals(1, writer.appliedCount());
        assertTrue(stageStore.proveComplete(109L, 3L).isComplete());
    }
}
