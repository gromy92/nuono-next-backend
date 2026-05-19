package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductProjectionMergePolicyTest {

    private final ProductProjectionMergePolicy policy = new ProductProjectionMergePolicy();

    @Test
    void shouldPreserveExistingProjectionWhenExternalFieldIsAbsent() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("price", new BigDecimal("48.00"));

        Map<String, ProductFieldRead<?>> reads = new LinkedHashMap<>();
        reads.put("price", ProductFieldRead.absent());

        Map<String, Object> merged = policy.mergeProjectionFields(existing, reads, Set.of());

        assertEquals(new BigDecimal("48.00"), merged.get("price"));
    }

    @Test
    void shouldPreserveExistingProjectionWhenExternalReadFailed() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("salePrice", new BigDecimal("39.90"));

        Map<String, ProductFieldRead<?>> reads = new LinkedHashMap<>();
        reads.put("salePrice", ProductFieldRead.error("pricing API returned 500"));

        Map<String, Object> merged = policy.mergeProjectionFields(existing, reads, Set.of());

        assertEquals(new BigDecimal("39.90"), merged.get("salePrice"));
    }

    @Test
    void shouldApplyReadValueToProjection() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("priceMin", new BigDecimal("11.00"));

        Map<String, ProductFieldRead<?>> reads = new LinkedHashMap<>();
        reads.put("priceMin", ProductFieldRead.value(new BigDecimal("10.50")));

        Map<String, Object> merged = policy.mergeProjectionFields(existing, reads, Set.of());

        assertEquals(new BigDecimal("10.50"), merged.get("priceMin"));
    }

    @Test
    void shouldNotClearProjectionForReadEmptyUnlessFieldIsAuthoritativeEmpty() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("saleEnd", "2026-05-29 23:59:59");

        Map<String, ProductFieldRead<?>> reads = new LinkedHashMap<>();
        reads.put("saleEnd", ProductFieldRead.empty());

        Map<String, Object> preserved = policy.mergeProjectionFields(existing, reads, Set.of());
        Map<String, Object> cleared = policy.mergeProjectionFields(existing, reads, Set.of("saleEnd"));

        assertEquals("2026-05-29 23:59:59", preserved.get("saleEnd"));
        assertTrue(cleared.containsKey("saleEnd"));
        assertNull(cleared.get("saleEnd"));
    }

    @Test
    void shouldBuildReadsFromLegacyMapUsingKeyPresenceNotOnlyValue() {
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("salePrice", "");

        Map<String, ProductFieldRead<?>> reads = policy.readsFromLegacyMap(
                incoming,
                Set.of("price", "salePrice")
        );

        assertEquals(ProductFieldReadState.ABSENT, reads.get("price").getState());
        assertEquals(ProductFieldReadState.READ_EMPTY, reads.get("salePrice").getState());
        assertFalse(reads.get("salePrice").hasValue());
    }
}
