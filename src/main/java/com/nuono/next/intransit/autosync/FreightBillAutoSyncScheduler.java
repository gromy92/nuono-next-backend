package com.nuono.next.intransit.autosync;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FreightBillAutoSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(FreightBillAutoSyncScheduler.class);

    private final LogisticsAutoSyncProperties properties;
    private final LogisticsAutoSyncMapper mapper;
    private final FreightBillAutoSyncService service;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public FreightBillAutoSyncScheduler(
            LogisticsAutoSyncProperties properties,
            LogisticsAutoSyncMapper mapper,
            FreightBillAutoSyncService service
    ) {
        this(properties, mapper, service, Clock.systemDefaultZone());
    }

    FreightBillAutoSyncScheduler(
            LogisticsAutoSyncProperties properties,
            LogisticsAutoSyncMapper mapper,
            FreightBillAutoSyncService service,
            Clock clock
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${nuono.logistics-auto-sync.freight-bill-scheduler.cron:0 15 */6 * * *}",
            zone = "${nuono.logistics-auto-sync.freight-bill-scheduler.zone:Asia/Shanghai}"
    )
    public void scheduledTick() {
        runOnce();
    }

    int runOnce() {
        LogisticsAutoSyncProperties.Scheduler scheduler = properties == null
                ? new LogisticsAutoSyncProperties.Scheduler()
                : properties.getFreightBillScheduler();
        if (scheduler == null || !scheduler.isEnabled() || !running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int limit = Math.max(1, scheduler.getMaxAccountsPerTick());
            List<LogisticsAutoSyncAccount> accounts = mapper.listDueFreightBillAccounts(limit);
            if (accounts == null || accounts.isEmpty()) {
                return 0;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            int executed = 0;
            for (LogisticsAutoSyncAccount account : accounts) {
                if (executed >= limit) {
                    break;
                }
                if (!runnable(account, now)) {
                    continue;
                }
                try {
                    service.runAccount(account);
                } catch (RuntimeException exception) {
                    log.warn(
                            "Freight bill auto sync account failed: accountId={}, error={}: {}",
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

    private boolean runnable(LogisticsAutoSyncAccount account, LocalDateTime now) {
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())
                || !Boolean.TRUE.equals(account.getFreightBillScheduleEnabled())) {
            return false;
        }
        if (account.getFreightBillCooldownUntil() != null && account.getFreightBillCooldownUntil().isAfter(now)) {
            return false;
        }
        LocalTime start = account.getScheduleWindowStart();
        LocalTime end = account.getScheduleWindowEnd();
        LocalTime current = now.toLocalTime();
        if (start == null || end == null || start.equals(end)) {
            return true;
        }
        return start.isBefore(end)
                ? !current.isBefore(start) && !current.isAfter(end)
                : !current.isBefore(start) || !current.isAfter(end);
    }
}
