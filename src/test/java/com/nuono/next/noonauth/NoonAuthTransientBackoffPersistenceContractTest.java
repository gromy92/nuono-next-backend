package com.nuono.next.noonauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoonAuthTransientBackoffPersistenceContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/init/203_noon_auth_transient_backoff_state.sql"
    );

    @Test
    void migrationKeepsOneIndependentRowPerLogicalStoreAndExactErrorType() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `noon_auth_transient_backoff_state`")
                .contains("PRIMARY KEY (`logical_store_id`, `error_type`)")
                .contains("`source_recovery_id` BIGINT")
                .contains("`attempt_count` INT NOT NULL")
                .contains("`blocked_until` DATETIME NOT NULL")
                .doesNotContain("`last_site_code`")
                .doesNotContain("`source_task_id`");
    }

    @Test
    void mapperUsesAtomicIncrementAndRecoveryFencedReset() throws Exception {
        Class<?> mapperType = Class.forName(
                "com.nuono.next.infrastructure.mapper.NoonAuthTransientBackoffMapper"
        );
        String mapperSource = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/NoonAuthTransientBackoffMapper.java"
        ));

        assertThat(mapperType.getSimpleName()).isEqualTo("NoonAuthTransientBackoffMapper");
        assertThat(mapperSource)
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("attempt_count + 1")
                .contains("WHEN attempt_count <= 0 THEN 2")
                .contains("WHEN attempt_count = 1 THEN 4")
                .contains("WHEN attempt_count = 2 THEN 8")
                .contains("ELSE 16")
                .contains("lockRecoveryById")
                .contains("FOR UPDATE")
                .contains("countCurrentRecoveryFence")
                .contains("lease_until > UTC_TIMESTAMP")
                .contains("active_identity_slot IS NOT NULL")
                .contains("INSERT INTO logical_store")
                .contains("ON DUPLICATE KEY UPDATE id = id")
                .doesNotContain("ON DUPLICATE KEY UPDATE\",\n            \"  project_name")
                .contains("WHERE logical_store_id = #{logicalStoreId}")
                .contains("AND source_recovery_id = #{recoveryId}")
                .contains("AND attempt_count > 0")
                .doesNotContain("attempt_count = VALUES(attempt_count)");
        assertThat(mapperSource.indexOf("blocked_until = TIMESTAMPADD"))
                .isLessThan(mapperSource.indexOf("attempt_count = IF"));
    }
}
