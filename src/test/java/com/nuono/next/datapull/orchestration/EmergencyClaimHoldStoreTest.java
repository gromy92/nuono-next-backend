package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EmergencyClaimHoldStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 1, 0);

    @Test
    void globalHoldBlocksEveryOperationAndExpiresWithoutChangingAFlag() {
        InMemoryEmergencyClaimHoldStore store = new InMemoryEmergencyClaimHoldStore();
        store.place(EmergencyClaimHold.global(
                NOW.plusMinutes(10),
                "PROVIDER_INCIDENT",
                NOW
        ));

        EmergencyClaimHoldSnapshot active = store.activeAt(NOW.plusMinutes(1));
        assertTrue(active.blocksAllClaims());
        assertTrue(active.isClaimHeld(OperationCode.DP04, "scope-a"));
        assertTrue(active.isClaimHeld(OperationCode.DP10, "scope-b"));
        assertFalse(store.activeAt(NOW.plusMinutes(10)).blocksAllClaims());
    }

    @Test
    void operationAndScopeHoldsDoNotLeakOutsideTheirTarget() {
        InMemoryEmergencyClaimHoldStore operationStore = new InMemoryEmergencyClaimHoldStore();
        operationStore.place(EmergencyClaimHold.operation(
                OperationCode.DP04,
                NOW.plusHours(1),
                "DP04_UPSTREAM_INCIDENT",
                NOW
        ));
        EmergencyClaimHoldSnapshot operationHolds = operationStore.activeAt(NOW);
        assertTrue(operationHolds.isClaimHeld(OperationCode.DP04, "scope-a"));
        assertFalse(operationHolds.isClaimHeld(OperationCode.DP05, "scope-a"));

        InMemoryEmergencyClaimHoldStore scopeStore = new InMemoryEmergencyClaimHoldStore();
        scopeStore.place(EmergencyClaimHold.scope(
                OperationCode.DP05,
                "scope-a",
                NOW.plusHours(1),
                "DP05_SCOPE_INCIDENT",
                NOW
        ));
        EmergencyClaimHoldSnapshot scopeHolds = scopeStore.activeAt(NOW);
        assertTrue(scopeHolds.isClaimHeld(OperationCode.DP05, "scope-a"));
        assertFalse(scopeHolds.isClaimHeld(OperationCode.DP05, "scope-b"));
        assertFalse(scopeHolds.isClaimHeld(OperationCode.DP04, "scope-a"));
    }

    @Test
    void repeatedPlacementIsVersionedAndCannotAccidentallyShortenTheHold() {
        InMemoryEmergencyClaimHoldStore store = new InMemoryEmergencyClaimHoldStore();
        store.place(EmergencyClaimHold.operation(
                OperationCode.DP06,
                NOW.plusMinutes(20),
                "FIRST_INCIDENT",
                NOW
        ));
        store.place(EmergencyClaimHold.operation(
                OperationCode.DP06,
                NOW.plusMinutes(5),
                "STALE_INCIDENT",
                NOW.plusMinutes(1)
        ));
        store.place(EmergencyClaimHold.operation(
                OperationCode.DP06,
                NOW.plusMinutes(30),
                "EXTENDED_INCIDENT",
                NOW.plusMinutes(2)
        ));

        EmergencyClaimHold stored = store.snapshot("OPERATION:DP06").orElseThrow();
        assertEquals(NOW.plusMinutes(30), stored.getBlockedUntil());
        assertEquals("EXTENDED_INCIDENT", stored.getSanitizedReason());
        assertEquals(2L, stored.getVersion());
        assertEquals(NOW.plusMinutes(2), stored.getUpdatedAt());
    }

    @Test
    void targetAndReasonMustRemainCanonicalAndSanitized() {
        assertEquals(
                "SCOPE:DP07A:scope-a",
                EmergencyClaimHoldKey.from(
                        EmergencyClaimHoldScope.SCOPE,
                        OperationCode.DP07A,
                        "scope-a"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EmergencyClaimHold.global(NOW.plusMinutes(1), "raw reason", NOW)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EmergencyClaimHoldKey.from(
                        EmergencyClaimHoldScope.GLOBAL,
                        OperationCode.DP04,
                        null
                )
        );
    }
}
