package com.nuono.next.sales;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
class SalesSyncTaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesSyncTaskScheduler.class);

    private final SalesSyncTaskRepository repository;
    private final SalesSyncTaskService service;

    @Value("${nuono.sales.sync-task.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${nuono.sales.sync-task.scheduler.max-items-per-tick:2}")
    private int maxItemsPerTick;

    SalesSyncTaskScheduler(
            SalesSyncTaskRepository repository,
            SalesSyncTaskService service
    ) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${nuono.sales.sync-task.scheduler.initial-delay-ms:5000}",
            fixedDelayString = "${nuono.sales.sync-task.scheduler.fixed-delay-ms:5000}"
    )
    void runQueuedTasks() {
        if (!enabled) {
            return;
        }
        List<SalesSyncTaskRecord> tasks = repository.listQueued(Math.max(1, maxItemsPerTick));
        for (SalesSyncTaskRecord task : tasks) {
            try {
                service.runQueued(task.getId());
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "sales sync task scheduler failed taskId={} error={}",
                        task.getId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
