package com.nuono.next.productanalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductLifecycleAnalysisMenuSeedContractTest {

    @Test
    void seedsProductAnalysisMenuAndGrantsInternalRoles() throws IOException {
        Path migration = Path.of("src/main/resources/db/init/071_product_lifecycle_analysis_menu.sql");

        assertTrue(Files.exists(migration), "Missing product lifecycle analysis menu migration.");

        String sql = Files.readString(migration);
        assertTrue(
                sql.contains("(9403, '商品分析', 9400, '/data/product-analysis/lifecycle'"),
                "Migration must seed menu 9403 under 数据 with the lifecycle analysis route."
        );
        assertTrue(
                sql.contains("SET @product_analysis_menu_id = 9403;"),
                "Migration must keep a named product analysis menu id variable."
        );
        assertTrue(containsRoleGrant(sql, 2), "Boss role must receive product analysis access.");
        assertTrue(containsRoleGrant(sql, 3), "Operator role must receive product analysis access.");
        assertTrue(containsRoleGrant(sql, 4), "Finance role must receive product analysis access.");
    }

    private boolean containsRoleGrant(String sql, int roleId) {
        return sql.contains("SELECT " + roleId + " AS `role_id`, 9403 AS `menu_id`")
                || sql.contains("UNION ALL SELECT " + roleId + ", 9403");
    }
}
