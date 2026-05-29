package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class InTransitBatchSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "072_in_transit_goods_batch.sql"
    );
    private static final Path LINE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "073_in_transit_goods_line.sql"
    );
    private static final Path NODE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "074_in_transit_logistics_node.sql"
    );
    private static final Path IMPORT_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "075_in_transit_import_audit.sql"
    );
    private static final Path OPERATION_AUDIT_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "076_in_transit_operation_audit.sql"
    );
    private static final Path PACKAGE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "078_in_transit_package.sql"
    );

    @Test
    void migrationDefinesBatchBasicsWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_batch`"));
        assertTrue(sql.contains("`transport_mode` ENUM('SEA','AIR')"));
        assertTrue(sql.contains("`batch_status` ENUM('draft','pending_shipment','shipped','in_transit','customs_clearance','delivering','warehouse_received','exception','completed','cancelled')"));
        assertTrue(sql.contains("`standard_forwarder_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`raw_forwarder_name` VARCHAR(255) DEFAULT NULL"));
        assertTrue(sql.contains("`target_store_code` VARCHAR(80) DEFAULT NULL"));
        assertTrue(sql.contains("`target_warehouse_name` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`sku_count` INT DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_in_transit_batch_filter`"));
        assertTrue(sql.contains("KEY `idx_in_transit_batch_eta`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void migrationDefinesGoodsLinesWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(LINE_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_goods_line`"));
        assertTrue(sql.contains("`package_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`box_no` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`sku` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`msku` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`psku` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`shipped_quantity` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`received_quantity` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`remaining_quantity` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`carton_count` INT DEFAULT NULL"));
        assertTrue(sql.contains("`units_per_carton` INT DEFAULT NULL"));
        assertTrue(sql.contains("`carton_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`carton_volume_cbm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_in_transit_goods_line_batch`"));
        assertTrue(sql.contains("KEY `idx_in_transit_goods_line_package`"));
        assertTrue(sql.contains("KEY `idx_in_transit_goods_line_sku`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void migrationDefinesPackageLayerBetweenBatchAndGoodsLinesWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(PACKAGE_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_package`"));
        assertTrue(sql.contains("`batch_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`box_no` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`tracking_no` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_in_transit_package_batch`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_in_transit_package_box`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void migrationDefinesLogisticsNodesWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(NODE_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_logistics_node`"));
        assertTrue(sql.contains("`node_status` ENUM('created','handed_to_forwarder','departed_origin','in_transit','arrived_port','customs_clearance','customs_released','delivering','warehouse_received','exception','cancelled') NOT NULL"));
        assertTrue(sql.contains("`node_happened_at` DATETIME NOT NULL"));
        assertTrue(sql.contains("`description` VARCHAR(1000) DEFAULT NULL"));
        assertTrue(sql.contains("`operator_name` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_in_transit_logistics_node_batch_time`"));
        assertTrue(sql.contains("KEY `idx_in_transit_logistics_node_status`"));
        assertTrue(sql.contains("column_name = 'latest_node_status'"));
        assertTrue(sql.contains("ADD COLUMN `latest_node_status`"));
        assertTrue(sql.contains("column_name = 'latest_node_happened_at'"));
        assertTrue(sql.contains("ADD COLUMN `latest_node_happened_at`"));
        assertTrue(sql.contains("column_name = 'latest_node_description'"));
        assertTrue(sql.contains("ADD COLUMN `latest_node_description`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void migrationDefinesImportAuditWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(IMPORT_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_import_batch`"));
        assertTrue(sql.contains("`source_type` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`status` ENUM('previewed','imported','failed')"));
        assertTrue(sql.contains("`total_row_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`valid_row_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`error_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`warning_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`summary_json` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("`raw_preview_json` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_in_transit_import_batch_owner_status`"));
        assertTrue(sql.contains("KEY `idx_in_transit_import_batch_updated`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void migrationDefinesOperationAuditWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(OPERATION_AUDIT_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_operation_audit`"));
        assertTrue(sql.contains("`operation_type` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`target_type` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`target_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`batch_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`store_code` VARCHAR(80) DEFAULT NULL"));
        assertTrue(sql.contains("`site_code` VARCHAR(40) DEFAULT NULL"));
        assertTrue(sql.contains("`detail_json` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_in_transit_operation_audit_owner_type`"));
        assertTrue(sql.contains("KEY `idx_in_transit_operation_audit_batch`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }
}
