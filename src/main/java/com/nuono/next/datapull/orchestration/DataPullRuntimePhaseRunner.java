package com.nuono.next.datapull.orchestration;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** Opens one independent monotonic deadline around one scheduler database phase. */
final class DataPullRuntimePhaseRunner {

    private DataPullRuntimePhaseRunner() { }

    static <T> T call(
            Duration budget,
            DataPullRuntimeStopSignal stopSignal,
            Supplier<T> action
    ) {
        Objects.requireNonNull(action, "action");
        try (DataPullAdvanceDeadline ignored = DataPullAdvanceDeadline.open(
                DataPullRuntimeSchedulerSupport.requirePositive(budget),
                Objects.requireNonNull(stopSignal, "stopSignal")
        )) {
            if (stopSignal.isStopping() || ignored.isExpired()) {
                throw new IllegalStateException("DP_RUNTIME_STOPPING");
            }
            T result = action.get();
            if (!stopSignal.isStopping()) DataPullAdvanceDeadline.requireRemaining();
            return result;
        }
    }
}
