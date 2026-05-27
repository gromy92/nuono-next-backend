package com.nuono.next.product;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class ProductProjectionReaderService {

    private final ProductProjectionPersistenceService productProjectionPersistenceService;

    public ProductProjectionReaderService(ProductProjectionPersistenceService productProjectionPersistenceService) {
        this.productProjectionPersistenceService = productProjectionPersistenceService;
    }

    public void hydrateSnapshotGroupFromCurrentProjection(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            ProductMasterSnapshotView snapshot,
            List<String> warnings
    ) {
        productProjectionPersistenceService.hydrateSnapshotGroupFromCurrentProjection(
                ownerUserId,
                storeCode,
                skuParent,
                snapshot,
                warnings
        );
    }

    public List<ProductListProjectionRecord> loadProductListProjection(
            Long ownerUserId,
            String storeCode,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadProductListProjection(ownerUserId, storeCode, warnings);
    }

    public List<ProductListSummaryView> loadProductListSummaries(
            Long ownerUserId,
            String storeCode,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadProductListSummaries(ownerUserId, storeCode, warnings);
    }

    public ProductListSummaryView loadProductListSummary(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadProductListSummary(
                ownerUserId,
                storeCode,
                skuParent,
                warnings
        );
    }

    public List<ProductListSummaryView> loadProductGroupCandidateSummaries(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String keyword,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadProductGroupCandidateSummaries(
                ownerUserId,
                storeCode,
                skuParent,
                keyword,
                warnings
        );
    }

    public ProductHistoryView loadProductHistoryView(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadProductHistoryView(
                ownerUserId,
                storeCode,
                skuParent,
                warnings
        );
    }

    public ProductMasterSnapshotView loadLatestBaselineSnapshot(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadLatestBaselineSnapshot(
                ownerUserId,
                storeCode,
                skuParent,
                warnings
        );
    }

    public ProductProjectionPersistenceService.PersistedWorkbenchState loadPersistedWorkbenchState(
            Long ownerUserId,
            ProductMasterSnapshotView liveSnapshot,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadPersistedWorkbenchState(
                ownerUserId,
                liveSnapshot,
                warnings
        );
    }

    public ProductProjectionPersistenceService.PersistedWorkbenchState loadPersistedWorkbenchState(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.loadPersistedWorkbenchState(
                ownerUserId,
                storeCode,
                skuParent,
                warnings
        );
    }
}
