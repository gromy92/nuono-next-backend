package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoonPullAuthRecoveryTaskPolicyTest {

    @Test
    void acceptsOnlyReadPullsReconstructibleFromPersistedTaskFields() {
        assertTrue(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                NoonPullTriggerMode.SCHEDULED_DAILY,
                "product-list:2026-07-16..2026-07-16"
        )));
        assertTrue(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                NoonPullTriggerMode.ONBOARDING,
                "catalog:list"
        )));
        assertTrue(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.PAGE_QUERY,
                NoonPullDataDomain.SALES,
                NoonPullTriggerMode.MANUAL_BACKFILL,
                "sales-page-query:2026-07-15..2026-07-15"
        )));
        assertTrue(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.REPORT,
                NoonPullDataDomain.ORDER,
                NoonPullTriggerMode.GAP_BACKFILL,
                "orders:2026-07-01..2026-07-15"
        )));
        assertTrue(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.INTERFACE,
                NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY,
                NoonPullTriggerMode.MANUAL_BACKFILL,
                "official-warehouse-inventory:2026-07-16"
        )));
    }

    @Test
    void rejectsCustomProductProviderContinuations() {
        assertFalse(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                NoonPullTriggerMode.MANUAL_REFRESH,
                "detail:PSKU-1"
        )));
        assertFalse(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                NoonPullTriggerMode.READBACK_CHECK,
                "detail:PSKU-2"
        )));
        assertFalse(NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task(
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                NoonPullTriggerMode.MANUAL_REFRESH,
                "product-list:custom-provider"
        )));
    }

    private NoonPullTaskRecord task(
            NoonPullType type,
            NoonPullDataDomain domain,
            NoonPullTriggerMode triggerMode,
            String targetIdentity
    ) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setPullType(type);
        task.setDataDomain(domain);
        task.setTriggerMode(triggerMode);
        task.setTargetIdentity(targetIdentity);
        return task;
    }
}
