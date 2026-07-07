package com.nuono.next.intransit.autosync;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogisticsAutoSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(LogisticsAutoSyncScheduler.class);

    private final LogisticsAutoSyncProperties properties;
    private final LogisticsAutoSyncMapper mapper;
    private final LogisticsAutoSyncService service;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LogisticsAutoSyncScheduler(
            LogisticsAutoSyncProperties properties,
            LogisticsAutoSyncMapper mapper,
            LogisticsAutoSyncService service
    ) {
        this(properties, mapper, service, Clock.systemDefaultZone());
    }

    LogisticsAutoSyncScheduler(
            LogisticsAutoSyncProperties properties,
            LogisticsAutoSyncMapper mapper,
            LogisticsAutoSyncService service,
            Clock clock
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${nuono.logistics-auto-sync.scheduler.cron:0 */30 * * * *}",
            zone = "${nuono.logistics-auto-sync.scheduler.zone:Asia/Shanghai}"
    )
    public void scheduledTick() {
        runOnce();
    }

    int runOnce() {
        LogisticsAutoSyncProperties.Scheduler scheduler = schedulerProperties();
        if (!scheduler.isEnabled()) {
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int maxAccounts = Math.max(1, scheduler.getMaxAccountsPerTick());
            List<LogisticsAutoSyncAccount> dueAccounts = mapper.listDueAccounts(maxAccounts);
            if (dueAccounts == null || dueAccounts.isEmpty()) {
                return 0;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            int executed = 0;
            for (LogisticsAutoSyncAccount account : dueAccounts) {
                if (executed >= maxAccounts) {
                    break;
                }
                if (!isRunnableNow(account, now)) {
                    continue;
                }
                try {
                    service.runAccount(account);
                } catch (RuntimeException exception) {
                    log.warn(
                            "Logistics auto sync account failed in scheduler: accountId={}, error={}: {}",
                            account == null ? null : account.getId(),
                            exception.getClass().getSimpleName(),
                            exception.getMessage()
                    );
                }
                executed += 1;
            }
            return executed;
        } finally {
            running.set(false);
        }
    }

    private boolean isRunnableNow(LogisticsAutoSyncAccount account, LocalDateTime now) {
        if (account == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(account.getEnabled()) || !Boolean.TRUE.equals(account.getScheduleEnabled())) {
            return false;
        }
        if (account.getCooldownUntil() != null && account.getCooldownUntil().isAfter(now)) {
            return false;
        }
        return inWindow(account.getScheduleWindowStart(), account.getScheduleWindowEnd(), now.toLocalTime());
    }

    private boolean inWindow(LocalTime start, LocalTime end, LocalTime now) {
        if (start == null || end == null || start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private LogisticsAutoSyncProperties.Scheduler schedulerProperties() {
        return properties == null || properties.getScheduler() == null
                ? new LogisticsAutoSyncProperties.Scheduler()
                : properties.getScheduler();
    }
}
