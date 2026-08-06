package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.leader.DataPullRuntimeLeadership;
import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Reconciles schedules, claims fairly, and submits bounded one-advance workers. */
public final class DataPullRuntimeCoordinator {
    private static final Runnable NO_IMMEDIATE_DISPATCH = () -> { };

    private final DataPullRuntimeReconciler reconcile;
    private final DispatchAction dispatch;
    private final RuntimeExecutor runtimeExecutor;
    private final List<DataPullRuntimeMaintenance> maintenance;
    private final Executor workerExecutor;
    private final Clock clock;
    private final DataPullRuntimeLeadership leadership;
    private final Duration leaseDuration;
    private final int maximumClaimsPerTick;
    private final int maximumInFlight;
    private final DataPullRuntimeStopSignal stopSignal;
    private final Duration phaseBudget;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicBoolean coordinating = new AtomicBoolean();
    private final AtomicReference<Runnable> immediateDispatchTrigger =
            new AtomicReference<>(NO_IMMEDIATE_DISPATCH);

    public DataPullRuntimeCoordinator(
            DataPullRuntimeReconciler reconciler,
            FairDispatcher dispatcher,
            RuntimeExecutor runtimeExecutor,
            List<DataPullRuntimeMaintenance> maintenance,
            Executor workerExecutor,
            Clock clock,
            DataPullRuntimeLeadership leadership,
            Duration leaseDuration,
            int maximumClaimsPerTick,
            int maximumInFlight
    ) {
        this(
                DataPullRuntimeCoordinatorSupport.reconcile(reconciler),
                DataPullRuntimeCoordinatorSupport.dispatch(dispatcher),
                runtimeExecutor,
                maintenance,
                workerExecutor,
                clock,
                leadership,
                leaseDuration,
                maximumClaimsPerTick,
                maximumInFlight,
                Objects.requireNonNull(runtimeExecutor, "runtimeExecutor").stopSignal()
        );
    }

    DataPullRuntimeCoordinator(
            DataPullRuntimeReconciler reconcile,
            DispatchAction dispatch,
            RuntimeExecutor runtimeExecutor,
            List<DataPullRuntimeMaintenance> maintenance,
            Executor workerExecutor,
            Clock clock,
            DataPullRuntimeLeadership leadership,
            Duration leaseDuration,
            int maximumClaimsPerTick,
            int maximumInFlight
    ) {
        this(
                reconcile, dispatch, runtimeExecutor, maintenance, workerExecutor, clock,
                leadership, leaseDuration, maximumClaimsPerTick, maximumInFlight,
                new DataPullRuntimeStopSignal(),
                Duration.ofSeconds(DataPullRuntimeProperties.SCHEDULER_PHASE_BUDGET_SECONDS)
        );
    }

    DataPullRuntimeCoordinator(
            DataPullRuntimeReconciler reconcile,
            DispatchAction dispatch,
            RuntimeExecutor runtimeExecutor,
            List<DataPullRuntimeMaintenance> maintenance,
            Executor workerExecutor,
            Clock clock,
            DataPullRuntimeLeadership leadership,
            Duration leaseDuration,
            int maximumClaimsPerTick,
            int maximumInFlight,
            DataPullRuntimeStopSignal stopSignal
    ) {
        this(
                reconcile, dispatch, runtimeExecutor, maintenance, workerExecutor, clock,
                leadership, leaseDuration, maximumClaimsPerTick, maximumInFlight, stopSignal,
                Duration.ofSeconds(DataPullRuntimeProperties.SCHEDULER_PHASE_BUDGET_SECONDS)
        );
    }

    DataPullRuntimeCoordinator(
            DataPullRuntimeReconciler reconcile,
            DispatchAction dispatch,
            RuntimeExecutor runtimeExecutor,
            List<DataPullRuntimeMaintenance> maintenance,
            Executor workerExecutor,
            Clock clock,
            DataPullRuntimeLeadership leadership,
            Duration leaseDuration,
            int maximumClaimsPerTick,
            int maximumInFlight,
            DataPullRuntimeStopSignal stopSignal,
            Duration phaseBudget
    ) {
        this.reconcile = Objects.requireNonNull(reconcile, "reconcile");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.runtimeExecutor = Objects.requireNonNull(runtimeExecutor, "runtimeExecutor");
        this.maintenance = List.copyOf(Objects.requireNonNull(maintenance, "maintenance"));
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "workerExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leadership = Objects.requireNonNull(leadership, "leadership");
        this.leaseDuration = DataPullRuntimeCoordinatorSupport.positive(
                leaseDuration, "leaseDuration");
        this.maximumClaimsPerTick = DataPullRuntimeCoordinatorSupport.positive(
                maximumClaimsPerTick, "maximumClaimsPerTick");
        this.maximumInFlight = DataPullRuntimeCoordinatorSupport.positive(
                maximumInFlight, "maximumInFlight");
        this.stopSignal = Objects.requireNonNull(stopSignal, "stopSignal");
        this.phaseBudget = DataPullRuntimeSchedulerSupport.requirePositive(phaseBudget);
    }

    public DataPullRuntimeTickResult tick() {
        if (stopSignal.isStopping()) return idleTick();
        if (!coordinating.compareAndSet(false, true)) return idleTick();
        try {
            if (stopSignal.isStopping()) return idleTick();
            DataPullRuntimeTickPhase phase = runPhase(() -> {
                Optional<DataPullRuntimeLeaderLease> lease = leadership.acquireOrRenew();
                if (!lease.isPresent()) return DataPullRuntimeTickPhase.noLeadership();
                Instant now = clock.instant();
                return DataPullRuntimeTickPhase.active(
                        lease.get(), now, reconcile.reconcileAt(now));
            });
            if (!phase.hasLeadership() || stopSignal.isStopping()) return idleTick();
            int reconciled = phase.reconciled();
            if (stopSignal.isStopping()) {
                return new DataPullRuntimeTickResult(reconciled, 0, inFlight.get());
            }
            int claimed = runPhase(() -> dispatchAvailableAt(phase.now(), phase.lease()));
            // Every successful claim must be submitted before the phase may report expiry.
            if (stopSignal.isStopping()) {
                return new DataPullRuntimeTickResult(reconciled, claimed, inFlight.get());
            }
            runPhase(() -> {
                if (leadership.isCurrent(phase.lease())) runMaintenance(phase.now());
                return null;
            });
            return new DataPullRuntimeTickResult(reconciled, claimed, inFlight.get());
        } finally {
            coordinating.set(false);
        }
    }

    /** Refills free slots without repeating reconciliation or overlapping a periodic tick. */
    public int dispatchAvailable() {
        if (stopSignal.isStopping()) return 0;
        if (!coordinating.compareAndSet(false, true)) {
            return 0;
        }
        try {
            if (stopSignal.isStopping()) return 0;
            return runPhase(() -> {
                Optional<DataPullRuntimeLeaderLease> lease = leadership.acquireOrRenew();
                if (!lease.isPresent() || stopSignal.isStopping()) return 0;
                return dispatchAvailableAt(clock.instant(), lease.get());
            });
        } finally {
            coordinating.set(false);
        }
    }

    void installImmediateDispatchTrigger(Runnable trigger) {
        Runnable nonNullTrigger = Objects.requireNonNull(trigger, "trigger");
        if (!immediateDispatchTrigger.compareAndSet(NO_IMMEDIATE_DISPATCH, nonNullTrigger)) {
            throw new IllegalStateException("the immediate DP dispatch trigger is already installed");
        }
    }

    private int dispatchAvailableAt(Instant now, DataPullRuntimeLeaderLease leaderLease) {
        if (stopSignal.isStopping()) return 0;
        int available = Math.max(0, maximumInFlight - inFlight.get());
        int claimLimit = Math.min(maximumClaimsPerTick, available);
        if (claimLimit == 0) {
            return 0;
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        List<DataPullTask> claimed = dispatch.dispatchDue(
                nowUtc,
                claimLimit,
                leaseDuration,
                leaderLease
        );
        for (DataPullTask task : claimed) {
            submit(task);
        }
        return claimed.size();
    }

    private void runMaintenance(Instant nowUtc) {
        for (DataPullRuntimeMaintenance action : maintenance) {
            if (stopSignal.isStopping()) return;
            DataPullAdvanceDeadline.requireRemaining();
            try {
                action.run(nowUtc);
            } catch (RuntimeException failure) {
                DataPullRuntimeCancellation.rethrowIfCancellation(failure, stopSignal);
                DataPullRuntimeCoordinatorSupport.warnMaintenance(action, failure);
            }
        }
        DataPullAdvanceDeadline.requireRemaining();
    }

    private void submit(DataPullTask task) {
        stopSignal.workerScheduled();
        inFlight.incrementAndGet();
        Runnable advance = () -> {
            try {
                runtimeExecutor.execute(
                        task, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            } catch (RuntimeException failure) {
                DataPullRuntimeCoordinatorSupport.warnAdvance(task, failure);
            } finally {
                inFlight.decrementAndGet();
                stopSignal.workerFinished();
                requestImmediateDispatch();
            }
        };
        try {
            workerExecutor.execute(advance);
        } catch (RuntimeException rejected) {
            inFlight.decrementAndGet();
            stopSignal.workerFinished();
            releaseUnstartedClaim(task);
            DataPullRuntimeCoordinatorSupport.warnSubmission(task, rejected);
        }
    }

    private void releaseUnstartedClaim(DataPullTask task) {
        try {
            dispatch.releaseUnstartedClaim(task, clock.instant());
        } catch (RuntimeException releaseFailure) {
            DataPullRuntimeCoordinatorSupport.warnUnstartedRelease(task, releaseFailure);
        }
    }

    private void requestImmediateDispatch() {
        if (stopSignal.isStopping()) return;
        try {
            immediateDispatchTrigger.get().run();
        } catch (RuntimeException triggerFailure) {
            DataPullRuntimeCoordinatorSupport.warnTrigger(triggerFailure);
        }
    }

    private <T> T runPhase(java.util.function.Supplier<T> action) {
        return DataPullRuntimePhaseRunner.call(phaseBudget, stopSignal, action);
    }

    DataPullRuntimeStopSignal stopSignal() {
        return stopSignal;
    }
    DataPullRuntimeLeadership leadership() { return leadership; }
    void releaseLeadership() { leadership.releaseIfOwned(); }

    private DataPullRuntimeTickResult idleTick() {
        return new DataPullRuntimeTickResult(0, 0, inFlight.get());
    }

    @FunctionalInterface
    interface DispatchAction {
        List<DataPullTask> dispatchDue(
                LocalDateTime nowUtc,
                int maximumClaims,
                Duration leaseDuration,
                DataPullRuntimeLeaderLease leaderLease
        );

        default boolean releaseUnstartedClaim(DataPullTask task, Instant observedAt) {
            return false;
        }
    }
}
