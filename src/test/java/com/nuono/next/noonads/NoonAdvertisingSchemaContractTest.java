package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoonAdvertisingSchemaContractTest {

    @Test
    void migrationDefinesReadOnlyAdvertisingFacts() throws IOException {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "init", "159_noon_advertising_read_model.sql"
        ));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_ad_report_batch`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_ad_campaign_fact`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_ad_query_fact`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_noon_ad_campaign_scope`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_noon_ad_query_scope`"));
        assertTrue(sql.contains("KEY `idx_noon_ad_query_performance`"));
        assertTrue(sql.contains("`ad_sku_code` VARCHAR(160) NOT NULL DEFAULT ''"));
        assertTrue(sql.contains("`partner_sku` VARCHAR(160) NOT NULL DEFAULT ''"));
        assertTrue(sql.contains("`campaign_code`, `query_hash`"));
        assertTrue(sql.contains("CHANGE COLUMN `sku` `ad_sku_code`"));
        assertTrue(sql.contains("REGEXP '^Z[A-Za-z0-9]+(-[0-9]+)?$'"));
        assertTrue(sql.contains("SET `query_hash` = SHA2(CONCAT("));
        assertTrue(sql.contains("`query_kind` VARCHAR(40) DEFAULT NULL"));
        assertTrue(sql.contains("`spend_amount` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`ad_revenue` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("'广告投放经营台'"));
        assertTrue(sql.contains("SET @noon_ads_menu_id = 9803"));
        assertFalse(sql.contains("`sku` VARCHAR(160)"));
        assertFalse(sql.contains("(9802, '广告投放经营台'"));
    }
}
