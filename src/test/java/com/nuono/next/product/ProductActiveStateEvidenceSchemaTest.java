package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductActiveStateEvidenceSchemaTest {

    @Test
    void migrationAddsTraceableTriStateEvidenceWithoutBackfillingUnknownProducts() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "init",
                "223_product_site_offer_active_state_evidence.sql"
        ));

        assertTrue(sql.contains("`active_state_source` VARCHAR(80) DEFAULT NULL"));
        assertTrue(sql.contains("`active_state_synced_at` DATETIME DEFAULT NULL"));
        assertTrue(sql.contains("`idx_product_site_offer_replenishment_coverage`"));
        assertTrue(sql.contains("`logical_store_id`, `site_id`, `maintenance_enabled`, `is_active`"));
        assertTrue(!sql.toUpperCase().contains("UPDATE `PRODUCT_SITE_OFFER` SET `IS_ACTIVE`"));
    }
}
