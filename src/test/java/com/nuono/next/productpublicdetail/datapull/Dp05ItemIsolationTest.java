package com.nuono.next.productpublicdetail.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.orchestration.InMemoryBackoffHoldStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp05ItemIsolationTest {

    @Test
    void applyIdentityMismatchSkipsOnlyCurrentItemAndContinuesTheCursor() {
        ProductPublicDetailCandidate mismatched =
                Dp05TestSupport.candidate(62L, "ZABCDEF62");
        ProductPublicDetailCandidate next =
                Dp05TestSupport.candidate(63L, "ZABCDEF63");
        Dp05TestSupport.RecordingWriter writer = new Dp05TestSupport.RecordingWriter();
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(next),
                request -> ProviderOutcome.success(Dp05ProviderValue.fact(
                        Dp05TestSupport.partial(request.getCandidate().getNoonProductCode()))),
                request -> ProviderOutcome.notFound("UNEXPECTED_PARTNER_CALL"),
                writer,
                new InMemoryBackoffHoldStore());
        String checkpoint = new Dp05CheckpointCodec(Dp05TestSupport.mapper()).encode(
                Dp05Checkpoint.apply(
                        0L, mismatched, Dp05TestSupport.partial("ZDIFFERENT62")));

        AdvanceResult skipped = advance(job, checkpoint);
        assertEquals(TaskState.QUEUED, skipped.getNextState());
        assertEquals(0, writer.writes().size());

        AdvanceResult selected = advance(job, skipped.getCheckpoint());
        AdvanceResult fetched = advance(job, selected.getCheckpoint());
        AdvanceResult written = advance(job, fetched.getCheckpoint());
        AdvanceResult finished = advance(job, written.getCheckpoint());

        assertEquals(TaskState.SUCCEEDED, finished.getNextState());
        assertEquals(1, writer.writes().size());
        assertEquals(63L, writer.writes().get(0).candidate.getProductSiteOfferId());
    }

    private AdvanceResult advance(Dp05ProductDetailJob job, String checkpoint) {
        return job.advance(Dp05TestSupport.context(checkpoint, 0));
    }
}
