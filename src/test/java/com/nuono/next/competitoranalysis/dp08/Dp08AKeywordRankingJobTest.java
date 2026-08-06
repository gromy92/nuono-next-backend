package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
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
import com.nuono.next.datapull.snapshot.InMemorySnapshotStageStore;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp08AKeywordRankingJobTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 0, 0);
    private final Dp08KeywordScope keyword = new Dp08KeywordScope(
            307L, 10L, 20L, 30L, "STORE", "SA", "paper", "en-SA", "scope-a",
            List.of(new Dp08TrackedProduct(
                    Dp08TrackedProduct.SubjectType.SELF, null, "N700001"
            ))
    );

    @Test
    void stagesTwoSeparateCallsBeforeOneAtomicWrite() {
        FakeProvider provider = new FakeProvider();
        provider.results.add(ProviderOutcome.success(rankPage(1)));
        provider.results.add(ProviderOutcome.success(rankPage(2)));
        FakeWriter writer = new FakeWriter();
        Dp08AKeywordRankingJob job = job(provider, writer);
        DataPullTask task = runningTask(Dp08AKeywordRankingJob.FETCH_PAGE_1, 1L);

        AdvanceResult first = job.advance(context(task));
        assertThat(first.getNextState()).isEqualTo(TaskState.QUEUED);
        assertThat(first.getStepCode()).isEqualTo(Dp08AKeywordRankingJob.FETCH_PAGE_2);
        assertThat(provider.pages).containsExactly(1);
        assertThat(writer.rankWrites).isZero();

        task.setStepCode(first.getStepCode());
        task.setFenceEpoch(2L);
        AdvanceResult second = job.advance(context(task));
        assertThat(second.getNextState()).isEqualTo(TaskState.QUEUED);
        assertThat(second.getStepCode()).isEqualTo(Dp08AKeywordRankingJob.APPLY);
        assertThat(provider.pages).containsExactly(1, 2);
        assertThat(writer.rankWrites).isZero();

        task.setStepCode(second.getStepCode());
        task.setFenceEpoch(3L);
        AdvanceResult applied = job.advance(context(task));
        assertThat(applied.getNextState()).isEqualTo(TaskState.SUCCEEDED);
        assertThat(writer.rankWrites).isOne();
        assertThat(writer.lastRanking.getProviderResultSlotCount()).isEqualTo(200);
    }

    @Test
    void pageTwoRiskWaitsAtTheSamePageAndDoesNotWritePartialFacts() {
        FakeProvider provider = new FakeProvider();
        provider.results.add(ProviderOutcome.success(rankPage(1)));
        provider.results.add(ProviderOutcome.riskControl("RATE_LIMITED"));
        provider.results.add(ProviderOutcome.success(rankPage(2)));
        FakeWriter writer = new FakeWriter();
        Dp08AKeywordRankingJob job = job(provider, writer);
        DataPullTask task = runningTask(Dp08AKeywordRankingJob.FETCH_PAGE_1, 1L);

        AdvanceResult first = job.advance(context(task));
        task.setStepCode(first.getStepCode());
        task.setFenceEpoch(2L);
        AdvanceResult held = job.advance(context(task));
        assertThat(held.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(held.getStepCode()).isEqualTo(Dp08AKeywordRankingJob.FETCH_PAGE_2);
        assertThat(writer.rankWrites).isZero();

        task.setStepCode(held.getStepCode());
        task.setFenceEpoch(3L);
        AdvanceResult resumed = job.advance(context(task));
        assertThat(resumed.getStepCode()).isEqualTo(Dp08AKeywordRankingJob.APPLY);
        assertThat(provider.pages).containsExactly(1, 2, 2);
        assertThat(writer.rankWrites).isZero();
    }

    @Test
    void pagesSeparatedBeyondTheCaptureWindowAreClearedAndRestartedFromPageOne() {
        FakeProvider provider = new FakeProvider();
        provider.results.add(ProviderOutcome.success(rankPage(1)));
        NoonSearchPage staleSecond = rankPage(2);
        staleSecond.setCapturedAt(NOW.plusMinutes(4));
        provider.results.add(ProviderOutcome.success(staleSecond));
        FakeWriter writer = new FakeWriter();
        Dp08AKeywordRankingJob job = job(provider, writer);
        DataPullTask task = runningTask(Dp08AKeywordRankingJob.FETCH_PAGE_1, 1L);

        AdvanceResult first = job.advance(context(task));
        task.setStepCode(first.getStepCode());
        task.setFenceEpoch(2L);
        AdvanceResult second = job.advance(context(task));
        task.setStepCode(second.getStepCode());
        task.setFenceEpoch(3L);

        AdvanceResult restarted = job.advance(context(task));

        assertThat(restarted.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(restarted.getStepCode()).isEqualTo(Dp08AKeywordRankingJob.FETCH_PAGE_1);
        assertThat(restarted.getSanitizedCode())
                .isEqualTo("DP08_RANK_CAPTURE_WINDOW_EXCEEDED");
        assertThat(writer.rankWrites).isZero();
    }

    @Test
    void missingOrReversedCaptureTimesNeverReachTheFactWriter() {
        assertCaptureFailure(null, NOW.plusMinutes(1), "DP08_RANK_CAPTURE_TIME_MISSING");
        assertCaptureFailure(
                NOW.plusMinutes(2), NOW.plusMinutes(1), "DP08_RANK_CAPTURE_TIME_REVERSED"
        );
    }

    private void assertCaptureFailure(
            LocalDateTime firstCapturedAt,
            LocalDateTime secondCapturedAt,
            String expectedCode
    ) {
        FakeProvider provider = new FakeProvider();
        NoonSearchPage firstPage = rankPage(1);
        firstPage.setCapturedAt(firstCapturedAt);
        NoonSearchPage secondPage = rankPage(2);
        secondPage.setCapturedAt(secondCapturedAt);
        provider.results.add(ProviderOutcome.success(firstPage));
        provider.results.add(ProviderOutcome.success(secondPage));
        FakeWriter writer = new FakeWriter();
        Dp08AKeywordRankingJob job = job(provider, writer);
        DataPullTask task = runningTask(Dp08AKeywordRankingJob.FETCH_PAGE_1, 1L);

        AdvanceResult first = job.advance(context(task));
        task.setStepCode(first.getStepCode());
        task.setFenceEpoch(2L);
        AdvanceResult second = job.advance(context(task));
        task.setStepCode(second.getStepCode());
        task.setFenceEpoch(3L);

        AdvanceResult restarted = job.advance(context(task));

        assertThat(restarted.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(restarted.getStepCode()).isEqualTo(Dp08AKeywordRankingJob.FETCH_PAGE_1);
        assertThat(restarted.getSanitizedCode()).isEqualTo(expectedCode);
        assertThat(writer.rankWrites).isZero();
    }

    private Dp08AKeywordRankingJob job(FakeProvider provider, FakeWriter writer) {
        Dp08RankPageCodec codec = new Dp08RankPageCodec(
                new ObjectMapper().findAndRegisterModules()
        );
        return new Dp08AKeywordRankingJob(
                new SingleScopeCatalog(keyword),
                provider,
                writer,
                new InMemorySnapshotStageStore<>(codec),
                new ProviderWaitTransition(new BackoffPolicy(
                        Duration.ofSeconds(1), Duration.ofMinutes(1), 0.0d
                )),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    private NoonSearchPage rankPage(int pageNo) {
        NoonSearchPage page = new NoonSearchPage();
        page.setProviderPage(pageNo);
        page.setProviderLimit(100);
        page.setProviderResultSlotCount(100);
        page.setProviderOrganicSlotCount(100);
        page.setProviderSponsoredSlotCount(0);
        page.setTotalHits(200);
        page.setTotalPages(2);
        page.setSourceUrl("https://example.test/page/" + pageNo);
        page.setParserVersion("test-v1");
        page.setProviderHttpStatus(200);
        page.setResponseHash("hash-" + pageNo);
        page.setCapturedAt(NOW.plusMinutes(pageNo));
        List<NoonSearchResult> results = new ArrayList<>();
        for (int position = 1; position <= 100; position++) {
            NoonSearchResult result = new NoonSearchResult();
            result.setPosition(position);
            result.setRankPosition(position);
            result.setNoonProductCode(String.format("Z%d%07d", pageNo, position));
            result.setTitleEn("product " + position);
            result.setCurrencyCode("SAR");
            results.add(result);
        }
        page.setResults(results);
        return page;
    }

    private DataPullTask runningTask(String step, long fence) {
        DataPullTask task = DataPullTask.queued(
                101L, OperationCode.DP08A, Dp08AKeywordRankingJob.PROVIDER_CHANNEL,
                307L, 10L, "307:STORE:SA", null, null, "STORE", "SA", "scope-a",
                NOW, "DP08A:slot", step, NOW
        );
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(fence);
        task.setLeaseOwner("worker");
        task.setLeaseUntil(NOW.plusMinutes(10));
        Dp08ScopeSnapshotCodec codec = new Dp08ScopeSnapshotCodec(
                new ObjectMapper().findAndRegisterModules()
        );
        DataPullScopeBindingCandidate candidate = new DataPullScopeBindingCandidate(
                OperationCode.DP08A,
                keyword.getStableScopeKey(),
                Dp08ScopeSnapshotCodec.KEYWORD_V1,
                codec.encode(keyword),
                NOW
        );
        DataPullTaskScopeSnapshot.bind(
                task, DataPullScopeBindingEpoch.open(candidate, NOW)
        );
        return task;
    }

    private ExecutionContext context(DataPullTask task) {
        return new ExecutionContext(task, NOW);
    }

    private static final class FakeProvider implements Dp08SearchProvider {
        private final ArrayDeque<ProviderOutcome<NoonSearchPage>> results = new ArrayDeque<>();
        private final List<Integer> pages = new ArrayList<>();

        @Override
        public ProviderOutcome<NoonSearchPage> fetchRankPage(Dp08KeywordScope scope, int pageNo) {
            pages.add(pageNo);
            return results.removeFirst();
        }

        @Override
        public ProviderOutcome<NoonSearchPage> searchExact(Dp08ListTarget target, String locale) {
            throw new AssertionError("DP-08-A must not run exact list search");
        }
    }

    private static final class FakeWriter implements Dp08FactWriter {
        private int rankWrites;
        private NoonSearchPage lastRanking;

        @Override
        public ApplyResult applyRanking(DataPullTask task, Dp08KeywordScope scope, NoonSearchPage page) {
            rankWrites++;
            lastRanking = page;
            return ApplyResult.APPLIED;
        }

        @Override
        public ApplyResult applyListFound(DataPullTask task, Dp08ListTarget target, NoonProductDetail detail) {
            throw new AssertionError("unexpected DP-08-B write");
        }

        @Override
        public ApplyResult applyListNotFound(DataPullTask task, Dp08ListTarget target, NoonSearchPage page) {
            throw new AssertionError("unexpected DP-08-B write");
        }
    }

    private static final class SingleScopeCatalog implements Dp08ScopeCatalog {
        private final Dp08KeywordScope scope;

        private SingleScopeCatalog(Dp08KeywordScope scope) { this.scope = scope; }
        @Override public List<DataPullScope> listKeywordScopes() { return List.of(scope.toDataPullScope()); }
        @Override public List<DataPullScope> listListTargetScopes(java.time.LocalDate date) { return List.of(); }
    }
}
