package com.nuono.next.productpublicdetail;

import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.noonmaintenance.StoreSiteMaintenanceGate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductPublicDetailSyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductPublicDetailSyncScheduler.class);
    private final ProductPublicDetailMapper mapper;
    private final ProductPublicDetailSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final boolean enabled;
    private final int maxScopesPerTick;
    private final int staleDays;
    private final int failureCooldownHours;
    private StoreSiteMaintenanceGate maintenanceGate = StoreSiteMaintenanceGate.allowAll();

    public ProductPublicDetailSyncScheduler(
            ProductPublicDetailMapper mapper,
            ProductPublicDetailSyncService syncService,
            @Value("${nuono.product-public-detail.scheduler.enabled:false}") boolean enabled,
            @Value("${nuono.product-public-detail.scheduler.max-scopes-per-tick:50}") int maxScopesPerTick,
            @Value("${nuono.product-public-detail.scheduler.stale-days:1}") int staleDays,
            @Value("${nuono.product-public-detail.scheduler.failure-cooldown-hours:12}") int failureCooldownHours
    ) {
        this.mapper = mapper;
        this.syncService = syncService;
        this.enabled = enabled;
        this.maxScopesPerTick = Math.max(1, Math.min(maxScopesPerTick, 500));
        this.staleDays = Math.max(1, staleDays);
        this.failureCooldownHours = Math.max(1, failureCooldownHours);
    }

    @Autowired(required = false)
    void setMaintenanceGate(StoreSiteMaintenanceGate maintenanceGate) {
        this.maintenanceGate = maintenanceGate == null ? StoreSiteMaintenanceGate.allowAll() : maintenanceGate;
    }

    @Scheduled(
            cron = "${nuono.product-public-detail.scheduler.cron:0 30 3 * * *}",
            zone = "${nuono.product-public-detail.scheduler.zone:Asia/Shanghai}"
    )
    public void runScheduledSync() {
        runOnce();
    }

    int runOnce() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            List<ProductPublicDetailScope> scopes = mapper.listDueScopes(maxScopesPerTick, staleDays, failureCooldownHours);
            int submitted = 0;
            int maintenanceSkipped = 0;
            for (ProductPublicDetailScope scope : scopes) {
                if (maintenanceGate.isUnderMaintenance(
                        scope.getOwnerUserId(),
                        scope.getStoreCode(),
                        scope.getSiteCode()
                )) {
                    maintenanceSkipped++;
                    continue;
                }
                syncService.submitScheduled(scope.getOwnerUserId(), scope.getStoreCode(), scope.getSiteCode());
                submitted++;
            }
            if (maintenanceSkipped > 0) {
                LOGGER.info("Skipped DP05 scheduled scopes under maintenance: count={}", maintenanceSkipped);
            }
            return submitted;
        } finally {
            running.set(false);
        }
    }
}
