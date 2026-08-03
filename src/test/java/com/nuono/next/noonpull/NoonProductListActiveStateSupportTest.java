package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NoonProductListActiveStateSupportTest {

    @ParameterizedTest
    @ValueSource(strings = {"LIVE", "ACTIVE", "true", "1", "yes", "enabled", "ON"})
    void mapsPositiveProductListStates(String value) {
        assertEquals(Boolean.TRUE, NoonProductListActiveStateSupport.resolve(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_LIVE", "OFFLINE", "INACTIVE", "false", "0", "no", "disabled"})
    void mapsNegativeProductListStates(String value) {
        assertEquals(Boolean.FALSE, NoonProductListActiveStateSupport.resolve(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"pending", "unknown"})
    void leavesUnsupportedStatesUnresolved(String value) {
        assertNull(NoonProductListActiveStateSupport.resolve(value));
    }
}
