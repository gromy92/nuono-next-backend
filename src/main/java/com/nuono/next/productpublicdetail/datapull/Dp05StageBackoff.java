package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.orchestration.BackoffHoldStore;
import com.nuono.next.datapull.orchestration.DataPullBackoffIdentity;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import java.time.Duration;
import java.util.Objects;

/**
 * Persists front and Partner holds under separate provider channels.
 *
 * <p>A DP-05 task crosses two channels while the generic task row has one immutable channel, so
 * this operation-level seam checks and records the precise stage identity before every call.</p>
 */
public final class Dp05StageBackoff {

    public enum Stage {
        FRONTEND("NOON_CONSUMER_FRONTEND"),
        PARTNER("NOON_PARTNER_CATALOG");

        private final String providerChannel;

        Stage(String providerChannel) {
            this.providerChannel = providerChannel;
        }
    }

    private static final Duration HOLD_RECHECK_DELAY = Duration.ofMinutes(1);

    private final BackoffHoldStore holdStore;
    private final ProviderWaitTransition providerWaitTransition;

    public Dp05StageBackoff(
            BackoffHoldStore holdStore,
            ProviderWaitTransition providerWaitTransition
    ) {
        this.holdStore = Objects.requireNonNull(holdStore, "holdStore");
        this.providerWaitTransition = Objects.requireNonNull(
                providerWaitTransition,
                "providerWaitTransition"
        );
    }

    public AdvanceResult waitIfHeld(
            Stage stage,
            ExecutionContext context,
            String stepCode,
            String checkpoint
    ) {
        DataPullBackoffIdentity identity = identity(stage, context);
        boolean held = holdStore.isHeld(RiskShareLevel.EXACT, identity, context.getNowUtc())
                || holdStore.isHeld(RiskShareLevel.ACCOUNT, identity, context.getNowUtc())
                || (identity.getEgressKey() != null
                && holdStore.isHeld(RiskShareLevel.EXIT, identity, context.getNowUtc()));
        return held
                ? AdvanceResult.waitingRemote(
                        stepCode,
                        null,
                        checkpoint,
                        HOLD_RECHECK_DELAY,
                        "DP05_STAGE_BACKOFF_ACTIVE"
                )
                : null;
    }

    public AdvanceResult recordAndWait(
            Stage stage,
            ExecutionContext context,
            String stepCode,
            String checkpoint,
            ProviderOutcome<?> outcome,
            int consecutiveAttempt
    ) {
        return providerWaitTransition.waitFor(
                context.getTask(),
                OperationCode.DP05,
                outcome,
                consecutiveAttempt,
                stepCode,
                null,
                checkpoint,
                Objects.requireNonNull(stage, "stage").providerChannel
        );
    }

    private DataPullBackoffIdentity identity(Stage stage, ExecutionContext context) {
        DataPullTask task = new DataPullTask();
        task.setProviderChannel(Objects.requireNonNull(stage, "stage").providerChannel);
        task.setAccountKey(context.getScope().getAccountKey());
        task.setOperationCode(OperationCode.DP05);
        task.setScopeKey(context.getScope().getStableScopeKey());
        task.setEgressKey(context.getScope().getEgressKey());
        return DataPullBackoffIdentity.from(task);
    }
}
