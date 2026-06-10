package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class InTransitGoodsSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "105_in_transit_goods_contract.sql"
    );
    private static final Path SEQUENCE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "117_in_transit_sequence_seed.sql"
    );

    @Test
    void migrationDefinesForwarderAndAliasTablesOnlyForIssueOne() throws IOException {
        String sql = Files.readString(MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_forwarder`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_forwarder_alias`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_contract_probe`"));
        assertTrue(sql.contains("`transport_mode` ENUM('SEA','AIR')"));
        assertTrue(sql.contains("`batch_status` ENUM('draft','pending_shipment','shipped','in_transit','customs_clearance','delivering','warehouse_received','exception','completed','cancelled')"));
        assertTrue(sql.contains("`node_status` ENUM('created','handed_to_forwarder','departed_origin','in_transit','arrived_port','customs_clearance','customs_released','delivering','warehouse_received','exception','cancelled')"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void sequenceMigrationSeedsEveryInTransitSequence() throws IOException {
        String sql = Files.readString(SEQUENCE_MIGRATION);

        assertTrue(sql.contains("'in_transit_forwarder'"));
        assertTrue(sql.contains("'in_transit_forwarder_alias'"));
        assertTrue(sql.contains("'in_transit_batch'"));
        assertTrue(sql.contains("'in_transit_goods_line'"));
        assertTrue(sql.contains("'in_transit_logistics_node'"));
        assertTrue(sql.contains("'in_transit_import_batch'"));
        assertTrue(sql.contains("'in_transit_operation_audit'"));
        assertTrue(sql.contains("'in_transit_package'"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("GREATEST(`product_management_id_sequence`.`next_id`, VALUES(`next_id`))"));
    }
}
