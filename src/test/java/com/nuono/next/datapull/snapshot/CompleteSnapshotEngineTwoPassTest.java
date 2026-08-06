package com.nuono.next.datapull.snapshot;

import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.CODEC;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.continueAsNextClaim;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.engine;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.item;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompleteSnapshotEngineTwoPassTest {
    @Test
    void noTokenSnapshotAllowsCrossPageReorderOnlyAfterEqualMultiset() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1,
                ProviderOutcome.success(twoPass(1, 2, false, 2,
                        item("A", "one"), item("B", "one"))),
                ProviderOutcome.success(twoPass(1, 2, false, 2, item("C", "one")))
        );
        provider.add(2,
                ProviderOutcome.success(twoPass(2, null, true, 2, item("C", "one"))),
                ProviderOutcome.success(twoPass(2, null, true, 2,
                        item("B", "one"), item("A", "one")))
        );
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04, provider, stageStore(), writer
        );
        DataPullTask task = task(120L, OperationCode.DP04, 1L, null);

        AdvanceResult result = runToTerminal(engine, task, 10);

        assertEquals(TaskState.SUCCEEDED, result.getNextState());
        assertEquals(List.of(1, 2, 1, 2), provider.calls);
        assertEquals(List.of("A:one", "B:one", "C:one"),
                values(writer.snapshot(120L).getItems()));
        assertEquals(SnapshotCollectionAuthority.Kind.TWO_PASS_OBSERVATION,
                writer.snapshot(120L).getAuthority().getKind());
    }

    @Test
    void passTwoPageReplayDoesNotDoubleCount() {
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> store = stageStore();
        store.stagePage(121L, 1L, twoPass(1, 2, false, 2, item("A", "one")));
        store.stagePage(121L, 2L, twoPass(2, null, true, 2, item("B", "one")));

        assertTrue(store.verifyPage(
                121L, 3L, twoPass(1, 2, false, 2, item("A", "one"))
        ).isAccepted());
        assertTrue(store.verifyPage(
                121L, 4L, twoPass(1, 2, false, 2, item("A", "one"))
        ).isAccepted());
        assertTrue(store.verifyPage(
                121L, 5L, twoPass(2, null, true, 2, item("B", "one"))
        ).isComplete());
        assertEquals(SnapshotComparisonResult.Status.MORE_WORK,
                store.compareNext(121L, 6L, 1).getStatus());
        assertEquals(SnapshotComparisonResult.Status.MORE_WORK,
                store.compareNext(121L, 7L, 1).getStatus());
        assertTrue(store.compareNext(121L, 8L, 1).isVerified());

        SnapshotStageProof<CompleteSnapshotEngineFixture.Item> proof =
                store.proveComplete(121L, 9L);
        assertTrue(proof.isComplete());
        assertEquals(2L, proof.getAuthority().orElseThrow().getDeclaredCollectionCount());
    }

    @Test
    void multiplicityDriftPoisonsAndQueuesWholeGenerationReset() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1,
                ProviderOutcome.success(twoPass(1, null, true, 1,
                        item("A", "one"), item("A", "one"))),
                ProviderOutcome.success(twoPass(1, null, true, 1,
                        item("A", "one"), item("B", "one")))
        );
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04, provider, stageStore(), writer
        );
        DataPullTask task = task(122L, OperationCode.DP04, 1L, null);

        continueAsNextClaim(task, engine.advance(task));
        continueAsNextClaim(task, engine.advance(task));
        AdvanceResult reset = engine.advance(task);

        assertEquals(TaskState.QUEUED, reset.getNextState());
        assertEquals(SnapshotCheckpoint.Phase.RESET, CODEC.decode(
                reset.getCheckpoint()
        ).getPhase());
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void businessSkipSentinelCannotBeReplacedByAProviderItem() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1,
                ProviderOutcome.success(twoPassWithSkipped(
                        List.of(item("A", "one")), 1
                )),
                ProviderOutcome.success(twoPassWithSkipped(
                        List.of(item("A", "one"), item("B", "one")), 0
                ))
        );
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP07A, provider, stageStore(), writer
        );
        DataPullTask task = task(126L, OperationCode.DP07A, 1L, null);

        continueAsNextClaim(task, engine.advance(task));
        continueAsNextClaim(task, engine.advance(task));
        AdvanceResult reset = engine.advance(task);

        assertEquals(SnapshotCheckpoint.Phase.RESET,
                CODEC.decode(reset.getCheckpoint()).getPhase());
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void passTwoPaginationDriftResetsBeforeComparison() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1,
                ProviderOutcome.success(twoPass(1, null, true, 1, item("A", "one"))),
                ProviderOutcome.success(twoPass(1, 2, false, 2, item("A", "one")))
        );
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP07A, provider, stageStore(), writer
        );
        DataPullTask task = task(123L, OperationCode.DP07A, 1L, null);

        continueAsNextClaim(task, engine.advance(task));
        AdvanceResult reset = engine.advance(task);

        assertEquals(SnapshotCheckpoint.Phase.RESET, CODEC.decode(
                reset.getCheckpoint()
        ).getPhase());
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void comparisonResumesAtMostTheRequestedKeyBatch() {
        List<CompleteSnapshotEngineFixture.Item> items = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            items.add(item("SKU-" + index, "one"));
        }
        SnapshotPage<CompleteSnapshotEngineFixture.Item> page = twoPass(
                1, null, true, 1, items
        );
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> store = stageStore();
        store.stagePage(124L, 1L, page);
        assertTrue(store.verifyPage(124L, 2L, page).isComplete());

        assertEquals(SnapshotComparisonResult.Status.MORE_WORK,
                store.compareNext(124L, 3L, 256).getStatus());
        assertEquals(SnapshotComparisonResult.Status.MORE_WORK,
                store.compareNext(124L, 4L, 256).getStatus());
        assertTrue(store.compareNext(124L, 5L, 256).isVerified());
    }

    @Test
    void emptyClosedCollectionCanBeVerifiedWithoutInventedRows() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1,
                ProviderOutcome.success(twoPass(1, null, true, 1)),
                ProviderOutcome.success(twoPass(1, null, true, 1))
        );
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04, provider, stageStore(), writer
        );

        AdvanceResult result = runToTerminal(
                engine, task(125L, OperationCode.DP04, 1L, null), 8
        );

        assertEquals(TaskState.SUCCEEDED, result.getNextState());
        assertEquals(0L, writer.snapshot(125L).getSourceItemCount());
    }

    private AdvanceResult runToTerminal(
            CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine,
            DataPullTask task,
            int maxAdvances
    ) {
        AdvanceResult result = null;
        for (int index = 0; index < maxAdvances; index++) {
            result = engine.advance(task);
            if (result.getNextState() == TaskState.SUCCEEDED) return result;
            continueAsNextClaim(task, result);
        }
        return result;
    }

    @SafeVarargs
    private final SnapshotPage<CompleteSnapshotEngineFixture.Item> twoPass(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            CompleteSnapshotEngineFixture.Item... items
    ) {
        return twoPass(pageNo, nextPage, lastPage, totalPages, List.of(items));
    }

    private SnapshotPage<CompleteSnapshotEngineFixture.Item> twoPass(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<CompleteSnapshotEngineFixture.Item> items
    ) {
        return SnapshotPage.twoPassRequired(
                pageNo, nextPage, lastPage, totalPages,
                items, items.size(), 0
        );
    }

    private SnapshotPage<CompleteSnapshotEngineFixture.Item> twoPassWithSkipped(
            List<CompleteSnapshotEngineFixture.Item> items,
            int skipped
    ) {
        return SnapshotPage.twoPassRequired(
                1, null, true, 1, items, items.size() + skipped, skipped,
                java.util.Collections.nCopies(skipped, "f".repeat(64))
        );
    }
}
