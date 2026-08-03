package com.nuono.next.store;

import com.nuono.next.infrastructure.mapper.StoreInitializationSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
class StoreInitializationTaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreInitializationTaskScheduler.class);

    private final StoreInitializationSnapshotMapper mapper;
    private final LocalDbStoreInitializationService service;

    @Value("${nuono.store.initialization.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${nuono.store.initialization.scheduler.max-items-per-tick:1}")
    private int maxItemsPerTick;

    StoreInitializationTaskScheduler(
            StoreInitializationSnapshotMapper mapper,
            LocalDbStoreInitializationService service
    ) {
        this.mapper = mapper;
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${nuono.store.initialization.scheduler.initial-delay-ms:5000}",
            fixedDelayString = "${nuono.store.initialization.scheduler.fixed-delay-ms:5000}"
    )
    void runQueuedTasks() {
        if (!enabled) {
            return;
        }
        for (StoreInitializationSnapshotRecord record : mapper.selectQueued(Math.max(1, maxItemsPerTick))) {
            if (record.getId() == null || mapper.claimQueued(record.getId()) != 1) {
                continue;
            }
            try {
                service.resumeQueued(record);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "store initialization resume failed snapshotId={} error={}",
                        record.getId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
