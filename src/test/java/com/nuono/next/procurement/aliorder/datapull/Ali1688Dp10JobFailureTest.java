package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10JobFailureTest extends Ali1688Dp10JobTestSupport {

    @Test
    void unmappedNullListRowFailsTheContainerWithZeroStageWrites() {
        Fixture fixture = fixture(order("PLACEHOLDER", NEWEST, true));
        fixture.provider.pages.clear();
        fixture.provider.pages.add(page(
                java.util.Arrays.asList(null, order("ORDER-2", OLDER, true)),
                1, 2, 2));

        AdvanceResult waiting = fixture.job.advance(context(fixture.task));

        assertThat(waiting.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(waiting.getSanitizedCode()).isEqualTo("DP10_PAGE_ROW_UNMAPPED");
        assertThat(fixture.stage.stagedPageCountForTest()).isZero();
    }

    @Test
    void listExceptionKeepsTheSamePartitionPageAndWindowCheckpoint() {
        Fixture fixture = fixture(order("ORDER-LIST", NEWEST, true));
        fixture.provider.listFailure = new IllegalStateException("upstream list failed");
        String exactCheckpoint = fixture.task.getCheckpoint();

        AdvanceResult waiting = fixture.job.advance(context(fixture.task));

        assertRetry(waiting, Ali1688Dp10Job.LIST_STEP, exactCheckpoint);
        continueTask(fixture.task, waiting);
        AdvanceResult recovered = fixture.job.advance(context(fixture.task));
        assertThat(recovered.getStepCode()).isEqualTo(Ali1688Dp10Job.LIST_STEP);
        assertThat(fixture.provider.listRequests).hasSize(2);
    }

    @Test
    void thrownDetailExceptionKeepsTheExactOrderCheckpoint() {
        Fixture fixture = fixture(order("ORDER-DETAIL", NEWEST, false));
        fixture.provider.detailFailure = new IllegalStateException("upstream detail failed");
        fixture.provider.details.add(Ali1688HistoricalOrderProvider.DetailResult.success(
                order("ORDER-DETAIL", NEWEST, true)));
        advanceUntilStep(fixture, Ali1688Dp10Job.DETAIL_STEP);
        String exactCheckpoint = fixture.task.getCheckpoint();

        AdvanceResult waiting = fixture.job.advance(context(fixture.task));

        assertRetry(waiting, Ali1688Dp10Job.DETAIL_STEP, exactCheckpoint);
        continueTask(fixture.task, waiting);
        AdvanceResult recovered = fixture.job.advance(context(fixture.task));
        assertThat(recovered.getStepCode()).isEqualTo(Ali1688Dp10Job.VERIFY_STEP);
        assertThat(fixture.provider.detailRequests)
                .containsExactly("ORDER-DETAIL", "ORDER-DETAIL");
    }

    @Test
    void finalApplyExceptionLeavesAllReadyPagesForOneBatchRetry() {
        Fixture fixture = fixture(order("ORDER-APPLY", NEWEST, true));
        advanceUntilStep(fixture, Ali1688Dp10Job.APPLY_STEP);
        fixture.writer.applyFailure = new IllegalStateException("fact transaction rolled back");
        String exactCheckpoint = fixture.task.getCheckpoint();

        AdvanceResult waiting = fixture.job.advance(context(fixture.task));

        assertRetry(waiting, Ali1688Dp10Job.APPLY_STEP, exactCheckpoint);
        assertThat(fixture.writer.commands).isEmpty();
        continueTask(fixture.task, waiting);
        AdvanceResult recovered = fixture.job.advance(context(fixture.task));
        assertThat(recovered.getStepCode()).isEqualTo(Ali1688Dp10Job.CLEANUP_STEP);
        continueTask(fixture.task, recovered);
        AdvanceResult cleaned = fixture.job.advance(context(fixture.task));
        assertThat(cleaned.getNextState()).isEqualTo(TaskState.SUCCEEDED);
        assertThat(fixture.writer.commands).hasSize(1);
        assertThat(fixture.writer.batches.get(0)).hasSize(1);
    }

    private Fixture fixture(Ali1688HistoricalOrderProvider.OrderSnapshot order) {
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
                scopeSource(authorization), fixture.provider, fixture.stage, fixture.writer,
                progress(false, null, 0L));
        continueTask(fixture.task, fixture.job.advance(context(fixture.task)));
        return fixture;
    }

    private void advanceUntilStep(Fixture fixture, String step) {
        for (int advance = 0; advance < 20 && !step.equals(fixture.task.getStepCode()); advance++) {
            continueTask(fixture.task, fixture.job.advance(context(fixture.task)));
        }
        assertThat(fixture.task.getStepCode()).isEqualTo(step);
    }

    private void assertRetry(AdvanceResult result, String step, String checkpoint) {
        assertThat(result.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(result.getStepCode()).isEqualTo(step);
        assertThat(result.getCheckpoint()).isEqualTo(checkpoint);
    }

    private static final class Fixture {
        private ScriptedProvider provider;
        private Ali1688Dp10InMemoryStageStore stage;
        private RecordingWriter writer;
        private DataPullTask task;
        private Ali1688Dp10Job job;
    }
}
