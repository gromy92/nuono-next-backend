package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class LogisticsAutoSyncSchemaContractTest {
    private static final Path SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "178_logistics_auto_sync.sql"
    );

    @Test
    void accountUniquenessOnlyConstrainsActiveRows() throws Exception {
        String sql = Files.readString(SCHEMA_PATH);

        assertThat(sql)
                .contains("`active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END) STORED")
                .contains("UNIQUE KEY `uk_logistics_forwarder_account_active` (`owner_user_id`, `source_system`, `login_account_hash`, `active_unique_key`)")
                .contains("KEY `idx_logistics_forwarder_account_owner` (`owner_user_id`, `source_system`, `is_deleted`)");
        assertThat(sql)
                .doesNotContain("UNIQUE KEY `uk_logistics_forwarder_account_active` (`owner_user_id`, `source_system`, `login_account_hash`, `is_deleted`)");
    }

    @Test
    void mapperUpdatesAreScopedAndFailureRunsPreserveLastSyncedAt() {
        String updateAccountSql = updateSql("updateAccount");
        String updateRunStateSql = updateSql("updateAccountRunState");

        assertThat(updateAccountSql)
                .contains("WHERE id = #{row.id} AND owner_user_id = #{row.ownerUserId} AND is_deleted = b'0'");
        assertThat(updateRunStateSql)
                .contains("last_synced_at = COALESCE(#{lastSyncedAt}, last_synced_at)");
    }

    private static String updateSql(String methodName) {
        Method method = Arrays.stream(LogisticsAutoSyncMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        assertThat(update).isNotNull();
        return String.join(" ", update.value()).replaceAll("\\s+", " ").trim();
    }
}
