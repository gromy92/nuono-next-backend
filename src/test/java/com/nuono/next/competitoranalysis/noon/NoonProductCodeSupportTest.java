package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoonProductCodeSupportTest {

    @Test
    void extractsNoonCodeFromProductLinksAndJsonFragments() {
        assertEquals(
                "Z6122BASKETSA",
                NoonProductCodeSupport.extractFirst("https://www.noon.com/saudi-en/foldable-basket/Z6122BASKETSA/p/?o=a1")
                        .orElseThrow()
        );
        assertEquals(
                "N51004211A",
                NoonProductCodeSupport.extractFirst("{\"sku\":\"n51004211a\",\"url\":\"/saudi-en/x/N51004211A/p/\"}")
                        .orElseThrow()
        );
    }

    @Test
    void rejectsShortOrNonNoonIdentifiers() {
        assertTrue(NoonProductCodeSupport.extractFirst("ABC-1234").isEmpty());
        assertTrue(NoonProductCodeSupport.extractFirst("Z123").isEmpty());
    }

    @Test
    void resolvesCodeType() {
        assertEquals("Z_CODE", NoonProductCodeSupport.codeType("z6122basketsa").orElseThrow());
        assertEquals("N_CODE", NoonProductCodeSupport.codeType("N51004211A").orElseThrow());
    }
}
