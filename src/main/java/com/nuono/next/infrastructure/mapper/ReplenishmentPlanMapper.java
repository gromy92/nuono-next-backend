package com.nuono.next.infrastructure.mapper;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.InboundLineRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.ProductIdentityRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.StockRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReplenishmentPlanMapper {

    String ACTIVE_BATCH_FILTER = "AND batch.batch_status NOT IN ('draft', 'warehouse_received', 'completed', 'cancelled')";

    @ConstructorArgs({
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "currentStockUnits", javaType = BigDecimal.class),
            @Arg(column = "fbnStockUnits", javaType = BigDecimal.class),
            @Arg(column = "supermallStockUnits", javaType = BigDecimal.class)
    })
    @Select({
            "SELECT",
            "  pv.partner_sku AS partnerSku,",
            "  COALESCE(NULLIF(pv.child_sku, ''), NULLIF(pso.offer_code, ''), NULLIF(pso.psku_code, '')) AS sku,",
            "  CASE WHEN pso.fbn_stock IS NULL AND pso.supermall_stock IS NULL THEN NULL",
            "    ELSE COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0)",
            "  END AS currentStockUnits,",
            "  pso.fbn_stock AS fbnStockUnits,",
            "  pso.supermall_stock AS supermallStockUnits",
            "FROM product_site_offer pso",
            "JOIN product_variant pv ON pv.id = pso.variant_id AND pv.is_deleted = b'0'",
            "JOIN product_master pm ON pm.id = pv.product_master_id AND pm.is_deleted = b'0'",
            "JOIN logical_store ls ON ls.id = pm.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN logical_store_site lss ON lss.id = pso.site_id",
            "  AND lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = b'0'",
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
            @Arg(column = "psku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "msku", javaType = String.class),
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
            "  line.psku AS psku,",
            "  line.sku AS sku,",
            "  line.msku AS msku,",
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
            "WHERE line.owner_user_id = #{ownerUserId}",
            "  AND line.is_deleted = b'0'",
            "  AND line.store_code = #{storeCode}",
            "  AND line.site_code = #{siteCode}",
            ACTIVE_BATCH_FILTER,
            "  AND GREATEST(COALESCE(line.remaining_quantity,",
            "    COALESCE(line.shipped_quantity, 0) - COALESCE(line.received_quantity, 0)), 0) > 0",
            "ORDER BY batch.eta_date IS NULL ASC, batch.eta_date ASC, batch.id ASC, line.id ASC"
    })
    List<InboundLineRow> selectActiveInboundLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "psoPartnerSku", javaType = String.class),
            @Arg(column = "psoPskuCode", javaType = String.class),
            @Arg(column = "psoOfferCode", javaType = String.class),
            @Arg(column = "childSku", javaType = String.class),
            @Arg(column = "skuParent", javaType = String.class),
            @Arg(column = "barcode", javaType = String.class)
    })
    @Select({
            "SELECT",
            "  pv.partner_sku AS partnerSku,",
            "  pso.partner_sku AS psoPartnerSku,",
            "  pso.psku_code AS psoPskuCode,",
            "  pso.offer_code AS psoOfferCode,",
            "  pv.child_sku AS childSku,",
            "  pm.sku_parent AS skuParent,",
            "  pb.barcode AS barcode",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.site_id = lss.id",
            "  AND pso.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.id = pso.variant_id",
            "  AND pv.logical_store_id = ls.id",
            "  AND pv.is_deleted = b'0'",
            "JOIN product_master pm ON pm.id = pv.product_master_id",
            "  AND pm.logical_store_id = ls.id",
            "  AND pm.is_deleted = b'0'",
            "LEFT JOIN product_barcode pb ON pb.variant_id = pv.id",
            "  AND pb.is_deleted = b'0'",
            "WHERE lss.store_code = #{storeCode}",
            "  AND lss.site = #{siteCode}",
            "  AND lss.is_deleted = b'0'",
            "  AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL",
            "ORDER BY pv.partner_sku ASC, pso.id ASC, pb.is_primary DESC, pb.id ASC"
    })
    List<ProductIdentityRow> selectProductIdentities(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );
}
