package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.time.Duration;
import java.util.Objects;

/** Owns DP-05 provider routing and per-channel retry semantics. */
final class Dp05ProviderSteps {

    private static final Duration LOCAL_RETRY_DELAY = Duration.ofMinutes(1);

    private final Dp05ProductDetailProvider frontendProvider;
    private final Dp05ProductDetailProvider partnerProvider;
    private final Dp05CheckpointCodec checkpointCodec;
    private final Dp05StageBackoff stageBackoff;

    Dp05ProviderSteps(
            Dp05ProductDetailProvider frontendProvider,
            Dp05ProductDetailProvider partnerProvider,
            Dp05CheckpointCodec checkpointCodec,
            Dp05StageBackoff stageBackoff
    ) {
        this.frontendProvider = Objects.requireNonNull(frontendProvider, "frontendProvider");
        this.partnerProvider = Objects.requireNonNull(partnerProvider, "partnerProvider");
        this.checkpointCodec = Objects.requireNonNull(checkpointCodec, "checkpointCodec");
        this.stageBackoff = Objects.requireNonNull(stageBackoff, "stageBackoff");
    }

    AdvanceResult fetchFrontend(ExecutionContext context, Dp05Checkpoint checkpoint) {
        AdvanceResult held = held(Dp05StageBackoff.Stage.FRONTEND, context, checkpoint);
        if (held != null) {
            return held;
        }
        ProviderOutcome<Dp05ProviderValue> outcome = safeFetch(
                frontendProvider,
                context,
                checkpoint
        );
        if (outcome.getType() == ProviderOutcomeType.NOT_FOUND) {
            return queued(Dp05Checkpoint.partner(
                    checkpoint.getAfterOfferId(),
                    checkpoint.getCandidate()
            ));
        }
        return handleOutcome(context, checkpoint, Dp05StageBackoff.Stage.FRONTEND, outcome);
    }

    AdvanceResult fetchPartner(ExecutionContext context, Dp05Checkpoint checkpoint) {
        AdvanceResult held = held(Dp05StageBackoff.Stage.PARTNER, context, checkpoint);
        if (held != null) {
            return held;
        }
        ProviderOutcome<Dp05ProviderValue> outcome = safeFetch(
                partnerProvider,
                context,
                checkpoint
        );
        if (outcome.getType() == ProviderOutcomeType.NOT_FOUND) {
            return queued(Dp05Checkpoint.apply(
                    checkpoint.getAfterOfferId(),
                    checkpoint.getCandidate(),
                    Dp05JobSupport.bothNotFound(context, checkpoint.getCandidate())
            ));
        }
        return handleOutcome(context, checkpoint, Dp05StageBackoff.Stage.PARTNER, outcome);
    }

    private AdvanceResult held(
            Dp05StageBackoff.Stage stage,
            ExecutionContext context,
            Dp05Checkpoint checkpoint
    ) {
        return stageBackoff.waitIfHeld(
                stage,
                context,
                checkpoint.getPhase().name(),
                checkpointCodec.encode(checkpoint)
        );
    }

    private AdvanceResult handleOutcome(
            ExecutionContext context,
            Dp05Checkpoint checkpoint,
            Dp05StageBackoff.Stage stage,
            ProviderOutcome<Dp05ProviderValue> outcome
    ) {
        switch (outcome.getType()) {
            case SUCCESS:
                return handleSuccess(checkpoint, outcome.getValue());
            case RISK_CONTROL:
            case TRANSIENT:
                return backoff(context, checkpoint, stage, outcome);
            case AUTH_REQUIRED:
                return stageBackoff.recordAndWait(
                        stage,
                        context,
                        checkpoint.getPhase().name(),
                        checkpointCodec.encode(checkpoint),
                        outcome,
                        Math.max(1, checkpoint.getConsecutiveRetryAttempt())
                );
            case CONTRACT_ERROR:
                return backoff(
                        context,
                        checkpoint,
                        stage,
                        ProviderOutcome.transientFailure(outcome.getSanitizedCode())
                );
            case UNKNOWN_OUTCOME:
                return waitingLocal(checkpoint, outcome.getSanitizedCode());
            case NOT_FOUND:
            default:
                return waitingLocal(checkpoint, outcome.getSanitizedCode());
        }
    }

    private AdvanceResult backoff(
            ExecutionContext context,
            Dp05Checkpoint checkpoint,
            Dp05StageBackoff.Stage stage,
            ProviderOutcome<Dp05ProviderValue> outcome
    ) {
        Dp05Checkpoint retry = checkpoint.retry();
        return stageBackoff.recordAndWait(
                stage,
                context,
                checkpoint.getPhase().name(),
                checkpointCodec.encode(retry),
                outcome,
                retry.getConsecutiveRetryAttempt()
        );
    }

    private AdvanceResult handleSuccess(Dp05Checkpoint checkpoint, Dp05ProviderValue value) {
        if (value == null) {
            return waitingLocal(checkpoint, "DP05_PROVIDER_EMPTY_SUCCESS");
        }
        long offerId = checkpoint.getCandidate().getProductSiteOfferId();
        if (value.isBusinessItemSkip()) {
            return queued(Dp05Checkpoint.next(offerId));
        }
        NoonPublicProductDetailResult result = value.getDetailResult();
        if (result == null || !Dp05JobSupport.isPersistable(result.getStatus())) {
            return waitingLocal(checkpoint, "DP05_PROVIDER_INVALID_FACT_STATUS");
        }
        return queued(Dp05Checkpoint.apply(
                checkpoint.getAfterOfferId(),
                checkpoint.getCandidate(),
                result
        ));
    }

    private ProviderOutcome<Dp05ProviderValue> safeFetch(
            Dp05ProductDetailProvider provider,
            ExecutionContext context,
            Dp05Checkpoint checkpoint
    ) {
        try {
            ProviderOutcome<Dp05ProviderValue> outcome = provider.fetch(
                    new Dp05FetchRequest(context.getScope(), checkpoint.getCandidate())
            );
            return outcome == null
                    ? ProviderOutcome.transientFailure("DP05_PROVIDER_OUTCOME_MISSING")
                    : outcome;
        } catch (RuntimeException unknownFailure) {
            return ProviderOutcome.transientFailure("DP05_PROVIDER_UNTYPED_FAILURE");
        }
    }

    private AdvanceResult queued(Dp05Checkpoint checkpoint) {
        return AdvanceResult.queued(
                checkpoint.getPhase().name(),
                null,
                checkpointCodec.encode(checkpoint)
        );
    }

    private AdvanceResult waitingLocal(Dp05Checkpoint checkpoint, String code) {
        return AdvanceResult.waitingRemote(
                checkpoint.getPhase().name(),
                null,
                checkpointCodec.encode(checkpoint),
                LOCAL_RETRY_DELAY,
                code
        );
    }
}
