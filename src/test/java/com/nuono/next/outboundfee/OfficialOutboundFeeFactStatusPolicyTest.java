package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OfficialOutboundFeeFactStatusPolicyTest {

    private final OfficialOutboundFeeFactStatusPolicy policy = new OfficialOutboundFeeFactStatusPolicy();

    @Test
    void shouldExposeCanonicalTransitionStatuses() {
        assertEquals("ACTIVE", policy.initialStatus(Map.of()));
        assertEquals("PENDING_MANUAL_CONFIRM", policy.initialStatus(Map.of("manualConfirmRequired", true)));
        assertEquals("CONFLICT", policy.conflictStatus());
        assertEquals("SUPERSEDED", policy.supersededStatus());
        assertEquals("DISABLED", policy.withdrawnStatus());
    }

    @Test
    void onlyActiveFactsShouldBeBusinessReadableByDefault() {
        assertTrue(policy.isBusinessReadable("ACTIVE"));
        assertFalse(policy.isBusinessReadable("SUPERSEDED"));
        assertFalse(policy.isBusinessReadable("DISABLED"));
        assertFalse(policy.isBusinessReadable("PENDING_MANUAL_CONFIRM"));
        assertFalse(policy.isBusinessReadable("CONFLICT"));
    }
}
