package com.nuono.next.logisticsquote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LogisticsQuoteFactSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "065_unified_logistics_quote_facts.sql"
    );

    private static final Set<String> FACT_TABLES = Set.of(
            "logistics_service_line",
            "logistics_cargo_category",
            "logistics_price_rule",
            "logistics_surcharge_rule",
            "logistics_billing_rule",
            "logistics_warehouse_fee_rule",
            "logistics_restriction_rule"
    );

    @Test
    void unifiedLogisticsQuoteFactMigrationDefinesSevenFactTablesWithoutSeparateBatch() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertFalse(sql.contains("logistics_quote_batch"), "file-management published version is the source batch");
        for (String table : FACT_TABLES) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `" + table + "`"), table + " must be defined");
        }
    }

    @Test
    void everyFactTableCarriesFileManagementSourceAndStatusFields() throws IOException {
        String sql = Files.readString(MIGRATION);

        for (String table : FACT_TABLES) {
            String tableSql = sectionForTable(sql, table);
            assertTrue(tableSql.contains("`source_type` VARCHAR(40) NOT NULL"), table + " must store source type");
            assertTrue(tableSql.contains("`source_task_id` BIGINT DEFAULT NULL"), table + " must store source task id");
            assertTrue(tableSql.contains("`source_result_id` BIGINT DEFAULT NULL"), table + " must store source result id");
            assertTrue(tableSql.contains("`source_version_id` BIGINT DEFAULT NULL"), table + " must store source version id");
            assertTrue(tableSql.contains("`source_version_item_id` BIGINT DEFAULT NULL"), table + " must store source version item id");
            assertTrue(tableSql.contains("`source_file_name` VARCHAR(255) DEFAULT NULL"), table + " must store source file name");
            assertTrue(tableSql.contains("`source_locator` VARCHAR(255) DEFAULT NULL"), table + " must store source locator");
            assertTrue(tableSql.contains("`natural_key` VARCHAR(500) NOT NULL"), table + " must store a stable business natural key");
            assertTrue(tableSql.contains("`status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE'"), table + " must store fact status");
            assertTrue(tableSql.contains("KEY `idx_" + table + "_source_version`"), table + " must index source version");
            assertTrue(tableSql.contains("KEY `idx_" + table + "_status`"), table + " must index status");
        }
    }

    @Test
    void factContractEnumsExposeTheCanonicalFactTypesAndStatuses() {
        assertEquals(7, LogisticsQuoteFactType.values().length);
        assertEquals(LogisticsQuoteFactType.SERVICE_LINE, LogisticsQuoteFactType.fromItemType("logistics_service_line"));
        assertEquals(LogisticsQuoteFactType.CARGO_CATEGORY, LogisticsQuoteFactType.fromItemType("logistics_cargo_category"));
        assertEquals(LogisticsQuoteFactType.PRICE_RULE, LogisticsQuoteFactType.fromItemType("logistics_base_price"));
        assertEquals(LogisticsQuoteFactType.SURCHARGE_RULE, LogisticsQuoteFactType.fromItemType("logistics_surcharge"));
        assertEquals(LogisticsQuoteFactType.BILLING_RULE, LogisticsQuoteFactType.fromItemType("logistics_billing_rule"));
        assertEquals(LogisticsQuoteFactType.WAREHOUSE_FEE_RULE, LogisticsQuoteFactType.fromItemType("logistics_warehouse_service_fee"));
        assertEquals(LogisticsQuoteFactType.RESTRICTION_RULE, LogisticsQuoteFactType.fromItemType("logistics_restriction"));

        assertEquals("ACTIVE", LogisticsQuoteFactStatus.ACTIVE.value());
        assertEquals("SUPERSEDED", LogisticsQuoteFactStatus.SUPERSEDED.value());
        assertEquals("DISABLED", LogisticsQuoteFactStatus.DISABLED.value());
        assertEquals("PENDING_MANUAL_CONFIRM", LogisticsQuoteFactStatus.PENDING_MANUAL_CONFIRM.value());
        assertEquals("CONFLICT", LogisticsQuoteFactStatus.CONFLICT.value());
    }

    private static String sectionForTable(String sql, String table) {
        int start = sql.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
        assertTrue(start >= 0, table + " section is missing");
        int next = sql.indexOf("CREATE TABLE IF NOT EXISTS `", start + 1);
        return next >= 0 ? sql.substring(start, next) : sql.substring(start);
    }
}
