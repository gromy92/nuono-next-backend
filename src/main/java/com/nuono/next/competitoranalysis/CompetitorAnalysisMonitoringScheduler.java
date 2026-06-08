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
    private final AtomicBoolean running = new AtomicBoolean(false);

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
            cron = "${nuono.competitor-analysis.monitor.scheduler.cron:0 0 8 * * *}",
            zone = "${nuono.competitor-analysis.monitor.scheduler.zone:Asia/Shanghai}"
    )
    public void runScheduledMonitoring() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            runOnce();
        } finally {
            running.set(false);
        }
    }

    public int runOnce() {
        if (!enabled) {
            return 0;
        }
        List<CompetitorWatchProductScopeRow> scopes = mapper.listRefreshableWatchProductScopes(
                Math.max(1, maxScopesPerTick)
        );
        int submitted = 0;
        for (CompetitorWatchProductScopeRow scope : scopes) {
            try {
                refreshService.requestScheduledStoreMonitoring(
                        scope.getOwnerUserId(),
                        scope.getStoreCode(),
                        scope.getSiteCode()
                );
                submitted++;
            } catch (RuntimeException exception) {
                log.warn(
                        "competitor scheduled monitoring submit failed ownerUserId={} storeCode={} siteCode={} error={}",
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
}
