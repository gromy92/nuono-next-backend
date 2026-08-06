package com.nuono.next.datapull.snapshot;

import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.CODEC;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.assertScopeSnapshot;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.continueAsNextClaim;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.engine;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.item;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.page;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.stageStore;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.task;
import static com.nuono.next.datapull.snapshot.CompleteSnapshotEngineFixture.values;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompleteSnapshotEngineFetchProgressTest {

    @Test
    void providerPageWithoutNativeCollectionAuthorityWaitsAndWritesNothing() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1, ProviderOutcome.success(new SnapshotPage<>(
                1, null, true, 1, List.of(item("A", "one"))
        )));
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> stageStore = stageStore();
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04, provider, stageStore, writer
        );

        AdvanceResult result = engine.advance(task(112L, OperationCode.DP04, 1L, null));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("SNAPSHOT_AUTHORITY_MISSING", result.getSanitizedCode());
        assertEquals(
                "SNAPSHOT_NO_STAGED_PAGES",
                stageStore.proveComplete(112L, 1L).getSanitizedCode()
        );
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void failedPageResumesAtThatPageAndApplyRunsOnlyOnTheFollowingAdvance() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1, ProviderOutcome.success(page(
                1, 2, false, 2, item("A", "one")
        )));
        provider.add(
                2,
                ProviderOutcome.transientFailure("HTTP_503", Duration.ofMinutes(3)),
                ProviderOutcome.success(page(2, null, true, 2, item("B", "one")))
        );
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> stageStore = stageStore();
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04,
                provider,
                stageStore,
                writer
        );
        DataPullTask task = task(101L, OperationCode.DP04, 1L, null);

        AdvanceResult pageOne = engine.advance(task);
        continueAsNextClaim(task, pageOne);
        AdvanceResult firstPageTwoAttempt = engine.advance(task);
        continueAsNextClaim(task, firstPageTwoAttempt);
        AdvanceResult pageTwo = engine.advance(task);

        assertEquals(TaskState.QUEUED, pageOne.getNextState());
        assertEquals(TaskState.WAITING_BACKOFF, firstPageTwoAttempt.getNextState());
        assertEquals(Duration.ofMinutes(3), firstPageTwoAttempt.getRetryAfter());
        assertEquals(
                CODEC.decode(pageOne.getCheckpoint()).getNextPage(),
                CODEC.decode(firstPageTwoAttempt.getCheckpoint()).getNextPage()
        );
        assertEquals(1, CODEC.decode(
                firstPageTwoAttempt.getCheckpoint()
        ).getConsecutiveRetryAttempt());
        assertEquals(TaskState.QUEUED, pageTwo.getNextState());
        assertEquals(SnapshotCheckpoint.Phase.APPLY, CODEC.decode(
                pageTwo.getCheckpoint()
        ).getPhase());
        assertEquals(List.of(1, 2, 2), provider.calls);
        assertEquals(0, writer.appliedCount());

        continueAsNextClaim(task, pageTwo);
        AdvanceResult applied = engine.advance(task);

        assertEquals(TaskState.SUCCEEDED, applied.getNextState());
        assertEquals(1, writer.appliedCount());
        assertEquals(List.of("A:one", "B:one"), values(writer.snapshot(101L).getItems()));
        assertScopeSnapshot(provider.lastRequest);
        assertScopeSnapshot(writer.snapshot(101L));
    }

    @Test
    void riskControlReleasesTheWorkerAndPreservesTheFetchCheckpoint() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(1, ProviderOutcome.riskControl(
                "HTTP_429",
                Duration.ofMinutes(9),
                RiskShareLevel.EXACT
        ));
        InMemorySnapshotStageStore<CompleteSnapshotEngineFixture.Item> stageStore = stageStore();
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP07A,
                provider,
                stageStore,
                writer
        );

        AdvanceResult result = engine.advance(task(106L, OperationCode.DP07A, 1L, null));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals(Duration.ofMinutes(9), result.getRetryAfter());
        assertEquals(1, CODEC.decode(result.getCheckpoint()).getNextPage());
        assertEquals(
                "SNAPSHOT_NO_STAGED_PAGES",
                stageStore.proveComplete(106L, 1L).getSanitizedCode()
        );
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void consecutiveTransientRetriesUseTheCheckpointAttemptInsteadOfTheTaskPageCount() {
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(
                1,
                ProviderOutcome.transientFailure("RESET"),
                ProviderOutcome.transientFailure("RESET")
        );
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04,
                provider,
                stageStore(),
                new CompleteSnapshotEngineFixture.RecordingWriter()
        );
        DataPullTask task = task(111L, OperationCode.DP04, 1L, null);

        AdvanceResult first = engine.advance(task);
        continueAsNextClaim(task, first);
        AdvanceResult second = engine.advance(task);

        assertEquals(Duration.ofMinutes(1), first.getRetryAfter());
        assertEquals(Duration.ofMinutes(2), second.getRetryAfter());
        assertEquals(1, CODEC.decode(first.getCheckpoint()).getConsecutiveRetryAttempt());
        assertEquals(2, CODEC.decode(second.getCheckpoint()).getConsecutiveRetryAttempt());
        assertEquals(List.of(1, 1), provider.calls);
    }

    @Test
    void authWaitsAndProviderContractFailureBacksOffWithoutWritingFacts() {
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> authEngine = engine(
                OperationCode.DP04,
                request -> ProviderOutcome.authRequired("COOKIE_EXPIRED"),
                stageStore(),
                writer
        );
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> contractEngine = engine(
                OperationCode.DP04,
                request -> ProviderOutcome.contractError("MISSING_REQUIRED_FIELD"),
                stageStore(),
                writer
        );

        AdvanceResult auth = authEngine.advance(task(107L, OperationCode.DP04, 1L, null));
        AdvanceResult contract = contractEngine.advance(task(108L, OperationCode.DP04, 1L, null));

        assertEquals(TaskState.WAITING_AUTH, auth.getNextState());
        assertEquals("COOKIE_EXPIRED", auth.getSanitizedCode());
        assertEquals(TaskState.WAITING_BACKOFF, contract.getNextState());
        assertEquals("MISSING_REQUIRED_FIELD", contract.getSanitizedCode());
        assertEquals(0, writer.appliedCount());
    }

    @Test
    void pageNumbersAreNotSubjectToABusinessMaximum() {
        int pageNo = 1001;
        CompleteSnapshotEngineFixture.ScriptedProvider provider =
                new CompleteSnapshotEngineFixture.ScriptedProvider();
        provider.add(pageNo, ProviderOutcome.success(page(
                pageNo, null, true, pageNo, item("LAST", "one")
        )));
        CompleteSnapshotEngineFixture.RecordingWriter writer =
                new CompleteSnapshotEngineFixture.RecordingWriter();
        CompleteSnapshotEngine<CompleteSnapshotEngineFixture.Item> engine = engine(
                OperationCode.DP04,
                provider,
                stageStore(),
                writer
        );
        DataPullTask task = task(
                110L,
                OperationCode.DP04,
                1L,
                CODEC.encode(SnapshotCheckpoint.fetch(pageNo, null))
        );

        AdvanceResult result = engine.advance(task);

        assertEquals(TaskState.QUEUED, result.getNextState());
        assertEquals(SnapshotCheckpoint.Phase.APPLY, CODEC.decode(
                result.getCheckpoint()
        ).getPhase());
        assertEquals(List.of(pageNo), provider.calls);
        assertEquals(0, writer.appliedCount());
    }
}
