package com.nuono.next.infrastructure.mapper;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.InboundLineRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.StockRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReplenishmentPlanMapper {

    String DEPARTURE_DATE_EXPRESSION = "COALESCE(DATE(batch.estimated_departure_at), batch.departure_date, "
            + "DATE(batch.source_created_at))";

    String ACTIVE_BATCH_FILTER = "AND batch.batch_status NOT IN ('draft', 'warehouse_received', 'completed', 'cancelled') "
            + "AND (batch.latest_node_status IS NULL OR batch.latest_node_status NOT IN ('warehouse_received', 'cancelled')) "
            + "AND NOT EXISTS (SELECT 1 FROM in_transit_logistics_node received "
            + "WHERE received.owner_user_id = batch.owner_user_id AND received.batch_id = batch.id "
            + "AND received.node_status IN ('warehouse_received', 'cancelled') AND received.is_deleted = b'0') "
            + "AND (batch.eta_date IS NOT NULL OR " + DEPARTURE_DATE_EXPRESSION + " IS NULL OR "
            + DEPARTURE_DATE_EXPRESSION + " >= DATE_SUB(CURDATE(), INTERVAL 7 MONTH))";

    String BATCH_SITE_EXPRESSION = "CASE "
            + "WHEN UPPER(TRIM(COALESCE(batch.target_store_code, ''))) = 'RUH' "
            + "AND UPPER(TRIM(COALESCE(batch.target_site_code, ''))) IN ('', 'SA') THEN 'SA' "
            + "WHEN UPPER(TRIM(COALESCE(batch.target_store_code, ''))) IN ('DB', 'DXB') "
            + "AND UPPER(TRIM(COALESCE(batch.target_site_code, ''))) IN ('', 'AE') THEN 'AE' "
            + "WHEN NULLIF(TRIM(batch.target_store_code), '') IS NULL "
            + "AND UPPER(TRIM(COALESCE(batch.target_site_code, ''))) IN ('SA', 'AE') "
            + "THEN UPPER(TRIM(batch.target_site_code)) "
            + "ELSE NULL END";

    String BATCH_DESTINATION_EXPRESSION = "CASE "
            + "WHEN " + BATCH_SITE_EXPRESSION + " = 'SA' THEN 'RUH' "
            + "WHEN " + BATCH_SITE_EXPRESSION + " = 'AE' THEN 'DB' "
            + "ELSE NULL END";

    @ConstructorArgs({
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "imageUrl", javaType = String.class),
            @Arg(column = "listingAt", javaType = LocalDate.class),
            @Arg(column = "currentStockUnits", javaType = BigDecimal.class),
            @Arg(column = "fbnStockUnits", javaType = BigDecimal.class),
            @Arg(column = "supermallStockUnits", javaType = BigDecimal.class)
    })
    @Select({
            "SELECT",
            "  pv.partner_sku AS partnerSku,",
            "  COALESCE(NULLIF(pv.child_sku, ''), NULLIF(pso.offer_code, ''), NULLIF(pso.psku_code, '')) AS sku,",
            "  COALESCE(",
            "    NULLIF(pm.cover_image_url, ''),",
            "    NULLIF(public_detail.main_image_url, ''),",
            "    NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.mainImageUrl')), 'null'), ''),",
            "    NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.images[0]')), 'null'), '')",
            "  ) AS imageUrl,",
            "  DATE(pso.listing_started_at) AS listingAt,",
            "  pso.fbn_stock AS currentStockUnits,",
            "  pso.fbn_stock AS fbnStockUnits,",
            "  pso.supermall_stock AS supermallStockUnits",
            "FROM product_site_offer pso",
            "JOIN product_variant pv ON pv.id = pso.variant_id AND pv.is_deleted = b'0'",
            "JOIN product_master pm ON pm.id = pv.product_master_id AND pm.is_deleted = b'0'",
            "JOIN logical_store ls ON ls.id = pm.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN logical_store_site lss ON lss.id = pso.site_id",
            "  AND lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = b'0'",
            "LEFT JOIN product_public_detail_snapshot public_detail",
            "  ON public_detail.owner_user_id = ls.owner_user_id",
            "  AND public_detail.store_code = lss.store_code",
            "  AND public_detail.site_code = lss.site",
            "  AND public_detail.product_variant_id = pv.id",
            "  AND public_detail.source_platform = 'NOON'",
            "  AND public_detail.sync_status IN ('SUCCEEDED', 'PARTIAL')",
            "  AND public_detail.is_latest = b'1'",
            "  AND public_detail.is_deleted = b'0'",
            "LEFT JOIN product_master_snapshot pms ON pms.product_master_id = pm.id",
            "  AND pms.snapshot_type = 'baseline'",
            "  AND pms.is_deleted = b'0'",
            "  AND pms.id = (",
            "    SELECT pms_latest.id",
            "    FROM product_master_snapshot pms_latest",
            "    WHERE pms_latest.product_master_id = pm.id",
            "      AND pms_latest.snapshot_type = 'baseline'",
            "      AND pms_latest.is_deleted = b'0'",
            "    ORDER BY pms_latest.fetched_at DESC, pms_latest.id DESC",
            "    LIMIT 1",
            "  )",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND lss.store_code = #{storeCode}",
            "  AND lss.site = #{siteCode}",
            "  AND pso.is_deleted = b'0'",
            "ORDER BY pv.partner_sku ASC, sku ASC"
    })
    List<StockRow> selectFbnSupermallStock(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "lineId", javaType = Long.class),
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "destinationCode", javaType = String.class),
            @Arg(column = "resolvedSiteCode", javaType = String.class),
            @Arg(column = "scopeStatus", javaType = String.class),
            @Arg(column = "batchId", javaType = Long.class),
            @Arg(column = "batchReferenceNo", javaType = String.class),
            @Arg(column = "transportMode", javaType = String.class),
            @Arg(column = "batchStatus", javaType = String.class),
            @Arg(column = "etaDate", javaType = LocalDate.class),
            @Arg(column = "remainingQuantity", javaType = BigDecimal.class)
    })
    @Select({
            "SELECT",
            "  line.id AS lineId,",
            "  pb.partner_sku AS partnerSku,",
            "  " + BATCH_DESTINATION_EXPRESSION + " AS destinationCode,",
            "  " + BATCH_SITE_EXPRESSION + " AS resolvedSiteCode,",
            "  CASE WHEN " + BATCH_SITE_EXPRESSION + " IS NULL THEN 'SITE_UNRESOLVED' ELSE 'MATCHED' END AS scopeStatus,",
            "  batch.id AS batchId,",
            "  batch.batch_reference_no AS batchReferenceNo,",
            "  batch.transport_mode AS transportMode,",
            "  batch.batch_status AS batchStatus,",
            "  batch.eta_date AS etaDate,",
            "  GREATEST(COALESCE(line.remaining_quantity,",
            "    COALESCE(line.shipped_quantity, 0) - COALESCE(line.received_quantity, 0)), 0) AS remainingQuantity",
            "FROM in_transit_goods_line line",
            "JOIN in_transit_batch batch ON batch.owner_user_id = line.owner_user_id",
            "  AND batch.id = line.batch_id",
            "  AND batch.is_deleted = b'0'",
            "JOIN product_barcode pb ON pb.barcode = line.sku",
            "  AND pb.is_deleted = b'0'",
            "  AND pb.logical_store_id IS NOT NULL",
            "  AND NULLIF(TRIM(pb.partner_sku), '') IS NOT NULL",
            "  AND COALESCE(pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'",
            "JOIN product_master pm ON pm.id = pb.product_master_id",
            "  AND pm.logical_store_id = pb.logical_store_id",
            "  AND BINARY pm.partner_sku = BINARY pb.partner_sku",
            "  AND pm.is_deleted = b'0'",
            "JOIN logical_store ls ON ls.id = pb.logical_store_id",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "JOIN logical_store_site requested_site ON requested_site.logical_store_id = pb.logical_store_id",
            "  AND requested_site.store_code = #{storeCode}",
            "  AND requested_site.site = #{siteCode}",
            "  AND requested_site.site_enabled = b'1'",
            "  AND requested_site.is_deleted = b'0'",
            "WHERE line.owner_user_id = #{ownerUserId}",
            "  AND line.is_deleted = b'0'",
            "  AND NULLIF(TRIM(line.sku), '') IS NOT NULL",
            ACTIVE_BATCH_FILTER,
            "  AND GREATEST(COALESCE(line.remaining_quantity,",
            "    COALESCE(line.shipped_quantity, 0) - COALESCE(line.received_quantity, 0)), 0) > 0",
            "  AND EXISTS (",
            "    SELECT 1 FROM product_site_offer target_offer",
            "    WHERE target_offer.logical_store_id = pb.logical_store_id",
            "      AND BINARY target_offer.partner_sku = BINARY pb.partner_sku",
            "      AND target_offer.site_code = requested_site.site",
            "      AND target_offer.site_id = requested_site.id",
            "      AND target_offer.is_deleted = b'0'",
            "  )",
            "  AND (" + BATCH_SITE_EXPRESSION + " = requested_site.site OR " + BATCH_SITE_EXPRESSION + " IS NULL)",
            "ORDER BY batch.eta_date IS NULL ASC, batch.eta_date ASC, batch.id ASC, line.id ASC"
    })
    List<InboundLineRow> selectActiveInboundLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

}
