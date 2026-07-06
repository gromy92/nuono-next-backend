package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class SalesForecastRunSchemaContractTest {

    @Test
    void salesDataFoundationMigrationDefinesForecastRunAndResultTables() throws IOException {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "init", "053_sales_data_analysis_foundation.sql"));

        assertTrue(sql.contains("'sales_forecast_run'"), "sales data sequence must include forecast run ids");
        assertTrue(sql.contains("'sales_forecast_result'"), "sales data sequence must include forecast result ids");
        assertTrue(sql.contains("'sales_forecast_follow_up'"), "sales data sequence must include forecast follow-up ids");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_forecast_run`"));
        assertTrue(sql.contains("`source_data_date` DATE NOT NULL"));
        assertTrue(sql.contains("`calculation_version` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`config_version` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`calendar_version_no` VARCHAR(120) DEFAULT NULL"));
        assertTrue(sql.contains("`calendar_version_name` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`calendar_version_source_label` VARCHAR(120) DEFAULT NULL"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_forecast_result`"));
        assertTrue(sql.contains("`forecast_units_30` INT NOT NULL"));
        assertTrue(sql.contains("`forecast_units_60` INT NOT NULL"));
        assertTrue(sql.contains("`forecast_units_90` INT NOT NULL"));
        assertTrue(sql.contains("`base_daily_sales` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`confidence_level` VARCHAR(40) DEFAULT NULL"));
        assertTrue(sql.contains("`risk_codes` VARCHAR(1000) DEFAULT NULL"));
        assertTrue(sql.contains("`activity_window_summary` VARCHAR(1000) DEFAULT NULL"));
        assertTrue(sql.contains("`feature_snapshot_json` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("UNIQUE KEY `uk_sales_forecast_result_run_product` (`run_id`, `partner_sku`)"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_forecast_follow_up`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_sales_forecast_follow_up_product` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`)"));
        assertTrue(sql.contains("KEY `idx_sales_forecast_follow_up_scope`"));
        assertTrue(sql.contains("KEY `idx_sales_forecast_run_scope`"));
        assertTrue(sql.contains("KEY `idx_sales_forecast_result_run`"));
    }

    @Test
    void pskuIdentityMigrationMergesForecastResultDuplicatesBeforeUniqueKeyUpgrade() throws IOException {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "init", "150_sales_product_psku_identity.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_forecast_result_invalid_psku_archive`"));
        assertTrue(sql.contains("TRIM(`partner_sku`) = '-'"));
        assertTrue(sql.contains("DELETE invalid_result"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_forecast_result_psku_merge_map`"));
        assertTrue(sql.contains("DELETE duplicate_result"));
        assertTrue(sql.contains("ALTER TABLE `sales_forecast_result` ADD UNIQUE KEY `uk_sales_forecast_result_run_product` (`run_id`, `partner_sku`)"));
        assertTrue(sql.contains("ALTER TABLE `sales_forecast_result` DROP INDEX `uk_sales_forecast_result_run_product`"));
    }

    @Test
    void mybatisRunAndResultSaveIsTransactional() throws NoSuchMethodException {
        Transactional transactional = MyBatisSalesForecastRunRepository.class
                .getMethod("saveRunWithResults", SalesForecastRunRecord.class, List.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
    }

    @Test
    void salesForecastCurrentProductBoundaryUsesActivePartnerSku() throws IOException {
        String mapper = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "com",
                "nuono",
                "next",
                "infrastructure",
                "mapper",
                "SalesDataMapper.java"
        ));

        assertTrue(mapper.contains("pv.partner_sku AS partnerSku"));
        assertFalse(mapper.contains("pso.psku_code AS partnerSku"));
        assertTrue(mapper.contains("COALESCE(pso.is_active, b'0') = b'1'"));
        assertTrue(mapper.contains("TRIM(pv.partner_sku) <> ''"));
    }

    @Test
    void salesActivityWindowMapperSqlUsesJavaAnnotationComparators() throws IOException {
        String mapper = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "com",
                "nuono",
                "next",
                "infrastructure",
                "mapper",
                "SalesDataMapper.java"
        ));

        assertTrue(mapper.contains("\"  AND date_to >= #{scope.dateFrom}\","));
        assertTrue(mapper.contains("\"  AND date_from <= #{scope.dateTo}\","));
        assertFalse(mapper.contains("\"  AND date_to &gt;= #{scope.dateFrom}\","));
        assertFalse(mapper.contains("\"  AND date_from &lt;= #{scope.dateTo}\","));
    }
}
