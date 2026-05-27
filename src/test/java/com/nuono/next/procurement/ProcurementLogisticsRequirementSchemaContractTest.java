package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProcurementLogisticsRequirementSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "071_procurement_logistics_requirement.sql"
    );

    @Test
    void migrationShouldCreateSingleRequirementTablePerDemandItem() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `procurement_logistics_requirement`"));
        assertTrue(sql.contains("`demand_item_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("UNIQUE KEY `uk_procurement_logistics_requirement_demand` (`demand_item_id`)"));
        assertTrue(sql.contains("`transport_mode` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`destination_country` VARCHAR(80) DEFAULT NULL"));
        assertTrue(sql.contains("`destination_node` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`origin_node` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`package_length_cm` DECIMAL(12,3) DEFAULT NULL"));
        assertTrue(sql.contains("`package_width_cm` DECIMAL(12,3) DEFAULT NULL"));
        assertTrue(sql.contains("`package_height_cm` DECIMAL(12,3) DEFAULT NULL"));
        assertTrue(sql.contains("`unit_weight_grams` DECIMAL(12,3) DEFAULT NULL"));
        assertTrue(sql.contains("`quantity` INT DEFAULT NULL"));
        assertTrue(sql.contains("`cargo_attributes` VARCHAR(500) DEFAULT NULL"));
    }
}
