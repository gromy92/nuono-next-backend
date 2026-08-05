package com.nuono.next.datapull.runtime;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.Duration;
import java.util.Objects;

/** Translates a retryable provider outcome into one durable, non-blocking wait. */
public final class ProviderWaitTransition {

    private static final Duration RECHECK_DELAY = Duration.ofMinutes(5);

    private final BackoffPolicy backoffPolicy;

    public ProviderWaitTransition(BackoffPolicy backoffPolicy) {
        this.backoffPolicy = Objects.requireNonNull(backoffPolicy, "backoffPolicy");
    }

    public AdvanceResult waitFor(
            DataPullTask task,
            OperationCode operation,
            ProviderOutcome<?> outcome,
            int consecutiveAttempt,
            String stepCode,
            String remoteHandle,
            String checkpoint,
            String providerChannelOverride
    ) {
        ProviderOutcome<?> result = requireWaitingOutcome(outcome);
        if (result.getType() == ProviderOutcomeType.AUTH_REQUIRED) {
            return AdvanceResult.waitingAuth(
                    stepCode,
                    remoteHandle,
                    checkpoint,
                    RECHECK_DELAY,
                    result.getSanitizedCode()
            );
        }
        BackoffKey key = validatedBackoffContext(
                task, operation, result, providerChannelOverride
        );
        if (key == null) {
            return AdvanceResult.failed(
                    stepCode,
                    remoteHandle,
                    checkpoint,
                    "PROVIDER_BACKOFF_CONTEXT_INVALID"
            );
        }
        Duration delay = backoffPolicy.delayFor(result, key, consecutiveAttempt);
        RiskShareLevel shareLevel = backoffPolicy.shareLevelFor(result, key);
        return providerChannelOverride == null
                ? AdvanceResult.waitingBackoff(
                        stepCode,
                        remoteHandle,
                        checkpoint,
                        delay,
                        result.getSanitizedCode(),
                        shareLevel
                )
                : AdvanceResult.waitingBackoffForProvider(
                        key.getProviderChannel(),
                        stepCode,
                        remoteHandle,
                        checkpoint,
                        delay,
                        result.getSanitizedCode(),
                        shareLevel
                );
    }

    private BackoffKey validatedBackoffContext(
            DataPullTask task,
            OperationCode operation,
            ProviderOutcome<?> outcome,
            String providerChannelOverride
    ) {
        if (task == null || operation == null || task.getOperationCode() != operation) {
            return null;
        }
        BackoffKey key;
        try {
            key = new BackoffKey(
                    providerChannelOverride == null
                            ? task.getProviderChannel()
                            : providerChannelOverride,
                    task.getAccountKey(),
                    operation,
                    task.getScopeKey(),
                    task.getEgressKey()
            );
        } catch (NullPointerException | IllegalArgumentException invalidIdentity) {
            return null;
        }
        if (outcome.getType() == ProviderOutcomeType.RISK_CONTROL
                && outcome.getShareLevel() == RiskShareLevel.EXIT
                && key.getEgressKey() == null) {
            return null;
        }
        return key;
    }

    private ProviderOutcome<?> requireWaitingOutcome(ProviderOutcome<?> outcome) {
        ProviderOutcome<?> result = Objects.requireNonNull(outcome, "outcome");
        if (result.getType() != ProviderOutcomeType.AUTH_REQUIRED
                && result.getType() != ProviderOutcomeType.CONTRACT_ERROR
                && result.getType() != ProviderOutcomeType.RISK_CONTROL
                && result.getType() != ProviderOutcomeType.TRANSIENT) {
            throw new IllegalArgumentException(
                    "provider wait accepts only AUTH_REQUIRED, CONTRACT_ERROR, RISK_CONTROL or TRANSIENT"
            );
        }
        return result;
    }
}
