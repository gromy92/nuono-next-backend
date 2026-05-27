package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OperationConfigPermissionSeedContractTest {

    private static final Pattern SYSTEM_ADMIN_CALENDAR_GRANT = roleGrant(1, 9501);
    private static final Pattern SYSTEM_ADMIN_VERSIONS_GRANT = roleGrant(1, 9503);
    private static final Pattern BOSS_VERSIONS_GRANT = roleGrant(2, 9503);
    private static final Pattern OPS_MANAGER_VERSIONS_GRANT = roleGrant(3, 9503);
    private static final Pattern OPERATOR_VERSIONS_GRANT = roleGrant(4, 9503);
    private static final Pattern BOSS_CALENDAR_GRANT = roleGrant(2, 9501);
    private static final Pattern OPS_MANAGER_CALENDAR_GRANT = roleGrant(3, 9501);
    private static final Pattern OPERATOR_CALENDAR_GRANT = roleGrant(4, 9501);
    private static final Pattern SYSTEM_ADMIN_LIFECYCLE_GRANT = roleGrant(1, 9502);
    private static final Pattern BOSS_LIFECYCLE_GRANT = roleGrant(2, 9502);
    private static final Pattern OPS_MANAGER_LIFECYCLE_GRANT = roleGrant(3, 9502);
    private static final Pattern OPERATOR_LIFECYCLE_GRANT = roleGrant(4, 9502);

    @Test
    void operationConfigMenuUsesDedicatedPermissionAndAuthorizedRoleGrants() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "060_advanced_operations_config_menu_permission.sql"
        ));

        assertTrue(sql.contains("9500"), "seed must define the operations config parent menu");
        assertTrue(sql.contains("9501"), "seed must define the business calendar menu");
        assertTrue(sql.contains("9502"), "seed must define the lifecycle rules menu");
        assertTrue(sql.contains("9503"), "seed must define the operations config versions menu");
        assertTrue(sql.contains("运营配置"));
        assertTrue(sql.contains("运营配置版本"));
        assertTrue(sql.contains("业务日历"));
        assertTrue(sql.contains("生命周期配置"));
        assertTrue(sql.contains("/operations/config/versions"));
        assertTrue(sql.contains("/operations/config/business-calendar"));
        assertTrue(sql.contains("/operations/config/lifecycle-rules"));
        assertTrue(SYSTEM_ADMIN_VERSIONS_GRANT.matcher(sql).find());
        assertTrue(BOSS_VERSIONS_GRANT.matcher(sql).find());
        assertTrue(OPS_MANAGER_VERSIONS_GRANT.matcher(sql).find());
        assertTrue(OPERATOR_VERSIONS_GRANT.matcher(sql).find());
        assertTrue(SYSTEM_ADMIN_CALENDAR_GRANT.matcher(sql).find());
        assertTrue(BOSS_CALENDAR_GRANT.matcher(sql).find());
        assertTrue(OPS_MANAGER_CALENDAR_GRANT.matcher(sql).find());
        assertTrue(OPERATOR_CALENDAR_GRANT.matcher(sql).find());
        assertTrue(SYSTEM_ADMIN_LIFECYCLE_GRANT.matcher(sql).find());
        assertTrue(BOSS_LIFECYCLE_GRANT.matcher(sql).find());
        assertTrue(OPS_MANAGER_LIFECYCLE_GRANT.matcher(sql).find());
        assertTrue(OPERATOR_LIFECYCLE_GRANT.matcher(sql).find());
        assertFalse(sql.contains("file_mgmt_parse"), "operation config must not reuse file management version/menu tables");
    }

    @Test
    void businessCapabilityRecognizesOperationConfigPaths() {
        assertTrue(BusinessCapability.ADVANCED_OPERATIONS_CONFIG.getMenuPathPrefixes()
                .contains("/operations/config"));
        assertTrue(BusinessCapability.ADVANCED_OPERATIONS_CONFIG.getMenuPathPrefixes()
                .contains("/api/operations-config"));
    }

    private static Pattern roleGrant(int roleId, int menuId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*" + menuId + "\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*" + menuId + "\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*" + menuId + ")",
                Pattern.CASE_INSENSITIVE
        );
    }
}
