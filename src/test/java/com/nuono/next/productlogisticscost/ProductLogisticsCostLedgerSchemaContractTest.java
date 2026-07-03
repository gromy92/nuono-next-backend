package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductLogisticsCostLedgerSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "163_product_logistics_cost_ledger.sql"
    );

    @Test
    void migrationDefinesHistoryCurrentProjectionExceptionsAndSequences() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_logistics_cost_history`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_logistics_current_cost`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_logistics_cost_exception`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_logistics_rate_card`");
        assertThat(sql).contains("`source_type` VARCHAR(60) NOT NULL");
        assertThat(sql).contains("`cost_type` VARCHAR(40) NOT NULL");
        assertThat(sql).contains("`idempotency_key` VARCHAR(420) NOT NULL");
        assertThat(sql).contains("UNIQUE KEY `uk_product_logistics_cost_history_source`");
        assertThat(sql).contains("UNIQUE KEY `uk_product_logistics_current_cost_active`");
        assertThat(sql).contains("UNIQUE KEY `uk_product_logistics_rate_card_active`");
        assertThat(sql).contains("SELECT 'product_logistics_cost_history'");
        assertThat(sql).contains("SELECT 'product_logistics_current_cost'");
        assertThat(sql).contains("SELECT 'product_logistics_cost_exception'");
        assertThat(sql).contains("SELECT 'product_logistics_rate_card'");
    }

    @Test
    void currentProjectionUsesProductForwarderSiteTransportAndFeeTypeAsTheActiveDimension() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("`current_cost_slot` VARCHAR(420)");
        assertThat(sql).contains("`owner_user_id`, ':'");
        assertThat(sql).contains("`logical_store_id`, ':'");
        assertThat(sql).contains("COALESCE(`partner_sku`, '')");
        assertThat(sql).contains("COALESCE(`site_code`, '')");
        assertThat(sql).contains("COALESCE(`forwarder_code`, '')");
        assertThat(sql).contains("COALESCE(`transport_mode`, '')");
        assertThat(sql).contains("COALESCE(`fee_type`, '')");
        assertThat(sql).doesNotContain("`product_variant_id`, ':'");
    }

    @Test
    void currentProjectionSlotChangeIsReplaySafe() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("FROM information_schema.STATISTICS");
        assertThat(sql).contains("DROP INDEX `uk_product_logistics_current_cost_active`");
        assertThat(sql).contains("MODIFY COLUMN `current_cost_slot` VARCHAR(420)");
        assertThat(sql).contains("COALESCE(`transport_mode`, ''), ':'");
        assertThat(sql).contains("COALESCE(`fee_type`, '')");
        assertThat(sql).contains("ADD UNIQUE KEY `uk_product_logistics_current_cost_active` (`current_cost_slot`)");
        assertThat(sql).contains("PREPARE stmt FROM @drop_product_logistics_current_cost_active_key");
        assertThat(sql).contains("PREPARE stmt FROM @add_product_logistics_current_cost_active_key");
    }

    @Test
    void migrationPersistsCargoCategoryOnProductCostFacts() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("`cargo_category_code` VARCHAR(80) DEFAULT NULL");
        assertThat(sql).contains("`cargo_category_name` VARCHAR(160) DEFAULT NULL");
        assertThat(sql).contains("TABLE_NAME = 'product_logistics_cost_history'");
        assertThat(sql).contains("TABLE_NAME = 'product_logistics_current_cost'");
        assertThat(sql).contains("COLUMN_NAME = 'cargo_category_code'");
        assertThat(sql).contains("COLUMN_NAME = 'cargo_category_name'");
    }

    @Test
    void migrationAddsBusinessStoreCodeToActualFreightComponentsForCuratedQuoteEvidenceFallback() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("in_transit_freight_actual_component");
        assertThat(sql).contains("COLUMN_NAME = 'store_code'");
        assertThat(sql).contains("ADD COLUMN `store_code` VARCHAR(120) DEFAULT NULL");
    }

    @Test
    void migrationPersistsStoreCodeOnProductCostExceptionsForDisplay() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_logistics_cost_exception`");
        assertThat(sql).contains("`store_code` VARCHAR(120) DEFAULT NULL");
        assertThat(sql).contains("TABLE_NAME = 'product_logistics_cost_exception'");
        assertThat(sql).contains("ADD COLUMN `store_code` VARCHAR(120) DEFAULT NULL");
        assertThat(sql).contains("idx_product_logistics_cost_exception_product");
        assertThat(sql).contains("`owner_user_id`, `store_code`, `partner_sku`, `site_code`");
    }

    @Test
    void migrationPersistsMaintainableRouteCategoryCurrentRateCards() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_logistics_rate_card`");
        assertThat(sql).contains("`rate_card_slot` VARCHAR(420)");
        assertThat(sql).contains("COALESCE(`site_code`, '')");
        assertThat(sql).contains("COALESCE(`forwarder_code`, '')");
        assertThat(sql).contains("COALESCE(`transport_mode`, '')");
        assertThat(sql).contains("COALESCE(`cargo_category_code`, '')");
        assertThat(sql).contains("`unit_cost_cny` DECIMAL(18, 6) NOT NULL");
        assertThat(sql).contains("KEY `idx_product_logistics_rate_card_route`");
        assertThat(sql).contains("GREATEST(COALESCE(MAX(`id`) + 1, 430000), 430000)");
    }
}
