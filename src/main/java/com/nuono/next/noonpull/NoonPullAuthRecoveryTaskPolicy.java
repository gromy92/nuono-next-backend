package com.nuono.next.noonpull;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Defines pull tasks whose continuation can be rebuilt from durable task fields alone.
 *
 * <p>The auth-recovery worker only requeues a task id. Any task accepted here must therefore
 * also be executable by the scheduled worker without an in-memory provider, callback or request
 * object from the original caller.</p>
 */
public final class NoonPullAuthRecoveryTaskPolicy {
    private static final String ONBOARDING_PRODUCT_LIST_TARGET = "catalog:list";
    private static final String SCHEDULED_PRODUCT_LIST_TARGET_PREFIX = "product-list:";

    private NoonPullAuthRecoveryTaskPolicy() {
    }

    public static boolean canAutomaticallyRecover(NoonPullTaskRecord task) {
        if (task == null || task.getPullType() == null || task.getDataDomain() == null) {
            return false;
        }
        if (task.getPullType() == NoonPullType.REPORT) {
            return isReconstructibleReport(task.getDataDomain());
        }
        if (task.getPullType() == NoonPullType.PAGE_QUERY) {
            return task.getDataDomain() == NoonPullDataDomain.SALES;
        }
        if (task.getPullType() != NoonPullType.INTERFACE) {
            return false;
        }
        if (task.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY) {
            return true;
        }
        return isReconstructibleProductList(task);
    }

    private static boolean isReconstructibleReport(NoonPullDataDomain domain) {
        return domain == NoonPullDataDomain.SALES
                || domain == NoonPullDataDomain.ORDER
                || domain == NoonPullDataDomain.FINANCE_TRANSACTION
                || domain == NoonPullDataDomain.NOON_ADVERTISING
                || domain == NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED;
    }

    private static boolean isReconstructibleProductList(NoonPullTaskRecord task) {
        if (task.getDataDomain() != NoonPullDataDomain.PRODUCT
                || !StringUtils.hasText(task.getTargetIdentity())) {
            return false;
        }
        String targetIdentity = task.getTargetIdentity().trim().toLowerCase(Locale.ROOT);
        if (task.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            return targetIdentity.startsWith(SCHEDULED_PRODUCT_LIST_TARGET_PREFIX);
        }
        return task.getTriggerMode() == NoonPullTriggerMode.ONBOARDING
                && ONBOARDING_PRODUCT_LIST_TARGET.equals(targetIdentity);
    }
}
