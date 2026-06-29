package com.nuono.next.infrastructure.mapper;

import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundStageRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyAsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyRematchSummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnLineMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptHistoryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptSummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryLineProductMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotSourceRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryWarehouseStockRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportImportInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportRowInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ProductStockSourceCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ScheduledDeliveryAccuracySummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionEventRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface OfficialWarehouseStatisticsMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_stock_correction_event")
    Long selectMaxStockCorrectionId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_inventory_sync_batch")
    Long selectMaxInventorySyncBatchId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_inventory_snapshot_line")
    Long selectMaxInventorySnapshotLineId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_report_import")
    Long selectMaxReportImportId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_report_row")
    Long selectMaxReportRowId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_inbound_receipt_line")
    Long selectMaxInboundReceiptLineId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_delivery_accuracy_asn")
    Long selectMaxDeliveryAccuracyAsnId();

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, #{minAllocatedId}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = GREATEST(next_id, VALUES(next_id)),",
            "                        gmt_updated = NOW()"
    })
    int ensureSequenceAtLeast(
            @Param("sequenceName") String sequenceName,
            @Param("minAllocatedId") Long minAllocatedId
    );

    default Long nextStockCorrectionId() {
        Long tableMaxId = selectMaxStockCorrectionId();
        if (tableMaxId != null && tableMaxId > 620000L) {
            ensureSequenceAtLeast("official_warehouse_stock_correction_event", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_stock_correction_event", 620000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓库存订正 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    default Long nextInventorySyncBatchId() {
        Long tableMaxId = selectMaxInventorySyncBatchId();
        if (tableMaxId != null && tableMaxId > 621000L) {
            ensureSequenceAtLeast("official_warehouse_inventory_sync_batch", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_inventory_sync_batch", 621000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓库存同步批次 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    default Long nextInventorySnapshotLineId() {
        Long tableMaxId = selectMaxInventorySnapshotLineId();
        if (tableMaxId != null && tableMaxId > 622000L) {
            ensureSequenceAtLeast("official_warehouse_inventory_snapshot_line", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_inventory_snapshot_line", 622000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓库存快照行 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    default Long nextReportImportId() {
        Long tableMaxId = selectMaxReportImportId();
        if (tableMaxId != null && tableMaxId > 623000L) {
            ensureSequenceAtLeast("official_warehouse_report_import", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_report_import", 623000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓报表导入批次 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    default Long nextReportRowId() {
        Long tableMaxId = selectMaxReportRowId();
        if (tableMaxId != null && tableMaxId > 624000L) {
            ensureSequenceAtLeast("official_warehouse_report_row", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_report_row", 624000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓报表原始行 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    default Long nextInboundReceiptLineId() {
        Long tableMaxId = selectMaxInboundReceiptLineId();
        if (tableMaxId != null && tableMaxId > 625000L) {
            ensureSequenceAtLeast("official_warehouse_inbound_receipt_line", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_inbound_receipt_line", 625000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓入仓回执行 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    default Long nextDeliveryAccuracyAsnId() {
        Long tableMaxId = selectMaxDeliveryAccuracyAsnId();
        if (tableMaxId != null && tableMaxId > 626000L) {
            ensureSequenceAtLeast("official_warehouse_delivery_accuracy_asn", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_delivery_accuracy_asn", 626000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓 ASN 到货准确率 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    @Select({
            "SELECT ls.owner_user_id AS ownerUserId, ls.id AS logicalStoreId,",
            "       lss.store_code AS storeCode, lss.site AS siteCode,",
            "       ls.project_code AS projectCode, up.noon_partner_id AS partnerId",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id AND lss.is_deleted = b'0'",
            "LEFT JOIN user_project up ON up.user_id = ls.owner_user_id",
            "  AND BINARY up.project_code = BINARY ls.project_code",
            "  AND up.is_deleted = 0",
            "WHERE ls.is_deleted = b'0'",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND BINARY lss.store_code = BINARY #{storeCode}",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "ORDER BY lss.id ASC",
            "LIMIT 1"
    })
    InventorySyncScopeRecord selectInventorySyncScope(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "<script>",
            "SELECT pm.id AS productMasterId, pv.id AS productVariantId, pso.id AS productSiteOfferId,",
            "       pm.sku_parent AS skuParent, pv.partner_sku AS partnerSku, pso.psku_code AS pskuCode,",
            "       COALESCE(NULLIF(pv.child_sku, ''), pm.sku_parent) AS noonSku,",
            "       COALESCE(pm.title_cn_cache, pm.title_cache, pv.partner_sku, pm.sku_parent) AS title,",
            "       pm.brand_cache AS brand",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id AND pso.is_deleted = b'0'",
            "WHERE lss.is_deleted = b'0'",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND BINARY lss.store_code = BINARY #{storeCode}",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  AND (",
            "    <if test='noonSku != null and noonSku != \"\"'>",
            "      BINARY COALESCE(NULLIF(pv.child_sku, ''), pm.sku_parent) = BINARY #{noonSku}",
            "      OR BINARY pso.psku_code = BINARY #{noonSku}",
            "      OR BINARY pm.sku_parent = BINARY #{noonSku}",
            "    </if>",
            "    <if test='noonSku != null and noonSku != \"\" and partnerSku != null and partnerSku != \"\"'>",
            "      OR",
            "    </if>",
            "    <if test='partnerSku != null and partnerSku != \"\"'>",
            "      BINARY pv.partner_sku = BINARY #{partnerSku}",
            "      OR BINARY pso.psku_code = BINARY #{partnerSku}",
            "    </if>",
            "  )",
            "ORDER BY",
            "  CASE",
            "    WHEN #{noonSku} IS NOT NULL AND BINARY COALESCE(NULLIF(pv.child_sku, ''), pm.sku_parent) = BINARY #{noonSku} THEN 0",
            "    WHEN #{partnerSku} IS NOT NULL AND BINARY pv.partner_sku = BINARY #{partnerSku} THEN 1",
            "    ELSE 2",
            "  END ASC,",
            "  pso.id ASC",
            "LIMIT 1",
            "</script>"
    })
    InventoryLineProductMatchRecord findInventoryLineProductMatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("noonSku") String noonSku,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT a.id AS asnId,",
            "       (SELECT MAX(ap.id) FROM official_warehouse_appointment ap",
            "        WHERE ap.asn_id = a.id AND ap.is_deleted = b'0') AS appointmentId,",
            "       a.local_asn_no AS localAsnNo, a.noon_asn_nr AS noonAsnNr",
            "FROM official_warehouse_asn a",
            "WHERE a.is_deleted = b'0'",
            "  AND a.owner_user_id = #{ownerUserId}",
            "  AND BINARY a.store_code = BINARY #{storeCode}",
            "  AND UPPER(a.site_code) = UPPER(#{siteCode})",
            "  AND (BINARY a.noon_asn_nr = BINARY #{noonAsnNr}",
            "       OR BINARY a.local_asn_no = BINARY #{noonAsnNr})",
            "ORDER BY a.id DESC",
            "LIMIT 1"
    })
    InboundReceiptAsnMatchRecord findInboundReceiptAsnMatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("noonAsnNr") String noonAsnNr
    );

    @Select({
            "<script>",
            "SELECT l.id AS asnLineId, l.product_master_id AS productMasterId,",
            "       l.product_variant_id AS productVariantId, l.product_site_offer_id AS productSiteOfferId,",
            "       l.partner_sku AS partnerSku, l.psku_code AS pskuCode, l.noon_sku AS noonSku",
            "FROM official_warehouse_asn_line l",
            "WHERE l.is_deleted = b'0'",
            "  AND l.asn_id = #{asnId}",
            "  AND l.owner_user_id = #{ownerUserId}",
            "  AND BINARY l.store_code = BINARY #{storeCode}",
            "  AND UPPER(l.site_code) = UPPER(#{siteCode})",
            "  AND (",
            "    <if test='noonSku != null and noonSku != \"\"'>",
            "      BINARY l.noon_sku = BINARY #{noonSku}",
            "      OR BINARY l.psku_code = BINARY #{noonSku}",
            "      OR BINARY l.child_sku = BINARY #{noonSku}",
            "    </if>",
            "    <if test='noonSku != null and noonSku != \"\" and partnerSku != null and partnerSku != \"\"'>",
            "      OR",
            "    </if>",
            "    <if test='partnerSku != null and partnerSku != \"\"'>",
            "      BINARY l.partner_sku = BINARY #{partnerSku}",
            "      OR BINARY l.psku_code = BINARY #{partnerSku}",
            "    </if>",
            "  )",
            "ORDER BY",
            "  CASE",
            "    WHEN #{noonSku} IS NOT NULL AND BINARY l.noon_sku = BINARY #{noonSku} THEN 0",
            "    WHEN #{partnerSku} IS NOT NULL AND BINARY l.partner_sku = BINARY #{partnerSku} THEN 1",
            "    ELSE 2",
            "  END ASC,",
            "  l.id ASC",
            "LIMIT 1",
            "</script>"
    })
    InboundReceiptAsnLineMatchRecord findInboundReceiptAsnLineMatch(
            @Param("asnId") Long asnId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("noonSku") String noonSku,
            @Param("partnerSku") String partnerSku
    );


    @Select({
            "<script>",
            "SELECT ls.owner_user_id AS ownerUserId, ls.id AS logicalStoreId,",
            "       lss.store_code AS storeCode, COALESCE(ls.project_name, ls.project_code) AS storeName,",
            "       lss.site AS siteCode, ls.project_code AS projectCode,",
            "       NULL AS partnerId,",
            "       pm.id AS productMasterId, pv.id AS productVariantId, pso.id AS productSiteOfferId,",
            "       pm.sku_parent AS skuParent, pv.partner_sku AS partnerSku, pso.psku_code AS pskuCode,",
            "       COALESCE(NULLIF(pv.child_sku, ''), pm.sku_parent) AS noonSku,",
            "       COALESCE(pm.title_cn_cache, pm.title_cache, pv.partner_sku, pm.sku_parent) AS title,",
            "       pm.brand_cache AS brand, pm.cover_image_url AS imageUrl,",
            "       'NOON_FALLBACK' AS warehouseCode,",
            "       pso.fbn_stock AS fbnStock, pso.supermall_stock AS supermallStock, pso.fbp_stock AS fbpStock,",
            "       DATE_FORMAT(COALESCE(pso.last_synced_at, pso.gmt_updated), '%Y-%m-%d %H:%i:%s') AS lastSyncedAt",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id AND pso.is_deleted = b'0'",
            "WHERE lss.is_deleted = b'0'",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(lss.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "  AND UPPER(#{warehouseCode}) = 'NOON_FALLBACK'",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (pm.sku_parent LIKE #{keywordLike}",
            "       OR pv.partner_sku LIKE #{keywordLike}",
            "       OR pso.psku_code LIKE #{keywordLike}",
            "       OR pv.child_sku LIKE #{keywordLike}",
            "       OR pm.title_cache LIKE #{keywordLike}",
            "       OR pm.title_cn_cache LIKE #{keywordLike})",
            "</if>",
            "ORDER BY COALESCE(pso.last_synced_at, pso.gmt_updated) DESC, pso.id DESC",
            "</script>"
    })
    List<StockSourceRecord> listStockSources(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("warehouseCode") String warehouseCode,
            @Param("stockBucket") String stockBucket
    );

    @Select({
            "<script>",
            "SELECT ls.owner_user_id AS ownerUserId, ls.id AS logicalStoreId,",
            "       lss.store_code AS storeCode, COALESCE(ls.project_name, ls.project_code, lss.store_code) AS storeName,",
            "       lss.site AS siteCode, ls.project_code AS projectCode, stock.partnerId AS partnerId,",
            "       pm.id AS productMasterId, pv.id AS productVariantId, pso.id AS productSiteOfferId,",
            "       pm.sku_parent AS skuParent, pv.partner_sku AS partnerSku, pso.psku_code AS pskuCode,",
            "       COALESCE(NULLIF(pv.child_sku, ''), pm.sku_parent) AS noonSku,",
            "       COALESCE(NULLIF(stock.titleCache, ''), pm.title_cn_cache, pm.title_cache, pv.partner_sku, pso.psku_code) AS title,",
            "       COALESCE(NULLIF(stock.brandCache, ''), pm.brand_cache) AS brand, pm.cover_image_url AS imageUrl,",
            "       stock.warehouseCode AS warehouseCode,",
            "       COALESCE(stock.currentStock, 0) AS currentStock,",
            "       COALESCE(stock.effectiveStock, 0) AS effectiveStock,",
            "       COALESCE(stock.returnStock, 0) AS returnStock,",
            "       COALESCE(stock.failedOrExceptionStock, 0) AS failedOrExceptionStock,",
            "       COALESCE(stock.pendingConfirmationStock, 0) AS pendingConfirmationStock,",
            "       CASE WHEN stock.productSiteOfferId IS NULL THEN 'NO_INVENTORY_RECORD'",
            "            WHEN stock.unmatchedCount > 0 THEN 'PARTIAL_MATCH'",
            "            ELSE 'CLASSIFIED_INVENTORY' END AS inventoryConfidence,",
            "       stock.lastSyncedAt AS lastSyncedAt",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id AND pso.is_deleted = b'0'",
            "LEFT JOIN (",
            "  SELECT l.product_site_offer_id AS productSiteOfferId, MAX(l.partner_id) AS partnerId,",
            "         MAX(NULLIF(l.title_cache, '')) AS titleCache, MAX(NULLIF(l.brand_cache, '')) AS brandCache,",
            "         CASE WHEN COUNT(DISTINCT NULLIF(l.warehouse_code, '')) = 1",
            "              THEN MAX(NULLIF(l.warehouse_code, '')) ELSE NULL END AS warehouseCode,",
            "         SUM(GREATEST(COALESCE(l.qty, 0), 0)) AS currentStock,",
            "         SUM(CASE WHEN l.stock_bucket = 'SELLABLE' THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS effectiveStock,",
            "         SUM(CASE WHEN l.stock_bucket = 'RETURNED' THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS returnStock,",
            "         SUM(CASE WHEN l.stock_bucket IN ('INBOUND_FAILED', 'RECEIVING_EXCEPTION', 'DAMAGED', 'LOST', 'QUALITY_HOLD')",
            "                  THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS failedOrExceptionStock,",
            "         SUM(CASE WHEN l.stock_bucket NOT IN ('SELLABLE', 'RETURNED', 'INBOUND_FAILED', 'RECEIVING_EXCEPTION', 'DAMAGED', 'LOST', 'QUALITY_HOLD')",
            "                  THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS pendingConfirmationStock,",
            "         SUM(CASE WHEN l.match_status = 'MATCHED' THEN 0 ELSE 1 END) AS unmatchedCount,",
            "         DATE_FORMAT(MAX(COALESCE(l.inventory_snapshot_at, l.gmt_updated)), '%Y-%m-%d %H:%i:%s') AS lastSyncedAt",
            "  FROM official_warehouse_inventory_snapshot_line l",
            "  WHERE l.is_deleted = b'0'",
            "    AND l.is_current = b'1'",
            "    AND l.owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "    AND UPPER(l.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "    AND UPPER(l.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "    AND UPPER(l.site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "    AND UPPER(l.warehouse_code) = UPPER(#{warehouseCode})",
            "</if>",
            "  GROUP BY l.product_site_offer_id",
            ") stock ON stock.productSiteOfferId = pso.id",
            "WHERE lss.is_deleted = b'0'",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND EXISTS (",
            "      SELECT 1 FROM official_warehouse_inventory_snapshot_line exists_l",
            "      WHERE exists_l.is_deleted = b'0'",
            "        AND exists_l.is_current = b'1'",
            "        AND exists_l.owner_user_id = #{ownerUserId}",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "        AND UPPER(exists_l.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "        AND UPPER(exists_l.site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "        AND UPPER(exists_l.warehouse_code) = UPPER(#{warehouseCode})",
            "</if>",
            "      LIMIT 1",
            "  )",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(lss.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "  AND stock.productSiteOfferId IS NOT NULL",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (pm.sku_parent LIKE #{keywordLike}",
            "       OR pv.partner_sku LIKE #{keywordLike}",
            "       OR pso.psku_code LIKE #{keywordLike}",
            "       OR pv.child_sku LIKE #{keywordLike}",
            "       OR stock.titleCache LIKE #{keywordLike}",
            "       OR pm.title_cache LIKE #{keywordLike}",
            "       OR pm.title_cn_cache LIKE #{keywordLike})",
            "</if>",
            "ORDER BY COALESCE(stock.lastSyncedAt, DATE_FORMAT(pso.gmt_updated, '%Y-%m-%d %H:%i:%s')) DESC, pso.id DESC",
            "</script>"
    })
    List<InventorySnapshotSourceRecord> listInventorySnapshotSources(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("warehouseCode") String warehouseCode,
            @Param("stockBucket") String stockBucket
    );

    @Select({
            "<script>",
            "SELECT l.product_site_offer_id AS productSiteOfferId,",
            "       l.partner_sku AS partnerSku, l.psku_code AS pskuCode, l.noon_sku AS noonSku,",
            "       COALESCE(NULLIF(l.warehouse_code, ''), 'UNMARKED') AS warehouseCode,",
            "       SUM(GREATEST(COALESCE(l.qty, 0), 0)) AS currentStock,",
            "       SUM(CASE WHEN l.stock_bucket = 'SELLABLE' THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS effectiveStock,",
            "       SUM(CASE WHEN l.stock_bucket = 'RETURNED' THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS returnStock,",
            "       SUM(CASE WHEN l.stock_bucket IN ('INBOUND_FAILED', 'RECEIVING_EXCEPTION', 'DAMAGED', 'LOST', 'QUALITY_HOLD')",
            "                THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS failedOrExceptionStock,",
            "       SUM(CASE WHEN l.stock_bucket NOT IN ('SELLABLE', 'RETURNED', 'INBOUND_FAILED', 'RECEIVING_EXCEPTION', 'DAMAGED', 'LOST', 'QUALITY_HOLD')",
            "                THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS pendingConfirmationStock",
            "FROM official_warehouse_inventory_snapshot_line l",
            "WHERE l.is_deleted = b'0'",
            "  AND l.is_current = b'1'",
            "  AND l.owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(l.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(l.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(l.site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "  AND UPPER(l.warehouse_code) = UPPER(#{warehouseCode})",
            "</if>",
            "GROUP BY l.product_site_offer_id, l.partner_sku, l.psku_code, l.noon_sku,",
            "         COALESCE(NULLIF(l.warehouse_code, ''), 'UNMARKED')",
            "ORDER BY MAX(COALESCE(l.inventory_snapshot_at, l.gmt_updated)) DESC,",
            "         COALESCE(NULLIF(l.warehouse_code, ''), 'UNMARKED') ASC",
            "</script>"
    })
    List<InventoryWarehouseStockRecord> listInventorySnapshotWarehouseStocks(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("warehouseCode") String warehouseCode
    );

    @Select({
            "<script>",
            "SELECT l.owner_user_id AS ownerUserId, l.logical_store_id AS logicalStoreId,",
            "       l.store_code AS storeCode, COALESCE(ls.project_name, ls.project_code, l.store_code) AS storeName,",
            "       l.site_code AS siteCode, l.project_code AS projectCode, l.partner_id AS partnerId,",
            "       NULL AS productMasterId, NULL AS productVariantId, NULL AS productSiteOfferId,",
            "       NULL AS skuParent, l.partner_sku AS partnerSku, l.psku_code AS pskuCode, l.noon_sku AS noonSku,",
            "       COALESCE(NULLIF(MAX(l.title_cache), ''), l.partner_sku, l.noon_sku) AS title,",
            "       MAX(NULLIF(l.brand_cache, '')) AS brand, NULL AS imageUrl,",
            "       CASE WHEN COUNT(DISTINCT NULLIF(l.warehouse_code, '')) = 1",
            "            THEN MAX(NULLIF(l.warehouse_code, '')) ELSE NULL END AS warehouseCode,",
            "       SUM(GREATEST(COALESCE(l.qty, 0), 0)) AS currentStock,",
            "       SUM(CASE WHEN l.stock_bucket = 'SELLABLE' THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS effectiveStock,",
            "       SUM(CASE WHEN l.stock_bucket = 'RETURNED' THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS returnStock,",
            "       SUM(CASE WHEN l.stock_bucket IN ('INBOUND_FAILED', 'RECEIVING_EXCEPTION', 'DAMAGED', 'LOST', 'QUALITY_HOLD')",
            "                THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS failedOrExceptionStock,",
            "       SUM(CASE WHEN l.stock_bucket NOT IN ('SELLABLE', 'RETURNED', 'INBOUND_FAILED', 'RECEIVING_EXCEPTION', 'DAMAGED', 'LOST', 'QUALITY_HOLD')",
            "                THEN GREATEST(COALESCE(l.qty, 0), 0) ELSE 0 END) AS pendingConfirmationStock,",
            "       'PRODUCT_UNMATCHED' AS inventoryConfidence,",
            "       DATE_FORMAT(MAX(COALESCE(l.inventory_snapshot_at, l.gmt_updated)), '%Y-%m-%d %H:%i:%s') AS lastSyncedAt",
            "FROM official_warehouse_inventory_snapshot_line l",
            "LEFT JOIN logical_store ls ON ls.id = l.logical_store_id AND ls.is_deleted = b'0'",
            "WHERE l.is_deleted = b'0'",
            "  AND l.is_current = b'1'",
            "  AND l.owner_user_id = #{ownerUserId}",
            "  AND (l.product_site_offer_id IS NULL OR l.match_status != 'MATCHED')",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(l.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(l.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(l.site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "  AND UPPER(l.warehouse_code) = UPPER(#{warehouseCode})",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (l.partner_sku LIKE #{keywordLike}",
            "       OR l.psku_code LIKE #{keywordLike}",
            "       OR l.noon_sku LIKE #{keywordLike}",
            "       OR l.title_cache LIKE #{keywordLike})",
            "</if>",
            "GROUP BY l.owner_user_id, l.logical_store_id, l.store_code, ls.project_name, ls.project_code,",
            "         l.site_code, l.project_code, l.partner_id, l.partner_sku, l.psku_code, l.noon_sku",
            "ORDER BY MAX(COALESCE(l.inventory_snapshot_at, l.gmt_updated)) DESC, MAX(l.id) DESC",
            "</script>"
    })
    List<InventorySnapshotSourceRecord> listUnmatchedInventorySnapshotSources(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("warehouseCode") String warehouseCode,
            @Param("stockBucket") String stockBucket
    );

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "       correction_type AS correctionType, target_ref_type AS targetRefType, target_ref_id AS targetRefId,",
            "       product_master_id AS productMasterId, product_variant_id AS productVariantId,",
            "       product_site_offer_id AS productSiteOfferId, partner_sku AS partnerSku, psku_code AS pskuCode,",
            "       noon_sku AS noonSku, warehouse_code AS warehouseCode,",
            "       from_stock_bucket AS fromStockBucket, to_stock_bucket AS toStockBucket, quantity,",
            "       reason_code AS reasonCode, reason_text AS reasonText",
            "FROM official_warehouse_stock_correction_event",
            "WHERE is_deleted = b'0'",
            "  AND owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='productSiteOfferIds != null and productSiteOfferIds.size() > 0'>",
            "  AND product_site_offer_id IN",
            "  <foreach item='productSiteOfferId' collection='productSiteOfferIds' open='(' separator=',' close=')'>",
            "    #{productSiteOfferId}",
            "  </foreach>",
            "</if>",
            "ORDER BY id ASC",
            "</script>"
    })
    List<StockCorrectionEventRecord> listStockCorrections(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("productSiteOfferIds") Collection<Long> productSiteOfferIds
    );

    @Select({
            "<script>",
            "SELECT a.id AS asnId, a.local_asn_no AS localAsnNo, a.noon_asn_nr AS noonAsnNr,",
            "       a.store_code AS storeCode, a.site_code AS siteCode, a.status, a.noon_asn_status AS noonAsnStatus,",
            "       a.total_quantity AS totalQuantity, a.noon_total_qty AS noonTotalQty,",
            "       a.selected_warehouse_code AS selectedWarehouseCode,",
            "       a.selected_warehouse_partner_code AS selectedWarehousePartnerCode,",
            "       ap.status AS appointmentStatus",
            "FROM official_warehouse_asn a",
            "LEFT JOIN official_warehouse_appointment ap",
            "  ON ap.id = (",
            "      SELECT MAX(ap2.id)",
            "      FROM official_warehouse_appointment ap2",
            "      WHERE ap2.asn_id = a.id AND ap2.is_deleted = b'0'",
            "  )",
            "WHERE a.is_deleted = b'0'",
            "  AND a.owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(a.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(a.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(a.site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (a.local_asn_no LIKE #{keywordLike} OR a.noon_asn_nr LIKE #{keywordLike})",
            "</if>",
            "<if test='asn != null and asn != \"\"'>",
            "  AND (a.local_asn_no = #{asn} OR a.noon_asn_nr = #{asn})",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "  AND (UPPER(a.selected_warehouse_code) = UPPER(#{warehouseCode})",
            "       OR UPPER(a.selected_warehouse_partner_code) = UPPER(#{warehouseCode}))",
            "</if>",
            "ORDER BY COALESCE(a.noon_updated_at, a.gmt_updated) DESC, a.id DESC",
            "LIMIT 5000",
            "</script>"
    })
    List<InboundStageRecord> listInboundStageRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("asn") String asn,
            @Param("warehouseCode") String warehouseCode,
            @Param("receiptStatus") String receiptStatus
    );

    @Select({
            "<script>",
            "SELECT MAX(l.import_id) AS latestImportId,",
            "       DATE_FORMAT(MAX(l.imported_at), '%Y-%m-%d %H:%i:%s') AS latestImportedAt,",
            "       COUNT(DISTINCT NULLIF(l.noon_asn_nr, '')) AS asnCount,",
            "       COUNT(*) AS receiptLineCount,",
            "       COALESCE(SUM(GREATEST(COALESCE(l.qty_expected, 0), 0)), 0) AS expectedQuantity,",
            "       COALESCE(SUM(GREATEST(COALESCE(l.received_qty, 0), 0)), 0) AS receivedQuantity,",
            "       COALESCE(SUM(GREATEST(COALESCE(l.qc_failed_qty, 0), 0)), 0) AS qcFailedQuantity,",
            "       COALESCE(SUM(GREATEST(COALESCE(l.unidentified_qty, 0), 0)), 0) AS unidentifiedQuantity,",
            "       SUM(CASE WHEN l.receipt_status = 'NORMAL' THEN 1 ELSE 0 END) AS normalLineCount,",
            "       SUM(CASE WHEN l.receipt_status = 'QC_FAILED' THEN 1 ELSE 0 END) AS qcFailedLineCount,",
            "       SUM(CASE WHEN l.receipt_status = 'SHORT_RECEIVED' THEN 1 ELSE 0 END) AS shortReceivedLineCount,",
            "       SUM(CASE WHEN l.receipt_status = 'OVER_RECEIVED' THEN 1 ELSE 0 END) AS overReceivedLineCount,",
            "       SUM(CASE WHEN l.receipt_status = 'UNIDENTIFIED' THEN 1 ELSE 0 END) AS unidentifiedLineCount,",
            "       SUM(CASE WHEN l.match_status = 'MATCHED' THEN 1 ELSE 0 END) AS matchedLineCount,",
            "       SUM(CASE WHEN l.match_status = 'NO_LOCAL_ASN' THEN 1 ELSE 0 END) AS noLocalAsnLineCount,",
            "       SUM(CASE WHEN l.match_status = 'LINE_UNMATCHED' THEN 1 ELSE 0 END) AS lineUnmatchedLineCount,",
            "       SUM(CASE WHEN l.match_status = 'PRODUCT_UNMATCHED' THEN 1 ELSE 0 END) AS productUnmatchedLineCount,",
            "       SUM(CASE WHEN l.receipt_status != 'NORMAL' OR l.match_status != 'MATCHED' THEN 1 ELSE 0 END) AS receiptExceptionLineCount",
            "FROM (",
            "  SELECT ranked.*",
            "  FROM (",
            "    SELECT l.*, i.snapshot_at AS imported_at,",
            "           ROW_NUMBER() OVER (",
            "             PARTITION BY l.owner_user_id, BINARY l.store_code, UPPER(l.site_code),",
            "                          COALESCE(l.noon_asn_nr, ''),",
            "                          COALESCE(l.partner_sku, ''),",
            "                          COALESCE(l.noon_sku, ''),",
            "                          COALESCE(l.pbarcode_canonical, ''),",
            "                          COALESCE(l.partner_warehouse, ''),",
            "                          COALESCE(l.noon_warehouse, ''),",
            "                          COALESCE(l.country_code, ''),",
            "                          COALESCE(CAST(l.asn_schedule_date AS CHAR), ''),",
            "                          GREATEST(COALESCE(l.qty_expected, 0), 0),",
            "                          GREATEST(COALESCE(l.received_qty, 0), 0),",
            "                          GREATEST(COALESCE(l.qc_failed_qty, 0), 0),",
            "                          GREATEST(COALESCE(l.unidentified_qty, 0), 0)",
            "             ORDER BY i.snapshot_at DESC, l.import_id DESC, l.id DESC",
            "           ) AS receipt_business_rank",
            "    FROM official_warehouse_inbound_receipt_line l",
            "    JOIN official_warehouse_report_import i ON i.id = l.import_id AND i.is_deleted = b'0'",
            "    WHERE l.is_deleted = b'0'",
            "      AND i.report_type = #{reportType}",
            "      AND l.owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "      AND UPPER(l.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "      AND UPPER(l.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "      AND UPPER(l.site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "      AND (l.noon_asn_nr LIKE #{keywordLike}",
            "           OR l.partner_sku LIKE #{keywordLike}",
            "           OR l.psku_code LIKE #{keywordLike}",
            "           OR l.noon_sku LIKE #{keywordLike}",
            "           OR l.pbarcode_canonical LIKE #{keywordLike})",
            "</if>",
            "<if test='asn != null and asn != \"\"'>",
            "      AND l.noon_asn_nr = #{asn}",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "      AND (UPPER(l.noon_warehouse) = UPPER(#{warehouseCode})",
            "           OR UPPER(l.partner_warehouse) = UPPER(#{warehouseCode}))",
            "</if>",
            "  ) ranked",
            "  WHERE ranked.receipt_business_rank = 1",
            ") l",
            "WHERE 1 = 1",
            "<if test='receiptStatus != null and receiptStatus != \"\"'>",
            "  AND (UPPER(l.receipt_status) = UPPER(#{receiptStatus})",
            "       OR UPPER(l.match_status) = UPPER(#{receiptStatus}))",
            "</if>",
            "</script>"
    })
    InboundReceiptSummaryRecord selectInboundReceiptSummary(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("asn") String asn,
            @Param("warehouseCode") String warehouseCode,
            @Param("receiptStatus") String receiptStatus,
            @Param("reportType") String reportType
    );

    @Select({
            "<script>",
            "SELECT l.import_id AS importId, l.report_row_id AS reportRowId,",
            "       l.product_master_id AS productMasterId, l.product_variant_id AS productVariantId,",
            "       l.product_site_offer_id AS productSiteOfferId, l.noon_asn_nr AS noonAsnNr,",
            "       l.partner_sku AS partnerSku, l.psku_code AS pskuCode, l.noon_sku AS noonSku,",
            "       l.pbarcode_canonical AS pbarcodeCanonical, l.partner_warehouse AS partnerWarehouse,",
            "       l.noon_warehouse AS noonWarehouse, l.qty_expected AS qtyExpected,",
            "       l.received_qty AS receivedQty, l.qc_failed_qty AS qcFailedQty,",
            "       l.unidentified_qty AS unidentifiedQty, l.qc_failed_reason AS qcFailedReason,",
            "       l.receipt_status AS receiptStatus, l.match_status AS matchStatus,",
            "       l.anomaly_flags_json AS anomalyFlagsJson,",
            "       DATE_FORMAT(l.asn_created_at, '%Y-%m-%d %H:%i:%s') AS asnCreatedAt,",
            "       DATE_FORMAT(l.asn_schedule_date, '%Y-%m-%d') AS asnScheduleDate,",
            "       DATE_FORMAT(l.asn_completed_at, '%Y-%m-%d %H:%i:%s') AS asnCompletedAt,",
            "       DATE_FORMAT(l.imported_at, '%Y-%m-%d %H:%i:%s') AS importedAt",
            "FROM (",
            "  SELECT ranked.*",
            "  FROM (",
            "    SELECT l.*, i.snapshot_at AS imported_at,",
            "           ROW_NUMBER() OVER (",
            "             PARTITION BY l.owner_user_id, BINARY l.store_code, UPPER(l.site_code),",
            "                          COALESCE(l.noon_asn_nr, ''),",
            "                          COALESCE(l.partner_sku, ''),",
            "                          COALESCE(l.noon_sku, ''),",
            "                          COALESCE(l.pbarcode_canonical, ''),",
            "                          COALESCE(l.partner_warehouse, ''),",
            "                          COALESCE(l.noon_warehouse, ''),",
            "                          COALESCE(l.country_code, ''),",
            "                          COALESCE(CAST(l.asn_schedule_date AS CHAR), ''),",
            "                          GREATEST(COALESCE(l.qty_expected, 0), 0),",
            "                          GREATEST(COALESCE(l.received_qty, 0), 0),",
            "                          GREATEST(COALESCE(l.qc_failed_qty, 0), 0),",
            "                          GREATEST(COALESCE(l.unidentified_qty, 0), 0)",
            "             ORDER BY i.snapshot_at DESC, l.import_id DESC, l.id DESC",
            "           ) AS receipt_business_rank",
            "    FROM official_warehouse_inbound_receipt_line l",
            "    JOIN official_warehouse_report_import i ON i.id = l.import_id AND i.is_deleted = b'0'",
            "    WHERE l.is_deleted = b'0'",
            "      AND l.owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "      AND UPPER(l.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "      AND UPPER(l.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "      AND UPPER(l.site_code) = UPPER(#{siteCode})",
            "</if>",
            "  ) ranked",
            "  WHERE ranked.receipt_business_rank = 1",
            ") l",
            "WHERE l.product_site_offer_id = #{productSiteOfferId}",
            "ORDER BY COALESCE(l.asn_completed_at, l.asn_schedule_date, l.imported_at, l.gmt_create) DESC, l.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<InboundReceiptHistoryRecord> listProductInboundReceiptHistory(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("productSiteOfferId") Long productSiteOfferId,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT b.id AS logisticsBatchId, b.batch_no AS logisticsBatchNo, b.status AS logisticsStatus,",
            "       s.purchase_order_id AS purchaseOrderId, s.purchase_order_no AS purchaseOrderNo,",
            "       s.source_store_code AS sourceStoreCode, s.site_code AS siteCode,",
            "       s.partner_sku AS partnerSku, s.sku_parent AS skuParent,",
            "       COALESCE(SUM(GREATEST(COALESCE(selected_source.quantity, s.reserved_quantity, 0), 0)), 0) AS quantity,",
            "       DATE_FORMAT(MAX(COALESCE(b.gmt_updated, s.gmt_updated)), '%Y-%m-%d %H:%i:%s') AS latestAt",
            "FROM product_site_offer pso",
            "JOIN warehouse_shipping_batch_source s",
            "  ON s.product_variant_id = pso.variant_id",
            " AND s.is_deleted = b'0'",
            " AND s.owner_user_id = #{ownerUserId}",
            " AND UPPER(s.site_code) = UPPER(#{siteCode})",
            "JOIN warehouse_shipping_batch b",
            "  ON b.id = s.batch_id",
            " AND b.owner_user_id = s.owner_user_id",
            " AND b.is_deleted = b'0'",
            "LEFT JOIN (",
            "  SELECT src.batch_source_id, SUM(GREATEST(COALESCE(src.quantity, 0), 0)) AS quantity",
            "  FROM warehouse_shipping_suggestion_line_source src",
            "  JOIN warehouse_shipping_batch selected_batch",
            "    ON selected_batch.id = src.batch_id",
            "   AND selected_batch.owner_user_id = #{ownerUserId}",
            "   AND selected_batch.is_deleted = b'0'",
            "   AND selected_batch.selected_option_id IS NOT NULL",
            "   AND src.option_id = selected_batch.selected_option_id",
            "  WHERE src.is_deleted = b'0'",
            "  GROUP BY src.batch_source_id",
            ") selected_source ON selected_source.batch_source_id = s.id",
            "WHERE pso.id = #{productSiteOfferId}",
            "  AND pso.is_deleted = b'0'",
            "GROUP BY b.id, b.batch_no, b.status,",
            "         s.purchase_order_id, s.purchase_order_no,",
            "         s.source_store_code, s.site_code, s.partner_sku, s.sku_parent",
            "HAVING quantity > 0",
            "ORDER BY MAX(COALESCE(b.gmt_updated, s.gmt_updated)) DESC, b.id DESC, s.purchase_order_id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductStockSourceCandidateRecord> listProductStockSourceCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("productSiteOfferId") Long productSiteOfferId,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT MAX(d.import_id) AS latestImportId,",
            "       DATE_FORMAT(MAX(d.imported_at), '%Y-%m-%d %H:%i:%s') AS latestImportedAt,",
            "       COUNT(DISTINCT NULLIF(d.noon_asn_nr, '')) AS asnCount,",
            "       COALESCE(SUM(GREATEST(COALESCE(d.scheduled_qty, 0), 0)), 0) AS scheduledQuantity,",
            "       COALESCE(SUM(GREATEST(COALESCE(d.grn_qty, 0), 0)), 0) AS grnQuantity,",
            "       COALESCE(SUM(GREATEST(COALESCE(d.inbound_qty_variance, 0), 0)), 0) AS inboundQuantityVariance,",
            "       SUM(CASE WHEN d.accuracy_status = 'PUTAWAY_COMPLETED' THEN 1 ELSE 0 END) AS putawayCompletedAsnCount,",
            "       SUM(CASE WHEN d.accuracy_status IN ('CANCELED', 'CANCELLED') THEN 1 ELSE 0 END) AS cancelledAsnCount,",
            "       SUM(CASE WHEN d.accuracy_status = 'EXPIRED' THEN 1 ELSE 0 END) AS expiredAsnCount,",
            "       SUM(CASE WHEN d.match_status = 'MATCHED' THEN 1 ELSE 0 END) AS matchedAsnCount,",
            "       SUM(CASE WHEN d.match_status = 'NO_LOCAL_ASN' THEN 1 ELSE 0 END) AS noLocalAsnCount,",
            "       SUM(CASE WHEN d.accuracy_status != 'PUTAWAY_COMPLETED'",
            "                 OR d.match_status != 'MATCHED'",
            "                 OR GREATEST(COALESCE(d.inbound_qty_variance, 0), 0) > 0",
            "                THEN 1 ELSE 0 END) AS exceptionAsnCount",
            "FROM (",
            "  SELECT ranked.*",
            "  FROM (",
            "    SELECT d.*, i.snapshot_at AS imported_at,",
            "           ROW_NUMBER() OVER (",
            "             PARTITION BY d.owner_user_id, BINARY d.store_code, UPPER(d.site_code),",
            "                          COALESCE(d.noon_asn_nr, ''),",
            "                          COALESCE(d.warehouse_code, ''),",
            "                          COALESCE(d.country_code, ''),",
            "                          COALESCE(CAST(d.scheduled_date AS CHAR), ''),",
            "                          COALESCE(CAST(d.delivery_date AS CHAR), ''),",
            "                          GREATEST(COALESCE(d.scheduled_qty, 0), 0),",
            "                          GREATEST(COALESCE(d.grn_qty, 0), 0),",
            "                          GREATEST(COALESCE(d.inbound_qty_variance, 0), 0),",
            "                          COALESCE(d.accuracy_status, '')",
            "             ORDER BY i.snapshot_at DESC, d.import_id DESC, d.id DESC",
            "           ) AS accuracy_business_rank",
            "    FROM official_warehouse_delivery_accuracy_asn d",
            "    JOIN official_warehouse_report_import i ON i.id = d.import_id AND i.is_deleted = b'0'",
            "    WHERE d.is_deleted = b'0'",
            "      AND i.report_type = #{reportType}",
            "      AND d.owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "      AND UPPER(d.store_code) IN",
            "  <foreach item='storeCodeItem' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCodeItem})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "      AND UPPER(d.store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "      AND UPPER(d.site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "      AND d.noon_asn_nr LIKE #{keywordLike}",
            "</if>",
            "<if test='asn != null and asn != \"\"'>",
            "      AND d.noon_asn_nr = #{asn}",
            "</if>",
            "<if test='warehouseCode != null and warehouseCode != \"\"'>",
            "      AND UPPER(d.warehouse_code) = UPPER(#{warehouseCode})",
            "</if>",
            "  ) ranked",
            "  WHERE ranked.accuracy_business_rank = 1",
            ") d",
            "WHERE 1 = 1",
            "<if test='receiptStatus != null and receiptStatus != \"\"'>",
            "  AND (UPPER(d.accuracy_status) = UPPER(#{receiptStatus})",
            "       OR UPPER(d.match_status) = UPPER(#{receiptStatus}))",
            "</if>",
            "</script>"
    })
    ScheduledDeliveryAccuracySummaryRecord selectScheduledDeliveryAccuracySummary(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("asn") String asn,
            @Param("warehouseCode") String warehouseCode,
            @Param("receiptStatus") String receiptStatus,
            @Param("reportType") String reportType
    );

    @Update({
            "UPDATE official_warehouse_inbound_receipt_line l",
            "JOIN official_warehouse_report_import i ON i.id = l.import_id",
            "SET l.is_deleted = b'1', l.updated_by = #{operatorUserId}, l.gmt_updated = NOW()",
            "WHERE i.is_deleted = b'0'",
            "  AND i.owner_user_id = #{ownerUserId}",
            "  AND BINARY i.store_code = BINARY #{storeCode}",
            "  AND UPPER(i.site_code) = UPPER(#{siteCode})",
            "  AND i.report_type = #{reportType}",
            "  AND i.source_export_code = #{sourceExportCode}"
    })
    int deactivatePreviousFbnReceivedReceiptLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("reportType") String reportType,
            @Param("sourceExportCode") String sourceExportCode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_report_row r",
            "JOIN official_warehouse_report_import i ON i.id = r.import_id",
            "SET r.is_deleted = b'1', r.updated_by = #{operatorUserId}, r.gmt_updated = NOW()",
            "WHERE i.is_deleted = b'0'",
            "  AND i.owner_user_id = #{ownerUserId}",
            "  AND BINARY i.store_code = BINARY #{storeCode}",
            "  AND UPPER(i.site_code) = UPPER(#{siteCode})",
            "  AND i.report_type = #{reportType}",
            "  AND i.source_export_code = #{sourceExportCode}"
    })
    int deactivatePreviousFbnReceivedReportRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("reportType") String reportType,
            @Param("sourceExportCode") String sourceExportCode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_report_import",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE is_deleted = b'0'",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY store_code = BINARY #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND report_type = #{reportType}",
            "  AND source_export_code = #{sourceExportCode}"
    })
    int deactivatePreviousFbnReceivedReportImportRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("reportType") String reportType,
            @Param("sourceExportCode") String sourceExportCode,
            @Param("operatorUserId") Long operatorUserId
    );

    default void deactivatePreviousFbnReceivedReportImports(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String reportType,
            String sourceExportCode,
            Long operatorUserId
    ) {
        deactivatePreviousFbnReceivedReceiptLines(ownerUserId, storeCode, siteCode, reportType, sourceExportCode, operatorUserId);
        deactivatePreviousFbnReceivedReportRows(ownerUserId, storeCode, siteCode, reportType, sourceExportCode, operatorUserId);
        deactivatePreviousFbnReceivedReportImportRows(ownerUserId, storeCode, siteCode, reportType, sourceExportCode, operatorUserId);
    }

    @Update({
            "UPDATE official_warehouse_delivery_accuracy_asn a",
            "JOIN official_warehouse_report_import i ON i.id = a.import_id",
            "SET a.is_deleted = b'1', a.updated_by = #{operatorUserId}, a.gmt_updated = NOW()",
            "WHERE i.is_deleted = b'0'",
            "  AND i.owner_user_id = #{ownerUserId}",
            "  AND BINARY i.store_code = BINARY #{storeCode}",
            "  AND UPPER(i.site_code) = UPPER(#{siteCode})",
            "  AND i.report_type = #{reportType}",
            "  AND i.source_export_code = #{sourceExportCode}"
    })
    int deactivatePreviousScheduledDeliveryAccuracyFacts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("reportType") String reportType,
            @Param("sourceExportCode") String sourceExportCode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_report_row r",
            "JOIN official_warehouse_report_import i ON i.id = r.import_id",
            "SET r.is_deleted = b'1', r.updated_by = #{operatorUserId}, r.gmt_updated = NOW()",
            "WHERE i.is_deleted = b'0'",
            "  AND i.owner_user_id = #{ownerUserId}",
            "  AND BINARY i.store_code = BINARY #{storeCode}",
            "  AND UPPER(i.site_code) = UPPER(#{siteCode})",
            "  AND i.report_type = #{reportType}",
            "  AND i.source_export_code = #{sourceExportCode}"
    })
    int deactivatePreviousScheduledDeliveryAccuracyReportRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("reportType") String reportType,
            @Param("sourceExportCode") String sourceExportCode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_report_import",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE is_deleted = b'0'",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY store_code = BINARY #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND report_type = #{reportType}",
            "  AND source_export_code = #{sourceExportCode}"
    })
    int deactivatePreviousScheduledDeliveryAccuracyReportImportRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("reportType") String reportType,
            @Param("sourceExportCode") String sourceExportCode,
            @Param("operatorUserId") Long operatorUserId
    );

    default void deactivatePreviousScheduledDeliveryAccuracyImports(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String reportType,
            String sourceExportCode,
            Long operatorUserId
    ) {
        deactivatePreviousScheduledDeliveryAccuracyFacts(ownerUserId, storeCode, siteCode, reportType, sourceExportCode, operatorUserId);
        deactivatePreviousScheduledDeliveryAccuracyReportRows(ownerUserId, storeCode, siteCode, reportType, sourceExportCode, operatorUserId);
        deactivatePreviousScheduledDeliveryAccuracyReportImportRows(ownerUserId, storeCode, siteCode, reportType, sourceExportCode, operatorUserId);
    }

    @Insert({
            "INSERT INTO official_warehouse_report_import (",
            "id, owner_user_id, logical_store_id, store_code, site_code, project_code, partner_id,",
            "report_type, source_type, source_export_code, file_name, file_sha256, snapshot_at,",
            "business_date_start, business_date_end, total_rows, valid_rows, warning_rows, error_rows,",
            "status, summary_json, raw_preview_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{projectCode}, #{partnerId},",
            "#{reportType}, #{sourceType}, #{sourceExportCode}, #{fileName}, #{fileSha256}, #{snapshotAt},",
            "#{businessDateStart}, #{businessDateEnd}, #{totalRows}, #{validRows}, #{warningRows}, #{errorRows},",
            "#{status}, #{summaryJson}, #{rawPreviewJson}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertReportImport(ReportImportInsertRecord record);

    @Insert({
            "INSERT INTO official_warehouse_report_row (",
            "id, import_id, report_type, row_no, business_key, business_key_hash, row_status, warning_code,",
            "error_message, raw_row_json, normalized_row_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{importId}, #{reportType}, #{rowNo}, #{businessKey}, #{businessKeyHash}, #{rowStatus}, #{warningCode},",
            "#{errorMessage}, #{rawRowJson}, #{normalizedRowJson}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertReportRow(ReportRowInsertRecord record);

    @Insert({
            "INSERT INTO official_warehouse_inbound_receipt_line (",
            "id, import_id, report_row_id, owner_user_id, logical_store_id, store_code, site_code, project_code, partner_id,",
            "asn_id, asn_line_id, noon_asn_nr, product_master_id, product_variant_id, product_site_offer_id,",
            "partner_sku, psku_code, noon_sku, pbarcode_canonical, partner_warehouse, noon_warehouse, country_code,",
            "qty_expected, received_qty, qc_failed_qty, unidentified_qty, qc_failed_reason, receipt_status, match_status,",
            "anomaly_flags_json, asn_created_at, asn_schedule_date, asn_completed_at, raw_payload_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{importId}, #{reportRowId}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{projectCode}, #{partnerId},",
            "#{asnId}, #{asnLineId}, #{noonAsnNr}, #{productMasterId}, #{productVariantId}, #{productSiteOfferId},",
            "#{partnerSku}, #{pskuCode}, #{noonSku}, #{pbarcodeCanonical}, #{partnerWarehouse}, #{noonWarehouse}, #{countryCode},",
            "#{qtyExpected}, #{receivedQty}, #{qcFailedQty}, #{unidentifiedQty}, #{qcFailedReason}, #{receiptStatus}, #{matchStatus},",
            "#{anomalyFlagsJson}, #{asnCreatedAt}, #{asnScheduleDate}, #{asnCompletedAt}, #{rawPayloadJson},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertInboundReceiptLine(InboundReceiptLineInsertRecord record);

    @Insert({
            "INSERT INTO official_warehouse_delivery_accuracy_asn (",
            "id, import_id, report_row_id, owner_user_id, logical_store_id, store_code, site_code, project_code, partner_id,",
            "asn_id, appointment_id, noon_asn_nr, warehouse_code, country_code, asn_creation_date, scheduled_date, delivery_date,",
            "scheduled_qty, grn_qty, inbound_qty_variance, accuracy_status, inbound_utilization_efficiency, match_status,",
            "anomaly_flags_json, raw_payload_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{importId}, #{reportRowId}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{projectCode}, #{partnerId},",
            "#{asnId}, #{appointmentId}, #{noonAsnNr}, #{warehouseCode}, #{countryCode}, #{asnCreationDate}, #{scheduledDate}, #{deliveryDate},",
            "#{scheduledQty}, #{grnQty}, #{inboundQtyVariance}, #{accuracyStatus}, #{inboundUtilizationEfficiency}, #{matchStatus},",
            "#{anomalyFlagsJson}, #{rawPayloadJson}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertDeliveryAccuracyAsn(DeliveryAccuracyAsnInsertRecord record);

    @Select({
            "SELECT COUNT(*) AS totalRows,",
            "       SUM(CASE WHEN match_status = 'MATCHED' THEN 1 ELSE 0 END) AS matchedRows,",
            "       SUM(CASE WHEN match_status = 'NO_LOCAL_ASN' THEN 1 ELSE 0 END) AS noLocalAsnRows",
            "FROM official_warehouse_delivery_accuracy_asn",
            "WHERE is_deleted = b'0'",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY store_code = BINARY #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND import_id = #{importId}"
    })
    DeliveryAccuracyRematchSummaryRecord selectDeliveryAccuracyRematchSummary(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("importId") Long importId
    );

    @Update({
            "UPDATE official_warehouse_delivery_accuracy_asn d",
            "JOIN official_warehouse_asn a ON a.is_deleted = b'0'",
            "  AND a.owner_user_id = d.owner_user_id",
            "  AND BINARY a.store_code = BINARY d.store_code",
            "  AND UPPER(a.site_code) = UPPER(d.site_code)",
            "  AND BINARY a.noon_asn_nr = BINARY d.noon_asn_nr",
            "SET d.asn_id = a.id,",
            "    d.appointment_id = (",
            "      SELECT MAX(ap.id)",
            "      FROM official_warehouse_appointment ap",
            "      WHERE ap.is_deleted = b'0'",
            "        AND ap.owner_user_id = d.owner_user_id",
            "        AND ap.asn_id = a.id",
            "    ),",
            "    d.match_status = 'MATCHED',",
            "    d.updated_by = #{operatorUserId},",
            "    d.gmt_updated = NOW()",
            "WHERE d.is_deleted = b'0'",
            "  AND d.owner_user_id = #{ownerUserId}",
            "  AND BINARY d.store_code = BINARY #{storeCode}",
            "  AND UPPER(d.site_code) = UPPER(#{siteCode})",
            "  AND d.import_id = #{importId}",
            "  AND d.match_status = 'NO_LOCAL_ASN'"
    })
    int rematchDeliveryAccuracyAsns(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("importId") Long importId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT DISTINCT noon_asn_nr",
            "FROM official_warehouse_delivery_accuracy_asn",
            "WHERE is_deleted = b'0'",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY store_code = BINARY #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND import_id = #{importId}",
            "  AND match_status = 'NO_LOCAL_ASN'",
            "  AND noon_asn_nr IS NOT NULL",
            "  AND noon_asn_nr != ''",
            "ORDER BY noon_asn_nr ASC",
            "LIMIT #{limit}"
    })
    List<String> listMissingDeliveryAccuracyNoonAsnNumbers(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("importId") Long importId,
            @Param("limit") int limit
    );

    @Insert({
            "INSERT INTO official_warehouse_inventory_sync_batch (",
            "id, owner_user_id, logical_store_id, store_code, site_code, project_code, partner_id,",
            "source_type, request_summary_json, response_summary_json, status, total_pages, total_rows, valid_rows, error_rows,",
            "synced_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{projectCode}, #{partnerId},",
            "#{sourceType}, #{requestSummaryJson}, #{responseSummaryJson}, #{status}, #{totalPages}, #{totalRows}, #{validRows}, #{errorRows},",
            "NOW(), b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertInventorySyncBatch(InventorySyncBatchInsertRecord record);

    @Update({
            "UPDATE official_warehouse_inventory_snapshot_line",
            "SET is_current = b'0', updated_by = NULL, gmt_updated = NOW()",
            "WHERE is_deleted = b'0'",
            "  AND is_current = b'1'",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY store_code = BINARY #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})"
    })
    int deactivateCurrentInventorySnapshotLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO official_warehouse_inventory_snapshot_line (",
            "id, sync_batch_id, owner_user_id, logical_store_id, store_code, site_code, project_code, partner_id,",
            "product_master_id, product_variant_id, product_site_offer_id, partner_sku, psku_code, noon_sku, pbarcode, barcode,",
            "warehouse_code, country_code, inventory_type, reason_code, classification_code, stock_bucket, qty,",
            "inventory_snapshot_at, title_cache, brand_cache, match_status, match_message, raw_payload_json,",
            "is_current, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{syncBatchId}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{projectCode}, #{partnerId},",
            "#{productMasterId}, #{productVariantId}, #{productSiteOfferId}, #{partnerSku}, #{pskuCode}, #{noonSku}, #{pbarcode}, #{barcode},",
            "#{warehouseCode}, #{countryCode}, #{inventoryType}, #{reasonCode}, #{classificationCode}, #{stockBucket}, #{quantity},",
            "#{inventorySnapshotAt}, #{titleCache}, #{brandCache}, #{matchStatus}, #{matchMessage}, #{rawPayloadJson},",
            "b'1', b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertInventorySnapshotLine(InventorySnapshotLineInsertRecord record);

    @Insert({
            "INSERT INTO official_warehouse_stock_correction_event (",
            "id, owner_user_id, logical_store_id, store_code, site_code, project_code, partner_id,",
            "correction_type, target_ref_type, target_ref_id, product_master_id, product_variant_id, product_site_offer_id,",
            "partner_sku, psku_code, noon_sku, warehouse_code, from_stock_bucket, to_stock_bucket, quantity,",
            "reason_code, reason_text, before_payload_json, after_payload_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{projectCode}, #{partnerId},",
            "#{correctionType}, #{targetRefType}, #{targetRefId}, #{productMasterId}, #{productVariantId}, #{productSiteOfferId},",
            "#{partnerSku}, #{pskuCode}, #{noonSku}, #{warehouseCode}, #{fromStockBucket}, #{toStockBucket}, #{quantity},",
            "#{reasonCode}, #{reasonText}, #{beforePayloadJson}, #{afterPayloadJson}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertStockCorrection(StockCorrectionInsertRecord record);
}
