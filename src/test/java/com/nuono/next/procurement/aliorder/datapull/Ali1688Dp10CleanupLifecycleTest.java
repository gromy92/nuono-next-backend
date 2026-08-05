package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10CleanupLifecycleTest extends Ali1688Dp10JobTestSupport {

    @Test
    void oldGenerationCleanupConsumesBoundedAdvancesBeforeAnyListCall() {
        Fixture fixture = fixture(order("ORDER-1", NEWEST, true), progress(false, null, 0L));
        fixture.stage.olderCleanupBatches = 2;
        AdvanceResult initialized = fixture.job.advance(context(fixture.task));
        continueTask(fixture.task, initialized);
        Ali1688Dp10CheckpointCodec codec = new Ali1688Dp10CheckpointCodec(
                new com.fasterxml.jackson.databind.ObjectMapper());
        fixture.task.setCheckpoint(codec.encode(
                codec.decode(fixture.task.getCheckpoint()).restartGeneration()));

        AdvanceResult first = fixture.job.advance(context(fixture.task));
        continueTask(fixture.task, first);
        AdvanceResult second = fixture.job.advance(context(fixture.task));

        assertThat(first.getStepCode()).isEqualTo(Ali1688Dp10Job.LIST_STEP);
        assertThat(second.getStepCode()).isEqualTo(Ali1688Dp10Job.LIST_STEP);
        assertThat(fixture.provider.listRequests).isEmpty();
        continueTask(fixture.task, second);
        fixture.job.advance(context(fixture.task));
        assertThat(fixture.provider.listRequests).hasSize(1);
    }

    @Test
    void committedHighWaterQueuesCrashSafeBoundedCleanupBeforeSuccess() {
        Fixture fixture = fixture(order("ORDER-2", NEWEST, true), progress(false, null, 0L));
        fixture.stage.currentCleanupBatches = 2;
        advanceUntilStep(fixture, Ali1688Dp10Job.APPLY_STEP);

        AdvanceResult committed = fixture.job.advance(context(fixture.task));
        assertThat(committed.getStepCode()).isEqualTo(Ali1688Dp10Job.CLEANUP_STEP);
        continueTask(fixture.task, committed);

        AdvanceResult firstDelete = fixture.job.advance(context(fixture.task));
        assertThat(firstDelete.getStepCode()).isEqualTo(Ali1688Dp10Job.CLEANUP_STEP);
        continueTask(fixture.task, firstDelete);
        AdvanceResult replayedDelete = fixture.job.advance(context(fixture.task));
        assertThat(replayedDelete.getStepCode()).isEqualTo(Ali1688Dp10Job.CLEANUP_STEP);
        continueTask(fixture.task, replayedDelete);

        assertThat(fixture.job.advance(context(fixture.task)).getNextState())
                .isEqualTo(TaskState.SUCCEEDED);
        assertThat(fixture.stage.currentCleanupCalls).isEqualTo(3);
    }

    @Test
    void coveredProgressConflictAlsoRoutesThroughCleanup() {
        MutableProgressStore progress = progress(false, null, 5L);
        Fixture fixture = fixture(order("ORDER-3", NEWEST, true), progress);
        fixture.writer.progressConflict = true;
        advanceUntilStep(fixture, Ali1688Dp10Job.APPLY_STEP);
        progress.current.setInitialFullCompleted(true);
        progress.current.setOfficialModifiedHighWaterUtc(NOW);
        progress.current.setVersion(6L);

        AdvanceResult reconciled = fixture.job.advance(context(fixture.task));

        assertThat(reconciled.getStepCode()).isEqualTo(Ali1688Dp10Job.CLEANUP_STEP);
        assertThat(reconciled.getNextState()).isEqualTo(TaskState.QUEUED);
    }

    @Test
    void replayAfterLastDetailCommitReturnsToVerifyNotApply() {
        Fixture fixture = fixture(order("ORDER-4", NEWEST, false), progress(false, null, 0L));
        fixture.provider.details.add(Ali1688HistoricalOrderProvider.DetailResult.success(
                order("ORDER-4", NEWEST, true)));
        advanceUntilStep(fixture, Ali1688Dp10Job.DETAIL_STEP);

        AdvanceResult transitionLostAfterCommit = fixture.job.advance(context(fixture.task));
        AdvanceResult replayed = fixture.job.advance(context(fixture.task));

        assertThat(transitionLostAfterCommit.getStepCode())
                .isEqualTo(Ali1688Dp10Job.VERIFY_STEP);
        assertThat(replayed.getStepCode()).isEqualTo(Ali1688Dp10Job.VERIFY_STEP);
        assertThat(fixture.provider.detailRequests).containsExactly("ORDER-4");
    }

    @Test
    void jobAcceptsEveryDurableCrossAdvanceStepThroughCleanup() {
        Fixture fixture = fixture(order("ORDER-5", NEWEST, true), progress(false, null, 0L));
        List<String> steps = new ArrayList<>();

        for (int advance = 0; advance < 30; advance++) {
            AdvanceResult result = fixture.job.advance(context(fixture.task));
            if (result.getStepCode() != null) steps.add(result.getStepCode());
            if (result.getNextState().isTerminal()) {
                assertThat(result.getNextState()).isEqualTo(TaskState.SUCCEEDED);
                break;
            }
            continueTask(fixture.task, result);
        }

        assertThat(steps).contains(
                Ali1688Dp10Job.LIST_STEP,
                Ali1688Dp10Job.SEAL_STEP,
                Ali1688Dp10Job.VERIFY_STEP,
                Ali1688Dp10Job.APPLY_STEP,
                Ali1688Dp10Job.CLEANUP_STEP);
    }

    private Fixture fixture(
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            MutableProgressStore progress
    ) {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        Fixture fixture = new Fixture();
        fixture.provider = new ScriptedProvider();
        fixture.provider.pages.add(page(List.of(order), 1, 1, 1));
        fixture.provider.pages.add(page(List.of(), 1, 1, 0));
        fixture.provider.pages.add(page(List.of(order), 1, 1, 1));
        fixture.provider.pages.add(page(List.of(), 1, 1, 0));
        fixture.stage = new Ali1688Dp10InMemoryStageStore();
        fixture.writer = new RecordingWriter();
        fixture.task = task(authorization);
        fixture.job = job(
                scopeSource(authorization), fixture.provider, fixture.stage,
                fixture.writer, progress);
        return fixture;
    }

    private void advanceUntilStep(Fixture fixture, String expectedStep) {
        for (int advance = 0; advance < 30
                && !expectedStep.equals(fixture.task.getStepCode()); advance++) {
            continueTask(fixture.task, fixture.job.advance(context(fixture.task)));
        }
        assertThat(fixture.task.getStepCode()).isEqualTo(expectedStep);
    }

    private static final class Fixture {
        private ScriptedProvider provider;
        private Ali1688Dp10InMemoryStageStore stage;
        private RecordingWriter writer;
        private DataPullTask task;
        private Ali1688Dp10Job job;
    }
}
