package com.nuono.next.productlisting;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProductListingRealRunTaskListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductListingRealRunTaskListener.class);

    private final ProductListingService service;

    public ProductListingRealRunTaskListener(ProductListingService service) {
        this.service = service;
    }

    @EventListener
    public void onProductListingRealRunSubmitted(ProductListingRealRunSubmittedEvent event) {
        if (event == null || event.getTaskId() == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                service.executeSubmittedRealRunTask(event.getTaskId());
            } catch (RuntimeException exception) {
                LOGGER.warn("Product listing real-run task execution failed: taskId={}", event.getTaskId(), exception);
            }
        });
    }
}
