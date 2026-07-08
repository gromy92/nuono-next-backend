package com.nuono.next.noonpull;

import com.nuono.next.product.ProductProjectionPersistenceService;
import org.springframework.stereotype.Component;

@Component
public class ProductProjectionNoonProductProjectionWriter implements NoonProductProjectionWriter {
    private final ProductProjectionPersistenceService persistenceService;

    public ProductProjectionNoonProductProjectionWriter(ProductProjectionPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
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
    }
}
