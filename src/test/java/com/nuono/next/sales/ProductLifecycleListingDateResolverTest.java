package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProductLifecycleListingDateResolverTest {

    private final ProductLifecycleListingDateResolver resolver = new ProductLifecycleListingDateResolver();

    @Test
    void resolvesListingDateByApprovedPriorityRatherThanEarliestDate() {
        ProductLifecycleListingDateResolution resolution = resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 10),
                90,
                45,
                60,
                1
        ), LocalDate.of(2026, 5, 20));

        assertEquals(LocalDate.of(2026, 4, 15), resolution.getListingDate());
        assertEquals("official", resolution.getSource());
        assertEquals("high", resolution.getConfidence());
        assertFalse(resolution.isEligibleForNewInitialization());
    }

    @Test
    void fallsBackThroughInventoryPvSalesButNeverUsesPulledDateAsListingDate() {
        assertEquals("inventory", resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 10),
                0,
                0,
                0,
                1
        ), LocalDate.of(2026, 5, 20)).getSource());

        assertEquals("pv", resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 10),
                0,
                0,
                1,
                0
        ), LocalDate.of(2026, 5, 20)).getSource());

        assertEquals("sales", resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 10),
                0,
                1,
                0,
                0
        ), LocalDate.of(2026, 5, 20)).getSource());

        ProductLifecycleListingDateResolution pulledOnly = resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 10),
                0,
                0,
                0,
                0
        ), LocalDate.of(2026, 5, 20));
        assertEquals("missing", pulledOnly.getSource());
        assertEquals("none", pulledOnly.getConfidence());
        assertNull(pulledOnly.getListingDate());
        assertFalse(pulledOnly.isEligibleForNewInitialization());
    }

    @Test
    void pulledDateDoesNotInitializeNewProductEvenWhenRecent() {
        ProductLifecycleListingDateResolution resolution = resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 18),
                60,
                20,
                45,
                0
        ), LocalDate.of(2026, 5, 20));

        assertEquals("missing", resolution.getSource());
        assertEquals("none", resolution.getConfidence());
        assertTrue(resolution.isHistoricalOldProduct());
        assertFalse(resolution.isEligibleForNewInitialization());
        assertTrue(resolution.getEvidenceJson().contains("\"historicalSignalDays\":60"));
    }

    @Test
    void partialSalesOrPvWindowsAreNotListingDates() {
        ProductLifecycleListingDateResolution salesOnly = resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                null,
                LocalDate.of(2026, 4, 21),
                LocalDate.of(2026, 5, 18),
                30,
                30,
                0,
                0
        ), LocalDate.of(2026, 5, 20));

        assertEquals("missing", salesOnly.getSource());
        assertNull(salesOnly.getListingDate());
        assertFalse(salesOnly.isEligibleForNewInitialization());
        assertTrue(salesOnly.getEvidenceJson().contains("\"leftTruncatedHistoricalWindow\":true"));

        ProductLifecycleListingDateResolution pvOnly = resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                LocalDate.of(2026, 4, 21),
                null,
                LocalDate.of(2026, 5, 18),
                30,
                0,
                30,
                0
        ), LocalDate.of(2026, 5, 20));

        assertEquals("missing", pvOnly.getSource());
        assertNull(pvOnly.getListingDate());
        assertFalse(pvOnly.isEligibleForNewInitialization());
        assertTrue(pvOnly.getEvidenceJson().contains("\"leftTruncatedHistoricalWindow\":true"));
    }

    private ProductLifecycleStateQuery query() {
        return new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1"
        );
    }
}
