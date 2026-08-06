package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DataPullScopeColumnLengthTest {

    @Test
    void storeIdentityLimitsMatchAuthoritativeExistingStoreTables() {
        assertDoesNotThrow(() -> new DataPullScope(
                307L,
                8001L,
                "account",
                "P".repeat(100),
                "S".repeat(100),
                "X".repeat(20),
                "scope"
        ));
        assertThrows(IllegalArgumentException.class, () -> new DataPullScope(
                307L, 8001L, "account", "P".repeat(101), "store", "SA", "scope"
        ));
        assertThrows(IllegalArgumentException.class, () -> new DataPullScope(
                307L, 8001L, "account", "project", "S".repeat(101), "SA", "scope"
        ));
        assertThrows(IllegalArgumentException.class, () -> new DataPullScope(
                307L, 8001L, "account", "project", "store", "X".repeat(21), "scope"
        ));
    }

    @Test
    void runtimeIdentityLimitsMatchTheRuntimeLedger() {
        assertDoesNotThrow(() -> new DataPullScope(
                307L,
                8001L,
                "A".repeat(160),
                "E".repeat(160),
                "project",
                "store",
                "SA",
                "K".repeat(96)
        ));
        assertThrows(IllegalArgumentException.class, () -> new DataPullScope(
                307L, 8001L, "A".repeat(161), "project", "store", "SA", "scope"
        ));
        assertThrows(IllegalArgumentException.class, () -> new DataPullScope(
                307L, 8001L, "account", "E".repeat(161),
                "project", "store", "SA", "scope"
        ));
        assertThrows(IllegalArgumentException.class, () -> new DataPullScope(
                307L, 8001L, "account", "project", "store", "SA", "K".repeat(97)
        ));
    }
}
