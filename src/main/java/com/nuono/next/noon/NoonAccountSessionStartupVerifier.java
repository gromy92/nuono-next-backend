package com.nuono.next.noon;

import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.nuono.next.noonauth.NoonAuthRecoveryRepository;
import com.nuono.next.noonauth.NoonAuthIdentityRecoveryRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import java.time.Clock;
import java.util.Collections;
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

/** Runs and observes one asynchronous full-scope session audit after Java starts. */
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
            NoonAuthRecoveryRepository repository,
            TaskSchedulerBuilder schedulerBuilder
    ) {
        this(
                () -> audit(verifier, repository),
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
                scheduledFuture = candidate.scheduleWithFixedDelay(
                        this::runSafely,
                        Date.from(clock.instant().plusMillis(properties.getStartupAuditDelayMs())),
                        properties.getStartupAuditPollMs()
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

    static void audit(
            NoonAccountSessionDailyVerifier verifier,
            NoonAuthRecoveryRepository repository
    ) {
        NoonAccountSessionAuditResult result = verifier.latestResult();
        if (result.isReady()) {
            return;
        }
        if ("NOT_RUN".equals(result.getStatus())) {
            verifier.verifyNow();
            return;
        }
        if (!"RECOVERY_QUEUED".equals(result.getStatus()) || result.recoveryId() == null) {
            return;
        }
        NoonAuthIdentityRecoveryRecord recovery = repository.selectRecovery(result.recoveryId());
        if (recovery == null || recovery.getStatus() == null || !recovery.getStatus().isTerminal()) {
            return;
        }
        verifier.recordRecoveryCompletion(
                recovery.getStatus() == NoonAuthRecoveryStatus.COMPLETED
                        ? repository.listRecoveryItems(result.recoveryId())
                        : Collections.emptyList()
        );
    }
}
