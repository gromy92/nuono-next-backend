package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class FreightBillAutoSyncSchemaContractTest {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "init", "191_logistics_freight_bill_auto_sync.sql"
    );

    @Test
    void migrationAddsIndependentDefaultOffCostSwitchesAndRunState() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("`freight_bill_schedule_enabled` BIT(1) NOT NULL DEFAULT b'0'")
                .contains("`freight_bill_commit_enabled` BIT(1) NOT NULL DEFAULT b'0'")
                .contains("`freight_bill_last_task_id` BIGINT DEFAULT NULL")
                .contains("`idx_logistics_forwarder_account_freight_bill_due`");
    }

    @Test
    void dueQueryAndRunStateUseOnlyIndependentCostColumns() {
        String dueSql = annotationSql("listDueFreightBillAccounts", Select.class);
        String updateSql = annotationSql("updateFreightBillRunState", Update.class);

        assertThat(dueSql)
                .contains("freight_bill_schedule_enabled = b'1'")
                .contains("source_system IN ('CHIC', 'YITE')")
                .doesNotContain("AND schedule_enabled = b'1'")
                .doesNotContain("AND commit_enabled = b'1'");
        assertThat(updateSql)
                .contains("freight_bill_last_synced_at = COALESCE(#{lastSyncedAt}, freight_bill_last_synced_at)")
                .contains("WHERE id = #{accountId} AND owner_user_id = #{ownerUserId}");
    }

    private static <T extends java.lang.annotation.Annotation> String annotationSql(String methodName, Class<T> type) {
        Method method = Arrays.stream(LogisticsAutoSyncMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        if (type == Select.class) {
            return String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        }
        return String.join(" ", method.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");
    }
}
