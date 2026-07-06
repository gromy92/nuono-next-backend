package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ReplenishmentPlanMapperSqlTest {

    @Test
    void stockQueryUsesOnlyFbnAndSupermallStock() throws Exception {
        String sql = selectSql("selectFbnSupermallStock", Long.class, String.class, String.class);

        assertTrue(sql.contains("pso.fbn_stock AS fbnStockUnits"));
        assertTrue(sql.contains("pso.supermall_stock AS supermallStockUnits"));
        assertTrue(sql.contains("CASE WHEN pso.fbn_stock IS NULL AND pso.supermall_stock IS NULL THEN NULL"));
        assertTrue(sql.contains("COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0)"));
        assertTrue(sql.contains("END AS currentStockUnits"));
        assertTrue(sql.contains("JOIN logical_store ls"));
        assertTrue(sql.contains("JOIN logical_store_site lss"));
        assertTrue(sql.contains("JOIN product_master pm"));
        assertTrue(sql.contains("WHERE ls.owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("AND lss.store_code = #{storeCode}"));
        assertTrue(sql.contains("AND lss.site = #{siteCode}"));
        assertFalse(sql.toLowerCase().contains("fbp_stock"));
    }

    @Test
    void inboundQueryKeepsEtaRowsAndAggregatesRemainingByScopedBatch() throws Exception {
        String sql = selectSql("selectActiveInbound", Long.class, String.class, String.class);

        assertTrue(sql.contains("batch.eta_date AS etaDate"));
        assertTrue(sql.contains("line.remaining_quantity"));
        assertTrue(sql.contains("line.shipped_quantity"));
        assertTrue(sql.contains("line.received_quantity"));
        assertTrue(sql.contains("GREATEST("));
        assertTrue(sql.contains("SUM(batch.lineRemaining) AS remainingQuantity"));
        assertTrue(sql.contains("batch.batch_status NOT IN"));
        assertTrue(sql.contains("line.store_code = #{storeCode}"));
        assertTrue(sql.contains("line.site_code = #{siteCode}"));
        assertTrue(sql.contains("GROUP BY partnerSku, batch.id"));
        assertTrue(sql.contains("SELECT DISTINCT line.id"));
        assertTrue(sql.contains("pv.partner_sku AS canonicalPartnerSku"));
        assertTrue(sql.contains("JOIN logical_store_site lss"));
        assertTrue(sql.contains("JOIN logical_store ls"));
        assertTrue(sql.contains("JOIN product_site_offer pso"));
        assertTrue(sql.contains("JOIN product_variant pv"));
        assertTrue(sql.contains("JOIN product_master pm"));
        assertTrue(sql.contains("LEFT JOIN product_barcode pb"));
        assertFalse(sql.contains("line.psku AS partnerSku"));
        assertFalse(sql.contains("batch.target_store_code"));
        assertFalse(sql.contains("batch.target_site_code"));
        assertFalse(sql.contains("eta_date IS NOT NULL"));
        assertFalse(sql.toLowerCase().contains("official_warehouse_asn"));
    }

    @Test
    void inboundActiveStatusExclusionIncludesAllInactiveStatuses() throws Exception {
        String sql = selectSql("selectActiveInbound", Long.class, String.class, String.class);

        List<String> inactiveStatuses = Arrays.asList("draft", "warehouse_received", "completed", "cancelled");
        for (String status : inactiveStatuses) {
            assertTrue(sql.contains("'" + status + "'"), "missing inactive status: " + status);
        }
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Class<?> mapperClass = Class.forName("com.nuono.next.infrastructure.mapper.ReplenishmentPlanMapper");
        Method method = mapperClass.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, methodName + " must use @Select");
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }
}
