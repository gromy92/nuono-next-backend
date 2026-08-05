package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.schedule.DataPullCatchUpPlan;
import com.nuono.next.datapull.schedule.DataPullScheduleSlot;
import java.util.Objects;

/** One already-admitted scope paired with one exact due schedule slot. */
public final class DataPullScheduledScope {
    private final DataPullScope scope;
    private final DataPullScheduleSlot slot;
    private final DataPullCatchUpPlan.Strategy catchUpStrategy;

    public DataPullScheduledScope(
            DataPullScope scope,
            DataPullScheduleSlot slot,
            DataPullCatchUpPlan.Strategy catchUpStrategy
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.catchUpStrategy = Objects.requireNonNull(catchUpStrategy, "catchUpStrategy");
    }

    public DataPullScope getScope() { return scope; }
    public DataPullScheduleSlot getSlot() { return slot; }
    public DataPullCatchUpPlan.Strategy getCatchUpStrategy() { return catchUpStrategy; }
}
