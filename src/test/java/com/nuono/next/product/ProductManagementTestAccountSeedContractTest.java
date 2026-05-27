package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductManagementTestAccountSeedContractTest {

    @Test
    void restoreScriptMakesXingyaoSmokeAccountTheCanonicalBossLogin() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "020_restore_xingyao_product_management_credentials.sql"
        ));

        assertTrue(sql.contains("account_no = 'xingyaoqw'"));
        assertTrue(sql.contains("`password` = 'boss123'"));
        assertTrue(sql.contains("role_id = 2"));
        assertTrue(sql.contains("account_type = 'internal'"));
        assertTrue(sql.contains("WHERE id = 10002"));
        assertTrue(sql.contains("account_no = CONCAT('legacy_', id, '_', account_no)"));
        assertTrue(sql.contains("WHERE account_no = 'xingyaoqw'"));
        assertTrue(sql.contains("AND id <> 10002"));
    }

    @Test
    void restoreScriptCreatesSmokeAccountWhenImportedTestDatabaseDoesNotHaveUser10002() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "020_restore_xingyao_product_management_credentials.sql"
        ));

        assertTrue(sql.contains("INSERT INTO `user`"));
        assertTrue(sql.contains("10002,"));
        assertTrue(sql.contains("'xingyaoqw'"));
        assertTrue(sql.contains("'boss123'"));
    }

    @Test
    void restoreScriptRenamesDeletedDuplicateSmokeAccountsBecauseAccountNoIsGloballyUnique() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "020_restore_xingyao_product_management_credentials.sql"
        ));

        String duplicateRenameBlock = sql.substring(
                sql.indexOf("UPDATE `user`"),
                sql.indexOf("INSERT INTO `user`")
        );

        assertFalse(duplicateRenameBlock.contains("AND is_deleted = 0"));
    }

    @Test
    void restoreScriptCreatesSmokeProjectBindingWhenImportedTestDatabaseOnlyHasLegacyOwner() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "020_restore_xingyao_product_management_credentials.sql"
        ));

        assertTrue(sql.contains("INSERT INTO user_project"));
        assertTrue(sql.contains("'PRJ245027'"));
        assertTrue(sql.contains("'nuonuo@p245027.idp.noon.partners'"));
    }

    @Test
    void restoreScriptGrantsCurrentBossMenusToSmokeAccount() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "020_restore_xingyao_product_management_credentials.sql"
        ));

        assertTrue(sql.contains("INSERT INTO user_menu"));
        assertTrue(sql.contains("JOIN role_menu rm ON rm.role_id = 2"));
        assertTrue(sql.contains("um.user_id = 10002"));
    }

    @Test
    void restoreScriptDoesNotHardCodeLocalSchemaName() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "020_restore_xingyao_product_management_credentials.sql"
        ));

        assertFalse(sql.contains("USE nuono_new_dev"));
    }
}
