package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class InTransitBatchSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "102_in_transit_goods_batch.sql"
    );
    private static final Path LINE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "103_in_transit_goods_line.sql"
    );
    private static final Path NODE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "104_in_transit_logistics_node.sql"
    );
    private static final Path IMPORT_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "105_in_transit_import_audit.sql"
    );
    private static final Path OPERATION_AUDIT_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "106_in_transit_operation_audit.sql"
    );
    private static final Path PACKAGE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "108_in_transit_package.sql"
    );
    private static final Path PACKAGE_CHARGEABLE_WEIGHT_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "112_in_transit_package_chargeable_weight.sql"
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
        assertTrue(sql.contains("`external_shipment_no` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`source_created_at` DATETIME DEFAULT NULL"));
        assertTrue(sql.contains("`estimated_departure_at` DATETIME DEFAULT NULL"));
        assertTrue(sql.contains("`estimated_arrival_at` DATETIME DEFAULT NULL"));
        assertTrue(sql.contains("`delivery_appointment_text` VARCHAR(120) DEFAULT NULL"));
        assertTrue(sql.contains("`box_count` INT DEFAULT NULL"));
        assertTrue(sql.contains("`sku_count` INT DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_in_transit_batch_filter`"));
        assertTrue(sql.contains("KEY `idx_in_transit_batch_eta`"));
        assertTrue(sql.contains("KEY `idx_in_transit_batch_external_shipment`"));
        assertFalse(sql.contains("`remark`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void batchMapperSqlReadsAndWritesExternalShipmentPlanningFields() throws NoSuchMethodException {
        String selectSql = InTransitGoodsMapper.BATCH_SELECT;
        assertTrue(selectSql.contains("batch.external_shipment_no"));
        assertTrue(selectSql.contains("batch.source_created_at"));
        assertTrue(selectSql.contains("batch.estimated_departure_at"));
        assertTrue(selectSql.contains("batch.estimated_arrival_at"));
        assertTrue(selectSql.contains("batch.delivery_appointment_text"));

        Method insertBatch = InTransitGoodsMapper.class.getMethod(
                "insertBatch",
                InTransitBatchRecords.BatchRow.class
        );
        String insertSql = String.join(" ", insertBatch.getAnnotation(Insert.class).value());
        assertTrue(insertSql.contains("external_shipment_no"));
        assertTrue(insertSql.contains("source_created_at"));
        assertTrue(insertSql.contains("estimated_departure_at"));
        assertTrue(insertSql.contains("estimated_arrival_at"));
        assertTrue(insertSql.contains("delivery_appointment_text"));

        Method updateBatch = InTransitGoodsMapper.class.getMethod(
                "updateBatch",
                InTransitBatchRecords.BatchRow.class
        );
        String updateSql = Arrays.stream(updateBatch.getAnnotation(Update.class).value())
                .reduce("", (left, right) -> left + " " + right);
        assertTrue(updateSql.contains("external_shipment_no"));
        assertTrue(updateSql.contains("source_created_at"));
        assertTrue(updateSql.contains("estimated_departure_at"));
        assertTrue(updateSql.contains("estimated_arrival_at"));
        assertTrue(updateSql.contains("delivery_appointment_text"));
    }

    @Test
    void migrationDefinesGoodsLinesWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(LINE_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_goods_line`"));
        assertTrue(sql.contains("`package_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`box_no` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`sku` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`msku` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`psku` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`shipped_quantity` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`received_quantity` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`remaining_quantity` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`carton_count` INT DEFAULT NULL"));
        assertTrue(sql.contains("`units_per_carton` INT DEFAULT NULL"));
        assertTrue(sql.contains("`carton_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`carton_volume_cbm` DECIMAL(18,6) DEFAULT NULL"));
        assertFalse(sql.contains("`source_image_url`"));
        assertFalse(sql.contains("`remark`"));
        assertTrue(sql.contains("KEY `idx_in_transit_goods_line_batch`"));
        assertTrue(sql.contains("KEY `idx_in_transit_goods_line_package`"));
        assertTrue(sql.contains("KEY `idx_in_transit_goods_line_sku`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_in_transit_line_box_psku`"));
        assertTrue(sql.contains("KEY `idx_in_transit_goods_line_batch_psku`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void lineSelectMatchesProductsBySitePskuThenOwnerPartnerSkuFallback() {
        String sql = InTransitGoodsMapper.LINE_SELECT;

        assertTrue(sql.contains("LEFT JOIN in_transit_package pkg"));
        assertTrue(sql.contains("pkg.external_box_no AS external_box_no"));
        assertTrue(sql.contains("pkg.weight_kg AS package_weight_kg"));
        assertTrue(sql.contains("pkg.volume_weight_kg AS package_volume_weight_kg"));
        assertTrue(sql.contains("pkg.chargeable_weight_kg AS package_chargeable_weight_kg"));
        assertTrue(sql.contains("pkg.measured_weight_kg AS measured_weight_kg"));
        assertTrue(sql.contains("pkg.package_status AS package_status"));
        assertTrue(sql.contains("pkg.logistics_status AS logistics_status"));
        assertTrue(sql.contains("exact_pso.psku_code = line.psku"));
        assertTrue(sql.contains("fallback_ls.owner_user_id = line.owner_user_id"));
        assertTrue(sql.contains("fallback_pv.partner_sku = line.psku"));
        assertTrue(sql.contains("pm.cover_image_url AS product_image_url"));
        assertFalse(sql.contains("line.source_image_url"));
    }

    @Test
    void batchAggregateUsesPackageLayerWeightAndVolumeWhenAvailable() throws NoSuchMethodException {
        Method aggregateLines = InTransitGoodsMapper.class.getMethod(
                "aggregateLines",
                Long.class,
                Long.class
        );
        String aggregateSql = String.join(" ", aggregateLines.getAnnotation(Select.class).value());

        assertTrue(aggregateSql.contains("FROM in_transit_package pkg"));
        assertTrue(aggregateSql.contains("SUM(pkg.weight_kg)"));
        assertTrue(aggregateSql.contains("SUM(pkg.volume_cbm)"));
        assertTrue(aggregateSql.contains("COALESCE("));
    }

    @Test
    void batchAggregateRefreshPreservesExistingWeightAndVolumeWhenNoSourceSpecs() throws NoSuchMethodException {
        Method refreshBatchAggregate = InTransitGoodsMapper.class.getMethod(
                "refreshBatchAggregate",
                Long.class,
                Long.class,
                InTransitBatchRecords.BatchAggregateRow.class
        );
        String updateSql = String.join(" ", refreshBatchAggregate.getAnnotation(Update.class).value());

        assertTrue(updateSql.contains("total_weight_kg = COALESCE(#{aggregate.totalWeightKg}, total_weight_kg)"));
        assertTrue(updateSql.contains("total_volume_cbm = COALESCE(#{aggregate.totalVolumeCbm}, total_volume_cbm)"));
    }

    @Test
    void migrationDefinesPackageLayerBetweenBatchAndGoodsLinesWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(PACKAGE_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_package`"));
        assertTrue(sql.contains("`batch_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`box_no` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`external_box_no` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`tracking_no` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`length_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`width_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`height_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`volume_cbm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_length_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_width_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_height_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_volume_cbm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`package_status` VARCHAR(60) DEFAULT NULL"));
        assertTrue(sql.contains("`logistics_status` VARCHAR(60) DEFAULT NULL"));
        assertFalse(sql.contains("`remark`"));
        assertFalse(sql.contains("`merchant_box_no`"));
        assertTrue(sql.contains("KEY `idx_in_transit_package_external_box`"));
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
    void packageMapperSqlReadsAndWritesCanonicalPackageFields() throws NoSuchMethodException {
        String selectSql = InTransitGoodsMapper.PACKAGE_SELECT;
        assertTrue(selectSql.contains("external_box_no"));
        assertTrue(selectSql.contains("length_cm"));
        assertTrue(selectSql.contains("width_cm"));
        assertTrue(selectSql.contains("height_cm"));
        assertTrue(selectSql.contains("volume_weight_kg"));
        assertTrue(selectSql.contains("chargeable_weight_kg"));
        assertTrue(selectSql.contains("measured_weight_kg"));
        assertTrue(selectSql.contains("measured_length_cm"));
        assertTrue(selectSql.contains("measured_width_cm"));
        assertTrue(selectSql.contains("measured_height_cm"));
        assertTrue(selectSql.contains("measured_volume_cbm"));
        assertTrue(selectSql.contains("package_status"));
        assertTrue(selectSql.contains("logistics_status"));

        Method insertPackage = InTransitGoodsMapper.class.getMethod(
                "insertPackage",
                InTransitBatchRecords.PackageRow.class
        );
        String insertSql = String.join(" ", insertPackage.getAnnotation(Insert.class).value());
        assertTrue(insertSql.contains("external_box_no"));
        assertTrue(insertSql.contains("length_cm"));
        assertTrue(insertSql.contains("width_cm"));
        assertTrue(insertSql.contains("height_cm"));
        assertTrue(insertSql.contains("volume_weight_kg"));
        assertTrue(insertSql.contains("chargeable_weight_kg"));
        assertTrue(insertSql.contains("measured_weight_kg"));
        assertTrue(insertSql.contains("measured_length_cm"));
        assertTrue(insertSql.contains("measured_width_cm"));
        assertTrue(insertSql.contains("measured_height_cm"));
        assertTrue(insertSql.contains("measured_volume_cbm"));
        assertTrue(insertSql.contains("package_status"));
        assertTrue(insertSql.contains("logistics_status"));
        assertFalse(insertSql.contains("merchant_box_no"));

        Method updatePackage = InTransitGoodsMapper.class.getMethod(
                "updatePackage",
                InTransitBatchRecords.PackageRow.class
        );
        String updateSql = Arrays.stream(updatePackage.getAnnotation(Update.class).value())
                .reduce("", (left, right) -> left + " " + right);
        assertTrue(updateSql.contains("external_box_no"));
        assertTrue(updateSql.contains("length_cm"));
        assertTrue(updateSql.contains("width_cm"));
        assertTrue(updateSql.contains("height_cm"));
        assertTrue(updateSql.contains("volume_weight_kg"));
        assertTrue(updateSql.contains("chargeable_weight_kg"));
        assertTrue(updateSql.contains("measured_weight_kg"));
        assertTrue(updateSql.contains("measured_length_cm"));
        assertTrue(updateSql.contains("measured_width_cm"));
        assertTrue(updateSql.contains("measured_height_cm"));
        assertTrue(updateSql.contains("measured_volume_cbm"));
        assertTrue(updateSql.contains("package_status"));
        assertTrue(updateSql.contains("logistics_status"));
        assertFalse(updateSql.contains("merchant_box_no"));
    }

    @Test
    void packageChargeableWeightMigrationAddsAirFreightWeightFields() throws IOException {
        String sql = Files.readString(PACKAGE_CHARGEABLE_WEIGHT_MIGRATION);

        assertTrue(sql.contains("`volume_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`chargeable_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertFalse(sql.toLowerCase(Locale.ROOT).contains("fee"));
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
