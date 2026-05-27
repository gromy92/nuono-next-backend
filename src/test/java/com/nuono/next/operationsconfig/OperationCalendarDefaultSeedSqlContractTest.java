package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OperationCalendarDefaultSeedSqlContractTest {

    @Test
    void seedsDefaultCalendarVersionWithCategoryScopedFactors() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/init/070_operation_calendar_default_category_factors.sql"
        ));

        assertTrue(sql.contains("DEFAULT_CALENDAR_CONFIG"));
        assertTrue(sql.contains("CONCAT(@default_calendar_item_count, ' 条日历配置')"));
        assertTrue(sql.contains("category:stationery-stationery"));
        assertTrue(sql.contains("category:electronic_accessories-accessories"));
        assertTrue(sql.contains("2026-02-18 ~ 2026-03-18 / 0.85"));
        assertEquals(54, countSeedRows(sql));
    }

    private int countSeedRows(String sql) {
        int count = 0;
        int index = 0;
        while ((index = sql.indexOf("业务日历", index)) >= 0) {
            count++;
            index += "业务日历".length();
        }
        return count;
    }
}
