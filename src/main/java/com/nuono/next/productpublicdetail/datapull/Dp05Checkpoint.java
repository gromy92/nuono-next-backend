package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.util.Objects;

/** Durable item cursor and phase for one DP-05 scope/day. */
public final class Dp05Checkpoint {

    public enum Phase {
        SELECT_NEXT,
        FRONTEND,
        PARTNER,
        APPLY
    }

    private static final int VERSION = 1;

    private int version = VERSION;
    private Phase phase = Phase.SELECT_NEXT;
    private long afterOfferId;
    private ProductPublicDetailCandidate candidate;
    private NoonPublicProductDetailResult detailResult;
    private int consecutiveRetryAttempt;

    public static Dp05Checkpoint initial() {
        return new Dp05Checkpoint();
    }

    public static Dp05Checkpoint frontend(long afterOfferId, ProductPublicDetailCandidate candidate) {
        return item(Phase.FRONTEND, afterOfferId, candidate, null);
    }

    public static Dp05Checkpoint partner(long afterOfferId, ProductPublicDetailCandidate candidate) {
        return item(Phase.PARTNER, afterOfferId, candidate, null);
    }

    public static Dp05Checkpoint apply(
            long afterOfferId,
            ProductPublicDetailCandidate candidate,
            NoonPublicProductDetailResult result
    ) {
        return item(Phase.APPLY, afterOfferId, candidate, Objects.requireNonNull(result, "result"));
    }

    public static Dp05Checkpoint next(long processedOfferId) {
        Dp05Checkpoint checkpoint = new Dp05Checkpoint();
        checkpoint.afterOfferId = requireCursor(processedOfferId);
        return checkpoint;
    }

    public Dp05Checkpoint retry() {
        if (phase != Phase.FRONTEND && phase != Phase.PARTNER) {
            throw new IllegalStateException("only a DP05 provider phase can be retried");
        }
        Dp05Checkpoint checkpoint = item(phase, afterOfferId, candidate, null);
        checkpoint.consecutiveRetryAttempt = consecutiveRetryAttempt == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : consecutiveRetryAttempt + 1;
        return checkpoint.validate();
    }

    private static Dp05Checkpoint item(
            Phase phase,
            long afterOfferId,
            ProductPublicDetailCandidate candidate,
            NoonPublicProductDetailResult result
    ) {
        Dp05Checkpoint checkpoint = new Dp05Checkpoint();
        checkpoint.phase = Objects.requireNonNull(phase, "phase");
        checkpoint.afterOfferId = requireCursor(afterOfferId);
        checkpoint.candidate = Objects.requireNonNull(candidate, "candidate");
        checkpoint.detailResult = result;
        checkpoint.validate();
        return checkpoint;
    }

    public Dp05Checkpoint validate() {
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported DP05 checkpoint version");
        }
        Objects.requireNonNull(phase, "phase");
        requireCursor(afterOfferId);
        if (consecutiveRetryAttempt < 0) {
            throw new IllegalArgumentException("DP05 retry attempt must not be negative");
        }
        if (phase != Phase.FRONTEND && phase != Phase.PARTNER
                && consecutiveRetryAttempt != 0) {
            throw new IllegalArgumentException("only a DP05 provider phase may retain retries");
        }
        boolean itemPhase = phase != Phase.SELECT_NEXT;
        if (itemPhase != (candidate != null)) {
            throw new IllegalArgumentException("DP05 item phases require exactly one candidate");
        }
        if (phase == Phase.APPLY) {
            Objects.requireNonNull(detailResult, "detailResult");
        } else if (detailResult != null) {
            throw new IllegalArgumentException("only DP05 APPLY may retain a detail result");
        }
        if (candidate != null) {
            Long offerId = candidate.getProductSiteOfferId();
            if (offerId == null || offerId <= afterOfferId) {
                throw new IllegalArgumentException("DP05 candidate must be after the durable offer cursor");
            }
        }
        return this;
    }

    private static long requireCursor(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("DP05 cursor must not be negative");
        }
        return value;
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }
    public long getAfterOfferId() { return afterOfferId; }
    public void setAfterOfferId(long afterOfferId) { this.afterOfferId = afterOfferId; }
    public ProductPublicDetailCandidate getCandidate() { return candidate; }
    public void setCandidate(ProductPublicDetailCandidate candidate) { this.candidate = candidate; }
    public NoonPublicProductDetailResult getDetailResult() { return detailResult; }
    public void setDetailResult(NoonPublicProductDetailResult detailResult) { this.detailResult = detailResult; }
    public int getConsecutiveRetryAttempt() { return consecutiveRetryAttempt; }
    public void setConsecutiveRetryAttempt(int value) { this.consecutiveRetryAttempt = value; }
}
