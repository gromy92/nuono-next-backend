package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompetitorAnalysisMonitoringScheduler {
    private static final Logger log = LoggerFactory.getLogger(CompetitorAnalysisMonitoringScheduler.class);

    private final CompetitorAnalysisMapper mapper;
    private final CompetitorAnalysisRefreshService refreshService;
    private final AtomicBoolean rankRunning = new AtomicBoolean(false);
    private final AtomicBoolean detailRunning = new AtomicBoolean(false);

    @Value("${nuono.competitor-analysis.monitor.scheduler.enabled:false}")
    private boolean enabled;

    @Value("${nuono.competitor-analysis.monitor.scheduler.max-scopes-per-tick:100}")
    private int maxScopesPerTick;

    public CompetitorAnalysisMonitoringScheduler(
            CompetitorAnalysisMapper mapper,
            CompetitorAnalysisRefreshService refreshService
    ) {
        this.mapper = mapper;
        this.refreshService = refreshService;
    }

    @Scheduled(
            cron = "${nuono.competitor-analysis.monitor.scheduler.rank-cron:0 0 8 * * *}",
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
            cron = "${nuono.competitor-analysis.monitor.scheduler.detail-cron:0 0 0/2 * * *}",
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

    public int runOnce() {
        return runRankOnce();
    }

    public int runRankOnce() {
        return runOnce("rank", this::submitRankMonitoring);
    }

    public int runDetailOnce() {
        return runOnce("detail", this::submitDetailMonitoring);
    }

    private int runOnce(String executionMode, ScopeSubmitter submitter) {
        if (!enabled) {
            return 0;
        }
        refreshService.recoverStaleRefreshTasks();
        List<CompetitorWatchProductScopeRow> scopes = mapper.listRefreshableWatchProductScopes(
                Math.max(1, maxScopesPerTick)
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
