package com.nuono.next.infrastructure.mapper;

import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.ConfirmedHeadhaulAllocationRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureAllocationRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureCandidateRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosurePurchaseBatchRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureSummaryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface PostSaleProfitLogisticsClosureMapper {
    String LATEST_RUN_SQL = "latest_run AS ("
            + " SELECT run.id"
            + " FROM post_sale_profit_recalculation_run run"
            + " WHERE run.owner_user_id = #{ownerUserId}"
            + " AND run.store_code = #{storeCode}"
            + " AND run.site_code = #{siteCode}"
            + " AND run.is_deleted = b'0'"
            + " <if test='dateFrom != null'>AND run.date_from = #{dateFrom}</if>"
            + " <if test='dateTo != null'>AND run.date_to = #{dateTo}</if>"
            + " ORDER BY CASE WHEN run.status = 'PREVIEW' THEN 0 ELSE 1 END, run.finished_at DESC, run.id DESC"
            + " LIMIT 1"
            + ")";

    String PROFIT_PURCHASE_SOURCE_SQL = "SELECT profit.purchase_source_type COLLATE utf8mb4_unicode_ci AS source_type,"
            + " profit.purchase_source_id COLLATE utf8mb4_unicode_ci AS source_id,"
            + " MAX(CASE WHEN profit.purchase_source_type = 'MANUAL_SKU_PURCHASE_BATCH'"
            + "          AND profit.purchase_source_id LIKE 'MANUAL_SKU_PURCHASE_BATCH:%'"
            + "          THEN CAST(SUBSTRING_INDEX(profit.purchase_source_id, ':', -1) AS UNSIGNED)"
            + "          ELSE NULL END) AS purchase_batch_id,"
            + " NULL AS source_line_id,"
            + " profit.store_code COLLATE utf8mb4_unicode_ci AS target_store_code,"
            + " profit.site_code COLLATE utf8mb4_unicode_ci AS target_site_code,"
            + " profit.partner_sku COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " MAX(profit.sku_parent COLLATE utf8mb4_unicode_ci) AS sku_parent,"
            + " MAX(profit.product_variant_id) AS product_variant_id,"
            + " MAX(profit.title_snapshot COLLATE utf8mb4_unicode_ci) AS product_title,"
            + " MAX(profit.image_url_snapshot COLLATE utf8mb4_unicode_ci) AS product_image_url,"
            + " MIN(profit.purchase_batch_time) AS purchase_batch_time,"
            + " MAX(profit.purchase_quantity) AS purchase_quantity, MAX(profit.purchase_cost_cny) AS purchase_cost_cny,"
            + " MAX(CASE WHEN profit.headhaul_cost_source_type = 'ESTIMATED_SKU_AVERAGE' THEN 1 ELSE 0 END) AS estimated_headhaul"
            + " FROM post_sale_profit_batch profit"
            + " JOIN latest_run ON latest_run.id = profit.run_id"
            + " WHERE profit.owner_user_id = #{ownerUserId}"
            + " AND profit.store_code = #{storeCode} AND profit.site_code = #{siteCode}"
            + " AND profit.purchase_source_type IN ('MANUAL_SKU_PURCHASE_BATCH', 'ALI1688_PRODUCT_LINK', 'ALI1688_ALLOCATION')"
            + " AND profit.purchase_source_id IS NOT NULL AND profit.purchase_source_id != ''"
            + " AND profit.partner_sku IS NOT NULL AND profit.partner_sku != ''"
            + " AND profit.purchase_quantity IS NOT NULL AND profit.purchase_quantity > 0"
            + " AND profit.is_deleted = b'0'"
            + " GROUP BY profit.purchase_source_type, profit.purchase_source_id, profit.store_code, profit.site_code, profit.partner_sku";

    String HEADHAUL_CANDIDATE_LINES_SQL = "batch_headhaul_units AS ("
            + " SELECT batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code,"
            + " SUM(batch_headhaul.cny_amount) / NULLIF(SUM(line_total.shipped_quantity), 0) AS batch_headhaul_unit_cost_cny"
            + " FROM (SELECT owner_user_id, batch_id, SUM(cny_amount) AS cny_amount"
            + "       FROM in_transit_freight_actual_component"
            + "       WHERE owner_user_id = #{ownerUserId}"
            + "       AND (psku IS NULL OR psku = '')"
            + "       AND standard_fee_type = 'HEADHAUL'"
            + "       AND cny_amount IS NOT NULL"
            + "       AND is_deleted = b'0'"
            + "       GROUP BY owner_user_id, batch_id) batch_headhaul"
            + " JOIN (SELECT owner_user_id, batch_id, site_code, SUM(shipped_quantity) AS shipped_quantity"
            + "       FROM in_transit_goods_line"
            + "       WHERE owner_user_id = #{ownerUserId}"
            + "       AND site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + "       AND shipped_quantity > 0"
            + "       AND is_deleted = b'0'"
            + "       GROUP BY owner_user_id, batch_id, site_code) line_total"
            + " ON line_total.owner_user_id = batch_headhaul.owner_user_id"
            + " AND line_total.batch_id = batch_headhaul.batch_id"
            + " GROUP BY batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code"
            + "), headhaul_candidate_lines AS ("
            + " SELECT line.owner_user_id, line.id AS line_id,"
            + " line.psku COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " line.site_code COLLATE utf8mb4_unicode_ci AS site_code,"
            + " line.shipped_quantity,"
            + " CASE WHEN SUM(component.cny_amount) IS NOT NULL OR MAX(batch_headhaul_units.batch_headhaul_unit_cost_cny) IS NOT NULL THEN 1 ELSE 0 END AS has_headhaul"
            + " FROM in_transit_goods_line line"
            + " LEFT JOIN in_transit_freight_actual_component component"
            + " ON component.owner_user_id = line.owner_user_id"
            + " AND component.batch_id = line.batch_id"
            + " AND component.psku COLLATE utf8mb4_unicode_ci = line.psku COLLATE utf8mb4_unicode_ci"
            + " AND component.target_site_code COLLATE utf8mb4_unicode_ci = line.site_code COLLATE utf8mb4_unicode_ci"
            + " AND component.standard_fee_type = 'HEADHAUL'"
            + " AND component.is_deleted = b'0'"
            + " LEFT JOIN batch_headhaul_units"
            + " ON batch_headhaul_units.owner_user_id = line.owner_user_id"
            + " AND batch_headhaul_units.batch_id = line.batch_id"
            + " AND batch_headhaul_units.site_code COLLATE utf8mb4_unicode_ci = line.site_code COLLATE utf8mb4_unicode_ci"
            + " WHERE line.owner_user_id = #{ownerUserId}"
            + " AND line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + " AND line.is_deleted = b'0'"
            + " GROUP BY line.owner_user_id, line.id, line.psku, line.site_code, line.shipped_quantity"
            + "), candidate_summary AS ("
            + " SELECT purchase.source_type, purchase.source_id,"
            + " COUNT(candidate.line_id) AS candidate_line_count,"
            + " COALESCE(SUM(candidate.shipped_quantity), 0) AS candidate_shipped_quantity,"
            + " COALESCE(SUM(CASE WHEN candidate.has_headhaul = 1 THEN 1 ELSE 0 END), 0) AS headhaul_candidate_line_count,"
            + " COALESCE(SUM(CASE WHEN candidate.has_headhaul = 1 THEN candidate.shipped_quantity ELSE 0 END), 0) AS headhaul_candidate_quantity"
            + " FROM purchase_sources purchase"
            + " LEFT JOIN headhaul_candidate_lines candidate"
            + " ON candidate.partner_sku COLLATE utf8mb4_unicode_ci = purchase.partner_sku COLLATE utf8mb4_unicode_ci"
            + " AND candidate.site_code COLLATE utf8mb4_unicode_ci = purchase.target_site_code COLLATE utf8mb4_unicode_ci"
            + " GROUP BY purchase.source_type, purchase.source_id"
            + ")";

    String PURCHASE_SOURCE_CLOSURE_CTE = "<script> WITH "
            + LATEST_RUN_SQL
            + ", purchase_sources AS ("
            + PROFIT_PURCHASE_SOURCE_SQL
            + "), "
            + HEADHAUL_CANDIDATE_LINES_SQL
            + ", closure AS (SELECT purchase.source_type, purchase.source_id, purchase.purchase_batch_id,"
            + " purchase.source_line_id, purchase.target_store_code, purchase.target_site_code,"
            + " purchase.partner_sku, purchase.sku_parent, purchase.product_variant_id,"
            + " purchase.product_title, purchase.product_image_url, purchase.purchase_batch_time,"
            + " purchase.purchase_quantity, COALESCE(SUM(allocation.allocated_quantity), 0) AS confirmed_quantity,"
            + " GREATEST(purchase.purchase_quantity - COALESCE(SUM(allocation.allocated_quantity), 0), 0) AS remaining_quantity,"
            + " purchase.purchase_cost_cny, purchase.estimated_headhaul,"
            + " COALESCE(MAX(candidate_summary.candidate_line_count), 0) AS candidate_line_count,"
            + " COALESCE(MAX(candidate_summary.candidate_shipped_quantity), 0) AS candidate_shipped_quantity,"
            + " COALESCE(MAX(candidate_summary.headhaul_candidate_line_count), 0) AS headhaul_candidate_line_count,"
            + " COALESCE(MAX(candidate_summary.headhaul_candidate_quantity), 0) AS headhaul_candidate_quantity,"
            + " CASE WHEN COALESCE(SUM(allocation.allocated_quantity), 0) = 0 THEN 'pending_binding'"
            + " WHEN COALESCE(SUM(allocation.allocated_quantity), 0) >= purchase.purchase_quantity THEN 'confirmed_headhaul'"
            + " ELSE 'partial_binding' END AS closure_status,"
            + " CASE WHEN COALESCE(SUM(allocation.allocated_quantity), 0) >= purchase.purchase_quantity THEN 'confirmed_headhaul'"
            + " WHEN purchase.estimated_headhaul = 1 THEN 'headhaul_available_unconfirmed'"
            + " WHEN COALESCE(MAX(candidate_summary.candidate_line_count), 0) = 0 THEN 'no_same_sku_in_transit_candidate'"
            + " WHEN COALESCE(MAX(candidate_summary.headhaul_candidate_line_count), 0) = 0 THEN 'same_sku_in_transit_without_headhaul_bill'"
            + " ELSE 'headhaul_candidate_quantity_short' END AS headhaul_gap_type"
            + " FROM purchase_sources purchase"
            + " LEFT JOIN procurement_logistics_shipment_allocation allocation ON allocation.owner_user_id = #{ownerUserId}"
            + " AND allocation.source_type = purchase.source_type"
            + " AND allocation.source_id = purchase.source_id"
            + " AND allocation.confirmation_status = 'CONFIRMED'"
            + " AND allocation.is_deleted = b'0'"
            + " LEFT JOIN candidate_summary ON candidate_summary.source_type = purchase.source_type AND candidate_summary.source_id = purchase.source_id"
            + " GROUP BY purchase.source_type, purchase.source_id, purchase.purchase_batch_id, purchase.source_line_id,"
            + " purchase.target_store_code, purchase.target_site_code, purchase.partner_sku, purchase.sku_parent,"
            + " purchase.product_variant_id, purchase.product_title, purchase.product_image_url, purchase.purchase_batch_time,"
            + " purchase.purchase_quantity, purchase.purchase_cost_cny, purchase.estimated_headhaul)";

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    void nextId(IdSequenceCommand command);

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        nextId(command);
        return command.getAllocatedId();
    }

    default Long nextAllocationId() {
        return nextId("procurement_logistics_shipment_allocation", 120000L);
    }

    @Select({
            PURCHASE_SOURCE_CLOSURE_CTE,
            "SELECT COUNT(*) AS purchaseBatchCount,",
            "       COALESCE(SUM(CASE WHEN confirmed_quantity >= purchase_quantity THEN 1 ELSE 0 END), 0) AS confirmedBatchCount,",
            "       COALESCE(SUM(CASE WHEN confirmed_quantity > 0 AND confirmed_quantity &lt; purchase_quantity THEN 1 ELSE 0 END), 0) AS partialBatchCount,",
            "       COALESCE(SUM(CASE WHEN confirmed_quantity = 0 AND headhaul_gap_type IN ('no_same_sku_in_transit_candidate', 'same_sku_in_transit_without_headhaul_bill', 'headhaul_candidate_quantity_short') THEN 1 ELSE 0 END), 0) AS missingHeadhaulBatchCount,",
            "       COALESCE(SUM(CASE WHEN confirmed_quantity = 0 AND headhaul_gap_type = 'headhaul_available_unconfirmed' THEN 1 ELSE 0 END), 0) AS estimatedHeadhaulBatchCount,",
            "       COALESCE(SUM(CASE WHEN confirmed_quantity = 0 AND headhaul_gap_type = 'no_same_sku_in_transit_candidate' THEN 1 ELSE 0 END), 0) AS missingInTransitCandidateBatchCount,",
            "       COALESCE(SUM(CASE WHEN confirmed_quantity = 0 AND headhaul_gap_type = 'same_sku_in_transit_without_headhaul_bill' THEN 1 ELSE 0 END), 0) AS missingHeadhaulBillBatchCount,",
            "       COALESCE(SUM(CASE WHEN confirmed_quantity = 0 AND headhaul_gap_type = 'headhaul_available_unconfirmed' THEN 1 ELSE 0 END), 0) AS headhaulAvailableUnconfirmedBatchCount",
            "FROM closure",
            "</script>"
    })
    LogisticsClosureSummaryView selectSummary(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    @Select({
            PURCHASE_SOURCE_CLOSURE_CTE,
            "SELECT source_type, source_id, purchase_batch_id, source_line_id, target_store_code, target_site_code,",
            "       partner_sku, sku_parent, product_variant_id, product_title, product_image_url, purchase_batch_time,",
            "       purchase_quantity, confirmed_quantity, remaining_quantity, purchase_cost_cny, closure_status,",
            "       candidate_line_count, candidate_shipped_quantity, headhaul_candidate_line_count, headhaul_candidate_quantity, headhaul_gap_type",
            "FROM closure",
            "WHERE 1 = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR product_title LIKE CONCAT('%', #{keyword}, '%')",
            "       OR source_id LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "  AND closure_status = #{status}",
            "</if>",
            "ORDER BY CASE closure_status WHEN 'pending_binding' THEN 0 WHEN 'partial_binding' THEN 1 ELSE 2 END,",
            "         purchase_batch_time DESC, source_id DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<LogisticsClosurePurchaseBatchRow> listPurchaseBatches(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset
    );

    @Select({
            PURCHASE_SOURCE_CLOSURE_CTE,
            "SELECT source_type, source_id, purchase_batch_id, source_line_id, target_store_code, target_site_code,",
            "       partner_sku, sku_parent, product_variant_id, product_title, product_image_url, purchase_batch_time,",
            "       purchase_quantity, confirmed_quantity, remaining_quantity, purchase_cost_cny, closure_status,",
            "       candidate_line_count, candidate_shipped_quantity, headhaul_candidate_line_count, headhaul_candidate_quantity, headhaul_gap_type",
            "FROM closure",
            "WHERE source_type = #{sourceType}",
            "  AND source_id = #{sourceId}",
            "LIMIT 1",
            "</script>"
    })
    LogisticsClosurePurchaseBatchRow selectPurchaseBatchBySource(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId
    );

    @Select({
            "SELECT line.batch_id AS in_transit_batch_id, line.id AS in_transit_goods_line_id,",
            "       COALESCE(NULLIF(batch.batch_reference_no, ''), CAST(batch.id AS CHAR)) AS batch_reference_no,",
            "       COALESCE(forwarder.forwarder_name, batch.normalized_raw_forwarder_name, batch.raw_forwarder_name) AS forwarder_name,",
            "       batch.transport_mode AS transport_mode, COALESCE(batch.latest_node_status, batch.batch_status) AS node_status,",
            "       batch.latest_node_happened_at AS node_happened_at, line.site_code AS site_code, line.psku AS partner_sku,",
            "       line.shipped_quantity AS shipped_quantity, COALESCE(confirmed.confirmed_quantity, 0) AS confirmed_quantity,",
            "       line.shipped_quantity - COALESCE(confirmed.confirmed_quantity, 0) AS remaining_quantity,",
            "       COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny) * line.shipped_quantity) AS headhaul_cost_cny,",
            "       COALESCE(SUM(component.cny_amount) / NULLIF(SUM(component.quantity), 0), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) AS headhaul_unit_cost_cny,",
            "       CASE WHEN COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) IS NOT NULL THEN 'confirmed' ELSE 'missing' END AS headhaul_status,",
            "       CASE WHEN line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci THEN 'strong' ELSE 'medium' END AS candidate_strength,",
            "       CASE WHEN COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) IS NOT NULL THEN 90 ELSE 70 END AS confidence_score,",
            "       'same_sku,same_site' AS match_reasons_text",
            "FROM in_transit_goods_line line",
            "JOIN in_transit_batch batch ON batch.owner_user_id = line.owner_user_id AND batch.id = line.batch_id AND batch.is_deleted = b'0'",
            "LEFT JOIN in_transit_forwarder forwarder ON forwarder.owner_user_id = batch.owner_user_id AND forwarder.id = batch.standard_forwarder_id AND forwarder.is_deleted = b'0'",
            "LEFT JOIN (",
            "  SELECT owner_user_id, in_transit_goods_line_id, SUM(allocated_quantity) AS confirmed_quantity",
            "  FROM procurement_logistics_shipment_allocation",
            "  WHERE owner_user_id = #{ownerUserId} AND confirmation_status = 'CONFIRMED' AND is_deleted = b'0'",
            "  GROUP BY owner_user_id, in_transit_goods_line_id",
            ") confirmed ON confirmed.owner_user_id = line.owner_user_id AND confirmed.in_transit_goods_line_id = line.id",
            "LEFT JOIN in_transit_freight_actual_component component",
            "  ON component.owner_user_id = line.owner_user_id",
            " AND component.batch_id = line.batch_id",
            " AND component.psku COLLATE utf8mb4_unicode_ci = line.psku COLLATE utf8mb4_unicode_ci",
            " AND component.target_site_code COLLATE utf8mb4_unicode_ci = line.site_code COLLATE utf8mb4_unicode_ci",
            " AND component.standard_fee_type = 'HEADHAUL'",
            " AND component.is_deleted = b'0'",
            "LEFT JOIN ( SELECT batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code,",
            "                  SUM(batch_headhaul.cny_amount) / NULLIF(SUM(line_total.shipped_quantity), 0) AS batch_headhaul_unit_cost_cny",
            "           FROM ( SELECT owner_user_id, batch_id, SUM(cny_amount) AS cny_amount",
            "                  FROM in_transit_freight_actual_component",
            "                  WHERE owner_user_id = #{ownerUserId}",
            "                    AND (psku IS NULL OR psku = '')",
            "                    AND standard_fee_type = 'HEADHAUL'",
            "                    AND cny_amount IS NOT NULL",
            "                    AND is_deleted = b'0'",
            "                  GROUP BY owner_user_id, batch_id ) batch_headhaul",
            "           JOIN ( SELECT owner_user_id, batch_id, site_code, SUM(shipped_quantity) AS shipped_quantity",
            "                  FROM in_transit_goods_line",
            "                  WHERE owner_user_id = #{ownerUserId}",
            "                    AND site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "                    AND shipped_quantity > 0",
            "                    AND is_deleted = b'0'",
            "                  GROUP BY owner_user_id, batch_id, site_code ) line_total",
            "             ON line_total.owner_user_id = batch_headhaul.owner_user_id",
            "            AND line_total.batch_id = batch_headhaul.batch_id",
            "           GROUP BY batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code ) batch_prorated",
            "  ON batch_prorated.owner_user_id = line.owner_user_id",
            " AND batch_prorated.batch_id = line.batch_id",
            " AND batch_prorated.site_code COLLATE utf8mb4_unicode_ci = line.site_code COLLATE utf8mb4_unicode_ci",
            "WHERE line.owner_user_id = #{ownerUserId}",
            "  AND line.is_deleted = b'0'",
            "  AND line.psku COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci",
            "  AND line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM procurement_logistics_shipment_allocation rejected",
            "    WHERE rejected.owner_user_id = line.owner_user_id",
            "      AND rejected.source_type = #{sourceType}",
            "      AND rejected.source_id = #{sourceId}",
            "      AND rejected.in_transit_goods_line_id = line.id",
            "      AND rejected.confirmation_status = 'REJECTED'",
            "      AND rejected.is_deleted = b'0'",
            "  )",
            "GROUP BY line.batch_id, line.id, batch.batch_reference_no, forwarder.forwarder_name, batch.normalized_raw_forwarder_name, batch.raw_forwarder_name,",
            "         batch.transport_mode, batch.latest_node_status, batch.batch_status, batch.latest_node_happened_at, line.site_code, line.psku, line.shipped_quantity, confirmed.confirmed_quantity",
            "HAVING remaining_quantity > 0",
            "ORDER BY confidence_score DESC, batch.latest_node_happened_at DESC, line.id DESC",
            "LIMIT 50"
    })
    List<LogisticsClosureCandidateRow> listCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT * FROM (",
            "SELECT line.batch_id AS in_transit_batch_id, line.id AS in_transit_goods_line_id,",
            "       COALESCE(NULLIF(batch.batch_reference_no, ''), CAST(batch.id AS CHAR)) AS batch_reference_no,",
            "       COALESCE(forwarder.forwarder_name, batch.normalized_raw_forwarder_name, batch.raw_forwarder_name) AS forwarder_name,",
            "       batch.transport_mode AS transport_mode, COALESCE(batch.latest_node_status, batch.batch_status) AS node_status,",
            "       batch.latest_node_happened_at AS node_happened_at, line.site_code AS site_code, line.psku AS partner_sku,",
            "       line.shipped_quantity AS shipped_quantity, COALESCE(confirmed.confirmed_quantity, 0) AS confirmed_quantity,",
            "       line.shipped_quantity - COALESCE(confirmed.confirmed_quantity, 0) AS remaining_quantity,",
            "       COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny) * line.shipped_quantity) AS headhaul_cost_cny,",
            "       COALESCE(SUM(component.cny_amount) / NULLIF(SUM(component.quantity), 0), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) AS headhaul_unit_cost_cny,",
            "       CASE WHEN COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) IS NOT NULL THEN 'confirmed' ELSE 'missing' END AS headhaul_status,",
            "       'strong' AS candidate_strength, CASE WHEN COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) IS NOT NULL THEN 90 ELSE 70 END AS confidence_score,",
            "       'same_sku,same_site' AS match_reasons_text",
            "FROM in_transit_goods_line line",
            "JOIN in_transit_batch batch ON batch.owner_user_id = line.owner_user_id AND batch.id = line.batch_id AND batch.is_deleted = b'0'",
            "LEFT JOIN in_transit_forwarder forwarder ON forwarder.owner_user_id = batch.owner_user_id AND forwarder.id = batch.standard_forwarder_id AND forwarder.is_deleted = b'0'",
            "LEFT JOIN (SELECT owner_user_id, in_transit_goods_line_id, SUM(allocated_quantity) AS confirmed_quantity FROM procurement_logistics_shipment_allocation WHERE owner_user_id = #{ownerUserId} AND confirmation_status = 'CONFIRMED' AND is_deleted = b'0' GROUP BY owner_user_id, in_transit_goods_line_id) confirmed ON confirmed.owner_user_id = line.owner_user_id AND confirmed.in_transit_goods_line_id = line.id",
            "LEFT JOIN in_transit_freight_actual_component component ON component.owner_user_id = line.owner_user_id AND component.batch_id = line.batch_id AND component.psku COLLATE utf8mb4_unicode_ci = line.psku COLLATE utf8mb4_unicode_ci AND component.target_site_code COLLATE utf8mb4_unicode_ci = line.site_code COLLATE utf8mb4_unicode_ci AND component.standard_fee_type = 'HEADHAUL' AND component.is_deleted = b'0'",
            "LEFT JOIN ( SELECT batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code, SUM(batch_headhaul.cny_amount) / NULLIF(SUM(line_total.shipped_quantity), 0) AS batch_headhaul_unit_cost_cny",
            "           FROM ( SELECT owner_user_id, batch_id, SUM(cny_amount) AS cny_amount FROM in_transit_freight_actual_component WHERE owner_user_id = #{ownerUserId} AND (psku IS NULL OR psku = '') AND standard_fee_type = 'HEADHAUL' AND cny_amount IS NOT NULL AND is_deleted = b'0' GROUP BY owner_user_id, batch_id ) batch_headhaul",
            "           JOIN ( SELECT owner_user_id, batch_id, site_code, SUM(shipped_quantity) AS shipped_quantity FROM in_transit_goods_line WHERE owner_user_id = #{ownerUserId} AND site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci AND shipped_quantity > 0 AND is_deleted = b'0' GROUP BY owner_user_id, batch_id, site_code ) line_total ON line_total.owner_user_id = batch_headhaul.owner_user_id AND line_total.batch_id = batch_headhaul.batch_id",
            "           GROUP BY batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code ) batch_prorated ON batch_prorated.owner_user_id = line.owner_user_id AND batch_prorated.batch_id = line.batch_id AND batch_prorated.site_code COLLATE utf8mb4_unicode_ci = line.site_code COLLATE utf8mb4_unicode_ci",
            "WHERE line.owner_user_id = #{ownerUserId} AND line.is_deleted = b'0'",
            "  AND line.batch_id = #{inTransitBatchId} AND line.id = #{inTransitGoodsLineId}",
            "  AND line.psku COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci",
            "  AND line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "GROUP BY line.batch_id, line.id, batch.batch_reference_no, forwarder.forwarder_name, batch.normalized_raw_forwarder_name, batch.raw_forwarder_name, batch.transport_mode, batch.latest_node_status, batch.batch_status, batch.latest_node_happened_at, line.site_code, line.psku, line.shipped_quantity, confirmed.confirmed_quantity",
            ") candidate",
            "LIMIT 1"
    })
    LogisticsClosureCandidateRow selectCandidateByLine(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("inTransitBatchId") Long inTransitBatchId,
            @Param("inTransitGoodsLineId") Long inTransitGoodsLineId
    );

    @Select({
            "SELECT COALESCE(SUM(allocated_quantity), 0)",
            "FROM procurement_logistics_shipment_allocation",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND source_type = #{sourceType}",
            "  AND source_id = #{sourceId}",
            "  AND confirmation_status = 'CONFIRMED'",
            "  AND is_deleted = b'0'"
    })
    BigDecimal sumConfirmedQuantityBySource(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId
    );

    @Select({
            "SELECT COALESCE(SUM(allocated_quantity), 0)",
            "FROM procurement_logistics_shipment_allocation",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND in_transit_goods_line_id = #{inTransitGoodsLineId}",
            "  AND confirmation_status = 'CONFIRMED'",
            "  AND is_deleted = b'0'"
    })
    BigDecimal sumConfirmedQuantityByInTransitLine(
            @Param("ownerUserId") Long ownerUserId,
            @Param("inTransitGoodsLineId") Long inTransitGoodsLineId
    );

    @Insert({
            "INSERT INTO procurement_logistics_shipment_allocation (",
            "id, owner_user_id, source_type, source_id, source_line_id, target_store_code, target_site_code,",
            "partner_sku, sku_parent, product_variant_id, purchase_batch_id, warehouse_shipping_batch_id,",
            "warehouse_shipping_batch_source_id, in_transit_batch_id, in_transit_goods_line_id, allocated_quantity,",
            "allocation_unit, match_method, confirmation_status, confidence_score, evidence_json, reject_reason,",
            "confirmed_by, confirmed_at, created_by, updated_by",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.sourceType}, #{row.sourceId}, #{row.sourceLineId},",
            "#{row.targetStoreCode}, #{row.targetSiteCode}, #{row.partnerSku}, #{row.skuParent}, #{row.productVariantId},",
            "#{row.purchaseBatchId}, #{row.warehouseShippingBatchId}, #{row.warehouseShippingBatchSourceId},",
            "#{row.inTransitBatchId}, #{row.inTransitGoodsLineId}, #{row.allocatedQuantity}, #{row.allocationUnit},",
            "#{row.matchMethod}, #{row.confirmationStatus}, #{row.confidenceScore}, #{row.evidenceJson}, #{row.rejectReason},",
            "#{row.confirmedBy}, #{row.confirmedAt}, #{row.createdBy}, #{row.updatedBy}",
            ")"
    })
    int insertAllocation(@Param("row") LogisticsClosureAllocationRow row);

    @Update({
            "UPDATE procurement_logistics_shipment_allocation",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND id = #{allocationId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteAllocation(
            @Param("ownerUserId") Long ownerUserId,
            @Param("allocationId") Long allocationId,
            @Param("operatorUserId") Long operatorUserId
    );

    default int insertRejectedAllocation(LogisticsClosureAllocationRow row) {
        return insertAllocation(row);
    }

    @Select({
            "SELECT allocation.source_id AS source_id, allocation.purchase_batch_id AS purchase_batch_id,",
            "       allocation.partner_sku AS partner_sku, allocation.target_site_code AS site_code,",
            "       allocation.in_transit_batch_id AS in_transit_batch_id, allocation.in_transit_goods_line_id AS in_transit_goods_line_id,",
            "       GROUP_CONCAT(DISTINCT COALESCE(bill.bill_no, batch_prorated.bill_no) ORDER BY COALESCE(bill.bill_no, batch_prorated.bill_no) SEPARATOR ',') AS bill_no,",
            "       COALESCE(NULLIF(batch.batch_reference_no, ''), CAST(batch.id AS CHAR)) AS batch_reference_no,",
            "       allocation.allocated_quantity AS allocated_quantity,",
            "       COALESCE(SUM(component.quantity), allocation.allocated_quantity) AS freight_quantity,",
            "       COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny) * allocation.allocated_quantity) AS headhaul_cost_cny,",
            "       COALESCE(SUM(component.cny_amount) / NULLIF(SUM(component.quantity), 0), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) AS headhaul_unit_cost_cny,",
            "       MAX(COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create, batch_prorated.freight_occurred_at)) AS freight_occurred_at,",
            "       CASE WHEN SUM(component.cny_amount) IS NOT NULL THEN 'confirmed_in_transit_goods_line_headhaul'",
            "            WHEN MAX(batch_prorated.batch_headhaul_unit_cost_cny) IS NOT NULL THEN 'confirmed_batch_headhaul_prorated_by_shipped_quantity'",
            "            ELSE 'confirmed_in_transit_line_without_sku_component' END AS allocation_basis,",
            "       CASE WHEN SUM(component.cny_amount) IS NOT NULL THEN 'confirmed allocation matched to SKU/site HEADHAUL component'",
            "            WHEN MAX(batch_prorated.batch_headhaul_unit_cost_cny) IS NOT NULL THEN 'confirmed allocation matched to batch-level HEADHAUL bill prorated by shipped quantity'",
            "            ELSE 'confirmed allocation exists but SKU/site HEADHAUL component is missing' END AS evidence_text,",
            "       allocation.confirmed_by AS confirmed_by, allocation.confirmed_at AS confirmed_at",
            "FROM procurement_logistics_shipment_allocation allocation",
            "JOIN in_transit_goods_line line",
            "  ON line.owner_user_id = allocation.owner_user_id",
            " AND line.id = allocation.in_transit_goods_line_id",
            " AND line.is_deleted = b'0'",
            "JOIN in_transit_batch batch",
            "  ON batch.owner_user_id = allocation.owner_user_id",
            " AND batch.id = allocation.in_transit_batch_id",
            " AND batch.is_deleted = b'0'",
            "LEFT JOIN in_transit_freight_actual_component component",
            "  ON component.owner_user_id = allocation.owner_user_id",
            " AND component.batch_id = allocation.in_transit_batch_id",
            " AND component.psku COLLATE utf8mb4_unicode_ci = allocation.partner_sku COLLATE utf8mb4_unicode_ci",
            " AND component.target_site_code COLLATE utf8mb4_unicode_ci = allocation.target_site_code COLLATE utf8mb4_unicode_ci",
            " AND component.standard_fee_type = 'HEADHAUL'",
            " AND component.is_deleted = b'0'",
            "LEFT JOIN in_transit_freight_actual_bill bill",
            "  ON bill.owner_user_id = component.owner_user_id",
            " AND bill.id = component.actual_bill_id",
            " AND bill.is_deleted = b'0'",
            "LEFT JOIN ( SELECT batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code, batch_headhaul.bill_no,",
            "                  SUM(batch_headhaul.cny_amount) / NULLIF(SUM(line_total.shipped_quantity), 0) AS batch_headhaul_unit_cost_cny,",
            "                  MAX(batch_headhaul.freight_occurred_at) AS freight_occurred_at",
            "           FROM ( SELECT component.owner_user_id, component.batch_id,",
            "                         GROUP_CONCAT(DISTINCT bill.bill_no ORDER BY bill.bill_no SEPARATOR ',') AS bill_no,",
            "                         SUM(component.cny_amount) AS cny_amount,",
            "                         MAX(COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create)) AS freight_occurred_at",
            "                  FROM in_transit_freight_actual_component component",
            "                  JOIN in_transit_freight_actual_bill bill",
            "                    ON bill.owner_user_id = component.owner_user_id",
            "                   AND bill.id = component.actual_bill_id",
            "                   AND bill.is_deleted = b'0'",
            "                  WHERE component.owner_user_id = #{ownerUserId}",
            "                    AND (component.psku IS NULL OR component.psku = '')",
            "                    AND component.standard_fee_type = 'HEADHAUL'",
            "                    AND component.cny_amount IS NOT NULL",
            "                    AND component.is_deleted = b'0'",
            "                  GROUP BY component.owner_user_id, component.batch_id ) batch_headhaul",
            "           JOIN ( SELECT owner_user_id, batch_id, site_code, SUM(shipped_quantity) AS shipped_quantity",
            "                  FROM in_transit_goods_line",
            "                  WHERE owner_user_id = #{ownerUserId}",
            "                    AND site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "                    AND shipped_quantity > 0",
            "                    AND is_deleted = b'0'",
            "                  GROUP BY owner_user_id, batch_id, site_code ) line_total",
            "             ON line_total.owner_user_id = batch_headhaul.owner_user_id",
            "            AND line_total.batch_id = batch_headhaul.batch_id",
            "           GROUP BY batch_headhaul.owner_user_id, batch_headhaul.batch_id, line_total.site_code, batch_headhaul.bill_no ) batch_prorated",
            "  ON batch_prorated.owner_user_id = line.owner_user_id",
            " AND batch_prorated.batch_id = line.batch_id",
            " AND batch_prorated.site_code COLLATE utf8mb4_unicode_ci = line.site_code COLLATE utf8mb4_unicode_ci",
            "WHERE allocation.owner_user_id = #{ownerUserId}",
            "  AND allocation.target_store_code = #{storeCode}",
            "  AND allocation.target_site_code = #{siteCode}",
            "  AND allocation.confirmation_status = 'CONFIRMED'",
            "  AND allocation.is_deleted = b'0'",
            "GROUP BY allocation.source_id, allocation.purchase_batch_id, allocation.partner_sku, allocation.target_site_code,",
            "         allocation.in_transit_batch_id, allocation.in_transit_goods_line_id, batch.batch_reference_no, batch.id,",
            "         allocation.allocated_quantity, allocation.confirmed_by, allocation.confirmed_at"
    })
    List<ConfirmedHeadhaulAllocationRow> listConfirmedHeadhaulAllocations(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );
}
