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
    void inboundLineQueryUsesExactBarcodeAndDeterministicBatchDestination() throws Exception {
        String sql = selectSql("selectActiveInboundLines", Long.class, String.class, String.class);

        assertTrue(sql.contains("line.id AS lineId"));
        assertTrue(sql.contains("pb.partner_sku AS partnerSku"));
        assertTrue(sql.contains("JOIN product_barcode pb ON pb.barcode = line.sku"));
        assertTrue(sql.contains("pb.logical_store_id IS NOT NULL"));
        assertTrue(sql.contains("COALESCE(pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'"));
        assertTrue(sql.contains("pm.logical_store_id = pb.logical_store_id"));
        assertTrue(sql.contains("BINARY pm.partner_sku = BINARY pb.partner_sku"));
        assertTrue(sql.contains("requested_site.store_code = #{storeCode}"));
        assertTrue(sql.contains("requested_site.site = #{siteCode}"));
        assertTrue(sql.contains("batch.id AS batchId"));
        assertTrue(sql.contains("batch.eta_date AS etaDate"));
        assertTrue(sql.contains("batch.target_store_code"));
        assertTrue(sql.contains("batch.target_site_code"));
        assertTrue(sql.contains("THEN 'SA'"));
        assertTrue(sql.contains("THEN 'AE'"));
        assertTrue(sql.contains("THEN 'RUH'"));
        assertTrue(sql.contains("THEN 'DB'"));
        assertTrue(sql.contains("AS destinationCode"));
        assertTrue(sql.contains("AS resolvedSiteCode"));
        assertTrue(sql.contains("THEN 'SITE_UNRESOLVED' ELSE 'MATCHED'"));
        assertTrue(sql.contains("line.remaining_quantity"));
        assertTrue(sql.contains("line.shipped_quantity"));
        assertTrue(sql.contains("line.received_quantity"));
        assertTrue(sql.contains("GREATEST("));
        assertTrue(sql.contains("AS remainingQuantity"));
        assertTrue(sql.contains("batch.batch_status NOT IN"));
        assertTrue(sql.contains("batch.latest_node_status IS NULL"));
        assertTrue(sql.contains("batch.latest_node_status NOT IN"));
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM in_transit_logistics_node received"));
        assertTrue(sql.contains("received.node_status IN ('warehouse_received', 'cancelled')"));
        assertTrue(sql.contains("DATE_SUB(CURDATE(), INTERVAL 7 MONTH)"));
        assertTrue(sql.contains("DATE(batch.estimated_departure_at)"));
        assertTrue(sql.contains("batch.departure_date"));
        assertTrue(sql.contains("DATE(batch.source_created_at)"));
        assertTrue(sql.contains("FROM in_transit_goods_line line"));
        assertTrue(sql.contains("JOIN in_transit_batch batch"));
        assertTrue(sql.contains("target_offer.logical_store_id = pb.logical_store_id"));
        assertTrue(sql.contains("target_offer.partner_sku = BINARY pb.partner_sku"));
        assertTrue(sql.contains("target_offer.site_id = requested_site.id"));
        assertTrue(sql.contains("= requested_site.site OR"));
        assertTrue(sql.contains("IS NULL)"));
        assertTrue(sql.contains("> 0"));
        assertFalse(sql.contains("line.store_code = #{storeCode}"));
        assertFalse(sql.contains("line.site_code = #{siteCode}"));
        assertFalse(sql.contains("line.psku"));
        assertFalse(sql.contains("line.msku"));
        assertFalse(sql.contains("product_variant"));
        assertFalse(sql.contains("variant_id"));
        assertFalse(sql.contains("AND batch.eta_date IS NOT NULL"));
        assertFalse(sql.toLowerCase().contains("official_warehouse_asn"));
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
