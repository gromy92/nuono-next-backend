package com.nuono.next.product;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProductDetailBaselineBatchExecutor {
    private static final Logger log = LoggerFactory.getLogger(ProductDetailBaselineBatchExecutor.class);
    private final ProductDetailBaselineBackfillService backfillService;
    private final LocalDbProductMasterService productMasterService;
    private final ProductActiveStateReconciliationGuard reconciliationGuard;

    ProductDetailBaselineBatchExecutor(
            ProductDetailBaselineBackfillService backfillService,
            LocalDbProductMasterService productMasterService,
            ProductActiveStateReconciliationGuard reconciliationGuard
    ) {
        this.backfillService = backfillService;
        this.productMasterService = productMasterService;
        this.reconciliationGuard = reconciliationGuard;
    }

    boolean isHeld(Long ownerUserId, String storeCode, String siteCode) {
        return reconciliationGuard.isHeld(ownerUserId, storeCode, siteCode);
    }

    void run(List<Request> requests) {
        for (Request request : requests) {
            ProductMasterFetchCommand command = request.command;
            if (reconciliationGuard.isHeld(command.getOwnerUserId(), command.getStoreCode(), request.siteCode)) break;
            try {
                backfillService.enqueueInline(
                        command,
                        request.reason,
                        (fetchCommand, ignoredReason) ->
                                reconciliationGuard.fetch(productMasterService, fetchCommand, request.siteCode)
                );
                if (reconciliationGuard.isHeld(
                        command.getOwnerUserId(), command.getStoreCode(), request.siteCode)) break;
            } catch (RuntimeException exception) {
                log.warn(
                        "daily product detail baseline execution failed owner={} store={} skuParent={} error={}",
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        command.getSkuParent(),
                        exception.getMessage(),
                        exception
                );
            }
        }
    }

    static final class Request {
        private final ProductMasterFetchCommand command;
        private final String reason;
        private final String siteCode;

        Request(ProductMasterFetchCommand command, String reason, String siteCode) {
            this.command = command;
            this.reason = reason;
            this.siteCode = siteCode;
        }
    }
}
