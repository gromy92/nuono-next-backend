package com.nuono.next.system.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseSchemaCatalogLoaderTest {
    @Test
    void loadsArtifactBoundLivechecksForEveryMigration() {
        List<ReleaseSchemaMigrationDescriptor> catalog =
                new ReleaseSchemaCatalogLoader().load();

        assertTrue(catalog.stream().allMatch(
                migration -> migration.getLivecheckChecksum().matches("[0-9a-f]{64}")
        ));
        ReleaseSchemaMigrationDescriptor migration237 = catalog.stream()
                .filter(migration -> migration.getOrder() == 237)
                .findFirst()
                .orElseThrow();
        assertNotEquals(
                migration237.getPostcheckChecksum(),
                migration237.getLivecheckChecksum()
        );
    }

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
