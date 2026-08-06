package com.nuono.next.datapull.runtime;

import java.util.Objects;

/**
 * Exhaustive provider-failure policy that keeps retry exhaustion out of the runtime.
 *
 * <p>An Adapter may return {@link ProviderOutcomeType#CONTRACT_ERROR} only when the
 * response or immutable container deterministically violates its contract. It remains
 * non-terminal because a later provider response or a repaired deployment may satisfy the
 * contract; retry uses exact-scope persistent backoff and never applies the bad container.
 * Transport, risk, authorization and unclassified failures have their own non-terminal types.
 * {@code NOT_FOUND} remains DP-specific, so every call site must declare whether it is
 * terminal, means retrying the same resource, or belongs to unknown-outcome reconciliation.</p>
 */
public final class ContractFailurePolicy {

    public enum NotFoundHandling {
        FAIL_TASK,
        RETRY_SAME_RESOURCE,
        WAIT_RECONCILE
    }

    public enum Decision {
        FAIL_TASK,
        RETRY_WITH_BACKOFF,
        WAIT_AUTH,
        WAIT_RECONCILE,
        RETRY_SAME_RESOURCE
    }

    private ContractFailurePolicy() {
    }

    public static Decision decide(
            ProviderOutcome<?> outcome,
            NotFoundHandling notFoundHandling
    ) {
        ProviderOutcomeType type = Objects.requireNonNull(outcome, "outcome").getType();
        NotFoundHandling notFound = Objects.requireNonNull(
                notFoundHandling,
                "notFoundHandling"
        );
        switch (type) {
            case NOT_FOUND:
                return notFoundDecision(notFound);
            case CONTRACT_ERROR:
            case RISK_CONTROL:
            case TRANSIENT:
                return Decision.RETRY_WITH_BACKOFF;
            case AUTH_REQUIRED:
                return Decision.WAIT_AUTH;
            case UNKNOWN_OUTCOME:
                return Decision.WAIT_RECONCILE;
            case SUCCESS:
            default:
                throw new IllegalArgumentException("successful provider outcome has no failure policy");
        }
    }

    private static Decision notFoundDecision(NotFoundHandling handling) {
        switch (handling) {
            case FAIL_TASK:
                return Decision.FAIL_TASK;
            case RETRY_SAME_RESOURCE:
                return Decision.RETRY_SAME_RESOURCE;
            case WAIT_RECONCILE:
                return Decision.WAIT_RECONCILE;
            default:
                throw new IllegalArgumentException("unsupported NOT_FOUND handling");
        }
    }
}
