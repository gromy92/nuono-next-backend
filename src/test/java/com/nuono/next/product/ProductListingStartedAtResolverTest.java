package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductListingStartedAtResolverTest {

    private final ProductListingStartedAtResolver resolver = new ProductListingStartedAtResolver();

    @Test
    void shouldPreferPvBeforeInventorySalesPurchaseAndFallback() {
        ProductListingStartedAtResolution resolution = resolver.resolve(new ProductListingStartedAtSignals(
                true,
                LocalDate.of(2026, 5, 10),
                LocalDateTime.of(2026, 5, 3, 9, 30),
                LocalDate.of(2026, 4, 20),
                LocalDateTime.of(2026, 4, 1, 8, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0)
        ));

        assertEquals(LocalDateTime.of(2026, 5, 10, 0, 0), resolution.getStartedAt());
        assertEquals("pv", resolution.getSource());
    }

    @Test
    void shouldUseInventoryBeforeSalesAndPurchaseWhenPvMissing() {
        ProductListingStartedAtResolution resolution = resolver.resolve(new ProductListingStartedAtSignals(
                true,
                null,
                LocalDateTime.of(2026, 5, 3, 9, 30),
                LocalDate.of(2026, 4, 20),
                LocalDateTime.of(2026, 4, 1, 8, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0)
        ));

        assertEquals(LocalDateTime.of(2026, 5, 3, 9, 30), resolution.getStartedAt());
        assertEquals("inventory", resolution.getSource());
    }

    @Test
    void shouldUseSalesBeforePurchaseWhenPvAndInventoryMissing() {
        ProductListingStartedAtResolution resolution = resolver.resolve(new ProductListingStartedAtSignals(
                true,
                null,
                null,
                LocalDate.of(2026, 4, 20),
                LocalDateTime.of(2026, 4, 1, 8, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0)
        ));

        assertEquals(LocalDateTime.of(2026, 4, 20, 0, 0), resolution.getStartedAt());
        assertEquals("sales", resolution.getSource());
    }

    @Test
    void shouldUsePurchaseBeforeFallbackWhenOnlyPurchaseExists() {
        ProductListingStartedAtResolution resolution = resolver.resolve(new ProductListingStartedAtSignals(
                true,
                null,
                null,
                null,
                LocalDateTime.of(2026, 4, 1, 8, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0)
        ));

        assertEquals(LocalDateTime.of(2026, 4, 1, 8, 0), resolution.getStartedAt());
        assertEquals("purchase", resolution.getSource());
    }

    @Test
    void shouldMarkNotListedWhenSalesDataExistsButNoSignalsExist() {
        ProductListingStartedAtResolution resolution = resolver.resolve(new ProductListingStartedAtSignals(
                true,
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 28, 12, 0)
        ));

        assertNull(resolution.getStartedAt());
        assertEquals("not_listed", resolution.getSource());
    }

    @Test
    void shouldMarkDataMissingWhenPvAndSalesDataAreMissing() {
        ProductListingStartedAtResolution resolution = resolver.resolve(new ProductListingStartedAtSignals(
                false,
                null,
                LocalDateTime.of(2026, 5, 3, 9, 30),
                null,
                LocalDateTime.of(2026, 4, 1, 8, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0)
        ));

        assertNull(resolution.getStartedAt());
        assertEquals("data_missing", resolution.getSource());
    }
}
