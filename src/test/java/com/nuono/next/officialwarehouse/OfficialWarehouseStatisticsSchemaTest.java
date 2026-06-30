package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OfficialWarehouseStatisticsSchemaTest {

    @Test
    void officialWarehouseStatisticsSchemaAddsCorrectionEventsAndInventorySnapshots() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"));
        String sql = Files.readString(Path.of("src/main/resources/db/init/143_official_warehouse_statistics.sql"));
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(bootstrap).contains("classpath:db/init/143_official_warehouse_statistics.sql");
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_stock_correction_event`")
                .contains("`correction_type` VARCHAR(40) NOT NULL")
                .contains("`target_ref_type` VARCHAR(80) NOT NULL")
                .contains("`product_site_offer_id` BIGINT DEFAULT NULL")
                .contains("`from_stock_bucket` VARCHAR(60) DEFAULT NULL")
                .contains("`to_stock_bucket` VARCHAR(60) NOT NULL")
                .contains("KEY `idx_official_warehouse_stock_correction_scope`")
                .contains("KEY `idx_official_warehouse_stock_correction_product`");
        assertThat(sql).contains("'official_warehouse_stock_correction_event'");

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_inventory_sync_batch`")
                .contains("`source_type` VARCHAR(60) NOT NULL")
                .contains("`request_summary_json` LONGTEXT DEFAULT NULL")
                .contains("`response_summary_json` LONGTEXT DEFAULT NULL")
                .contains("KEY `idx_official_warehouse_inventory_sync_scope`");
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_inventory_snapshot_line`")
                .contains("`sync_batch_id` BIGINT NOT NULL")
                .contains("`inventory_type` VARCHAR(100) DEFAULT NULL")
                .contains("`reason_code` VARCHAR(100) DEFAULT NULL")
                .contains("`stock_bucket` VARCHAR(60) NOT NULL")
                .contains("`is_current` BIT(1) NOT NULL DEFAULT b'1'")
                .contains("KEY `idx_official_warehouse_inventory_line_scope`")
                .contains("KEY `idx_official_warehouse_inventory_line_sku`")
                .contains("KEY `idx_official_warehouse_inventory_line_partner_sku`");
        assertThat(sql).contains("'official_warehouse_inventory_sync_batch'");
        assertThat(sql).contains("'official_warehouse_inventory_snapshot_line'");

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_report_import`")
                .contains("`report_type` VARCHAR(80) NOT NULL")
                .contains("`source_export_code` VARCHAR(120) DEFAULT NULL")
                .contains("`file_sha256` VARCHAR(64) DEFAULT NULL")
                .contains("`summary_json` LONGTEXT DEFAULT NULL")
                .contains("KEY `idx_official_warehouse_report_import_export`");
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_report_row`")
                .contains("`business_key_hash` VARCHAR(64) DEFAULT NULL")
                .contains("`raw_row_json` LONGTEXT DEFAULT NULL")
                .contains("`normalized_row_json` LONGTEXT DEFAULT NULL")
                .contains("UNIQUE KEY `uk_official_warehouse_report_row_import_row`");
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_inbound_receipt_line`")
                .contains("`report_row_id` BIGINT NOT NULL")
                .contains("`received_qty` INT NOT NULL DEFAULT 0")
                .contains("`qc_failed_qty` INT NOT NULL DEFAULT 0")
                .contains("`unidentified_qty` INT NOT NULL DEFAULT 0")
                .contains("`receipt_status` VARCHAR(60) NOT NULL")
                .contains("`match_status` VARCHAR(60) NOT NULL")
                .contains("KEY `idx_official_warehouse_receipt_line_scope`");
        assertThat(sql).contains("'official_warehouse_report_import'");
        assertThat(sql).contains("'official_warehouse_report_row'");
        assertThat(sql).contains("'official_warehouse_inbound_receipt_line'");

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_delivery_accuracy_asn`")
                .contains("`report_row_id` BIGINT NOT NULL")
                .contains("`appointment_id` BIGINT DEFAULT NULL")
                .contains("`scheduled_qty` INT NOT NULL DEFAULT 0")
                .contains("`grn_qty` INT NOT NULL DEFAULT 0")
                .contains("`inbound_qty_variance` INT NOT NULL DEFAULT 0")
                .contains("`accuracy_status` VARCHAR(80) DEFAULT NULL")
                .contains("`inbound_utilization_efficiency` DECIMAL(8,2) DEFAULT NULL")
                .contains("`match_status` VARCHAR(60) NOT NULL")
                .contains("KEY `idx_official_warehouse_delivery_accuracy_scope`")
                .contains("KEY `idx_official_warehouse_delivery_accuracy_status`");
        assertThat(sql).contains("'official_warehouse_delivery_accuracy_asn'");

        assertThat(normalized).doesNotContain("create table if not exists `official_warehouse_stock_bucket_projection`");
        assertThat(normalized).doesNotContain("authorization");
        assertThat(normalized).doesNotContain("`cookie`");
    }

    @Test
    void stockStatisticsMapperDoesNotTruncateRowsBeforeSummaryAggregation() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseStatisticsMapper.java"));

        assertThat(mapper).doesNotContain("\"LIMIT 1000\"");
    }

    @Test
    void stockStatisticsMapperAggregatesInventoryRowsByProductInsteadOfWarehouse() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseStatisticsMapper.java"));

        assertThat(mapper)
                .contains("COUNT(DISTINCT NULLIF(l.warehouse_code, ''))")
                .contains("JOIN product_site_offer pso")
                .contains("LEFT JOIN (")
                .contains("FROM official_warehouse_inventory_snapshot_line")
                .doesNotContain("\"         pm.cover_image_url, l.warehouse_code\"");
    }

    @Test
    void inboundReportStatisticsDeduplicateAcrossHistoricalImportsByBusinessKey() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseStatisticsMapper.java"));

        assertThat(mapper)
                .contains("ROW_NUMBER() OVER (")
                .contains("AS receipt_business_rank")
                .contains("AS accuracy_business_rank")
                .contains("WHERE ranked.receipt_business_rank = 1")
                .contains("WHERE ranked.accuracy_business_rank = 1");
        assertThat(mapper).doesNotContain("SELECT MAX(i2.id)");
    }

    @Test
    void inventoryLineProductMatchTreatsPartnerSkuAsBusinessPskuOnly() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseStatisticsMapper.java"));

        assertThat(mapper)
                .contains("BINARY pv.partner_sku = BINARY #{partnerSku}")
                .doesNotContain("BINARY pso.psku_code = BINARY #{partnerSku}");
    }

    @Test
    void productPerspectiveStockMatchingUsesPartnerSkuOnlyAsProductIdentity() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseStatisticsService.java"));

        assertThat(service)
                .contains("lookupKeys(row.partnerSku)")
                .contains("lookupKeys(product.getPartnerSku())")
                .doesNotContain("lookupKeys(row.skuParent, row.partnerSku, row.pskuCode, row.noonSku)")
                .doesNotContain("lookupKeys(warehouseStock.partnerSku, warehouseStock.pskuCode, warehouseStock.noonSku)")
                .doesNotContain("lookupKeys(stockRow.skuParent, stockRow.partnerSku, stockRow.pskuCode, stockRow.noonSku)")
                .doesNotContain("lookupKeys(product.getSkuParent(), product.getPartnerSku(), product.getPskuCode(), product.getOfferCode())");
    }
}
