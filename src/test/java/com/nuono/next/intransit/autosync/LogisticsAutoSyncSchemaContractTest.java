package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
