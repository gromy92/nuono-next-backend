package com.nuono.next.systemreports;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SystemReportsPermissionSeedContractTest {

    private static final Pattern SYSTEM_ADMIN_GRANT = roleGrant(1);
    private static final Pattern BOSS_GRANT = roleGrant(2);
    private static final Pattern OPS_MANAGER_GRANT = roleGrant(3);
    private static final Pattern OPS_GRANT = roleGrant(4);

    @Test
    void systemReportsMenuUsesDedicatedPermissionAndBusinessRoleGrants() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "064_system_reports_store_data_menu_permission.sql"
        ));

        assertStoreDataMenuAndRoleMatrix(sql);
    }

    @Test
    void supportedPermissionSeedKeepsSystemReportsInCanonicalWhitelist() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "035_align_supported_permission_menus.sql"
        ));

        assertStoreDataMenuAndRoleMatrix(sql);
    }

    private static void assertStoreDataMenuAndRoleMatrix(String sql) {
        assertTrue(sql.contains("9600"), "system reports seed must define the 系统报表 parent menu");
        assertTrue(sql.contains("9601"), "system reports seed must define the 店铺数据 menu");
        assertTrue(sql.contains("系统报表"));
        assertTrue(sql.contains("店铺数据"));
        assertTrue(sql.contains("/system-reports/store-data"));
        assertTrue(SYSTEM_ADMIN_GRANT.matcher(sql).find(), "system admin role must receive system reports menu access");
        assertTrue(BOSS_GRANT.matcher(sql).find(), "boss role must receive store data reports access");
        assertTrue(OPS_MANAGER_GRANT.matcher(sql).find(), "operations supervisor role must receive store data reports access");
        assertTrue(OPS_GRANT.matcher(sql).find(), "operator role must receive store data reports access");
    }

    private static Pattern roleGrant(int roleId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*9601\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*9601\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*9601)",
                Pattern.CASE_INSENSITIVE
        );
    }
}
