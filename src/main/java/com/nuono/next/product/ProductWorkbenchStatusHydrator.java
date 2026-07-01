package com.nuono.next.product;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
class ProductWorkbenchStatusHydrator {

    private final ProductProjectionPersistenceService productProjectionPersistenceService;

    ProductWorkbenchStatusHydrator(ProductProjectionPersistenceService productProjectionPersistenceService) {
        this.productProjectionPersistenceService = productProjectionPersistenceService;
    }

    void syncProductMasterStatus(
            ProductMasterSnapshotView snapshot,
            String syncStatus,
            String lastSyncedAt,
            List<String> warnings
    ) {
        if (snapshot == null || productProjectionPersistenceService == null) {
            return;
        }
        Object ownerUserId = snapshot.getStoreContext().get("ownerUserId");
        Long resolvedOwnerUserId = ownerUserId instanceof Number ? ((Number) ownerUserId).longValue() : null;
        productProjectionPersistenceService.updateProductMasterStatus(
                resolvedOwnerUserId,
                textValue(snapshot.getStoreContext().get("projectCode")),
                textValue(snapshot.getStoreContext().get("projectName")),
                textValue(snapshot.getIdentity().get("skuParent")),
                textValue(snapshot.getIdentity().get("partnerSku")),
                syncStatus,
                lastSyncedAt,
                warnings
        );
    }

    void hydrateListSummaryState(
            Long ownerUserId,
            ProductMasterSnapshotView snapshot,
            List<String> warnings
    ) {
        if (!(snapshot instanceof ProductMasterWorkbenchView)
                || ownerUserId == null
                || snapshot == null
                || productProjectionPersistenceService == null) {
            return;
        }
        ProductMasterWorkbenchView workbenchView = (ProductMasterWorkbenchView) snapshot;
        ProductListSummaryView summary = productProjectionPersistenceService.loadProductListSummary(
                ownerUserId,
                textValue(snapshot.getStoreContext().get("storeCode")),
                textValue(snapshot.getIdentity().get("skuParent")),
                warnings
        );
        workbenchView.setListSummary(summary);
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
