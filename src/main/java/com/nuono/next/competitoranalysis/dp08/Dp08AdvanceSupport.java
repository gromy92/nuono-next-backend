package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.Duration;

/** Exhaustive translation from typed provider results to durable runtime waits. */
final class Dp08AdvanceSupport {
    private static final Duration AUTH_POLL = Duration.ofMinutes(5);

    private Dp08AdvanceSupport() {
    }

    static AdvanceResult failure(
            DataPullTask task,
            OperationCode operation,
            ProviderOutcome<?> outcome,
            ProviderWaitTransition waitTransition,
            String step,
            String checkpoint
    ) {
        ProviderOutcome<?> waitOutcome;
        switch (outcome.getType()) {
            case AUTH_REQUIRED:
            case RISK_CONTROL:
            case TRANSIENT:
                waitOutcome = outcome;
                break;
            case CONTRACT_ERROR:
                waitOutcome = ProviderOutcome.transientFailure(outcome.getSanitizedCode());
                break;
            case NOT_FOUND:
            case UNKNOWN_OUTCOME:
                return AdvanceResult.waitingRemote(
                        step,
                        null,
                        checkpoint,
                        AUTH_POLL,
                        outcome.getSanitizedCode()
                );
            case SUCCESS:
            default:
                throw new IllegalArgumentException("successful DP-08 outcomes cannot wait");
        }
        return waitTransition.waitFor(
                task,
                operation,
                waitOutcome,
                Math.max(1, value(task.getAttempt()) + 1),
                step,
                null,
                checkpoint, null
        );
    }

    static AdvanceResult localRetry(
            DataPullTask task,
            OperationCode operation,
            ProviderWaitTransition waitTransition,
            String step,
            String checkpoint
    ) {
        return failure(
                task,
                operation,
                ProviderOutcome.transientFailure("DP08_LOCAL_WRITE_RETRY"),
                waitTransition,
                step,
                checkpoint
        );
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
