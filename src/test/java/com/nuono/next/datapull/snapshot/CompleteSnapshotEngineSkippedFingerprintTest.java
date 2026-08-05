package com.nuono.next.datapull.snapshot;

import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.CODEC;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.continueAsNextClaim;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.engine;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.item;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.stageStore;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.task;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompleteSnapshotEngineSkippedFingerprintTest {
    private static final String BAD_A = "a".repeat(64);
    private static final String BAD_B = "b".repeat(64);

    @Test
    void sameSkippedCountWithDifferentRawRowsQueuesWholeGenerationReset() {
        AdvanceResult result = run(BAD_A, BAD_B, 801L);

        assertEquals(TaskState.QUEUED, result.getNextState());
        assertEquals(SnapshotCheckpoint.Phase.RESET,
                CODEC.decode(result.getCheckpoint()).getPhase());
    }

    @Test
    void sameSkippedRawRowInBothPassesCompletes() {
        AdvanceResult result = run(BAD_A, BAD_A, 802L);

        assertEquals(TaskState.SUCCEEDED, result.getNextState());
    }

    private AdvanceResult run(String passOneSkipped, String passTwoSkipped, long taskId) {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1,
                ProviderOutcome.success(page(passOneSkipped)),
                ProviderOutcome.success(page(passTwoSkipped))
        );
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP07A, provider, stageStore(),
                new CompleteSnapshotEngineFixture.RecordingWriter()
        );
        DataPullTask task = task(taskId, OperationCode.DP07A, 1L, null);
        AdvanceResult result = null;
        for (int index = 0; index < 8; index++) {
            result = engine.advance(task);
            if (result.getNextState() == TaskState.SUCCEEDED
                    || CODEC.decode(result.getCheckpoint()).getPhase()
                    == SnapshotCheckpoint.Phase.RESET) {
                return result;
            }
            continueAsNextClaim(task, result);
        }
        return result;
    }

    private SnapshotPage<CompleteSnapshotEngineFixture.Item> page(String skippedFingerprint) {
        return SnapshotPage.twoPassRequired(
                1, null, true, 1, List.of(item("A", "one")), 2, 1,
                List.of(skippedFingerprint)
        );
    }
}
