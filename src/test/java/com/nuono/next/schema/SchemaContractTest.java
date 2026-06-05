package com.nuono.next.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SchemaContractTest {
    private static final List<String> REQUIRED_OBJECTS = List.of(
            "CREATE TABLE IF NOT EXISTS file_mgmt_parse_task",
            "CREATE TABLE IF NOT EXISTS file_mgmt_parse_target_plan",
            "CREATE TABLE IF NOT EXISTS product_site_offer",
            "listing_started_at",
            "CREATE TABLE IF NOT EXISTS product_variant_spec",
            "CREATE TABLE IF NOT EXISTS noon_finance_transaction_fact",
            "CREATE TABLE IF NOT EXISTS operational_task",
            "CREATE TABLE IF NOT EXISTS daily_sales_fact"
    );

    @Test
    void initSqlContainsProductionRequiredObjects() throws IOException, InterruptedException {
        StringBuilder sqlBuilder = new StringBuilder();
        for (var path : TrackedSqlFiles.initSqlFiles()) {
            sqlBuilder.append('\n').append(Files.readString(path));
        }
        String sql = sqlBuilder.toString()
                .replace("`", "")
                .toLowerCase(Locale.ROOT);

        for (String object : REQUIRED_OBJECTS) {
            assertTrue(sql.contains(object.toLowerCase(Locale.ROOT)), "Missing schema object: " + object);
        }
    }
}
