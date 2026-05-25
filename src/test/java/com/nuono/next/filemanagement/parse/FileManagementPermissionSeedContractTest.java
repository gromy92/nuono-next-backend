package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FileManagementPermissionSeedContractTest {

    private static final Pattern ADMIN_GRANT = roleGrant(1);
    private static final Pattern BOSS_GRANT = roleGrant(2);
    private static final Pattern OPS_MANAGER_GRANT = roleGrant(3);
    private static final Pattern OPS_GRANT = roleGrant(4);

    @Test
    void supportedPermissionSeedGrantsFileManagementToAdminBossAndOpsManagerOnly() throws IOException {
        String initSql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "035_align_supported_permission_menus.sql"
        ));

        assertFileManagementRoleMatrix(initSql, "035_align_supported_permission_menus.sql");
    }

    @Test
    void releasePermissionCleanupPreservesFileManagementForBossAndOpsManager() throws IOException {
        String releaseSql = Files.readString(Path.of(
                "..",
                "release",
                "20260511_permission_data_integrity_cleanup.sql"
        ));

        assertFileManagementRoleMatrix(releaseSql, "20260511_permission_data_integrity_cleanup.sql");
    }

    private static void assertFileManagementRoleMatrix(String sql, String fileName) {
        assertTrue(ADMIN_GRANT.matcher(sql).find(), fileName + " must grant 9301 文件管理 to system admin");
        assertTrue(BOSS_GRANT.matcher(sql).find(), fileName + " must grant 9301 文件管理 to boss");
        assertTrue(OPS_MANAGER_GRANT.matcher(sql).find(), fileName + " must grant 9301 文件管理 to operations supervisor");
        assertFalse(OPS_GRANT.matcher(sql).find(), fileName + " must not grant 9301 文件管理 to operations");
    }

    private static Pattern roleGrant(int roleId) {
        return Pattern.compile("\\(\\s*" + roleId + "\\s*,\\s*9301\\s*\\)");
    }
}
