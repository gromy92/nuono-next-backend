package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ContractFailurePolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.Duration;
import java.util.Objects;

/** Centralizes DP-06 checkpoint, auth, local retry, and shared backoff transitions. */
final class AdvertisingJobTransitions {

    private final ProviderWaitTransition providerWaitTransition;
    private final Duration localRetryDelay;
    private final AdvertisingCheckpointCodec checkpointCodec;

    AdvertisingJobTransitions(
            ProviderWaitTransition providerWaitTransition,
            Duration localRetryDelay,
            AdvertisingCheckpointCodec checkpointCodec
    ) {
        this.providerWaitTransition = Objects.requireNonNull(
                providerWaitTransition,
                "providerWaitTransition"
        );
        this.localRetryDelay = requirePositive(localRetryDelay, "localRetryDelay");
        this.checkpointCodec = Objects.requireNonNull(checkpointCodec, "checkpointCodec");
    }

    AdvanceResult waitForProvider(
            DataPullTask task,
            AdvertisingCheckpoint checkpoint,
            ProviderOutcome<?> outcome
    ) {
        ContractFailurePolicy.Decision decision = ContractFailurePolicy.decide(
                outcome,
                ContractFailurePolicy.NotFoundHandling.RETRY_SAME_RESOURCE
        );
        if (decision == ContractFailurePolicy.Decision.FAIL_TASK) {
            return failure(checkpoint, outcome.getSanitizedCode());
        }
        AdvertisingCheckpoint retry = checkpoint.retry();
        if (decision == ContractFailurePolicy.Decision.WAIT_RECONCILE
                || decision == ContractFailurePolicy.Decision.RETRY_SAME_RESOURCE) {
            return waitingLocal(retry, outcome.getSanitizedCode());
        }
        return providerWaitTransition.waitFor(
                task,
                OperationCode.DP06,
                outcome,
                retry.getConsecutiveRetryAttempt(),
                step(checkpoint),
                null,
                checkpointCodec.encode(retry), null
        );
    }

    AdvanceResult queued(AdvertisingCheckpoint checkpoint) {
        return AdvanceResult.queued(
                step(checkpoint),
                null,
                checkpointCodec.encode(checkpoint)
        );
    }

    AdvanceResult waitingLocal(AdvertisingCheckpoint checkpoint, String code) {
        return AdvanceResult.waitingRemote(
                step(checkpoint),
                null,
                checkpointCodec.encode(checkpoint),
                localRetryDelay,
                code
        );
    }

    AdvanceResult failure(AdvertisingCheckpoint checkpoint, String code) {
        return AdvanceResult.failed(
                step(checkpoint),
                null,
                checkpointCodec.encode(checkpoint),
                code
        );
    }

    private String step(AdvertisingCheckpoint checkpoint) {
        return "ADS_" + checkpoint.getPhase().name();
    }

    private Duration requirePositive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
