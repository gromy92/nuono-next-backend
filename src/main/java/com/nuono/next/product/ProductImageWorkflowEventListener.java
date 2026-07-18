package com.nuono.next.product;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ProductImageWorkflowEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageWorkflowEventListener.class);
    private final ProductImageWorkflowService workflowService;

    ProductImageWorkflowEventListener(ProductImageWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGeneration(ProductImageGenerationSubmittedEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                workflowService.generate(event.suiteId(), event.ownerUserId(), event.storeCode(), event.operatorUserId());
            } catch (RuntimeException exception) {
                LOGGER.warn("Product image generation failed: suiteId={}", event.suiteId(), exception);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPublish(ProductImagePublishSubmittedEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                workflowService.publish(event.suiteId(), event.ownerUserId(), event.storeCode(), event.operatorUserId());
            } catch (RuntimeException exception) {
                LOGGER.warn("Product image Noon publication failed: suiteId={}", event.suiteId(), exception);
            }
        });
    }
}
