package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoonProductDetailBackfillPlannerTest {

    @Test
    void shouldSelectOnlyDemandedOrPriorityDetailsWithoutBlindAllSkuFetch() {
        NoonProductDetailBackfillPlanner planner = new NoonProductDetailBackfillPlanner();

        NoonProductDetailBackfillPlan plan = planner.plan(NoonProductDetailBackfillRequest.builder()
                .allSkuParents(List.of("Z1", "Z2", "Z3", "Z4", "Z5", "Z6", "Z7", "Z8", "Z9", "Z10"))
                .openedSkuParent("Z1")
                .missingBaselineSkuParents(List.of("Z2"))
                .explicitRefreshSkuParents(List.of("Z3"))
                .publishReadbackSkuParents(List.of("Z4"))
                .prioritySkuParents(List.of("Z5", "Z6", "Z7"))
                .maxDetailFetches(6)
                .build());

        assertEquals(List.of("Z1", "Z2", "Z3", "Z4", "Z5", "Z6"), plan.getSkuParents());
        assertEquals("bounded_priority", plan.getMode());
        assertFalse(plan.getSkuParents().contains("Z7"));
        assertFalse(plan.getSkuParents().contains("Z8"));
        assertFalse(plan.isBlindFullStoreFetch());
    }
}
