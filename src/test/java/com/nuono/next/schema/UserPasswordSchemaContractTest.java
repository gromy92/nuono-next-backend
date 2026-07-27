package com.nuono.next.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserPasswordSchemaContractTest {

    @Test
    void bootstrapAndForwardMigrationShouldDefineVersionedPasswordCredentials() throws Exception {
        String bootstrap = Files.readString(
                Path.of("src/main/resources/db/init/000_local_dev_bootstrap.sql")
        );
        String migration = Files.readString(
                Path.of("src/main/resources/db/init/208_user_authentication_credentials.sql")
        );
        String legacyImport = Files.readString(
                Path.of("src/main/resources/db/init/008_import_legacy_internal_account_master_data.sql")
        );
        String localAdmin = Files.readString(
                Path.of("src/main/resources/db/init/013_local_admin_account.sql")
        );
        String whitelistImport = Files.readString(
                Path.of("src/main/resources/db/init/002_import_whitelist_sample.sql")
        );
        String legacyAlignment = Files.readString(
                Path.of("src/main/resources/db/init/010_align_core_tables_to_online_schema.sql")
        );

        assertThat(bootstrap)
                .contains("`password` VARCHAR(200)")
                .contains("`credential_version` BIGINT NOT NULL DEFAULT 0")
                .doesNotContain("`password` VARCHAR(32)");
        assertThat(migration)
                .contains("ALTER TABLE `user`")
                .contains("MODIFY COLUMN `password` VARCHAR(200)")
                .contains("CHARACTER_MAXIMUM_LENGTH < 200")
                .contains("ADD COLUMN `credential_version` BIGINT NOT NULL DEFAULT 0")
                .contains("COLUMN_NAME = 'credential_version'")
                .contains("NOT EXISTS")
                .startsWith("-- Expand adaptive password credentials")
                .contains("SELECT `password` FROM `user` LIMIT 0;")
                .endsWith("SELECT `password`, `credential_version` FROM `user` LIMIT 0;\n");
        assertThat(legacyImport)
                .contains("`credential_version` = `credential_version` + IF(")
                .contains("BINARY `password` <=> BINARY VALUES(`password`)");
        assertThat(localAdmin)
                .contains("credential_version = credential_version + IF(BINARY password <=> BINARY @local_admin_password_hash, 0, 1), password = @local_admin_password_hash")
                .doesNotContain("local_admin_credential_bump");
        assertThat(whitelistImport)
                .contains("INSERT INTO `user` (")
                .contains("credential_version,")
                .contains("u.password,\n  0 AS credential_version,\n  u.token")
                .contains("WHERE imported.id = u.id")
                .doesNotContain("INSERT INTO `user`\nSELECT *");
        assertThat(legacyAlignment)
                .contains("MODIFY COLUMN `password` VARCHAR(200)")
                .contains("ADD COLUMN `credential_version` BIGINT NOT NULL DEFAULT 0");

        assertThat(legacyImport.indexOf("`credential_version` = `credential_version` + IF("))
                .isLessThan(legacyImport.indexOf("`password` = VALUES(`password`)"));
        assertThat(localAdmin.indexOf("credential_version = credential_version + IF("))
                .isLessThan(localAdmin.indexOf(", password = @local_admin_password_hash"));
        assertThat(legacyAlignment.indexOf("INSERT INTO `user`\nSELECT *"))
                .isLessThan(legacyAlignment.indexOf("ADD COLUMN `credential_version`"));
        assertThat(legacyAlignment.indexOf("SELECT `id` FROM cross_border_erp_snapshot_20260428.`role` LIMIT 0"))
                .isLessThan(legacyAlignment.indexOf("DROP TABLE IF EXISTS `user_store`"));
    }
}
