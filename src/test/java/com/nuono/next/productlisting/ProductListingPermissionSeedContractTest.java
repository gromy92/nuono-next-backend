package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProductListingPermissionSeedContractTest {

    private static final Pattern SYSTEM_ADMIN_GRANT = roleGrant(1);
    private static final Pattern BUSINESS_ROLE_GRANT = Pattern.compile(
            "r\\.`id`\\s+IN\\s*\\(\\s*2\\s*,\\s*3\\s*,\\s*4\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BOSS_GRANT = roleGrant(2);
    private static final Pattern OPS_MANAGER_GRANT = roleGrant(3);
    private static final Pattern OPS_GRANT = roleGrant(4);

    @Test
    void productListingMenuUsesDedicatedPermissionAndBusinessRoleGrants() throws IOException {
        Path seedPath = Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "081_product_listing_menu_permission.sql"
        );
        assertTrue(Files.exists(seedPath), "product listing permission seed must exist for local databases");

        String sql = Files.readString(seedPath);
        assertTrue(sql.contains("2401"), "product listing seed must define a dedicated menu id");
        assertTrue(sql.contains("商品上架"));
        assertTrue(sql.contains("/purchase/listing"));
        assertFalse(SYSTEM_ADMIN_GRANT.matcher(sql).find(), "system admin must not receive store-level listing access");
        assertTrue(
                BUSINESS_ROLE_GRANT.matcher(sql).find()
                        || (BOSS_GRANT.matcher(sql).find()
                        && OPS_MANAGER_GRANT.matcher(sql).find()
                        && OPS_GRANT.matcher(sql).find()),
                "business roles 2/3/4 must receive product listing access"
        );
    }

    @Test
    void localDbBootstrapStatusListsProductListingScripts() throws IOException {
        String source = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "com",
                "nuono",
                "next",
                "system",
                "LocalDbBootstrapStatusService.java"
        ));

        assertTrue(source.contains("classpath:db/init/080_product_listing_dry_run.sql"));
        assertTrue(source.contains("classpath:db/init/081_product_listing_menu_permission.sql"));
    }

    private static Pattern roleGrant(int roleId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*2401\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*2401\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*2401)",
                Pattern.CASE_INSENSITIVE
        );
    }
}
