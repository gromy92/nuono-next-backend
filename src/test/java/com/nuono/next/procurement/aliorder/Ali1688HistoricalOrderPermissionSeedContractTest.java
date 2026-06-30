package com.nuono.next.procurement.aliorder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderPermissionSeedContractTest {

    @Test
    void historicalOrderMenuIsReadVisibleToBusinessExecutionRoles() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "071_procurement_ali1688_historical_order_sync.sql"
        )).replaceAll("\\s+", " ");

        assertTrue(sql.contains("2402, '1688 历史订单'"), "seed must define the 1688 historical order menu without reusing sales ids");
        assertTrue(sql.contains("/purchase/ali1688-orders"), "seed must use the historical order route");
        assertTrue(sql.contains("2403, 'SKU 采购历史'"), "seed must define the SKU purchase history menu without reusing sales ids");
        assertTrue(sql.contains("/purchase/ali1688-sku-purchase-history"), "seed must use the SKU purchase history route");
        assertFalse(sql.contains("9401, '1688 历史订单'"), "9401 belongs to 销量分析");
        assertFalse(sql.contains("9402, 'SKU 采购历史'"), "9402 belongs to 销量预测");
        assertTrue(sql.contains("r.`id` IN (2, 3, 4, 5, 6)"),
                "boss, ops manager, operator, procurement, and warehouse roles must receive the menu");
        assertTrue(sql.contains("u.`role_id` IN (2, 3, 4, 5, 6)"),
                "existing users in those roles must receive active user_menu grants");
        assertFalse(sql.contains("r.`id` IN (2, 3, 4)"),
                "permission seed must not stop at boss/ops roles");
        assertFalse(sql.contains("u.`role_id` IN (2, 3, 4)"),
                "user_menu activation must not stop at boss/ops roles");
    }

    @Test
    void localAcceptanceUsersExistForNonBossHistoricalOrderViewingRoles() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "071_procurement_ali1688_historical_order_sync.sql"
        ));

        assertTrue(sql.contains("'operations.manager.demo'"), "local-db must provide an operations manager acceptance user");
        assertTrue(sql.contains("'operation.demo'"), "local-db must provide an operator acceptance user");
        assertTrue(sql.contains("'warehouse.demo'"), "local-db must provide a warehouse acceptance user");
        assertTrue(sql.contains("'procurement.demo'"), "local-db must bind the procurement demo user into this permission");
        assertTrue(sql.contains("user_project_access"), "local acceptance users must receive project scope");
        assertTrue(sql.contains("PRJ108065"), "local acceptance users must be able to view the canman historical orders");
    }
}
