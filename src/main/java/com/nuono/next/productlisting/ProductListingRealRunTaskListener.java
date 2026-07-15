package com.nuono.next.productlisting;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductListingRealRunTaskListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductListingRealRunTaskListener.class);

    private final ProductListingService service;

    public ProductListingRealRunTaskListener(ProductListingService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
