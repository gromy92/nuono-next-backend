package com.nuono.next.aiopsdashboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AiOperationsDashboardPermissionSeedContractTest {

    private static final Pattern SYSTEM_ADMIN_GRANT = roleGrant(1);
    private static final Pattern BOSS_GRANT = roleGrant(2);
    private static final Pattern OPS_MANAGER_GRANT = roleGrant(3);
    private static final Pattern OPS_GRANT = roleGrant(4);

    @Test
    void dashboardMenuUsesDedicatedPermissionAndBusinessRoleGrants() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "054_sales_data_menu_permission.sql"
        ));

        assertDashboardMenuAndRoleMatrix(sql);
    }

    @Test
    void supportedPermissionSeedKeepsDashboardInCanonicalWhitelist() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "035_align_supported_permission_menus.sql"
        ));

        assertDashboardMenuAndRoleMatrix(sql);
    }

    private static void assertDashboardMenuAndRoleMatrix(String sql) {
        assertTrue(sql.contains("9400"), "dashboard seed must keep the 数据 parent menu");
        assertTrue(sql.contains("9403"), "dashboard seed must define a dedicated AI运营看板 menu");
        assertTrue(sql.contains("AI运营看板"));
        assertTrue(sql.contains("/operations/dashboard"));
        assertFalse(SYSTEM_ADMIN_GRANT.matcher(sql).find(), "system admin must not receive store-level AI dashboard by default");
        assertTrue(BOSS_GRANT.matcher(sql).find(), "boss role must receive AI dashboard access");
        assertTrue(OPS_MANAGER_GRANT.matcher(sql).find(), "operations supervisor role must receive AI dashboard access");
        assertTrue(OPS_GRANT.matcher(sql).find(), "operator role must receive AI dashboard access");
    }

    private static Pattern roleGrant(int roleId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*9403\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*9403\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*9403)",
                Pattern.CASE_INSENSITIVE
        );
    }
}
