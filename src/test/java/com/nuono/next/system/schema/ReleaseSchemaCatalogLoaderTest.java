package com.nuono.next.system.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseSchemaCatalogLoaderTest {
    @Test
    void acceptsTheCatalogBoundaryAndContinuousOrders() {
        assertDoesNotThrow(() -> ReleaseSchemaCatalogLoader.validateCatalogOrders(
                List.of(227, 228, 229, 230)
        ));
    }

    @Test
    void rejectsACatalogThatStartsAfterThePublishedBoundary() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ReleaseSchemaCatalogLoader.validateCatalogOrders(List.of(228))
        );

        assertTrue(error.getMessage().contains("start at 227"));
    }

    @Test
    void rejectsAGapInTheCatalogOrder() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ReleaseSchemaCatalogLoader.validateCatalogOrders(
                        List.of(227, 229)
                )
        );

        assertTrue(error.getMessage().contains("continuous"));
    }
}
