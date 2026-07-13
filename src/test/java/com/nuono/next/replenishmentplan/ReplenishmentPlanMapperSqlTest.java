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
    void stockQueryUsesOnlyFbnStockForCurrentReplenishmentInventory() throws Exception {
        String sql = selectSql("selectFbnSupermallStock", Long.class, String.class, String.class);

        assertTrue(sql.contains("pso.fbn_stock AS currentStockUnits"));
        assertTrue(sql.contains("pso.fbn_stock AS fbnStockUnits"));
        assertTrue(sql.contains("pso.supermall_stock AS supermallStockUnits"));
        assertTrue(sql.contains("DATE(pso.listing_started_at) AS listingAt"));
        assertFalse(sql.contains("COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0)"));
        assertTrue(sql.contains("COALESCE("));
        assertTrue(sql.contains("pm.cover_image_url"));
        assertTrue(sql.contains("public_detail.main_image_url"));
        assertTrue(sql.contains("$.content.mainImageUrl"));
        assertTrue(sql.contains("$.content.images[0]"));
        assertTrue(sql.contains("JOIN logical_store ls"));
        assertTrue(sql.contains("JOIN logical_store_site lss"));
        assertTrue(sql.contains("JOIN product_master pm"));
        assertTrue(sql.contains("LEFT JOIN product_public_detail_snapshot public_detail"));
        assertTrue(sql.contains("LEFT JOIN product_master_snapshot pms"));
        assertTrue(sql.contains("WHERE ls.owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("AND lss.store_code = #{storeCode}"));
        assertTrue(sql.contains("AND lss.site = #{siteCode}"));
        assertFalse(sql.toLowerCase().contains("fbp_stock"));
    }

    @Test
    void inboundLineQueryKeepsEtaRowsAndUsesScopedOrUnassignedActiveLineCandidates() throws Exception {
        String sql = selectSql("selectActiveInboundLines", Long.class, String.class, String.class);

        assertTrue(sql.contains("line.id AS lineId"));
        assertTrue(sql.contains("line.psku AS psku"));
        assertTrue(sql.contains("line.sku AS sku"));
        assertTrue(sql.contains("line.msku AS msku"));
        assertTrue(sql.contains("batch.id AS batchId"));
        assertTrue(sql.contains("batch.eta_date AS etaDate"));
        assertTrue(sql.contains("line.remaining_quantity"));
        assertTrue(sql.contains("line.shipped_quantity"));
        assertTrue(sql.contains("line.received_quantity"));
        assertTrue(sql.contains("GREATEST("));
        assertTrue(sql.contains("AS remainingQuantity"));
        assertTrue(sql.contains("batch.batch_status NOT IN"));
        assertTrue(sql.contains("batch.latest_node_status IS NULL"));
        assertTrue(sql.contains("batch.latest_node_status NOT IN"));
        assertTrue(sql.contains("DATE_SUB(CURDATE(), INTERVAL 7 MONTH)"));
        assertTrue(sql.contains("DATE(batch.estimated_departure_at)"));
        assertTrue(sql.contains("batch.departure_date"));
        assertTrue(sql.contains("DATE(batch.source_created_at)"));
        assertTrue(sql.contains("FROM in_transit_goods_line line"));
        assertTrue(sql.contains("JOIN in_transit_batch batch"));
        assertTrue(sql.contains("line.store_code = #{storeCode}"));
        assertTrue(sql.contains("line.site_code = #{siteCode}"));
        assertTrue(sql.contains("NULLIF(TRIM(line.store_code), '') IS NULL"));
        assertTrue(sql.contains("NULLIF(TRIM(line.site_code), '') IS NULL"));
        assertTrue(sql.contains("OR line.site_code = #{siteCode}"));
        assertTrue(sql.contains("> 0"));
        assertFalse(sql.contains("JOIN product_site_offer pso"));
        assertFalse(sql.contains("JOIN product_variant pv"));
        assertFalse(sql.contains("line.psku AS partnerSku"));
        assertFalse(sql.contains("batch.target_store_code"));
        assertFalse(sql.contains("batch.target_site_code"));
        assertFalse(sql.contains("AND batch.eta_date IS NOT NULL"));
        assertFalse(sql.toLowerCase().contains("official_warehouse_asn"));
    }

    @Test
    void productIdentityQueryReadsCanonicalProductCodesForScopedSite() throws Exception {
        String sql = selectSql("selectProductIdentities", Long.class, String.class, String.class);

        assertTrue(sql.contains("pv.partner_sku AS partnerSku"));
        assertTrue(sql.contains("pso.partner_sku AS psoPartnerSku"));
        assertTrue(sql.contains("pso.psku_code AS psoPskuCode"));
        assertTrue(sql.contains("pso.offer_code AS psoOfferCode"));
        assertTrue(sql.contains("pv.child_sku AS childSku"));
        assertTrue(sql.contains("pm.sku_parent AS skuParent"));
        assertTrue(sql.contains("pb.barcode AS barcode"));
        assertTrue(sql.contains("FROM logical_store_site lss"));
        assertTrue(sql.contains("JOIN logical_store ls"));
        assertTrue(sql.contains("JOIN product_site_offer pso"));
        assertTrue(sql.contains("JOIN product_variant pv"));
        assertTrue(sql.contains("JOIN product_master pm"));
        assertTrue(sql.contains("LEFT JOIN product_barcode pb"));
        assertTrue(sql.contains("ls.owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("lss.store_code = #{storeCode}"));
        assertTrue(sql.contains("lss.site = #{siteCode}"));
        assertFalse(sql.contains("in_transit_goods_line"));
    }

    @Test
    void inboundActiveStatusExclusionIncludesAllInactiveStatuses() throws Exception {
        String sql = selectSql("selectActiveInboundLines", Long.class, String.class, String.class);

        List<String> inactiveStatuses = Arrays.asList("draft", "warehouse_received", "completed", "cancelled");
        for (String status : inactiveStatuses) {
            assertTrue(sql.contains("'" + status + "'"), "missing inactive status: " + status);
        }
        assertTrue(sql.contains("batch.latest_node_status NOT IN ('warehouse_received', 'cancelled')"));
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Class<?> mapperClass = Class.forName("com.nuono.next.infrastructure.mapper.ReplenishmentPlanMapper");
        Method method = mapperClass.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, methodName + " must use @Select");
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }
}
