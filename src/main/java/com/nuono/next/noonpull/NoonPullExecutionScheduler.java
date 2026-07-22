package com.nuono.next.noonpull;

import java.time.Clock;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Keeps Noon pull I/O away from Spring's shared single-thread scheduler.
 */
@Component
@Profile("local-db")
public class NoonPullExecutionScheduler implements SmartLifecycle {
    static final String THREAD_NAME_PREFIX = "noon-pull-execution-";
    private static final Logger LOGGER = LoggerFactory.getLogger(NoonPullExecutionScheduler.class);

    private final NoonPullScheduledExecutionService service;
    private final Clock clock;
    private final long initialDelayMillis;
    private final long fixedDelayMillis;
    private final Supplier<ThreadPoolTaskScheduler> schedulerFactory;
    private final Object lifecycleMonitor = new Object();

    private volatile boolean running;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> scheduledFuture;

    @Autowired
    public NoonPullExecutionScheduler(
            NoonPullScheduledExecutionService service,
            TaskSchedulerBuilder schedulerBuilder,
            @Value("${nuono.noon.pull.scheduler.initial-delay-ms:60000}") long initialDelayMillis,
            @Value("${nuono.noon.pull.scheduler.fixed-delay-ms:300000}") long fixedDelayMillis
    ) {
        this(
                service,
                Clock.systemUTC(),
                initialDelayMillis,
                fixedDelayMillis,
                () -> schedulerBuilder.poolSize(1).threadNamePrefix(THREAD_NAME_PREFIX).build()
        );
    }

    NoonPullExecutionScheduler(
            NoonPullScheduledExecutionService service,
            Clock clock,
            long initialDelayMillis,
            long fixedDelayMillis,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory
    ) {
        this.service = service;
        this.clock = clock;
        this.initialDelayMillis = Math.max(0L, initialDelayMillis);
        this.fixedDelayMillis = Math.max(1L, fixedDelayMillis);
        this.schedulerFactory = schedulerFactory;
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            ThreadPoolTaskScheduler candidate = schedulerFactory.get();
            if (candidate == null) {
                throw new IllegalStateException("Noon pull scheduler factory returned null.");
            }
            configure(candidate);
            candidate.initialize();
            try {
                ScheduledFuture<?> future = candidate.scheduleWithFixedDelay(
                        this::runSafely,
                        Date.from(clock.instant().plusMillis(initialDelayMillis)),
                        fixedDelayMillis
                );
                if (future == null) {
                    throw new IllegalStateException("Noon pull scheduler did not create a periodic task.");
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
            service.runScheduledTick();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Noon pull tick failed; the private scheduler will retry. errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
