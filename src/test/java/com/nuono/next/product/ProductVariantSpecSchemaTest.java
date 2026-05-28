package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductVariantSpecSchemaTest {

    @Test
    void migrationCreatesVariantSpecTableWithRequiredBusinessColumns() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/074_product_variant_spec.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `product_variant_spec`"));
        assertTrue(sql.contains("`variant_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`product_weight_g` DECIMAL(12,2) DEFAULT NULL"));
        assertTrue(sql.contains("`carton_weight_kg` DECIMAL(12,3) DEFAULT NULL"));
        assertTrue(sql.contains("`battery_magnetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown'"));
        assertTrue(sql.contains("`liquid_powder_type` VARCHAR(40) NOT NULL DEFAULT 'unknown'"));
        assertTrue(sql.contains("UNIQUE KEY `uk_product_variant_spec_variant` (`variant_id`)"));
        assertTrue(sql.contains("SELECT 'product_variant_spec'"));
    }
}
