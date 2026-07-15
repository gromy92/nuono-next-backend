package com.nuono.next.productlisting;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductListingRealRunTaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductListingRealRunTaskScheduler.class);

    private final ProductListingService service;

    @Value("${nuono.product-listing.real-run-task.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${nuono.product-listing.real-run-task.scheduler.max-items-per-tick:2}")
    private int maxItemsPerTick;

    @Value("${nuono.product-listing.real-run-task.scheduler.stale-running-minutes:30}")
    private long staleRunningMinutes;

    public ProductListingRealRunTaskScheduler(ProductListingService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${nuono.product-listing.real-run-task.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${nuono.product-listing.real-run-task.scheduler.initial-delay-ms:3000}"
    )
    public void runRealRunTaskScheduler() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            Duration staleAge = Duration.ofMinutes(Math.max(1L, staleRunningMinutes));
            int recovered = service.recoverStaleRunningRealRunTasks(staleAge);
            List<ProductListingTaskView> executed =
                    service.executeRunnableRealRunTasks(Math.max(1, maxItemsPerTick));
            if (recovered > 0 || !executed.isEmpty()) {
                LOGGER.info(
                        "product-listing real-run scheduler tick: recovered={}, executed={}",
                        recovered,
                        executed.size()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("product-listing real-run scheduler tick failed", exception);
        }
    }
}
