package com.nuono.next.noonauth;

import java.time.Clock;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Runs the potentially long-lived mailbox recovery loop outside Spring's shared scheduler.
 *
 * <p>The scheduler is deliberately a private lifecycle resource rather than a {@code TaskScheduler}
 * bean. Registering a dedicated scheduler bean would make Spring Boot back off its default
 * scheduler and could route every other {@code @Scheduled} task through this single recovery
 * thread.</p>
 */
@Component
@Profile("local-db")
public class NoonAuthRecoveryScheduler implements SmartLifecycle {
    static final String THREAD_NAME_PREFIX = "noon-auth-recovery-";
    private static final Logger LOGGER = LoggerFactory.getLogger(NoonAuthRecoveryScheduler.class);

    private final NoonAuthRecoveryWorker worker;
    private final NoonAuthRecoveryProperties properties;
    private final Clock clock;
    private final Supplier<ThreadPoolTaskScheduler> schedulerFactory;
    private final Object lifecycleMonitor = new Object();

    private volatile boolean running;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> scheduledFuture;

    @Autowired
    public NoonAuthRecoveryScheduler(
            NoonAuthRecoveryWorker worker,
            NoonAuthRecoveryProperties properties,
            TaskSchedulerBuilder schedulerBuilder
    ) {
        this(
                worker,
                properties,
                Clock.systemUTC(),
                () -> schedulerBuilder.poolSize(1).threadNamePrefix(THREAD_NAME_PREFIX).build()
        );
    }

    NoonAuthRecoveryScheduler(
            NoonAuthRecoveryWorker worker,
            NoonAuthRecoveryProperties properties,
            Clock clock,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory
    ) {
        this.worker = worker;
        this.properties = properties;
        this.clock = clock;
        this.schedulerFactory = schedulerFactory;
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            worker.validateEnabledConfiguration();
            ThreadPoolTaskScheduler candidate = schedulerFactory.get();
            if (candidate == null) {
                throw new IllegalStateException("Noon auth recovery scheduler factory returned null.");
            }
            configure(candidate);
            candidate.initialize();
            try {
                Date firstRun = Date.from(clock.instant().plusMillis(properties.getSchedulerInitialDelayMs()));
                ScheduledFuture<?> future = candidate.scheduleWithFixedDelay(
                        this::runSafely,
                        firstRun,
                        properties.getSchedulerFixedDelayMs()
                );
                if (future == null) {
                    throw new IllegalStateException("Noon auth recovery scheduler did not create a periodic task.");
                }
                scheduler = candidate;
                scheduledFuture = future;
                running = true;
            } catch (RuntimeException exception) {
                candidate.shutdown();
                throw exception;
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (!running && scheduler == null) {
                return;
            }
            running = false;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }
            if (scheduler != null) {
                scheduler.shutdown();
                scheduler = null;
            }
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void configure(ThreadPoolTaskScheduler candidate) {
        candidate.setPoolSize(1);
        candidate.setThreadNamePrefix(THREAD_NAME_PREFIX);
        candidate.setRemoveOnCancelPolicy(true);
        candidate.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        candidate.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        candidate.setWaitForTasksToCompleteOnShutdown(false);
    }

    private void runSafely() {
        try {
            worker.runOnce();
        } catch (RuntimeException exception) {
            // A ScheduledExecutor suppresses every later execution after an uncaught exception.
            // Do not include the exception message/stack because persistence failures can echo
            // SQL parameters containing authentication material.
            LOGGER.warn(
                    "Noon auth recovery tick failed; the private scheduler will retry. errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
