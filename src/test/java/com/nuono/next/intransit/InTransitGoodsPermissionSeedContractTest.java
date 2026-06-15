package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InTransitGoodsPermissionSeedContractTest {

    private static final int MENU_ID = 9302;

    @Test
    void inTransitGoodsUsesDedicatedBusinessMenuAndRoleGrants() throws IOException {
        Path seedPath = Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "107_in_transit_goods_menu_permission.sql"
        );
        assertTrue(Files.exists(seedPath), "in-transit goods permission seed must exist for local and test databases");

        String sql = Files.readString(seedPath);
        assertTrue(sql.contains("在途商品"));
        assertTrue(sql.contains("/purchase/in-transit-goods"));
        assertTrue(sql.contains(String.valueOf(MENU_ID)), "in-transit goods must use menu id 9302");
        assertFalse(roleGrant(1).matcher(sql).find(), "system admin must not receive store business access by default");
        assertTrue(roleGrant(2).matcher(sql).find(), "boss role must receive in-transit goods access");
        assertTrue(roleGrant(3).matcher(sql).find(), "operations supervisor role must receive in-transit goods access");
        assertTrue(roleGrant(4).matcher(sql).find(), "operator role must receive in-transit goods access");
        assertTrue(roleGrant(5).matcher(sql).find(), "procurement role must receive in-transit goods access");
        assertTrue(roleGrant(6).matcher(sql).find(), "warehouse role must receive in-transit goods access");
        assertFalse(sql.contains("(9301, '在途商品'"), "9301 belongs to file management");
    }

    private static Pattern roleGrant(int roleId) {
        return Pattern.compile(
                "(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*@in_transit_goods_menu_id\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*@in_transit_goods_menu_id)",
                Pattern.CASE_INSENSITIVE
        );
    }
}
