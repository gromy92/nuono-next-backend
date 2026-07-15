package com.nuono.next.product;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("local-db")
public class ProductPublishTaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductPublishTaskScheduler.class);

    private final LocalDbProductMasterService productMasterService;

    @Value("${nuono.product-management.publish-task.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public ProductPublishTaskScheduler(LocalDbProductMasterService productMasterService) {
        this.productMasterService = productMasterService;
    }

    @PostConstruct
    public void logSchedulerConfiguration() {
        LOGGER.info("product-management publish task scheduler initialized enabled={}", schedulerEnabled);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runProductPublishTaskSchedulerOnStartup() {
        LOGGER.info("product-management publish task scheduler startup wake enabled={}", schedulerEnabled);
        runProductPublishTaskScheduler();
    }

    @Scheduled(
            fixedDelayString = "${nuono.product-management.publish-task.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${nuono.product-management.publish-task.scheduler.initial-delay-ms:3000}"
    )
    public void runProductPublishTaskScheduler() {
        if (!schedulerEnabled) {
            return;
        }
        productMasterService.runProductPublishTaskScheduler();
    }
}
