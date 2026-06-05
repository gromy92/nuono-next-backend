package com.nuono.next.product;

import java.util.List;
import org.springframework.util.StringUtils;

class ProductWorkbenchViewFinalizer {

    private final ProductProjectionPersistenceService productProjectionPersistenceService;
    private final ProductWorkbenchDirtySiteResolver productWorkbenchDirtySiteResolver;
    private final ProductWorkbenchStatusHydrator productWorkbenchStatusHydrator;
    private final ProductWorkbenchPublishTaskAttacher productWorkbenchPublishTaskAttacher;
    private final ProductWorkbenchViewAssembler productWorkbenchViewAssembler = new ProductWorkbenchViewAssembler();

    ProductWorkbenchViewFinalizer(
            ProductProjectionPersistenceService productProjectionPersistenceService,
            ProductWorkbenchDirtySiteResolver productWorkbenchDirtySiteResolver,
            ProductWorkbenchStatusHydrator productWorkbenchStatusHydrator,
            ProductWorkbenchPublishTaskAttacher productWorkbenchPublishTaskAttacher
    ) {
        this.productProjectionPersistenceService = productProjectionPersistenceService;
        this.productWorkbenchDirtySiteResolver = productWorkbenchDirtySiteResolver;
        this.productWorkbenchStatusHydrator = productWorkbenchStatusHydrator;
        this.productWorkbenchPublishTaskAttacher = productWorkbenchPublishTaskAttacher;
    }

    ProductMasterWorkbenchView finalizeView(
            Long ownerUserId,
            String actionType,
            String targetSiteCode,
            ProductWorkbenchRecord record,
            String message,
            List<String> warnings,
            ProductMasterSnapshotView actionBaselineSnapshot,
            ProductMasterSnapshotView actionDraftSnapshot
    ) {
        productWorkbenchPublishTaskAttacher.attachActivePublishTask(ownerUserId, record);
        ProductMasterWorkbenchView view = productWorkbenchViewAssembler.buildWorkbenchView(record, message, warnings);
        productWorkbenchStatusHydrator.syncProductMasterStatus(
                view,
                view.getSyncStatus(),
                view.getLastSyncedAt(),
                view.getWarnings()
        );
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
        productWorkbenchStatusHydrator.hydrateListSummaryState(ownerUserId, view, view.getWarnings());
        return view;
    }
}
