package com.nuono.next.datapull.orchestration;

import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Construction helpers kept outside the coordinator's phase and worker state machine. */
final class DataPullRuntimeCoordinatorSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataPullRuntimeCoordinator.class);

    private DataPullRuntimeCoordinatorSupport() { }

    static Duration positive(Duration value, String name) {
        Duration nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isZero() || nonNull.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return nonNull;
    }

    static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    static DataPullRuntimeReconciler reconcile(DataPullRuntimeReconciler reconciler) {
        return Objects.requireNonNull(reconciler, "reconciler");
    }

    static DataPullRuntimeCoordinator.DispatchAction dispatch(FairDispatcher dispatcher) {
        FairDispatcher target = Objects.requireNonNull(dispatcher, "dispatcher");
        return new DataPullRuntimeCoordinator.DispatchAction() {
            @Override
            public java.util.List<com.nuono.next.datapull.persistence.DataPullTask> dispatchDue(
                    java.time.LocalDateTime nowUtc,
                    int maximumClaims,
                    Duration leaseDuration,
                    com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease leaderLease
            ) {
                return target.dispatchDue(nowUtc, maximumClaims, leaseDuration, leaderLease);
            }

            @Override
            public boolean releaseUnstartedClaim(
                    com.nuono.next.datapull.persistence.DataPullTask task,
                    java.time.Instant observedAt
            ) {
                return target.releaseUnstartedClaim(task, observedAt);
            }
        };
    }

    static void warnMaintenance(DataPullRuntimeMaintenance action, RuntimeException failure) {
        LOGGER.warn("DP runtime maintenance failed maintenance={} errorType={}",
                action.getClass().getSimpleName(), failure.getClass().getSimpleName());
    }

    static void warnAdvance(
            com.nuono.next.datapull.persistence.DataPullTask task,
            RuntimeException failure
    ) {
        LOGGER.warn("DP runtime advance failed taskId={} operation={} errorType={}",
                task.getId(), task.getOperationCode(), failure.getClass().getSimpleName());
    }

    static void warnSubmission(
            com.nuono.next.datapull.persistence.DataPullTask task,
            RuntimeException failure
    ) {
        LOGGER.warn("DP worker submission rejected; exact unstarted release was attempted and "
                        + "lease recovery remains the fallback taskId={} errorType={}",
                task.getId(), failure.getClass().getSimpleName());
    }

    static void warnUnstartedRelease(
            com.nuono.next.datapull.persistence.DataPullTask task,
            RuntimeException failure
    ) {
        LOGGER.warn("DP unstarted claim release failed; lease recovery remains available "
                        + "taskId={} errorType={}",
                task.getId(), failure.getClass().getSimpleName());
    }

    static void warnTrigger(RuntimeException failure) {
        LOGGER.warn("DP immediate dispatch trigger failed; periodic dispatch remains available "
                        + "errorType={}", failure.getClass().getSimpleName());
    }
}
