package com.nuono.next.productpublicdetail.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nuono.next.datapull.orchestration.InMemoryBackoffHoldStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Dp05ProductDetailJobTest {

    @Test
    void resumesTheSameItemAndUsesPartnerOnlyAfterFrontendNotFound() {
        ProductPublicDetailCandidate first = Dp05TestSupport.candidate(11L, "ZABCDEF11");
        ProductPublicDetailCandidate second = Dp05TestSupport.candidate(12L, "ZABCDEF12");
        AtomicInteger frontendCalls = new AtomicInteger();
        AtomicInteger partnerCalls = new AtomicInteger();
        Dp05TestSupport.RecordingWriter writer = new Dp05TestSupport.RecordingWriter();

        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(first, second),
                request -> {
                    frontendCalls.incrementAndGet();
                    return request.getCandidate().getProductSiteOfferId() == 11L
                            ? ProviderOutcome.notFound("DP05_FRONTEND_NOT_FOUND")
                            : ProviderOutcome.success(Dp05ProviderValue.fact(
                                    Dp05TestSupport.partial(request.getCandidate().getNoonProductCode())
                            ));
                },
                request -> {
                    partnerCalls.incrementAndGet();
                    return ProviderOutcome.success(Dp05ProviderValue.fact(
                            Dp05TestSupport.partial(request.getCandidate().getNoonProductCode())
                    ));
                },
                writer,
                new InMemoryBackoffHoldStore()
        );

        AdvanceResult selectedFirst = advance(job, null);
        assertEquals("FRONTEND", selectedFirst.getStepCode());
        assertEquals(0, frontendCalls.get());

        AdvanceResult frontendNotFound = advance(job, selectedFirst.getCheckpoint());
        assertEquals("PARTNER", frontendNotFound.getStepCode());
        assertEquals(1, frontendCalls.get());
        assertEquals(0, partnerCalls.get());

        Dp05ProductDetailJob restartedJob = Dp05TestSupport.job(
                List.of(first, second),
                request -> {
                    frontendCalls.incrementAndGet();
                    return ProviderOutcome.contractError("UNEXPECTED_FRONTEND_REPLAY");
                },
                request -> {
                    partnerCalls.incrementAndGet();
                    return ProviderOutcome.success(Dp05ProviderValue.fact(
                            Dp05TestSupport.partial(request.getCandidate().getNoonProductCode())
                    ));
                },
                writer,
                new InMemoryBackoffHoldStore()
        );
        AdvanceResult partnerSuccess = advance(restartedJob, frontendNotFound.getCheckpoint());
        assertEquals("APPLY", partnerSuccess.getStepCode());
        assertEquals(1, frontendCalls.get());
        assertEquals(1, partnerCalls.get());

        AdvanceResult firstApplied = advance(restartedJob, partnerSuccess.getCheckpoint());
        AdvanceResult selectedSecond = advance(job, firstApplied.getCheckpoint());
        AdvanceResult secondFrontend = advance(job, selectedSecond.getCheckpoint());
        AdvanceResult secondApplied = advance(job, secondFrontend.getCheckpoint());
        AdvanceResult finished = advance(job, secondApplied.getCheckpoint());

        assertEquals(TaskState.SUCCEEDED, finished.getNextState());
        assertEquals(2, frontendCalls.get());
        assertEquals(1, partnerCalls.get());
        assertEquals(2, writer.writes().size());
        assertEquals(LocalDate.of(2026, 8, 2), writer.writes().get(0).factDate);
        assertEquals(307L, writer.writes().get(0).actorUserId);
    }

    @Test
    void frontendRiskKeepsTheFrontendCursorAndNeverCallsPartner() {
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(21L, "ZABCDEF21");
        AtomicInteger frontendCalls = new AtomicInteger();
        AtomicInteger partnerCalls = new AtomicInteger();
        InMemoryBackoffHoldStore holds = new InMemoryBackoffHoldStore();
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(candidate),
                request -> {
                    frontendCalls.incrementAndGet();
                    return ProviderOutcome.riskControl(
                            "DP05_FRONTEND_RATE_LIMITED",
                            Duration.ofMinutes(17),
                            RiskShareLevel.EXACT
                    );
                },
                request -> {
                    partnerCalls.incrementAndGet();
                    return ProviderOutcome.notFound("DP05_PARTNER_NOT_FOUND");
                },
                new Dp05TestSupport.RecordingWriter(),
                holds
        );

        AdvanceResult selected = advance(job, null);
        AdvanceResult held = advance(job, selected.getCheckpoint());

        assertEquals(TaskState.WAITING_BACKOFF, held.getNextState());
        assertEquals(Duration.ofMinutes(17), held.getRetryAfter());
        assertEquals("FRONTEND", held.getStepCode());
        assertEquals(1, frontendCalls.get());
        assertEquals(0, partnerCalls.get());
        assertEquals(1, new Dp05CheckpointCodec(Dp05TestSupport.mapper())
                .decode(held.getCheckpoint()).getConsecutiveRetryAttempt());

        // The shared hold is committed only after the runtime task fence CAS; a direct job call
        // must not publish cross-task state from an uncommitted or stale worker.
        assertEquals("NOON_CONSUMER_FRONTEND", held.getBackoffProviderChannel());
        assertEquals(1, frontendCalls.get());
        assertEquals(0, partnerCalls.get());
    }

    @Test
    void deterministicItemProblemsAdvanceOnlyThatItem() {
        ProductPublicDetailCandidate missingCode = Dp05TestSupport.candidate(31L, null);
        ProductPublicDetailCandidate ambiguous = Dp05TestSupport.candidate(32L, "ZABCDEF32");
        AtomicInteger frontendCalls = new AtomicInteger();
        AtomicInteger partnerCalls = new AtomicInteger();
        Dp05TestSupport.RecordingWriter writer = new Dp05TestSupport.RecordingWriter();
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(missingCode, ambiguous),
                request -> {
                    frontendCalls.incrementAndGet();
                    return ProviderOutcome.notFound("DP05_FRONTEND_NOT_FOUND");
                },
                request -> {
                    partnerCalls.incrementAndGet();
                    return ProviderOutcome.success(
                            Dp05ProviderValue.skipBusinessItem("DP05_PARTNER_IDENTITY_AMBIGUOUS")
                    );
                },
                writer,
                new InMemoryBackoffHoldStore()
        );

        AdvanceResult skippedMissingCode = advance(job, null);
        AdvanceResult selectedAmbiguous = advance(job, skippedMissingCode.getCheckpoint());
        AdvanceResult sentToPartner = advance(job, selectedAmbiguous.getCheckpoint());
        AdvanceResult skippedAmbiguous = advance(job, sentToPartner.getCheckpoint());
        AdvanceResult finished = advance(job, skippedAmbiguous.getCheckpoint());

        assertEquals(TaskState.SUCCEEDED, finished.getNextState());
        assertEquals(1, frontendCalls.get());
        assertEquals(1, partnerCalls.get());
        assertEquals(0, writer.writes().size());
    }

    @Test
    void writesNotFoundOnlyAfterBothChannelsExplicitlyReturnNotFound() {
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(41L, "ZABCDEF41");
        Dp05TestSupport.RecordingWriter writer = new Dp05TestSupport.RecordingWriter();
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(candidate),
                request -> ProviderOutcome.notFound("DP05_FRONTEND_NOT_FOUND"),
                request -> ProviderOutcome.notFound("DP05_PARTNER_NOT_FOUND"),
                writer,
                new InMemoryBackoffHoldStore()
        );

        AdvanceResult selected = advance(job, null);
        AdvanceResult partnerPhase = advance(job, selected.getCheckpoint());
        AdvanceResult applyPhase = advance(job, partnerPhase.getCheckpoint());
        assertNotNull(applyPhase.getCheckpoint());
        advance(job, applyPhase.getCheckpoint());

        assertEquals(1, writer.writes().size());
        assertEquals(ProductPublicDetailSyncStatus.NOT_FOUND, writer.writes().get(0).result.getStatus());
        assertEquals(
                "PUBLIC_AND_PARTNER_DETAIL_NOT_FOUND",
                writer.writes().get(0).result.getFailureCode()
        );
    }

    @Test
    void parseOrUnknownProviderFailureRetriesTheSameChannelAndItem() {
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(51L, "ZABCDEF51");
        AtomicInteger partnerCalls = new AtomicInteger();
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(candidate),
                request -> ProviderOutcome.contractError("DP05_FRONTEND_PARSE_FAILED"),
                request -> {
                    partnerCalls.incrementAndGet();
                    return ProviderOutcome.notFound("UNEXPECTED_PARTNER_CALL");
                },
                new Dp05TestSupport.RecordingWriter(),
                new InMemoryBackoffHoldStore()
        );

        AdvanceResult selected = advance(job, null);
        AdvanceResult retry = advance(job, selected.getCheckpoint());

        assertEquals(TaskState.WAITING_BACKOFF, retry.getNextState());
        assertEquals("FRONTEND", retry.getStepCode());
        assertEquals("DP05_FRONTEND_PARSE_FAILED", retry.getSanitizedCode());
        assertEquals("NOON_CONSUMER_FRONTEND", retry.getBackoffProviderChannel());
        assertNotNull(retry.getRetryAfter());
        Dp05Checkpoint checkpoint = new Dp05CheckpointCodec(Dp05TestSupport.mapper())
                .decode(retry.getCheckpoint());
        assertEquals(Dp05Checkpoint.Phase.FRONTEND, checkpoint.getPhase());
        assertEquals(0L, checkpoint.getAfterOfferId());
        assertEquals(51L, checkpoint.getCandidate().getProductSiteOfferId());
        assertEquals(1, checkpoint.getConsecutiveRetryAttempt());
        assertEquals(0, partnerCalls.get());
    }

    @Test
    void persistedItemFromAnotherScopeFailsBeforeAnyProviderOrFactCall() {
        ProductPublicDetailCandidate drifted = Dp05TestSupport.candidate(61L, "ZABCDEF61");
        drifted.setOwnerUserId(999L);
        AtomicInteger providerCalls = new AtomicInteger();
        Dp05TestSupport.RecordingWriter writer = new Dp05TestSupport.RecordingWriter();
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(),
                request -> {
                    providerCalls.incrementAndGet();
                    return ProviderOutcome.success(Dp05ProviderValue.fact(
                            Dp05TestSupport.partial(request.getCandidate().getNoonProductCode())
                    ));
                },
                request -> {
                    providerCalls.incrementAndGet();
                    return ProviderOutcome.notFound("UNEXPECTED_PARTNER_CALL");
                },
                writer,
                new InMemoryBackoffHoldStore()
        );
        String checkpoint = new Dp05CheckpointCodec(Dp05TestSupport.mapper()).encode(
                Dp05Checkpoint.frontend(0L, drifted)
        );

        AdvanceResult result = advance(job, checkpoint);

        assertEquals(TaskState.FAILED, result.getNextState());
        assertEquals(
                "DP05_CHECKPOINT_CANDIDATE_SCOPE_MISMATCH",
                result.getSanitizedCode()
        );
        assertEquals(0, providerCalls.get());
        assertEquals(0, writer.writes().size());
    }

    @Test
    void persistedFactForAnotherProductSkipsOnlyThatItemBeforeTheWriter() {
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(62L, "ZABCDEF62");
        Dp05TestSupport.RecordingWriter writer = new Dp05TestSupport.RecordingWriter();
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(),
                request -> ProviderOutcome.notFound("UNEXPECTED_FRONTEND_CALL"),
                request -> ProviderOutcome.notFound("UNEXPECTED_PARTNER_CALL"),
                writer,
                new InMemoryBackoffHoldStore()
        );
        String checkpoint = new Dp05CheckpointCodec(Dp05TestSupport.mapper()).encode(
                Dp05Checkpoint.apply(
                        0L, candidate, Dp05TestSupport.partial("ZDIFFERENT62")
                )
        );

        AdvanceResult result = advance(job, checkpoint);

        assertEquals(TaskState.QUEUED, result.getNextState());
        Dp05Checkpoint next = new Dp05CheckpointCodec(Dp05TestSupport.mapper())
                .decode(result.getCheckpoint());
        assertEquals(Dp05Checkpoint.Phase.SELECT_NEXT, next.getPhase());
        assertEquals(62L, next.getAfterOfferId());
        assertEquals(0, writer.writes().size());
    }

    private AdvanceResult advance(Dp05ProductDetailJob job, String checkpoint) {
        return job.advance(Dp05TestSupport.context(checkpoint, 0));
    }
}
