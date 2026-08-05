package com.nuono.next.competitoranalysis;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Restores only user-requested competitor refresh work after interruption.
 * Daily DP-08 creation and compensation belong exclusively to the pull runtime.
 */
@Component
public class CompetitorManualRefreshRecoveryScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompetitorManualRefreshRecoveryScheduler.class);

    private final CompetitorAnalysisRefreshService refreshService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CompetitorManualRefreshRecoveryScheduler(CompetitorAnalysisRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        try {
            runOnce();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "competitor manual refresh startup recovery deferred errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    @Scheduled(
            fixedDelayString = "${nuono.competitor-analysis.manual-refresh-recovery.fixed-delay-ms:60000}",
            initialDelayString = "${nuono.competitor-analysis.manual-refresh-recovery.initial-delay-ms:60000}"
    )
    public void recoverPeriodically() {
        runOnce();
    }

    int runOnce() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int recovered = refreshService.resumeQueuedManualRefreshTasks();
            return recovered + refreshService.recoverStaleManualRefreshTasks();
        } finally {
            running.set(false);
        }
    }
}
