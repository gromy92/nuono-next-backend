package com.nuono.next.product;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
class ProductWorkbenchViewFinalizer {

    private final ProductProjectionPersistenceService productProjectionPersistenceService;
    private final ProductWorkbenchDirtySiteResolver productWorkbenchDirtySiteResolver;
    private final ProductWorkbenchViewAssembler productWorkbenchViewAssembler = new ProductWorkbenchViewAssembler();

    ProductWorkbenchViewFinalizer(
            ProductProjectionPersistenceService productProjectionPersistenceService,
            ProductWorkbenchDirtySiteResolver productWorkbenchDirtySiteResolver
    ) {
        this.productProjectionPersistenceService = productProjectionPersistenceService;
        this.productWorkbenchDirtySiteResolver = productWorkbenchDirtySiteResolver;
    }

    ProductMasterWorkbenchView finalizeView(
            Long ownerUserId,
            String actionType,
            String targetSiteCode,
            ProductWorkbenchRecord record,
            String message,
            List<String> warnings,
            ProductMasterSnapshotView actionBaselineSnapshot,
            ProductMasterSnapshotView actionDraftSnapshot,
            FinalizeSupport support
    ) {
        support.attachActivePublishTask(ownerUserId, record);
        ProductMasterWorkbenchView view = productWorkbenchViewAssembler.buildWorkbenchView(record, message, warnings);
        support.syncProductMasterStatus(view, view.getSyncStatus(), view.getLastSyncedAt(), view.getWarnings());
        if (StringUtils.hasText(actionType)) {
            productProjectionPersistenceService.persistWorkbenchState(
                    ownerUserId,
                    view,
                    productWorkbenchDirtySiteResolver.resolveDirtySiteCodes(
                            view.getDraftSnapshot(),
                            view.getBaselineSnapshot()
                    ),
                    actionType,
                    targetSiteCode,
                    view.getWarnings(),
                    actionBaselineSnapshot,
                    actionDraftSnapshot
            );
        }
        support.hydrateListSummaryState(ownerUserId, view, view.getWarnings());
        return view;
    }

    interface FinalizeSupport {
        void attachActivePublishTask(Long ownerUserId, ProductWorkbenchRecord record);

        void syncProductMasterStatus(
                ProductMasterSnapshotView snapshot,
                String syncStatus,
                String lastSyncedAt,
                List<String> warnings
        );

        void hydrateListSummaryState(
                Long ownerUserId,
                ProductMasterSnapshotView snapshot,
                List<String> warnings
        );
    }
}
