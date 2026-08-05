package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeProvider;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** DP-05 item state machine: frontend first; Partner only after explicit NOT_FOUND. */
public final class Dp05ProductDetailJob implements DataPullJob {

    public static final String ROUTER_CHANNEL = "NOON_DP05_ROUTER";
    public static final String INITIAL_STEP = "SELECT_NEXT";
    private static final Duration LOCAL_RETRY_DELAY = Duration.ofMinutes(1);

    private final DataPullScopeProvider scopeProvider;
    private final Dp05ProductCursor productCursor;
    private final Dp05ProductDetailFactWriter factWriter;
    private final Dp05CheckpointCodec checkpointCodec;
    private final Dp05ProviderSteps providerSteps;

    public Dp05ProductDetailJob(
            DataPullScopeProvider scopeProvider,
            Dp05ProductCursor productCursor,
            Dp05ProductDetailProvider frontendProvider,
            Dp05ProductDetailProvider partnerProvider,
            Dp05ProductDetailFactWriter factWriter,
            Dp05CheckpointCodec checkpointCodec,
            Dp05StageBackoff stageBackoff
    ) {
        this.scopeProvider = Objects.requireNonNull(scopeProvider, "scopeProvider");
        this.productCursor = Objects.requireNonNull(productCursor, "productCursor");
        this.factWriter = Objects.requireNonNull(factWriter, "factWriter");
        this.checkpointCodec = Objects.requireNonNull(checkpointCodec, "checkpointCodec");
        this.providerSteps = new Dp05ProviderSteps(
                frontendProvider,
                partnerProvider,
                checkpointCodec,
                stageBackoff
        );
    }

    @Override
    public OperationCode operationCode() {
        return OperationCode.DP05;
    }

    @Override
    public String providerChannel() {
        return ROUTER_CHANNEL;
    }

    @Override
    public String initialStep() {
        return INITIAL_STEP;
    }

    @Override
    public List<DataPullScope> listScopes() {
        return List.copyOf(scopeProvider.listScopes());
    }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        ExecutionContext nonNull = Objects.requireNonNull(context, "context");
        Dp05Checkpoint checkpoint;
        try {
            checkpoint = checkpointCodec.decode(nonNull.getTask().getCheckpoint());
        } catch (IllegalArgumentException invalidCheckpoint) {
            return AdvanceResult.failed(
                    nonNull.getTask().getStepCode(),
                    nonNull.getTask().getRemoteHandle(),
                    nonNull.getTask().getCheckpoint(),
                    "DP05_CHECKPOINT_INVALID"
            );
        }
        if (checkpoint.getPhase() != Dp05Checkpoint.Phase.SELECT_NEXT) {
            String mismatch = Dp05JobSupport.candidateMismatch(
                    nonNull.getScope(), checkpoint, checkpoint.getCandidate()
            );
            if (mismatch != null) {
                return failed(checkpoint, "DP05_CHECKPOINT_CANDIDATE_SCOPE_MISMATCH");
            }
        }
        switch (checkpoint.getPhase()) {
            case SELECT_NEXT:
                return selectNext(nonNull, checkpoint);
            case FRONTEND:
                return providerSteps.fetchFrontend(nonNull, checkpoint);
            case PARTNER:
                return providerSteps.fetchPartner(nonNull, checkpoint);
            case APPLY:
                return apply(nonNull, checkpoint);
            default:
                return failed(checkpoint, "DP05_UNKNOWN_PHASE");
        }
    }

    private AdvanceResult selectNext(ExecutionContext context, Dp05Checkpoint checkpoint) {
        ProductPublicDetailCandidate candidate = productCursor.next(
                context.getScope(),
                checkpoint.getAfterOfferId()
        );
        if (candidate == null) {
            return AdvanceResult.succeeded();
        }
        String mismatch = Dp05JobSupport.candidateMismatch(context.getScope(), checkpoint, candidate);
        if (mismatch != null) {
            return waitingLocal(checkpoint, mismatch);
        }
        long offerId = candidate.getProductSiteOfferId();
        String productCode = NoonProductCodeSupport.normalize(candidate.getNoonProductCode());
        if (!StringUtils.hasText(productCode)
                || NoonProductCodeSupport.codeType(productCode).isEmpty()) {
            return queued(Dp05Checkpoint.next(offerId));
        }
        return queued(Dp05Checkpoint.frontend(checkpoint.getAfterOfferId(), candidate));
    }

    private AdvanceResult apply(ExecutionContext context, Dp05Checkpoint checkpoint) {
        NoonPublicProductDetailResult result = checkpoint.getDetailResult();
        if (result == null || !Dp05JobSupport.isPersistable(result.getStatus())) {
            return waitingLocal(checkpoint, "DP05_APPLY_INVALID_FACT_STATUS");
        }
        ProductPublicDetailCandidate candidate = checkpoint.getCandidate();
        if (!Dp05JobSupport.hasExactProductIdentity(candidate, result)) {
            return queued(Dp05Checkpoint.next(candidate.getProductSiteOfferId()));
        }
        Dp05ProductDetailFactWriter.ApplyResult applied;
        try {
            applied = Objects.requireNonNull(
                    factWriter.apply(
                            context.getTask(),
                            candidate,
                            result,
                            Dp05JobSupport.factDate(context),
                            context.getScope().getOwnerUserId()
                    ),
                    "DP05 fact apply result"
            );
        } catch (RuntimeException unknownLocalOutcome) {
            return AdvanceResult.waitingRemote(
                    checkpoint.getPhase().name(),
                    null,
                    checkpointCodec.encode(checkpoint),
                    LOCAL_RETRY_DELAY,
                    "DP05_APPLY_OUTCOME_UNKNOWN"
            );
        }
        if (applied == Dp05ProductDetailFactWriter.ApplyResult.STALE_FENCE) {
            return waitingLocal(checkpoint, "DP05_APPLY_STALE_FENCE");
        }
        return queued(Dp05Checkpoint.next(candidate.getProductSiteOfferId()));
    }

    private AdvanceResult queued(Dp05Checkpoint checkpoint) {
        return AdvanceResult.queued(checkpoint.getPhase().name(), null, checkpointCodec.encode(checkpoint));
    }

    private AdvanceResult failed(Dp05Checkpoint checkpoint, String code) {
        return AdvanceResult.failed(
                checkpoint.getPhase().name(),
                null,
                checkpointCodec.encode(checkpoint),
                code
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
