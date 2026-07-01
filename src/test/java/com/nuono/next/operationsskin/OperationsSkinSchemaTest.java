package com.nuono.next.operationsskin;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessCapability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OperationsSkinSchemaTest {

    @Test
    void migrationCreatesSkinTablesAndMenuContract() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/154_operations_image_skin_management.sql"));

        String skinTable = tableDefinition(sql, "operations_image_skin");
        String assetTable = tableDefinition(sql, "operations_image_skin_asset");
        String componentTable = tableDefinition(sql, "operations_image_skin_component");
        String normalizedSql = normalizeSql(sql);

        assertAll(
                () -> assertTrue(sql.contains("SET NAMES utf8mb4;")),
                () -> assertSkinTableContract(skinTable),
                () -> assertAssetTableContract(assetTable),
                () -> assertComponentTableContract(componentTable),
                () -> assertTrue(sql.contains("(9800, '运营', 0, '/operations'")),
                () -> assertTrue(sql.contains("(9802, '皮肤管理', 9800, '/operations/skin-management'")),
                () -> assertRoleGrantContract(sql),
                () -> assertRoleOneDisablementContract(normalizedSql),
                () -> assertUserMenuRestoreValidityWindowContract(normalizedSql)
        );
    }

    @Test
    void businessCapabilityRecognizesOperationsSkinManagementPaths() {
        assertTrue(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT.getMenuPathPrefixes()
                .contains("/operations/skin-management"));
        assertTrue(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT.getMenuPathPrefixes()
                .contains("/api/operations/skin-management"));
    }

    private static void assertSkinTableContract(String tableSql) {
        assertAll(
                () -> assertTrue(tableSql.contains("`owner_user_id` BIGINT NOT NULL")),
                () -> assertTrue(tableSql.contains("`store_code` VARCHAR(64) NOT NULL")),
                () -> assertTrue(tableSql.contains("`skin_name` VARCHAR(120) NOT NULL")),
                () -> assertTrue(tableSql.contains("`status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'")),
                () -> assertTrue(tableSql.contains("`cover_image_url` VARCHAR(1024) DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`style_description` TEXT DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`remark` VARCHAR(1024) DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`sort_order` INT NOT NULL DEFAULT 0")),
                () -> assertTrue(tableSql.contains("`created_by` BIGINT DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`updated_by` BIGINT DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP")),
                () -> assertTrue(tableSql.contains("`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")),
                () -> assertTrue(tableSql.contains("`deleted` BIT(1) NOT NULL DEFAULT b'0'")),
                () -> assertTrue(tableSql.contains("`active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED")),
                () -> assertTrue(tableSql.contains("KEY `idx_ops_image_skin_scope` (`owner_user_id`, `store_code`, `deleted`, `status`, `sort_order`, `updated_at`)")),
                () -> assertTrue(tableSql.contains("UNIQUE KEY `uk_ops_image_skin_name` (`owner_user_id`, `store_code`, `skin_name`, `active_unique_key`)")),
                () -> assertFalse(tableSql.contains("UNIQUE KEY `uk_ops_image_skin_name` (`owner_user_id`, `store_code`, `skin_name`, `deleted`)"))
        );
    }

    private static void assertAssetTableContract(String tableSql) {
        assertAll(
                () -> assertTrue(tableSql.contains("`skin_id` BIGINT NOT NULL")),
                () -> assertTrue(tableSql.contains("`asset_type` VARCHAR(32) NOT NULL DEFAULT 'REFERENCE_IMAGE'")),
                () -> assertTrue(tableSql.contains("`image_url` VARCHAR(1024) NOT NULL")),
                () -> assertTrue(tableSql.contains("`caption` VARCHAR(255) DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`sort_order` INT NOT NULL DEFAULT 0")),
                () -> assertTrue(tableSql.contains("`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP")),
                () -> assertTrue(tableSql.contains("`deleted` BIT(1) NOT NULL DEFAULT b'0'")),
                () -> assertTrue(tableSql.contains("KEY `idx_ops_image_skin_asset_skin` (`skin_id`, `deleted`, `sort_order`, `id`)"))
        );
    }

    private static void assertComponentTableContract(String tableSql) {
        assertAll(
                () -> assertTrue(tableSql.contains("`skin_id` BIGINT NOT NULL")),
                () -> assertTrue(tableSql.contains("`template_role` VARCHAR(32) NOT NULL")),
                () -> assertTrue(tableSql.contains("`component_key` VARCHAR(64) NOT NULL")),
                () -> assertTrue(tableSql.contains("`image_url` VARCHAR(1024) DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`x` INT NOT NULL DEFAULT 0")),
                () -> assertTrue(tableSql.contains("`y` INT NOT NULL DEFAULT 0")),
                () -> assertTrue(tableSql.contains("`width` INT NOT NULL DEFAULT 0")),
                () -> assertTrue(tableSql.contains("`height` INT NOT NULL DEFAULT 0")),
                () -> assertTrue(tableSql.contains("`z_index` INT NOT NULL DEFAULT 0")),
                () -> assertTrue(tableSql.contains("`required` BIT(1) NOT NULL DEFAULT b'0'")),
                () -> assertTrue(tableSql.contains("`locked` BIT(1) NOT NULL DEFAULT b'0'")),
                () -> assertTrue(tableSql.contains("`style_json` LONGTEXT DEFAULT NULL")),
                () -> assertTrue(tableSql.contains("`active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED")),
                () -> assertTrue(tableSql.contains("UNIQUE KEY `uk_ops_image_skin_component_slot` (`skin_id`, `template_role`, `component_key`, `active_unique_key`)")),
                () -> assertTrue(tableSql.contains("KEY `idx_ops_image_skin_component_skin` (`skin_id`, `deleted`, `template_role`, `z_index`, `id`)"))
        );
    }

    private static void assertRoleGrantContract(String sql) {
        assertAll(
                () -> assertTrue(roleGrantPattern(2, 9800).matcher(sql).find()),
                () -> assertTrue(roleGrantPattern(2, 9802).matcher(sql).find()),
                () -> assertTrue(roleGrantPattern(3, 9800).matcher(sql).find()),
                () -> assertTrue(roleGrantPattern(3, 9802).matcher(sql).find()),
                () -> assertTrue(roleGrantPattern(4, 9800).matcher(sql).find()),
                () -> assertTrue(roleGrantPattern(4, 9802).matcher(sql).find())
        );
    }

    private static void assertRoleOneDisablementContract(String normalizedSql) {
        assertAll(
                () -> assertTrue(normalizedSql.contains("update `role_menu` set `is_deleted` = b'1', `gmt_updated` = now() where `role_id` = 1")),
                () -> assertTrue(normalizedSql.contains("and `menu_id` in (@operations_menu_id, @skin_management_menu_id)")),
                () -> assertTrue(normalizedSql.contains("update `user_menu` um join `user` u on u.`id` = um.`user_id` set um.`status` = 0")),
                () -> assertTrue(normalizedSql.contains("um.`is_deleted` = b'1'")),
                () -> assertTrue(normalizedSql.contains("and u.`role_id` = 1"))
        );
    }

    private static void assertUserMenuRestoreValidityWindowContract(String normalizedSql) {
        assertAll(
                () -> assertTrue(normalizedSql.contains("coalesce(max(u.`expired_time`), '2099-12-31 23:59:59') as `expired_time`")),
                () -> assertTrue(normalizedSql.contains("set um.`status` = 1, um.`effective_time` = now(), um.`expired_time` = keep_row.`expired_time`, um.`is_deleted` = b'0'")),
                () -> assertFalse(normalizedSql.contains("um.`expired_time` = coalesce(u.`expired_time`")),
                () -> assertTrue(normalizedSql.contains("and u.`role_id` in (2, 3, 4)")),
                () -> assertTrue(normalizedSql.contains("and coalesce(u.`account_type`, '') = 'internal'"))
        );
    }

    private static String tableDefinition(String sql, String tableName) {
        Pattern pattern = Pattern.compile(
                "CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+`" + tableName + "`\\s*\\(.*?\\)\\s+ENGINE=InnoDB",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), "missing table definition for " + tableName);
        return matcher.group();
    }

    private static Pattern roleGrantPattern(int roleId, int menuId) {
        return Pattern.compile(
                "(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*" + menuId + "\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*" + menuId + ")",
                Pattern.CASE_INSENSITIVE
        );
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
