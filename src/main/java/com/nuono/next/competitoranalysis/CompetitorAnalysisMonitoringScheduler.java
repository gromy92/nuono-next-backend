package com.nuono.next.competitoranalysis;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompetitorAnalysisMonitoringScheduler {
    private final CompetitorAnalysisRefreshService refreshService;
    private final AtomicBoolean rankRunning = new AtomicBoolean(false);
    private final AtomicBoolean detailRunning = new AtomicBoolean(false);
    private final AtomicBoolean compensationRunning = new AtomicBoolean(false);
    private final AtomicBoolean taskRecoveryRunning = new AtomicBoolean(false);

    @Value("${nuono.competitor-analysis.monitor.scheduler.enabled:false}")
    private boolean enabled;

    @Value("${nuono.competitor-analysis.monitor.scheduler.max-compensation-keywords-per-tick:50}")
    private int maxCompensationKeywordsPerTick;

    @Value("${nuono.competitor-analysis.monitor.scheduler.compensation-lookback-hours:24}")
    private int compensationLookbackHours;

    public CompetitorAnalysisMonitoringScheduler(
            CompetitorAnalysisRefreshService refreshService
    ) {
        this.refreshService = refreshService;
    }

    @Scheduled(
            cron = "${nuono.competitor-analysis.monitor.scheduler.rank-cron:0 0 0,6,12,18 * * *}",
            zone = "${nuono.competitor-analysis.monitor.scheduler.zone:Asia/Shanghai}"
    )
    public void runScheduledRankMonitoring() {
        if (!enabled || !rankRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            runRankOnce();
        } finally {
            rankRunning.set(false);
        }
    }

    @Scheduled(
            cron = "${nuono.competitor-analysis.monitor.scheduler.detail-cron:0 0 2 * * *}",
            zone = "${nuono.competitor-analysis.monitor.scheduler.zone:Asia/Shanghai}"
    )
    public void runScheduledDetailMonitoring() {
        if (!enabled || !detailRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            runDetailOnce();
        } finally {
            detailRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${nuono.competitor-analysis.monitor.scheduler.compensation-fixed-delay-ms:600000}",
            initialDelayString = "${nuono.competitor-analysis.monitor.scheduler.compensation-initial-delay-ms:60000}"
    )
    public void runScheduledRankFailureCompensation() {
        if (!enabled || !compensationRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            runRankFailureCompensationOnce();
        } finally {
            compensationRunning.set(false);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeQueuedRefreshTasksAfterStartup() {
        runStartupRecoveryOnce();
    }

    @Scheduled(
            fixedDelayString = "${nuono.competitor-analysis.monitor.scheduler.task-recovery-fixed-delay-ms:60000}",
            initialDelayString = "${nuono.competitor-analysis.monitor.scheduler.task-recovery-initial-delay-ms:60000}"
    )
    public void runScheduledTaskRecovery() {
        runTaskRecoveryOnce();
    }

    public int runOnce() {
        return runRankOnce();
    }

    public int runRankOnce() {
        if (!enabled) {
            return 0;
        }
        runTaskRecoveryOnce();
        return refreshService.runScheduledRankCycle();
    }

    public int runDetailOnce() {
        if (!enabled) {
            return 0;
        }
        runTaskRecoveryOnce();
        return refreshService.runScheduledDetailCycle();
    }

    public int runRankFailureCompensationOnce() {
        if (!enabled) {
            return 0;
        }
        return refreshService.retryRecentTransientRankKeywordFailures(
                Duration.ofHours(Math.max(1, compensationLookbackHours)),
                Math.max(1, maxCompensationKeywordsPerTick)
        );
    }

    public int runStartupRecoveryOnce() {
        return runTaskRecovery();
    }

    public int runTaskRecoveryOnce() {
        return runTaskRecovery();
    }

    private int runTaskRecovery() {
        if (!taskRecoveryRunning.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int recovered = refreshService.resumeQueuedRefreshTasks();
            recovered += refreshService.recoverStaleRefreshTasks();
            return recovered;
        } finally {
            taskRecoveryRunning.set(false);
        }
    }

}
