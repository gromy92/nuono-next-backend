package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskScopeSnapshot;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import com.nuono.next.datapull.scope.DataPullScopeBindingEpoch;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp08BExactListBackfillJobTest {
    private static final LocalDate FACT_DATE = LocalDate.of(2026, 8, 2);
    private static final LocalDateTime SLOT_UTC = LocalDateTime.of(2026, 8, 1, 18, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 2, 0);

    private final Dp08ListTarget target = new Dp08ListTarget(
            307L,
            10L,
            "STORE",
            "SA",
            "Z1234567890",
            "scope-list",
            FACT_DATE,
            true,
            List.of(new Dp08ListTarget.Reference(20L, null))
    );

    @Test
    void untypedAndContractFailuresBackOffAtTheSameExactListCall() {
        RecordingWriter writer = new RecordingWriter();
        Dp08BExactListBackfillJob thrownJob = job(
                (ignored, locale) -> { throw new IllegalStateException("raw provider detail"); },
                writer
        );
        Dp08BExactListBackfillJob contractJob = job(
                (ignored, locale) -> ProviderOutcome.contractError("DP08_LIST_PARSE_FAILED"),
                writer
        );

        AdvanceResult thrown = thrownJob.advance(context(task()));
        AdvanceResult contract = contractJob.advance(context(task()));

        assertThat(thrown.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(thrown.getStepCode()).isEqualTo(Dp08BExactListBackfillJob.SEARCH_PRIMARY);
        assertThat(contract.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(contract.getSanitizedCode()).isEqualTo("DP08_LIST_PARSE_FAILED");
        assertThat(writer.foundWrites + writer.notFoundWrites).isZero();
    }

    @Test
    void anExplicitEmptyExactListAppliesOnlyTheNotFoundFactAfterOneProviderCall() {
        int[] calls = {0};
        NoonSearchPage emptyPage = new NoonSearchPage();
        emptyPage.setProviderPage(1);
        emptyPage.setResults(List.of());
        RecordingWriter writer = new RecordingWriter();
        Dp08BExactListBackfillJob job = job((ignored, locale) -> {
            calls[0]++;
            return ProviderOutcome.success(emptyPage);
        }, writer);
        DataPullTask task = task();

        AdvanceResult searched = job.advance(context(task));
        task.setStepCode(searched.getStepCode());
        task.setCheckpoint(searched.getCheckpoint());
        task.setFenceEpoch(2L);
        AdvanceResult applied = job.advance(context(task));

        assertThat(searched.getStepCode()).isEqualTo(Dp08BExactListBackfillJob.APPLY_NOT_FOUND);
        assertThat(applied.getNextState()).isEqualTo(TaskState.SUCCEEDED);
        assertThat(calls[0]).isOne();
        assertThat(writer.notFoundWrites).isOne();
        assertThat(writer.foundWrites).isZero();
    }

    @Test
    void replacingTheLiveCatalogDoesNotMutateAnAlreadyBoundHistoricalTask() {
        Dp08ListTarget replacement = new Dp08ListTarget(
                307L, 10L, "STORE", "SA", "Z9999999999", "replacement-scope",
                FACT_DATE, true, List.of(new Dp08ListTarget.Reference(99L, null))
        );
        String[] requestedCode = {null};
        Dp08BExactListBackfillJob job = job(
                (boundTarget, locale) -> {
                    requestedCode[0] = boundTarget.getNoonProductCode();
                    NoonSearchPage empty = new NoonSearchPage();
                    empty.setProviderPage(1);
                    empty.setResults(List.of());
                    return ProviderOutcome.success(empty);
                },
                new RecordingWriter(),
                new SingleTargetCatalog(replacement)
        );

        AdvanceResult result = job.advance(context(task()));

        assertThat(result.getStepCode()).isEqualTo(Dp08BExactListBackfillJob.APPLY_NOT_FOUND);
        assertThat(requestedCode[0]).isEqualTo(target.getNoonProductCode());
        assertThat(requestedCode[0]).isNotEqualTo(replacement.getNoonProductCode());
    }

    @Test
    void aMissingTaskScopeSnapshotFailsBeforeAnyProviderCall() {
        int[] calls = {0};
        Dp08BExactListBackfillJob job = job((ignored, locale) -> {
            calls[0]++;
            return ProviderOutcome.transientFailure("UNEXPECTED");
        }, new RecordingWriter());
        DataPullTask task = task();
        task.setScopeBindingId(null);
        task.setScopePayloadType(null);
        task.setScopePayloadSha256(null);
        task.setScopePayload(null);
        task.setScopeBindingEffectiveFromUtc(null);

        AdvanceResult result = job.advance(context(task));

        assertThat(result.getNextState()).isEqualTo(TaskState.FAILED);
        assertThat(result.getSanitizedCode()).isEqualTo("DP08B_SCOPE_SNAPSHOT_INVALID");
        assertThat(calls[0]).isZero();
    }

    private Dp08BExactListBackfillJob job(
            ExactSearch search,
            RecordingWriter writer
    ) {
        return job(search, writer, new SingleTargetCatalog(target));
    }

    private Dp08BExactListBackfillJob job(
            ExactSearch search,
            RecordingWriter writer,
            Dp08ScopeCatalog scopeCatalog
    ) {
        Dp08SearchProvider provider = new Dp08SearchProvider() {
            @Override
            public ProviderOutcome<NoonSearchPage> fetchRankPage(
                    Dp08KeywordScope scope,
                    int pageNo
            ) {
                throw new AssertionError("DP-08-B must not fetch rank pages");
            }

            @Override
            public ProviderOutcome<NoonSearchPage> searchExact(
                    Dp08ListTarget target,
                    String locale
            ) {
                return search.execute(target, locale);
            }
        };
        return new Dp08BExactListBackfillJob(
                scopeCatalog,
                provider,
                writer,
                new ObjectMapper().findAndRegisterModules(),
                new ProviderWaitTransition(new BackoffPolicy(
                        Duration.ofSeconds(1), Duration.ofMinutes(1), 0.0d
                )),
                Clock.fixed(Instant.parse("2026-08-01T18:00:00Z"), ZoneOffset.UTC)
        );
    }

    private DataPullTask task() {
        DataPullTask task = DataPullTask.queued(
                801L,
                OperationCode.DP08B,
                Dp08AKeywordRankingJob.PROVIDER_CHANNEL,
                307L,
                10L,
                "307:STORE:SA",
                null,
                null,
                "STORE",
                "SA",
                "scope-list",
                SLOT_UTC,
                "DP08B:date:2026-08-02",
                Dp08BExactListBackfillJob.SEARCH_PRIMARY,
                NOW
        );
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(1L);
        task.setLeaseOwner("worker");
        task.setLeaseUntil(NOW.plusMinutes(10));
        Dp08ScopeSnapshotCodec codec = new Dp08ScopeSnapshotCodec(
                new ObjectMapper().findAndRegisterModules()
        );
        DataPullScopeBindingCandidate candidate = new DataPullScopeBindingCandidate(
                OperationCode.DP08B,
                target.getStableScopeKey(),
                Dp08ScopeSnapshotCodec.LIST_TARGET_V1,
                codec.encode(target),
                SLOT_UTC
        );
        DataPullTaskScopeSnapshot.bind(
                task, DataPullScopeBindingEpoch.open(candidate, SLOT_UTC)
        );
        return task;
    }

    private ExecutionContext context(DataPullTask task) {
        task.setState(TaskState.RUNNING);
        return new ExecutionContext(task, NOW);
    }

    @FunctionalInterface
    private interface ExactSearch {
        ProviderOutcome<NoonSearchPage> execute(Dp08ListTarget target, String locale);
    }

    private static final class RecordingWriter implements Dp08FactWriter {
        private int foundWrites;
        private int notFoundWrites;

        @Override
        public ApplyResult applyRanking(
                DataPullTask task,
                Dp08KeywordScope scope,
                NoonSearchPage completeTop200
        ) {
            throw new AssertionError("DP-08-B must not write ranking facts");
        }

        @Override
        public ApplyResult applyListFound(
                DataPullTask task,
                Dp08ListTarget target,
                NoonProductDetail detail
        ) {
            foundWrites++;
            return ApplyResult.APPLIED;
        }

        @Override
        public ApplyResult applyListNotFound(
                DataPullTask task,
                Dp08ListTarget target,
                NoonSearchPage evidence
        ) {
            notFoundWrites++;
            return ApplyResult.APPLIED;
        }
    }

    private static final class SingleTargetCatalog implements Dp08ScopeCatalog {
        private final Dp08ListTarget target;

        private SingleTargetCatalog(Dp08ListTarget target) {
            this.target = target;
        }

        @Override
        public List<DataPullScope> listKeywordScopes() {
            return List.of();
        }

        @Override
        public List<DataPullScope> listListTargetScopes(LocalDate factDate) {
            return FACT_DATE.equals(factDate) ? List.of(target.toDataPullScope()) : List.of();
        }
    }
}
