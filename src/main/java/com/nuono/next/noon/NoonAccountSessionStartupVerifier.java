package com.nuono.next.noon;

import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
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

/** Runs one asynchronous full-scope session audit shortly after the Java service starts. */
@Component
@Profile("local-db")
final class NoonAccountSessionStartupVerifier implements SmartLifecycle {
    static final String THREAD_NAME_PREFIX = "noon-session-startup-audit-";
    private static final Logger LOGGER = LoggerFactory.getLogger(NoonAccountSessionStartupVerifier.class);

    private final Runnable auditTask;
    private final NoonAuthRecoveryProperties properties;
    private final Clock clock;
    private final Supplier<ThreadPoolTaskScheduler> schedulerFactory;
    private final Object lifecycleMonitor = new Object();

    private volatile boolean running;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> scheduledFuture;

    @Autowired
    NoonAccountSessionStartupVerifier(
            NoonAccountSessionDailyVerifier verifier,
            NoonAuthRecoveryProperties properties,
            TaskSchedulerBuilder schedulerBuilder
    ) {
        this(
                verifier::verifyNow,
                properties,
                Clock.systemUTC(),
                () -> schedulerBuilder.poolSize(1).threadNamePrefix(THREAD_NAME_PREFIX).build()
        );
    }

    NoonAccountSessionStartupVerifier(
            Runnable auditTask,
            NoonAuthRecoveryProperties properties,
            Clock clock,
            Supplier<ThreadPoolTaskScheduler> schedulerFactory
    ) {
        this.auditTask = auditTask;
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
            running = true;
            if (!properties.isEnabled() || !properties.isStartupAuditEnabled()) {
                return;
            }
            ThreadPoolTaskScheduler candidate = schedulerFactory.get();
            if (candidate == null) {
                running = false;
                throw new IllegalStateException("Noon startup session audit scheduler is unavailable.");
            }
            configure(candidate);
            candidate.initialize();
            try {
                scheduledFuture = candidate.schedule(
                        this::runSafely,
                        Date.from(clock.instant().plusMillis(properties.getStartupAuditDelayMs()))
                );
                if (scheduledFuture == null) {
                    throw new IllegalStateException("Noon startup session audit was not scheduled.");
                }
                scheduler = candidate;
            } catch (RuntimeException exception) {
                running = false;
                candidate.shutdown();
                throw exception;
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleMonitor) {
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
    public boolean isRunning() { return running; }

    @Override
    public boolean isAutoStartup() { return true; }

    @Override
    public int getPhase() { return Integer.MAX_VALUE; }

    private void configure(ThreadPoolTaskScheduler candidate) {
        candidate.setPoolSize(1);
        candidate.setThreadNamePrefix(THREAD_NAME_PREFIX);
        candidate.setRemoveOnCancelPolicy(true);
        candidate.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        candidate.setWaitForTasksToCompleteOnShutdown(false);
    }

    private void runSafely() {
        try {
            auditTask.run();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Noon startup Project session audit failed. errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
