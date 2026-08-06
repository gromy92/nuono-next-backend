package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DataPullRuntimePropertiesTest {

    @Test
    void leaseMustCoverThreeBoundedAdvanceBudgets() {
        DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
        assertDoesNotThrow(properties::validate);

        properties.setLeaseSeconds(
                DataPullRuntimeProperties.ADVANCE_BUDGET_SECONDS
                        * DataPullRuntimeProperties.MINIMUM_LEASE_MULTIPLIER - 1L
        );

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void leaderLeaseMustOutliveOneSchedulerPhaseAndOneCompleteAdvance() {
        DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
        properties.setLeaderLeaseSeconds(
                DataPullRuntimeProperties.MINIMUM_LEADER_LEASE_SECONDS - 1L
        );

        assertThrows(IllegalStateException.class, properties::validate);
    }
}
