package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ContractFailurePolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.Duration;
import java.util.Objects;

/** Shared persistent backoff/auth routing for FETCH and VERIFY provider calls. */
final class SnapshotProviderFailureHandler {
    private final OperationCode operationCode;
    private final SnapshotCheckpointCodec codec;
    private final ProviderWaitTransition waits;

    SnapshotProviderFailureHandler(
            OperationCode operationCode,
            SnapshotCheckpointCodec codec,
            ProviderWaitTransition waits
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.waits = Objects.requireNonNull(waits, "waits");
    }

    AdvanceResult fromOutcome(
            DataPullTask task,
            SnapshotCheckpoint checkpoint,
            ProviderOutcome<?> outcome
    ) {
        String encoded = codec.encode(checkpoint);
        ContractFailurePolicy.Decision decision = ContractFailurePolicy.decide(
                outcome, ContractFailurePolicy.NotFoundHandling.RETRY_SAME_RESOURCE
        );
        switch (decision) {
            case RETRY_WITH_BACKOFF:
                return backoff(task, checkpoint, outcome, encoded);
            case WAIT_AUTH:
                return waits.waitFor(
                        task, operationCode, outcome, 1, null, null, encoded, null
                );
            case FAIL_TASK:
                return AdvanceResult.failed(encoded, outcome.getSanitizedCode());
            case WAIT_RECONCILE:
            case RETRY_SAME_RESOURCE:
                return AdvanceResult.waitingRemote(
                        encoded, Duration.ofMinutes(5), outcome.getSanitizedCode()
                );
            default:
                throw new IllegalArgumentException("unsupported snapshot failure decision");
        }
    }

    AdvanceResult transientFailure(
            DataPullTask task,
            SnapshotCheckpoint checkpoint,
            String code
    ) {
        return fromOutcome(task, checkpoint, ProviderOutcome.transientFailure(code));
    }

    private AdvanceResult backoff(
            DataPullTask task,
            SnapshotCheckpoint checkpoint,
            ProviderOutcome<?> outcome,
            String encoded
    ) {
        SnapshotCheckpoint retry;
        try {
            retry = checkpoint.nextRetryAttempt();
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.waitingRemote(
                    encoded, Duration.ofMinutes(5), "SNAPSHOT_RETRY_CHECKPOINT_INVALID"
            );
        }
        return waits.waitFor(
                task, operationCode, outcome, retry.getConsecutiveRetryAttempt(),
                null, null, codec.encode(retry), null
        );
    }
}
