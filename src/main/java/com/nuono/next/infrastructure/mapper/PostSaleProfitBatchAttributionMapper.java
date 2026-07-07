package com.nuono.next.infrastructure.mapper;

import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionCurrentStockRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionCandidateRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionLineRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionSkuRow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PostSaleProfitBatchAttributionMapper {

    String LATEST_RUN_CTE = "latest_run AS ("
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

    String STORE_SCOPE_CTE = "store_scope AS ("
            + " SELECT ls.id AS logical_store_id, ls.project_code, lss.id AS site_id, lss.store_code, lss.site"
            + " FROM logical_store_site lss"
            + " JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'"
            + " WHERE ls.owner_user_id = #{ownerUserId}"
            + " AND lss.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci"
            + " AND lss.site COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + " AND lss.is_deleted = b'0'"
            + " LIMIT 1"
            + ")";

    String STOCK_CTE = "stock AS ("
            + " SELECT stock_line.owner_user_id,"
            + " stock_line.store_code COLLATE utf8mb4_unicode_ci AS store_code,"
            + " stock_line.site_code COLLATE utf8mb4_unicode_ci AS site_code,"
            + " CONVERT(COALESCE(NULLIF(stock_line.partner_sku, ''), NULLIF(stock_line.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " MAX(product_master.sku_parent COLLATE utf8mb4_unicode_ci) AS sku_parent,"
            + " MAX(COALESCE(NULLIF(stock_line.title_cache, ''), NULLIF(product_master.title_cn_cache, ''), NULLIF(product_master.title_cache, '')) COLLATE utf8mb4_unicode_ci) AS product_title,"
            + " MAX(product_master.cover_image_url COLLATE utf8mb4_unicode_ci) AS product_image_url,"
            + " SUM(stock_line.qty) AS stock_quantity,"
            + " SUM(CASE WHEN UPPER(stock_line.stock_bucket) = 'SELLABLE' THEN stock_line.qty ELSE 0 END) AS sellable_stock_quantity"
            + " FROM official_warehouse_inventory_snapshot_line stock_line"
            + " LEFT JOIN product_master ON product_master.id = stock_line.product_master_id AND product_master.is_deleted = b'0'"
            + " WHERE stock_line.owner_user_id = #{ownerUserId}"
            + " AND stock_line.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci"
            + " AND stock_line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + " AND stock_line.is_current = b'1'"
            + " AND stock_line.is_deleted = b'0'"
            + " AND stock_line.qty > 0"
            + " AND COALESCE(NULLIF(stock_line.partner_sku, ''), NULLIF(stock_line.psku_code, '')) IS NOT NULL"
            + " GROUP BY stock_line.owner_user_id, stock_line.store_code, stock_line.site_code,"
            + " CONVERT(COALESCE(NULLIF(stock_line.partner_sku, ''), NULLIF(stock_line.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci"
            + ")";

    String SOLD_CTE = "sold AS ("
            + " SELECT CONVERT(batch.partner_sku USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " SUM(batch.sold_quantity) AS sold_quantity"
            + " FROM post_sale_profit_batch batch"
            + " JOIN latest_run ON latest_run.id = batch.run_id"
            + " WHERE batch.owner_user_id = #{ownerUserId}"
            + " AND batch.store_code = #{storeCode}"
            + " AND batch.site_code = #{siteCode}"
            + " AND batch.is_deleted = b'0'"
            + " GROUP BY CONVERT(batch.partner_sku USING utf8mb4) COLLATE utf8mb4_unicode_ci"
            + ")";

    String PURCHASE_CTE = "purchase AS ("
            + " SELECT CONVERT(COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " COUNT(DISTINCT site.id) AS purchase_order_count,"
            + " COUNT(DISTINCT po.order_no) AS purchase_order_no_count,"
            + " GROUP_CONCAT(DISTINCT po.order_no ORDER BY po.gmt_create ASC, po.id ASC SEPARATOR ',') AS purchase_order_nos,"
            + " SUM(site.quantity) AS purchase_quantity"
            + " FROM procurement_purchase_order_item_site site"
            + " JOIN store_scope ON store_scope.logical_store_id = site.logical_store_id"
            + " JOIN procurement_purchase_order_item item"
            + " ON item.id = site.purchase_order_item_id"
            + " AND item.owner_user_id = site.owner_user_id"
            + " AND item.is_deleted = b'0'"
            + " JOIN procurement_purchase_order po"
            + " ON po.id = site.purchase_order_id"
            + " AND po.owner_user_id = site.owner_user_id"
            + " AND po.is_deleted = b'0'"
            + " WHERE site.owner_user_id = #{ownerUserId}"
            + " AND site.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + " AND site.is_deleted = b'0'"
            + " AND COALESCE(site.status, 'ACTIVE') != 'CANCELLED'"
            + " AND COALESCE(po.status, 'ACTIVE') != 'CANCELLED'"
            + " GROUP BY CONVERT(COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci"
            + ")";

    String IN_TRANSIT_CTE = "in_transit AS ("
            + " SELECT CONVERT(line.psku USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " COUNT(DISTINCT line.batch_id) AS in_transit_batch_count,"
            + " GROUP_CONCAT(DISTINCT COALESCE(NULLIF(in_batch.batch_reference_no, ''), CAST(in_batch.id AS CHAR)) ORDER BY in_batch.source_created_at ASC, in_batch.id ASC SEPARATOR ',') AS logistics_batch_nos,"
            + " SUM(line.shipped_quantity) AS in_transit_shipped_quantity"
            + " FROM in_transit_goods_line line"
            + " JOIN in_transit_batch in_batch"
            + " ON in_batch.owner_user_id = line.owner_user_id"
            + " AND in_batch.id = line.batch_id"
            + " AND in_batch.is_deleted = b'0'"
            + " WHERE line.owner_user_id = #{ownerUserId}"
            + " AND line.is_deleted = b'0'"
            + " AND line.shipped_quantity > 0"
            + " AND line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + " AND (line.store_code IS NULL OR line.store_code = '' OR line.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci)"
            + " AND COALESCE(in_batch.batch_status, '') != 'cancelled'"
            + " GROUP BY CONVERT(line.psku USING utf8mb4) COLLATE utf8mb4_unicode_ci"
            + ")";

    String ASN_CTE = "asn AS ("
            + " SELECT CONVERT(COALESCE(NULLIF(asn_line.partner_sku, ''), NULLIF(asn_line.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " COUNT(DISTINCT asn_line.asn_id) AS asn_count,"
            + " GROUP_CONCAT(DISTINCT COALESCE(NULLIF(asn.noon_asn_nr, ''), asn.local_asn_no) ORDER BY asn.finished_at ASC, asn.id ASC SEPARATOR ',') AS asn_nos,"
            + " SUM(asn_line.qty) AS asn_quantity"
            + " FROM official_warehouse_asn_line asn_line"
            + " JOIN official_warehouse_asn asn"
            + " ON asn.id = asn_line.asn_id"
            + " AND asn.owner_user_id = asn_line.owner_user_id"
            + " AND asn.is_deleted = b'0'"
            + " WHERE asn_line.owner_user_id = #{ownerUserId}"
            + " AND asn_line.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci"
            + " AND asn_line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + " AND asn_line.is_deleted = b'0'"
            + " AND asn_line.qty > 0"
            + " AND asn.status = 'LINES_CREATED'"
            + " AND asn.noon_asn_status IN ('grn_completed', 'sealed')"
            + " GROUP BY CONVERT(COALESCE(NULLIF(asn_line.partner_sku, ''), NULLIF(asn_line.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci"
            + ")";

    String BRIDGE_CTE = "bridge AS ("
            + " SELECT CONVERT(COALESCE(NULLIF(link.partner_sku, ''), NULLIF(link.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku,"
            + " COUNT(DISTINCT bridge.in_transit_batch_id) AS bridgeLogisticsCount,"
            + " COUNT(DISTINCT bridge.purchase_order_id) AS bridgePurchaseOrderCount,"
            + " GROUP_CONCAT(DISTINCT COALESCE(NULLIF(bridge.batch_reference_no, ''), NULLIF(bridge.shipping_batch_no, ''), CAST(bridge.in_transit_batch_id AS CHAR)) ORDER BY bridge.id ASC SEPARATOR ',') AS bridge_logistics_nos,"
            + " GROUP_CONCAT(DISTINCT bridge.purchase_order_no ORDER BY bridge.id ASC SEPARATOR ',') AS bridge_purchase_order_nos"
            + " FROM official_warehouse_asn_shipping_batch_link link"
            + " JOIN official_warehouse_asn_shipping_batch_link bridge ON bridge.id = link.id"
            + " WHERE link.owner_user_id = #{ownerUserId}"
            + " AND link.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci"
            + " AND link.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci"
            + " AND link.is_deleted = b'0'"
            + " AND link.relation_status = 'LINKED'"
            + " GROUP BY CONVERT(COALESCE(NULLIF(link.partner_sku, ''), NULLIF(link.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci"
            + ")";

    @Select({
            "<script>",
            "WITH",
            STORE_SCOPE_CTE,
            ",",
            STOCK_CTE,
            ",",
            LATEST_RUN_CTE,
            ",",
            SOLD_CTE,
            ",",
            PURCHASE_CTE,
            ",",
            IN_TRANSIT_CTE,
            ",",
            ASN_CTE,
            ",",
            BRIDGE_CTE,
            "SELECT stock.partner_sku AS partnerSku,",
            "       stock.sku_parent AS skuParent,",
            "       stock.product_title AS productTitle,",
            "       stock.product_image_url AS productImageUrl,",
            "       stock.stock_quantity AS stockQuantity,",
            "       stock.sellable_stock_quantity AS sellableStockQuantity,",
            "       COALESCE(sold.sold_quantity, 0) AS soldQuantity,",
            "       COALESCE(purchase.purchase_order_count, 0) AS purchaseOrderCount,",
            "       COALESCE(purchase.purchase_order_no_count, 0) AS purchaseOrderNoCount,",
            "       purchase.purchase_order_nos AS purchaseOrderNos,",
            "       COALESCE(purchase.purchase_quantity, 0) AS purchaseQuantity,",
            "       COALESCE(in_transit.in_transit_batch_count, 0) AS inTransitBatchCount,",
            "       in_transit.logistics_batch_nos AS logisticsBatchNos,",
            "       COALESCE(in_transit.in_transit_shipped_quantity, 0) AS inTransitShippedQuantity,",
            "       COALESCE(asn.asn_count, 0) AS asnCount,",
            "       asn.asn_nos AS asnNos,",
            "       COALESCE(asn.asn_quantity, 0) AS asnQuantity,",
            "       COALESCE(bridge.bridgeLogisticsCount, 0) AS bridgeLogisticsCount,",
            "       COALESCE(bridge.bridgePurchaseOrderCount, 0) AS bridgePurchaseOrderCount,",
            "       bridge.bridge_logistics_nos AS bridgeLogisticsNos,",
            "       bridge.bridge_purchase_order_nos AS bridgePurchaseOrderNos",
            "FROM stock",
            "LEFT JOIN sold ON sold.partner_sku = stock.partner_sku",
            "LEFT JOIN purchase ON purchase.partner_sku = stock.partner_sku",
            "LEFT JOIN in_transit ON in_transit.partner_sku = stock.partner_sku",
            "LEFT JOIN asn ON asn.partner_sku = stock.partner_sku",
            "LEFT JOIN bridge ON bridge.partner_sku = stock.partner_sku",
            "WHERE 1 = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (stock.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR stock.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR stock.product_title LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY stock.partner_sku ASC",
            "</script>"
    })
    List<BatchAttributionSkuRow> listSkuRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("keyword") String keyword
    );

    @Select({
            "<script>",
            "WITH",
            LATEST_RUN_CTE,
            "SELECT 'profit_batch' AS lineType,",
            "       batch.purchase_source_id AS sourceId,",
            "       batch.purchase_batch_time AS purchaseBatchTime,",
            "       batch.purchase_quantity AS purchaseQuantity,",
            "       batch.sold_quantity AS soldQuantity,",
            "       0 AS stockQuantity,",
            "       batch.purchase_cost_cny AS purchaseCostCny,",
            "       COALESCE(NULLIF(batch.shipping_batch_no, ''), NULLIF(batch.in_transit_reference_no, '')) AS logisticsBatchNo,",
            "       COALESCE(NULLIF(asn.noon_asn_nr, ''), asn.local_asn_no) AS asnNo,",
            "       CASE WHEN batch.profit_cny IS NULL THEN 'sold_profit_missing_profit' ELSE 'sold_profit_batch' END AS lineStatus,",
            "       'post_sale_profit_batch latest run' AS evidenceText",
            "FROM post_sale_profit_batch batch",
            "JOIN latest_run ON latest_run.id = batch.run_id",
            "LEFT JOIN official_warehouse_asn_shipping_batch_link link",
            "  ON link.owner_user_id = batch.owner_user_id",
            " AND link.store_code COLLATE utf8mb4_unicode_ci = batch.store_code COLLATE utf8mb4_unicode_ci",
            " AND link.site_code COLLATE utf8mb4_unicode_ci = batch.site_code COLLATE utf8mb4_unicode_ci",
            " AND link.partner_sku COLLATE utf8mb4_unicode_ci = batch.partner_sku COLLATE utf8mb4_unicode_ci",
            " AND link.is_deleted = b'0'",
            "LEFT JOIN official_warehouse_asn asn ON asn.id = link.asn_id AND asn.owner_user_id = link.owner_user_id AND asn.is_deleted = b'0'",
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "  AND batch.store_code = #{storeCode}",
            "  AND batch.site_code = #{siteCode}",
            "  AND batch.partner_sku COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci",
            "  AND batch.is_deleted = b'0'",
            "ORDER BY batch.purchase_batch_time ASC, batch.id ASC",
            "</script>"
    })
    List<BatchAttributionLineRow> listProfitBatchLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "<script>",
            "WITH",
            LATEST_RUN_CTE,
            ",",
            STORE_SCOPE_CTE,
            "SELECT candidate.sourceType,",
            "       candidate.sourceId,",
            "       candidate.purchaseOrderNo,",
            "       candidate.purchaseBatchTime,",
            "       candidate.purchaseQuantity,",
            "       candidate.purchaseCostCny,",
            "       candidate.logisticsBatchNo,",
            "       candidate.asnNo,",
            "       candidate.evidenceText",
            "FROM (",
            "  SELECT batch.purchase_source_type AS sourceType,",
            "         batch.purchase_source_id AS sourceId,",
            "         NULL AS purchaseOrderNo,",
            "         MIN(batch.purchase_batch_time) AS purchaseBatchTime,",
            "         MAX(batch.purchase_quantity) AS purchaseQuantity,",
            "         MAX(batch.purchase_cost_cny) AS purchaseCostCny,",
            "         GROUP_CONCAT(DISTINCT COALESCE(NULLIF(batch.shipping_batch_no, ''), NULLIF(batch.in_transit_reference_no, '')) ORDER BY batch.purchase_batch_time ASC SEPARATOR ',') AS logisticsBatchNo,",
            "         GROUP_CONCAT(DISTINCT COALESCE(NULLIF(asn.noon_asn_nr, ''), asn.local_asn_no) ORDER BY asn.finished_at ASC, asn.id ASC SEPARATOR ',') AS asnNo,",
            "         'post_sale_profit_batch latest run purchase source' AS evidenceText",
            "  FROM post_sale_profit_batch batch",
            "  JOIN latest_run ON latest_run.id = batch.run_id",
            "  LEFT JOIN official_warehouse_asn_shipping_batch_link link",
            "    ON link.owner_user_id = batch.owner_user_id",
            "   AND link.store_code COLLATE utf8mb4_unicode_ci = batch.store_code COLLATE utf8mb4_unicode_ci",
            "   AND link.site_code COLLATE utf8mb4_unicode_ci = batch.site_code COLLATE utf8mb4_unicode_ci",
            "   AND link.partner_sku COLLATE utf8mb4_unicode_ci = batch.partner_sku COLLATE utf8mb4_unicode_ci",
            "   AND link.is_deleted = b'0'",
            "   AND link.relation_status = 'LINKED'",
            "  LEFT JOIN official_warehouse_asn asn",
            "    ON asn.id = link.asn_id",
            "   AND asn.owner_user_id = link.owner_user_id",
            "   AND asn.is_deleted = b'0'",
            "  WHERE batch.owner_user_id = #{ownerUserId}",
            "    AND batch.store_code = #{storeCode}",
            "    AND batch.site_code = #{siteCode}",
            "    AND batch.partner_sku COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci",
            "    AND batch.purchase_source_id IS NOT NULL",
            "    AND batch.purchase_source_id != ''",
            "    AND batch.purchase_quantity IS NOT NULL",
            "    AND batch.purchase_quantity > 0",
            "    AND batch.is_deleted = b'0'",
            "  GROUP BY batch.purchase_source_type, batch.purchase_source_id",
            "  UNION ALL",
            "  SELECT 'MANUAL_SKU_PURCHASE_BATCH' AS sourceType,",
            "         CONCAT('MANUAL_SKU_PURCHASE_BATCH:', batch.id) AS sourceId,",
            "         MIN(source.source_order_no) AS purchaseOrderNo,",
            "         COALESCE(MIN(source.source_order_time), batch.gmt_create) AS purchaseBatchTime,",
            "         batch.counted_quantity AS purchaseQuantity,",
            "         batch.counted_cost AS purchaseCostCny,",
            "         NULL AS logisticsBatchNo,",
            "         NULL AS asnNo,",
            "         CONCAT('procurement_ali1688_sku_purchase_batch ', batch.batch_label, ' purchase cost evidence') AS evidenceText",
            "  FROM procurement_ali1688_sku_purchase_batch batch",
            "  JOIN logical_store ls",
            "    ON ls.owner_user_id = batch.owner_user_id",
            "   AND ls.project_code COLLATE utf8mb4_unicode_ci = batch.target_store_code COLLATE utf8mb4_unicode_ci",
            "   AND ls.is_deleted = b'0'",
            "  JOIN logical_store_site lss",
            "    ON lss.logical_store_id = ls.id",
            "   AND lss.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci",
            "   AND lss.site COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "   AND lss.is_deleted = b'0'",
            "  LEFT JOIN procurement_ali1688_sku_purchase_batch_source source",
            "    ON source.owner_user_id = batch.owner_user_id",
            "   AND source.batch_id = batch.id",
            "   AND source.status = 'active'",
            "   AND source.is_deleted = b'0'",
            "  WHERE batch.owner_user_id = #{ownerUserId}",
            "    AND batch.status = 'active'",
            "    AND batch.is_deleted = b'0'",
            "    AND batch.counted_quantity > 0",
            "    AND batch.counted_cost IS NOT NULL",
            "    AND (batch.target_site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "         OR batch.target_site_code = '*')",
            "    AND COALESCE(NULLIF(batch.partner_sku, ''), NULLIF(batch.psku_code, '')) COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci",
            "    AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM post_sale_profit_batch existing_profit",
            "      JOIN latest_run ON latest_run.id = existing_profit.run_id",
            "      WHERE existing_profit.owner_user_id = batch.owner_user_id",
            "        AND existing_profit.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci",
            "        AND existing_profit.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "        AND existing_profit.partner_sku COLLATE utf8mb4_unicode_ci = COALESCE(NULLIF(batch.partner_sku, ''), NULLIF(batch.psku_code, '')) COLLATE utf8mb4_unicode_ci",
            "        AND existing_profit.purchase_source_id = CONCAT('MANUAL_SKU_PURCHASE_BATCH:', batch.id)",
            "        AND existing_profit.is_deleted = b'0'",
            "    )",
            "  GROUP BY batch.id, batch.gmt_create, batch.batch_label, batch.counted_quantity, batch.counted_cost",
            "  UNION ALL",
            "  SELECT 'PROCUREMENT_PURCHASE_ORDER_ITEM_SITE' AS sourceType,",
            "         CONCAT('PROCUREMENT_PO_ITEM_SITE:', site.id, ':', COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, ''))) AS sourceId,",
            "         po.order_no AS purchaseOrderNo,",
            "         COALESCE(po.submitted_collect_at, site.gmt_create, po.gmt_create) AS purchaseBatchTime,",
            "         site.quantity AS purchaseQuantity,",
            "         NULL AS purchaseCostCny,",
            "         GROUP_CONCAT(DISTINCT COALESCE(NULLIF(in_batch.batch_reference_no, ''), CAST(in_batch.id AS CHAR)) ORDER BY COALESCE(in_batch.source_created_at, in_batch.gmt_create) ASC, in_batch.id ASC SEPARATOR ',') AS logisticsBatchNo,",
            "         GROUP_CONCAT(DISTINCT COALESCE(NULLIF(asn.noon_asn_nr, ''), asn.local_asn_no) ORDER BY asn.finished_at ASC, asn.id ASC SEPARATOR ',') AS asnNo,",
            "         CONCAT('procurement_purchase_order_item_site ', po.order_no, ' quantity evidence; purchase cost missing') AS evidenceText",
            "  FROM procurement_purchase_order_item_site site",
            "  JOIN store_scope ON store_scope.logical_store_id = site.logical_store_id",
            "  JOIN procurement_purchase_order_item item",
            "    ON item.id = site.purchase_order_item_id",
            "   AND item.owner_user_id = site.owner_user_id",
            "   AND item.is_deleted = b'0'",
            "  JOIN procurement_purchase_order po",
            "    ON po.id = site.purchase_order_id",
            "   AND po.owner_user_id = site.owner_user_id",
            "   AND po.is_deleted = b'0'",
            "  LEFT JOIN procurement_logistics_shipment_allocation allocation",
            "    ON allocation.owner_user_id = site.owner_user_id",
            "   AND allocation.target_store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci",
            "   AND allocation.target_site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "   AND allocation.partner_sku COLLATE utf8mb4_unicode_ci = COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, '')) COLLATE utf8mb4_unicode_ci",
            "   AND allocation.confirmation_status = 'CONFIRMED'",
            "   AND allocation.is_deleted = b'0'",
            "   AND (allocation.source_line_id = site.id OR allocation.purchase_batch_id = site.id OR allocation.source_id = CONCAT('PROCUREMENT_PO_ITEM_SITE:', site.id, ':', COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, ''))))",
            "  LEFT JOIN in_transit_goods_line line",
            "    ON line.owner_user_id = allocation.owner_user_id",
            "   AND line.id = allocation.in_transit_goods_line_id",
            "   AND line.is_deleted = b'0'",
            "  LEFT JOIN in_transit_batch in_batch",
            "    ON in_batch.owner_user_id = allocation.owner_user_id",
            "   AND in_batch.id = allocation.in_transit_batch_id",
            "   AND in_batch.is_deleted = b'0'",
            "  LEFT JOIN official_warehouse_asn_shipping_batch_link link",
            "    ON link.owner_user_id = site.owner_user_id",
            "   AND link.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci",
            "   AND link.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "   AND link.partner_sku COLLATE utf8mb4_unicode_ci = COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, '')) COLLATE utf8mb4_unicode_ci",
            "   AND link.in_transit_batch_id = in_batch.id",
            "   AND link.relation_status = 'LINKED'",
            "   AND link.is_deleted = b'0'",
            "  LEFT JOIN official_warehouse_asn asn",
            "    ON asn.id = link.asn_id",
            "   AND asn.owner_user_id = link.owner_user_id",
            "   AND asn.is_deleted = b'0'",
            "  WHERE site.owner_user_id = #{ownerUserId}",
            "    AND site.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "    AND COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, '')) COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci",
            "    AND site.is_deleted = b'0'",
            "    AND site.quantity > 0",
            "    AND COALESCE(site.status, 'ACTIVE') != 'CANCELLED'",
            "    AND COALESCE(po.status, 'ACTIVE') != 'CANCELLED'",
            "    AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_sku_purchase_batch existing_batch",
            "      WHERE existing_batch.owner_user_id = site.owner_user_id",
            "        AND existing_batch.target_store_code COLLATE utf8mb4_unicode_ci = store_scope.project_code COLLATE utf8mb4_unicode_ci",
            "        AND (existing_batch.target_site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "             OR existing_batch.target_site_code = '*')",
            "        AND COALESCE(NULLIF(existing_batch.partner_sku, ''), NULLIF(existing_batch.psku_code, '')) COLLATE utf8mb4_unicode_ci = COALESCE(NULLIF(item.partner_sku, ''), NULLIF(site.psku_code, '')) COLLATE utf8mb4_unicode_ci",
            "        AND existing_batch.status = 'active'",
            "        AND existing_batch.is_deleted = b'0'",
            "    )",
            "  GROUP BY site.id, item.partner_sku, site.psku_code, po.order_no, po.submitted_collect_at, site.gmt_create, po.gmt_create, site.quantity",
            ") candidate",
            "WHERE candidate.purchaseQuantity > 0",
            "ORDER BY purchaseBatchTime ASC, sourceId ASC",
            "</script>"
    })
    List<BatchAttributionCandidateRow> listCandidateBatchLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT CONVERT(COALESCE(NULLIF(stock_line.partner_sku, ''), NULLIF(stock_line.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partnerSku,",
            "       SUM(stock_line.qty) AS stockQuantity,",
            "       SUM(CASE WHEN UPPER(stock_line.stock_bucket) = 'SELLABLE' THEN stock_line.qty ELSE 0 END) AS sellableStockQuantity",
            "FROM official_warehouse_inventory_snapshot_line stock_line",
            "WHERE stock_line.owner_user_id = #{ownerUserId}",
            "  AND stock_line.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci",
            "  AND stock_line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci",
            "  AND CONVERT(COALESCE(NULLIF(stock_line.partner_sku, ''), NULLIF(stock_line.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci",
            "  AND stock_line.is_current = b'1'",
            "  AND stock_line.is_deleted = b'0'",
            "  AND stock_line.qty > 0",
            "GROUP BY CONVERT(COALESCE(NULLIF(stock_line.partner_sku, ''), NULLIF(stock_line.psku_code, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci"
    })
    BatchAttributionCurrentStockRow selectCurrentStock(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku
    );
}
