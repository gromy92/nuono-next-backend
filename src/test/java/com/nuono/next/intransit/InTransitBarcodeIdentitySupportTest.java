package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InTransitBarcodeIdentitySupportTest {

    @Test
    void barcodeEqualityIsCaseSensitive() {
        assertTrue(InTransitBarcodeIdentitySupport.sameBarcode("SGGRB329", "SGGRB329"));
        assertFalse(InTransitBarcodeIdentitySupport.sameBarcode("SGGRB329", "sggrb329"));
    }
}
