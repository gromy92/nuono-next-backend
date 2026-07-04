package com.nuono.next.procurement.aliorder;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Ali1688HistoricalOrderWeeklySyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Ali1688HistoricalOrderWeeklySyncScheduler.class);

    private final Ali1688HistoricalOrderMapper mapper;
    private final LocalDbAli1688HistoricalOrderService service;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final boolean enabled;
    private final int maxAuthorizationsPerTick;
    private final int recentScheduledDays;
    private final int staleRunningMinutes;
    private final Long systemOperatorUserId;

    public Ali1688HistoricalOrderWeeklySyncScheduler(
            Ali1688HistoricalOrderMapper mapper,
            LocalDbAli1688HistoricalOrderService service,
            @Value("${nuono.procurement.ali1688.historical-order.scheduler.enabled:false}") boolean enabled,
            @Value("${nuono.procurement.ali1688.historical-order.scheduler.max-authorizations-per-tick:10}") int maxAuthorizationsPerTick,
            @Value("${nuono.procurement.ali1688.historical-order.scheduler.recent-scheduled-days:6}") int recentScheduledDays,
            @Value("${nuono.procurement.ali1688.historical-order.scheduler.stale-running-minutes:120}") int staleRunningMinutes,
            @Value("${nuono.procurement.ali1688.historical-order.scheduler.system-operator-user-id:10003}") Long systemOperatorUserId
    ) {
        this.mapper = mapper;
        this.service = service;
        this.enabled = enabled;
        this.maxAuthorizationsPerTick = Math.max(1, Math.min(maxAuthorizationsPerTick, 100));
        this.recentScheduledDays = Math.max(1, recentScheduledDays);
        this.staleRunningMinutes = Math.max(1, staleRunningMinutes);
        this.systemOperatorUserId = systemOperatorUserId;
    }

    @Scheduled(
            cron = "${nuono.procurement.ali1688.historical-order.scheduler.cron:0 0 3 ? * MON}",
            zone = "${nuono.procurement.ali1688.historical-order.scheduler.zone:Asia/Shanghai}"
    )
    public void runScheduledWeeklySync() {
        runOnce();
    }

    int runOnce() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return 0;
        }
        int submitted = 0;
        try {
            List<Ali1688HistoricalOrderAuthorizationRow> authorizations =
                    mapper.listScheduledOpenApiAuthorizations(
                            LocalDbAli1688HistoricalOrderService.OPEN_API_PROVIDER_CODE,
                            maxAuthorizationsPerTick
                    );
            for (Ali1688HistoricalOrderAuthorizationRow authorization : authorizations) {
                if (authorization == null || authorization.getOwnerUserId() == null || authorization.getId() == null) {
                    continue;
                }
                if (shouldSkip(authorization)) {
                    continue;
                }
                try {
                    Ali1688HistoricalOrderSyncTaskRow task = service.runScheduledWeekly(
                            authorization.getOwnerUserId(),
                            authorization.getId(),
                            systemOperatorUserId
                    );
                    if (task != null) {
                        submitted++;
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "1688 scheduled weekly order sync failed ownerUserId={} authorizationId={} error={}",
                            authorization.getOwnerUserId(),
                            authorization.getId(),
                            exception.getMessage(),
                            exception
                    );
                }
            }
            return submitted;
        } finally {
            running.set(false);
        }
    }

    private boolean shouldSkip(Ali1688HistoricalOrderAuthorizationRow authorization) {
        int runningTasks = mapper.countRecentRunningSyncTasks(
                authorization.getOwnerUserId(),
                authorization.getId(),
                staleRunningMinutes
        );
        if (runningTasks > 0) {
            return true;
        }
        int recentScheduledTasks = mapper.countRecentSyncTasksByType(
                authorization.getOwnerUserId(),
                authorization.getId(),
                LocalDbAli1688HistoricalOrderService.SCHEDULED_WEEKLY_TASK_TYPE,
                recentScheduledDays
        );
        return recentScheduledTasks > 0;
    }
}
