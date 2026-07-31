package com.nuono.next.noonpull;

import com.nuono.next.product.ProductProjectionPersistenceService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProductProjectionNoonProductProjectionWriter implements NoonProductProjectionWriter {
    private final ProductProjectionPersistenceService persistenceService;
    private final ProductListActiveStateReconciler activeStateReconciler;

    public ProductProjectionNoonProductProjectionWriter(
            ProductProjectionPersistenceService persistenceService,
            ProductListActiveStateReconciler activeStateReconciler
    ) {
        this.persistenceService = persistenceService;
        this.activeStateReconciler = activeStateReconciler;
    }

    @Override
    @Transactional
    public void write(NoonProductProjectionWriteCommand command) {
        persistenceService.persistInitializationProjection(
                command.getOwnerUserId(),
                command.getProjectCode(),
                command.getProjectName(),
                command.getReferenceStoreCode(),
                command.getSiteSeeds(),
                command.getProductSeeds(),
                command.getWarnings(),
                command.isPreserveDrafts(),
                command.isCompleteSiteScope()
        );
        activeStateReconciler.reconcile(command);
    }
}
