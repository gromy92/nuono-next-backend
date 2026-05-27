package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SalesForecastPermissionSeedContractTest {

    private static final Pattern SYSTEM_ADMIN_GRANT = roleGrant(1);
    private static final Pattern BOSS_GRANT = roleGrant(2);
    private static final Pattern OPS_MANAGER_GRANT = roleGrant(3);
    private static final Pattern OPS_GRANT = roleGrant(4);

    @Test
    void salesForecastMenuUsesDedicatedPermissionAndBusinessRoleGrants() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "054_sales_data_menu_permission.sql"
        ));

        assertSalesForecastMenuAndRoleMatrix(sql);
    }

    @Test
    void supportedPermissionSeedKeepsSalesForecastInCanonicalWhitelist() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "035_align_supported_permission_menus.sql"
        ));

        assertSalesForecastMenuAndRoleMatrix(sql);
    }

    private static void assertSalesForecastMenuAndRoleMatrix(String sql) {
        assertTrue(sql.contains("9400"), "sales forecast seed must keep the 数据 parent menu");
        assertTrue(sql.contains("9402"), "sales forecast seed must define a dedicated 销量预测 menu");
        assertTrue(sql.contains("销量预测"));
        assertTrue(sql.contains("/data/sales-forecast"));
        assertFalse(SYSTEM_ADMIN_GRANT.matcher(sql).find(), "system admin must not receive store-level sales forecast by default");
        assertTrue(BOSS_GRANT.matcher(sql).find(), "boss role must receive sales forecast access");
        assertTrue(OPS_MANAGER_GRANT.matcher(sql).find(), "operations supervisor role must receive sales forecast access");
        assertTrue(OPS_GRANT.matcher(sql).find(), "operator role must receive sales forecast access");
    }

    private static Pattern roleGrant(int roleId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*9402\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*9402\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*9402)",
                Pattern.CASE_INSENSITIVE
        );
    }
}
