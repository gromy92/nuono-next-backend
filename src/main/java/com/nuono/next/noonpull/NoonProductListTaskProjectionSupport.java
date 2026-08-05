package com.nuono.next.noonpull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NoonProductListTaskProjectionSupport {

    private static final Logger log = LoggerFactory.getLogger(NoonProductListTaskProjectionSupport.class);

    private NoonProductListTaskProjectionSupport() {
    }

    static boolean apply(
            NoonProductListPullAdapter adapter,
            NoonPullTaskRecord task,
            NoonInterfacePullResult pullResult,
            NoonPullFoundationService foundationService
    ) {
        try {
            adapter.apply(NoonProductListApplyCommand.builder()
                    .ownerUserId(task.getOwnerUserId())
                    .projectCode(NoonPullScheduledExecutionSupport.deriveProjectCode(task.getStoreCode()))
                    .storeCode(task.getStoreCode())
                    .siteCode(task.getSiteCode())
                    .sourceBatchId(pullResult.getSourceBatchId())
                    .items(pullResult.getItems())
                    .build());
            return true;
        } catch (RuntimeException exception) {
            foundationService.markFailed(task.getId(), "product_projection_failed", diagnostic(exception));
            log.warn(
                    "product list projection failed taskId={} store={} site={}",
                    task.getId(),
                    task.getStoreCode(),
                    task.getSiteCode(),
                    exception
            );
            return false;
        }
    }

    private static String diagnostic(RuntimeException exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMessage = root.getMessage() == null || root.getMessage().isBlank()
                ? "no detail"
                : root.getMessage().trim();
        return "product list fetched but projection failed: "
                + exception.getClass().getSimpleName()
                + "; root=" + root.getClass().getSimpleName()
                + ": " + rootMessage;
    }
}
