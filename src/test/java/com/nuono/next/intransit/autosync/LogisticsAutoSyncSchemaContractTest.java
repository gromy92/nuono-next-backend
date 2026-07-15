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

    @Test
    void zdDefaultsToDisabledRollingReadWindow() throws Exception {
        LogisticsAutoSyncProperties.Zd zd = new LogisticsAutoSyncProperties().getZd();

        assertThat(zd.isEnabled()).isFalse();
        assertThat(zd.getBaseUrl()).isEqualTo("http://www.erpzd.com");
        assertThat(zd.getLoginPath()).isEqualTo("/api/v1/login");
        assertThat(zd.getExpressPath()).isEqualTo("/api/v1/customer/wuliu/express/integral/q");
        assertThat(zd.getBoxPath()).isEqualTo("/api/v1/customer/wuliu/box/q");
        assertThat(zd.getLookbackDays()).isEqualTo(59);
        assertThat(zd.getLookaheadDays()).isEqualTo(1);

        String applicationYaml = Files.readString(Path.of("src", "main", "resources", "application.yml"));
        assertThat(applicationYaml)
                .contains("NUONO_LOGISTICS_AUTO_SYNC_ZD_ENABLED:false")
                .contains("NUONO_LOGISTICS_AUTO_SYNC_ZD_LOOKBACK_DAYS:59")
                .contains("NUONO_LOGISTICS_AUTO_SYNC_ZD_LOOKAHEAD_DAYS:1");
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
