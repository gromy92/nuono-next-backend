package com.nuono.next.datapull.orchestration;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** One private technical scheduler for reconciliation and bounded dispatch only. */
public final class DataPullRuntimeScheduler implements SmartLifecycle {
    static final String THREAD_NAME_PREFIX = "dp-runtime-scheduler-";
    private static final Duration STOP_BUDGET = Duration.ofSeconds(15);
    private final Runnable periodicTick;
    private final Runnable immediateDispatch;
    private final DataPullRuntimeProperties properties;
    private final Clock clock;
    private final Supplier<ThreadPoolTaskScheduler> schedulerFactory;
    private final DataPullRuntimeStopSignal stopSignal;
    private final Runnable startupLeadershipAcquire;
    private final Runnable leadershipRelease;
    private final Duration schedulerActionBudget;
    private final Duration stopBudget;
    private final boolean coordinatorOwnsPhaseDeadlines;
    private final Object monitor = new Object();
    private final Object executionMonitor = new Object();
    private final AtomicBoolean immediateDispatchRequested = new AtomicBoolean();
    private final AtomicBoolean immediateDispatchScheduled = new AtomicBoolean();
    private volatile boolean running;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> scheduledFuture;
    public DataPullRuntimeScheduler(
            DataPullRuntimeCoordinator coordinator,
            DataPullRuntimeProperties properties,
            Clock clock,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory
    ) {
        this(coordinator, properties, clock, schedulerFactory,
                Objects.requireNonNull(coordinator, "coordinator").stopSignal());
    }

    DataPullRuntimeScheduler(
            DataPullRuntimeCoordinator coordinator,
            DataPullRuntimeProperties properties,
            Clock clock,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory,
            DataPullRuntimeStopSignal stopSignal
    ) {
        this(coordinator, properties, clock, schedulerFactory, stopSignal,
                STOP_BUDGET);
    }

    DataPullRuntimeScheduler(
            DataPullRuntimeCoordinator coordinator,
            DataPullRuntimeProperties properties,
            Clock clock,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory,
            DataPullRuntimeStopSignal stopSignal,
            Duration stopBudget
    ) {
        DataPullRuntimeCoordinator target = Objects.requireNonNull(coordinator, "coordinator");
        this.periodicTick = target::tick;
        this.immediateDispatch = target::dispatchAvailable;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.schedulerFactory = Objects.requireNonNull(schedulerFactory, "schedulerFactory");
        this.stopSignal = Objects.requireNonNull(stopSignal, "stopSignal");
        this.startupLeadershipAcquire = target.leadership()::acquireOrRenew;
        this.leadershipRelease = target::releaseLeadership;
        this.schedulerActionBudget = Duration.ofSeconds(
                DataPullRuntimeProperties.SCHEDULER_PHASE_BUDGET_SECONDS);
        this.stopBudget = DataPullRuntimeSchedulerSupport.requirePositive(stopBudget);
        this.coordinatorOwnsPhaseDeadlines = true;
        target.installImmediateDispatchTrigger(this::requestImmediateDispatch);
    }

    DataPullRuntimeScheduler(
            Runnable periodicTick,
            Runnable immediateDispatch,
            Consumer<Runnable> immediateDispatchTriggerInstaller,
            DataPullRuntimeProperties properties,
            Clock clock,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory
    ) {
        this(periodicTick, immediateDispatch, immediateDispatchTriggerInstaller,
                properties, clock, schedulerFactory, () -> { }, new DataPullRuntimeStopSignal(),
                Duration.ofSeconds(DataPullRuntimeProperties.SCHEDULER_PHASE_BUDGET_SECONDS),
                STOP_BUDGET);
    }

    DataPullRuntimeScheduler(
            Runnable periodicTick,
            Runnable immediateDispatch,
            Consumer<Runnable> immediateDispatchTriggerInstaller,
            DataPullRuntimeProperties properties,
            Clock clock,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory,
            Runnable startupLeadershipAcquire,
            DataPullRuntimeStopSignal stopSignal,
            Duration schedulerActionBudget,
            Duration stopBudget
    ) {
        this.periodicTick = Objects.requireNonNull(periodicTick, "periodicTick");
        this.immediateDispatch = Objects.requireNonNull(immediateDispatch, "immediateDispatch");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.schedulerFactory = Objects.requireNonNull(schedulerFactory, "schedulerFactory");
        this.stopSignal = Objects.requireNonNull(stopSignal, "stopSignal");
        this.startupLeadershipAcquire = Objects.requireNonNull(
                startupLeadershipAcquire, "startupLeadershipAcquire");
        this.leadershipRelease = () -> { };
        this.schedulerActionBudget = DataPullRuntimeSchedulerSupport.requirePositive(
                schedulerActionBudget);
        this.stopBudget = DataPullRuntimeSchedulerSupport.requirePositive(stopBudget);
        this.coordinatorOwnsPhaseDeadlines = false;
        Objects.requireNonNull(immediateDispatchTriggerInstaller,
                "immediateDispatchTriggerInstaller").accept(this::requestImmediateDispatch);
    }

    @Override
    public void start() {
        synchronized (monitor) {
            if (running) {
                return;
            }
            properties.validate();
            stopSignal.markRunning();
            immediateDispatchRequested.set(false);
            immediateDispatchScheduled.set(false);
            ThreadPoolTaskScheduler candidate = Objects.requireNonNull(schedulerFactory.get(),
                    "DP scheduler factory returned null");
            DataPullRuntimeSchedulerSupport.configure(candidate);
            candidate.setClock(clock);
            candidate.initialize();
            try {
                acquireLeadershipAtStartup();
                ScheduledFuture<?> future = candidate.scheduleWithFixedDelay(
                        this::runSafely,
                        Date.from(clock.instant().plusMillis(properties.getSchedulerInitialDelayMs())),
                        properties.getSchedulerFixedDelayMs()
                );
                if (future == null) {
                    throw new IllegalStateException("DP scheduler did not create its periodic tick");
                }
                scheduler = candidate;
                scheduledFuture = future;
                running = true;
            } catch (RuntimeException failure) {
                stopSignal.markStopping();
                candidate.shutdown();
                leadershipRelease.run();
                throw failure;
            }
        }
    }

    @Override
    public void stop() {
        stopSignal.markStopping();
        synchronized (monitor) {
            running = false;
            immediateDispatchRequested.set(false);
            immediateDispatchScheduled.set(false);
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }
            if (scheduler != null) {
                scheduler.shutdown();
                scheduler = null;
            }
        }
        stopSignal.awaitQuiescence(stopBudget);
        leadershipRelease.run();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }

    void runSafely() {
        try {
            synchronized (executionMonitor) {
                if (isRunningForExecution()) {
                    runScheduled(periodicTick);
                }
            }
        } catch (RuntimeException failure) {
            DataPullRuntimeSchedulerSupport.warn(
                    "DP runtime tick failed and will be retried", failure);
        }
    }

    void requestImmediateDispatch() {
        if (stopSignal.isStopping()) {
            immediateDispatchRequested.set(false);
            return;
        }
        immediateDispatchRequested.set(true);
        ThreadPoolTaskScheduler target;
        synchronized (monitor) {
            if (!running || scheduler == null) {
                immediateDispatchRequested.set(false);
                return;
            }
            if (!immediateDispatchScheduled.compareAndSet(false, true)) {
                return;
            }
            target = scheduler;
        }
        try {
            ScheduledFuture<?> future = target.schedule(
                    this::runImmediateDispatchSafely,
                    Date.from(clock.instant())
            );
            if (future == null) {
                throw new IllegalStateException("DP scheduler did not create an immediate dispatch");
            }
            synchronized (monitor) {
                if (!running || scheduler != target) {
                    future.cancel(true);
                    immediateDispatchScheduled.set(false);
                    return;
                }
            }
        } catch (RuntimeException schedulingFailure) {
            immediateDispatchScheduled.set(false);
            DataPullRuntimeSchedulerSupport.warn(
                    "DP immediate dispatch could not be scheduled; periodic dispatch remains available",
                    schedulingFailure);
        }
    }

    private void runImmediateDispatchSafely() {
        immediateDispatchRequested.set(false);
        try {
            synchronized (executionMonitor) {
                if (isRunningForExecution()) {
                    runScheduled(immediateDispatch);
                }
            }
        } catch (RuntimeException failure) {
            DataPullRuntimeSchedulerSupport.warn(
                    "DP immediate dispatch failed and periodic dispatch remains available", failure);
        } finally {
            boolean reschedule;
            synchronized (monitor) {
                immediateDispatchScheduled.set(false);
                reschedule = running
                        && !stopSignal.isStopping()
                        && immediateDispatchRequested.get();
            }
            if (reschedule) {
                requestImmediateDispatch();
            }
        }
    }

    private boolean isRunningForExecution() {
        synchronized (monitor) {
            return running && !stopSignal.isStopping();
        }
    }

    private void runBounded(Runnable action) {
        try (DataPullAdvanceDeadline ignored = DataPullAdvanceDeadline.open(
                schedulerActionBudget,
                stopSignal
        )) {
            if (stopSignal.isStopping() || ignored.isExpired()) return;
            action.run();
            DataPullAdvanceDeadline.requireRemaining();
        }
    }

    private void runScheduled(Runnable action) {
        if (coordinatorOwnsPhaseDeadlines) action.run();
        else runBounded(action);
    }

    private void acquireLeadershipAtStartup() {
        try {
            runBounded(startupLeadershipAcquire);
        } catch (RuntimeException failure) {
            DataPullRuntimeSchedulerSupport.warn(
                    "DP startup leader acquisition failed; periodic retry remains available",
                    failure);
        }
    }
}
