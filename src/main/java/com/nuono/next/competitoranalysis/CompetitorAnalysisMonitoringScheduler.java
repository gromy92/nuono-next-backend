package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompetitorAnalysisMonitoringScheduler {
    private static final Logger log = LoggerFactory.getLogger(CompetitorAnalysisMonitoringScheduler.class);

    private final CompetitorAnalysisMapper mapper;
    private final CompetitorAnalysisRefreshService refreshService;
    private final AtomicBoolean rankRunning = new AtomicBoolean(false);
    private final AtomicBoolean detailRunning = new AtomicBoolean(false);
    private final AtomicBoolean compensationRunning = new AtomicBoolean(false);
    private final AtomicBoolean taskRecoveryRunning = new AtomicBoolean(false);

    @Value("${nuono.competitor-analysis.monitor.scheduler.enabled:false}")
    private boolean enabled;

    @Value("${nuono.competitor-analysis.monitor.scheduler.max-scopes-per-tick:100}")
    private int maxScopesPerTick;

    @Value("${nuono.competitor-analysis.monitor.scheduler.max-compensation-keywords-per-tick:50}")
    private int maxCompensationKeywordsPerTick;

    @Value("${nuono.competitor-analysis.monitor.scheduler.compensation-lookback-hours:24}")
    private int compensationLookbackHours;

    public CompetitorAnalysisMonitoringScheduler(
            CompetitorAnalysisMapper mapper,
            CompetitorAnalysisRefreshService refreshService
    ) {
        this.mapper = mapper;
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
        return runOnce("rank", this::submitRankMonitoring, Math.max(1, maxScopesPerTick));
    }

    public int runDetailOnce() {
        return runOnce("detail", this::submitDetailMonitoring, Integer.MAX_VALUE);
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
        if (!enabled || !taskRecoveryRunning.compareAndSet(false, true)) {
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

    private int runOnce(String executionMode, ScopeSubmitter submitter, int scopeLimit) {
        if (!enabled) {
            return 0;
        }
        runTaskRecoveryOnce();
        List<CompetitorWatchProductScopeRow> scopes = mapper.listRefreshableWatchProductScopes(
                scopeLimit
        );
        int submitted = 0;
        for (CompetitorWatchProductScopeRow scope : scopes) {
            try {
                submitter.submit(scope);
                submitted++;
            } catch (RuntimeException exception) {
                log.warn(
                        "competitor scheduled {} monitoring submit failed ownerUserId={} storeCode={} siteCode={} error={}",
                        executionMode,
                        scope.getOwnerUserId(),
                        scope.getStoreCode(),
                        scope.getSiteCode(),
                        exception.getMessage(),
                        exception
                );
            }
        }
        return submitted;
    }

    private void submitRankMonitoring(CompetitorWatchProductScopeRow scope) {
        refreshService.requestScheduledRankMonitoring(
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode()
        );
    }

    private void submitDetailMonitoring(CompetitorWatchProductScopeRow scope) {
        refreshService.requestScheduledDetailMonitoring(
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode()
        );
    }

    @FunctionalInterface
    private interface ScopeSubmitter {
        void submit(CompetitorWatchProductScopeRow scope);
    }
}
