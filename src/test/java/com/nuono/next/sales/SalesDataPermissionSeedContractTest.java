package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SalesDataPermissionSeedContractTest {

    private static final Pattern SYSTEM_ADMIN_GRANT = roleGrant(1);
    private static final Pattern BOSS_GRANT = roleGrant(2);
    private static final Pattern OPS_MANAGER_GRANT = roleGrant(3);
    private static final Pattern OPS_GRANT = roleGrant(4);

    @Test
    void salesDataMenuUsesDedicatedPermissionAndBusinessRoleGrants() throws IOException {
        Path seedPath = Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "054_sales_data_menu_permission.sql"
        );
        assertTrue(Files.exists(seedPath), "sales data permission seed must exist for existing local databases");

        String sql = Files.readString(seedPath);
        assertSalesDataMenuAndRoleMatrix(sql);
        assertFalse(sql.contains("9301"), "sales data must not reuse 9301, which belongs to 文件管理");
    }

    @Test
    void supportedPermissionSeedKeepsSalesDataMenuInCanonicalWhitelist() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "035_align_supported_permission_menus.sql"
        ));

        assertSalesDataMenuAndRoleMatrix(sql);
    }

    private static void assertSalesDataMenuAndRoleMatrix(String sql) {
        assertTrue(sql.contains("9400"), "sales data seed must define the 数据 parent menu");
        assertTrue(sql.contains("9401"), "sales data seed must define the 销量分析 menu");
        assertTrue(sql.contains("销量分析"));
        assertTrue(sql.contains("/data/sales-analysis"));
        assertFalse(SYSTEM_ADMIN_GRANT.matcher(sql).find(), "system admin must not receive store-level sales data by default");
        assertTrue(BOSS_GRANT.matcher(sql).find(), "boss role must receive sales data access");
        assertTrue(OPS_MANAGER_GRANT.matcher(sql).find(), "operations supervisor role must receive sales data access");
        assertTrue(OPS_GRANT.matcher(sql).find(), "operator role must receive sales data access");
    }

    private static Pattern roleGrant(int roleId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*9401\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*9401\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*9401)",
                Pattern.CASE_INSENSITIVE
        );
    }
}
