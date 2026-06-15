package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InTransitFreightCostSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "113_in_transit_freight_cost_foundation.sql"
    );

    @Test
    void migrationDefinesActualFreightBillsDetailsEstimatesAndComparisons() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_freight_actual_bill`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_freight_actual_component`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_freight_estimate_snapshot`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_freight_estimate_component`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_freight_estimate_match`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_freight_rate_card_version`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_freight_rate_card_rule`"));

        assertTrue(sql.contains("`source_type` ENUM('PLUGIN_SYNC') NOT NULL DEFAULT 'PLUGIN_SYNC'"));
        assertTrue(sql.contains("`source_system` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`bill_no` VARCHAR(120) NOT NULL"));
        assertFalse(sql.contains("source_document_no"));
        assertTrue(sql.contains("`transport_mode` VARCHAR(20) DEFAULT NULL"));
        assertTrue(sql.contains("`destination_code` VARCHAR(40) DEFAULT NULL"));
        assertTrue(sql.contains("`target_site_code` VARCHAR(80) DEFAULT NULL"));
        assertTrue(sql.contains("`currency_code` VARCHAR(12) NOT NULL DEFAULT 'CNY'"));
        assertTrue(sql.contains("`exchange_rate_to_cny` DECIMAL(18,8) NOT NULL DEFAULT 1.00000000"));
        assertTrue(sql.contains("`raw_fee_name` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`standard_fee_type` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`chargeable_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_volume_cbm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`source_estimate_type` VARCHAR(60) NOT NULL"));
        assertTrue(sql.contains("`rate_card_version_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`match_status` ENUM('matched','unmatched','ambiguous')"));
        assertTrue(sql.contains("`effective_from` DATETIME NOT NULL"));
        assertTrue(sql.contains("`effective_to` DATETIME DEFAULT NULL"));
        assertTrue(sql.contains("`rule_status` VARCHAR(40) NOT NULL DEFAULT 'active'"));
    }
}
