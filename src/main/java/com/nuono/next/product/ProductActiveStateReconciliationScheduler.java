package com.nuono.next.product;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class ProductActiveStateReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ProductActiveStateReconciliationScheduler.class);

    private final ProductActiveStateReconciliationService service;
    private final boolean enabled;
    private final int maxScopesPerTick;

    public ProductActiveStateReconciliationScheduler(
            ProductActiveStateReconciliationService service,
            @Value("${nuono.product-management.active-state-reconciliation.scheduler.enabled:false}")
            boolean enabled,
            @Value("${nuono.product-management.active-state-reconciliation.scheduler.max-scopes-per-tick:4}")
            int maxScopesPerTick
    ) {
        this.service = service;
        this.enabled = enabled;
        this.maxScopesPerTick = Math.max(1, maxScopesPerTick);
    }

    @PostConstruct
    public void logConfiguration() {
        log.info(
                "product active-state reconciliation scheduler initialized enabled={} maxScopesPerTick={}",
                enabled,
                maxScopesPerTick
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${nuono.product-management.active-state-reconciliation.scheduler.fixed-delay-ms:300000}",
            initialDelayString =
                    "${nuono.product-management.active-state-reconciliation.scheduler.initial-delay-ms:90000}"
    )
    public void reconcileUnknownActiveStates() {
        if (!enabled) {
            return;
        }
        int queued = service.enqueueUnknownScopes(maxScopesPerTick);
        if (queued > 0) {
            log.info("product active-state reconciliation scheduler queued {} exact site offers", queued);
        }
    }
}
