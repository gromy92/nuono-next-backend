package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OfficialOutboundFeeFactSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "067_official_fbn_outbound_fee_facts.sql"
    );

    private static final Set<String> FACT_TABLES = Set.of(
            "official_outbound_size_classification_rule",
            "official_outbound_fee_weight_slab_rule",
            "official_outbound_fee_calculation_policy"
    );

    @Test
    void migrationDefinesThreeOfficialOutboundFactTables() throws IOException {
        String sql = Files.readString(MIGRATION);

        for (String table : FACT_TABLES) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `" + table + "`"), table + " must be defined");
        }
    }

    @Test
    void everyFactTableCarriesFileManagementLineageAndStatusFields() throws IOException {
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
            assertTrue(tableSql.contains("`natural_key` VARCHAR(500) NOT NULL"), table + " must store natural key");
            assertTrue(tableSql.contains("`status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE'"), table + " must store fact status");
            assertTrue(tableSql.contains("KEY `idx_" + table + "_source_version`"), table + " must index source version");
            assertTrue(tableSql.contains("KEY `idx_" + table + "_status`"), table + " must index status");
        }
    }

    @Test
    void factContractEnumsExposeCanonicalFactTypesAndStatuses() {
        assertEquals(3, OfficialOutboundFeeFactType.values().length);
        assertEquals(OfficialOutboundFeeFactType.SIZE_CLASSIFICATION,
                OfficialOutboundFeeFactType.fromItemType("outbound_size_classification_rule"));
        assertEquals(OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB,
                OfficialOutboundFeeFactType.fromItemType("outbound_fee_weight_slab_rule"));
        assertEquals(OfficialOutboundFeeFactType.CALCULATION_POLICY,
                OfficialOutboundFeeFactType.fromItemType("outbound_fee_calculation_policy"));

        assertEquals("ACTIVE", OfficialOutboundFeeFactStatus.ACTIVE.value());
        assertEquals("SUPERSEDED", OfficialOutboundFeeFactStatus.SUPERSEDED.value());
        assertEquals("DISABLED", OfficialOutboundFeeFactStatus.DISABLED.value());
        assertEquals("PENDING_MANUAL_CONFIRM", OfficialOutboundFeeFactStatus.PENDING_MANUAL_CONFIRM.value());
        assertEquals("CONFLICT", OfficialOutboundFeeFactStatus.CONFLICT.value());
    }

    private static String sectionForTable(String sql, String table) {
        int start = sql.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
        assertTrue(start >= 0, table + " section is missing");
        int next = sql.indexOf("CREATE TABLE IF NOT EXISTS `", start + 1);
        return next >= 0 ? sql.substring(start, next) : sql.substring(start);
    }
}
