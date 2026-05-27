package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoonProductPullPlanPolicyTest {

    @Test
    void shouldNotCreateScheduledDailyProductPullPlansByDefault() {
        NoonProductPullPlanPolicy policy = new NoonProductPullPlanPolicy();

        List<NoonPullPlanDraft> drafts = policy.defaultProductPlans(307L, "STR245027-NAE", "AE");

        assertFalse(drafts.stream().anyMatch((draft) -> draft.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY));
        assertTrue(drafts.stream().anyMatch((draft) -> draft.getTriggerMode() == NoonPullTriggerMode.MANUAL_REFRESH));
        assertTrue(drafts.stream().allMatch((draft) -> draft.getPullType() == NoonPullType.INTERFACE));
        assertTrue(drafts.stream().allMatch((draft) -> draft.getDataDomain() == NoonPullDataDomain.PRODUCT));
    }
}
