package com.nuono.next.officialwarehouse;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfficialWarehousePreflightAuditSchemaTest {
    @Test
    void migrationKeepsFailureEvidenceSeparateFromARealAsn() throws Exception {
        assertInitScriptsInclude("classpath:db/init/252_official_warehouse_asn_preflight_audit.sql");
        String sql = Files.readString(Path.of(
                "src/main/resources/db/init/252_official_warehouse_asn_preflight_audit.sql"
        ));
        String mapper = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseAsnPreflightAuditMapper.java"
        ));

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_asn_preflight_audit`")
                .contains("`attempt_asn_id` BIGINT NOT NULL")
                .contains("`invalid_lines_json` LONGTEXT NOT NULL")
                .contains("`failure_code` VARCHAR(120) NOT NULL")
                .doesNotContain("`cookie`")
                .doesNotContain("authorization");
        assertThat(mapper).contains("insertAsnPreflightAudit").contains("nextAsnPreflightAuditId");
    }
}
