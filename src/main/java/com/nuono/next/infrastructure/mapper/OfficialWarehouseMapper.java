package com.nuono.next.infrastructure.mapper;

import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnNoonListSyncRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnShippingBatchLinkInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnShippingBatchLinkRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ProductCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface OfficialWarehouseMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_asn")
    Long selectMaxAsnId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_asn_line")
    Long selectMaxAsnLineId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_appointment")
    Long selectMaxAppointmentId();

    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_asn_shipping_batch_link")
    Long selectMaxAsnShippingBatchLinkId();

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

    default Long nextAsnId() {
        return nextIdAfterTableMax("official_warehouse_asn", 500000L, selectMaxAsnId());
    }

    default Long nextAsnLineId() {
        return nextIdAfterTableMax("official_warehouse_asn_line", 510000L, selectMaxAsnLineId());
    }

    default Long nextAppointmentId() {
        return nextIdAfterTableMax("official_warehouse_appointment", 610000L, selectMaxAppointmentId());
    }

    default Long nextAsnShippingBatchLinkId() {
        return nextIdAfterTableMax(
                "official_warehouse_asn_shipping_batch_link",
                620000L,
                selectMaxAsnShippingBatchLinkId()
        );
    }

    default Long nextIdAfterTableMax(String sequenceName, long initialValue, Long tableMaxId) {
        if (tableMaxId != null && tableMaxId > initialValue) {
            ensureSequenceAtLeast(sequenceName, tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }

    @Select({
            "<script>",
            "SELECT ls.owner_user_id AS ownerUserId, ls.id AS logicalStoreId, lss.id AS logicalStoreSiteId,",
            "       lss.store_code AS storeCode, COALESCE(ls.project_name, ls.project_code) AS storeName,",
            "       lss.site AS siteCode, ls.project_code AS projectCode",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "WHERE lss.is_deleted = b'0'",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "</if>",
            "ORDER BY lss.is_reference_site DESC, lss.id ASC",
            "LIMIT 1",
            "</script>"
    })
    StoreSiteRecord selectStoreSite(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "<script>",
            "SELECT ls.owner_user_id AS ownerUserId, ls.id AS logicalStoreId, lss.id AS logicalStoreSiteId,",
            "       lss.store_code AS storeCode, COALESCE(ls.project_name, ls.project_code) AS storeName, lss.site AS siteCode,",
            "       pm.id AS productMasterId, pv.id AS productVariantId, pso.id AS productSiteOfferId,",
            "       pm.sku_parent AS skuParent, COALESCE(NULLIF(pm.partner_sku, ''), pv.partner_sku) AS partnerSku, pv.child_sku AS childSku,",
            "       COALESCE(NULLIF(TRIM(official.noon_partner_psku_code), ''), NULLIF(TRIM(pso.psku_code), '')) AS pskuCode,",
            "       COALESCE(NULLIF(pv.child_sku, ''), pm.sku_parent) AS noonSku,",
            "       COALESCE(pm.title_cn_cache, pm.title_cache, pv.partner_sku, pm.sku_parent) AS titleCache,",
            "       pm.title_cache AS titleEn, pm.brand_cache AS brandCache,",
            "       COALESCE(imageAsset.url, pm.cover_image_url) AS imageUrlCache,",
            "       COALESCE(official.product_length_cm, effective.product_length_cm, warehouseSpec.product_length_cm, ali1688Spec.product_length_cm, pvs.product_length_cm) AS productLengthCm,",
            "       COALESCE(official.product_width_cm, effective.product_width_cm, warehouseSpec.product_width_cm, ali1688Spec.product_width_cm, pvs.product_width_cm) AS productWidthCm,",
            "       COALESCE(official.product_height_cm, effective.product_height_cm, warehouseSpec.product_height_cm, ali1688Spec.product_height_cm, pvs.product_height_cm) AS productHeightCm,",
            "       COALESCE(official.product_weight_g, effective.product_weight_g, warehouseSpec.product_weight_g, ali1688Spec.product_weight_g, pvs.product_weight_g) AS productWeightG,",
            "       COALESCE(official.carton_length_cm, effective.carton_length_cm, warehouseSpec.carton_length_cm, ali1688Spec.carton_length_cm, pvs.carton_length_cm) AS cartonLengthCm,",
            "       COALESCE(official.carton_width_cm, effective.carton_width_cm, warehouseSpec.carton_width_cm, ali1688Spec.carton_width_cm, pvs.carton_width_cm) AS cartonWidthCm,",
            "       COALESCE(official.carton_height_cm, effective.carton_height_cm, warehouseSpec.carton_height_cm, ali1688Spec.carton_height_cm, pvs.carton_height_cm) AS cartonHeightCm,",
            "       COALESCE(official.carton_weight_kg, effective.carton_weight_kg, warehouseSpec.carton_weight_kg, ali1688Spec.carton_weight_kg, pvs.carton_weight_kg) AS cartonWeightKg,",
            "       COALESCE(official.carton_quantity, effective.carton_quantity, warehouseSpec.carton_quantity, ali1688Spec.carton_quantity, pvs.carton_quantity) AS cartonQuantity,",
            "       COALESCE(NULLIF(official.storage_type_code, ''), 'standard') AS storageTypeCode,",
            "       COALESCE(pvlp.profile_status, 'needs_review') AS logisticsProfileStatus,",
            "       COALESCE(pvlp.battery_electric_type, 'unknown') AS batteryElectricType,",
            "       COALESCE(pvlp.magnetic_type, 'unknown') AS magneticType,",
            "       COALESCE(pvlp.liquid_type, 'unknown') AS liquidType,",
            "       COALESCE(pvlp.powder_type, 'unknown') AS powderType,",
            "       COALESCE(pvlp.wooden_material_type, 'unknown') AS woodenMaterialType,",
            "       COALESCE(pvlp.blade_weapon_type, 'unknown') AS bladeWeaponType,",
            "       CASE WHEN pvlp.manual_confirm_required IS NULL THEN 1",
            "            WHEN pvlp.manual_confirm_required = b'1' THEN 1 ELSE 0 END AS manualConfirmRequired",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id AND pso.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs ON pvs.variant_id = pv.id AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_logistics_profile pvlp ON pvlp.variant_id = pv.id AND pvlp.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source effective",
            "  ON effective.id = pvs.effective_source_id AND effective.variant_id = pv.id AND effective.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source official",
            "  ON official.variant_id = pv.id AND official.source_type = 'noon_official' AND official.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source warehouseSpec",
            "  ON warehouseSpec.variant_id = pv.id AND warehouseSpec.source_type = 'warehouse' AND warehouseSpec.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source ali1688Spec",
            "  ON ali1688Spec.variant_id = pv.id AND ali1688Spec.source_type = 'ali1688' AND ali1688Spec.is_deleted = b'0'",
            "LEFT JOIN product_image_asset imageAsset",
            "  ON imageAsset.product_master_id = pm.id",
            " AND imageAsset.source_type IN ('noon-cover', 'noon')",
            " AND imageAsset.asset_status = 'synced'",
            " AND imageAsset.is_deleted = b'0'",
            " AND imageAsset.id = (",
            "   SELECT MIN(pia.id)",
            "   FROM product_image_asset pia",
            "   WHERE pia.product_master_id = pm.id",
            "     AND pia.source_type IN ('noon-cover', 'noon')",
            "     AND pia.asset_status = 'synced'",
            "     AND pia.is_deleted = b'0'",
            " )",
            "WHERE lss.is_deleted = b'0'",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  AND COALESCE(NULLIF(pv.child_sku, ''), pm.sku_parent) IS NOT NULL",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (pm.sku_parent LIKE #{keywordLike}",
            "       OR pv.partner_sku LIKE #{keywordLike}",
            "       OR pv.child_sku LIKE #{keywordLike}",
            "       OR pso.psku_code LIKE #{keywordLike}",
            "       OR official.noon_partner_psku_code LIKE #{keywordLike}",
            "       OR pm.title_cache LIKE #{keywordLike}",
            "       OR pm.title_cn_cache LIKE #{keywordLike})",
            "</if>",
            "<if test='(partnerSkus != null and partnerSkus.size() > 0) or (variantIds != null and variantIds.size() > 0)'>",
            "  AND (",
            "    1 = 0",
            "    <if test='partnerSkus != null and partnerSkus.size() > 0'>",
            "      OR UPPER(COALESCE(NULLIF(pm.partner_sku, ''), pv.partner_sku)) IN",
            "      <foreach item='partnerSku' collection='partnerSkus' open='(' separator=',' close=')'>",
            "        UPPER(#{partnerSku})",
            "      </foreach>",
            "    </if>",
            "    <if test='variantIds != null and variantIds.size() > 0'>",
            "      OR pv.id IN",
            "      <foreach item='variantId' collection='variantIds' open='(' separator=',' close=')'>",
            "        #{variantId}",
            "      </foreach>",
            "    </if>",
            "  )",
            "</if>",
            "ORDER BY pm.gmt_updated DESC, pv.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductCandidateRecord> listProductCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("variantIds") Collection<Long> variantIds,
            @Param("partnerSkus") Collection<String> partnerSkus,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT b.id, 'IN_TRANSIT_BATCH' AS sourceKind,",
            "       COALESCE(NULLIF(b.batch_reference_no, ''), NULLIF(b.tracking_no, ''), NULLIF(b.external_shipment_no, ''), CAST(b.id AS CHAR)) AS batchNo,",
            "       b.tracking_no AS trackingNo, b.external_shipment_no AS externalShipmentNo,",
            "       COALESCE(f.forwarder_name, b.raw_forwarder_name) AS forwarderName,",
            "       b.transport_mode AS transportMode, b.batch_status AS status, b.latest_node_status AS latestNodeStatus,",
            "       NULL AS selectedOptionId,",
            "       COALESCE(SUM(GREATEST(COALESCE(line.shipped_quantity, 0), 0)), 0) AS totalQuantity,",
            "       COALESCE(SUM(GREATEST(COALESCE(line.shipped_quantity, 0), 0)), 0) AS storeSiteQuantity,",
            "       COALESCE(SUM(LEAST(GREATEST(COALESCE(linked.linkedQuantity, 0), 0), GREATEST(COALESCE(line.shipped_quantity, 0), 0))), 0) AS linkedQuantity,",
            "       COALESCE(SUM(GREATEST(GREATEST(COALESCE(line.shipped_quantity, 0), 0) - GREATEST(COALESCE(linked.scheduledAppointmentQuantity, 0), 0), 0)), 0) AS remainingQuantity,",
            "       COALESCE(SUM(LEAST(GREATEST(COALESCE(linked.scheduledAppointmentQuantity, 0), 0), GREATEST(COALESCE(line.shipped_quantity, 0), 0))), 0) AS scheduledAppointmentQuantity,",
            "       CASE WHEN COALESCE(SUM(GREATEST(COALESCE(linked.scheduledAppointmentQuantity, 0), 0)), 0) &gt; 0 THEN 1 ELSE 0 END AS alreadyAppointed,",
            "       CASE WHEN COALESCE(SUM(GREATEST(COALESCE(linked.linkedQuantity, 0), 0)), 0) &gt; 0 THEN 1 ELSE 0 END AS batchUsedByAsn,",
            "       CASE WHEN COALESCE(SUM(GREATEST(COALESCE(linked.scheduledAppointmentQuantity, 0), 0)), 0) &gt; 0 THEN '已约仓'",
            "            WHEN COALESCE(SUM(GREATEST(COALESCE(linked.linkedQuantity, 0), 0)), 0) &gt; 0 THEN '已建ASN'",
            "            ELSE '可约仓' END AS batchUsageLabel,",
            "       COUNT(DISTINCT COALESCE(NULLIF(line.psku, ''), NULLIF(line.sku, ''), NULLIF(line.msku, ''))) AS skuCount,",
            "       COALESCE(SUM(COALESCE(purchase.purchaseOrderCount, 0)), 0) AS purchaseOrderCount,",
            "       NULL AS storeSummaryJson, NULL AS siteSummaryJson, NULL AS transportSummaryJson,",
            "       DATE_FORMAT(b.gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM in_transit_batch b",
            "JOIN in_transit_goods_line line",
            "  ON line.batch_id = b.id",
            " AND line.owner_user_id = b.owner_user_id",
            " AND line.is_deleted = b'0'",
            "JOIN logical_store_site lss",
            "  ON UPPER(lss.store_code) = UPPER(#{storeCode})",
            " AND UPPER(lss.site) = UPPER(#{siteCode})",
            " AND lss.is_deleted = b'0'",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.owner_user_id = b.owner_user_id",
            " AND ls.is_deleted = b'0'",
            "JOIN product_site_offer pso",
            "  ON pso.site_id = lss.id",
            " AND pso.is_deleted = b'0'",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            " AND pv.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN in_transit_forwarder f",
            "  ON f.id = b.standard_forwarder_id",
            " AND f.owner_user_id = b.owner_user_id",
            " AND f.is_deleted = b'0'",
            "LEFT JOIN (",
            "  SELECT link.owner_user_id, link.in_transit_goods_line_id,",
            "         SUM(GREATEST(COALESCE(link.quantity, 0), 0)) AS linkedQuantity,",
            "         SUM(CASE WHEN EXISTS (",
            "           SELECT 1",
            "           FROM official_warehouse_appointment scheduledAppointment",
            "           WHERE scheduledAppointment.asn_id = link.asn_id",
            "             AND scheduledAppointment.owner_user_id = link.owner_user_id",
            "             AND scheduledAppointment.is_deleted = b'0'",
            "             AND scheduledAppointment.status = 'SCHEDULED'",
            "         ) THEN GREATEST(COALESCE(link.quantity, 0), 0) ELSE 0 END) AS scheduledAppointmentQuantity",
            "  FROM official_warehouse_asn_shipping_batch_link link",
            "  JOIN official_warehouse_asn linkedAsn",
            "    ON linkedAsn.id = link.asn_id",
            "   AND linkedAsn.owner_user_id = link.owner_user_id",
            "   AND linkedAsn.is_deleted = b'0'",
            "   AND UPPER(COALESCE(linkedAsn.status, '')) NOT IN ('FAILED', 'CANCELED', 'CANCELLED')",
            "  WHERE link.is_deleted = b'0'",
            "    AND link.in_transit_goods_line_id IS NOT NULL",
            "  GROUP BY link.owner_user_id, link.in_transit_goods_line_id",
            ") linked",
            "  ON linked.owner_user_id = b.owner_user_id",
            " AND linked.in_transit_goods_line_id = line.id",
            "LEFT JOIN (",
            "  SELECT owner_user_id, in_transit_goods_line_id, COUNT(DISTINCT source_id) AS purchaseOrderCount",
            "  FROM procurement_logistics_shipment_allocation",
            "  WHERE is_deleted = b'0'",
            "    AND confirmation_status = 'CONFIRMED'",
            "  GROUP BY owner_user_id, in_transit_goods_line_id",
            ") purchase",
            "  ON purchase.owner_user_id = b.owner_user_id",
            " AND purchase.in_transit_goods_line_id = line.id",
            "WHERE b.owner_user_id = #{ownerUserId}",
            "  AND b.is_deleted = b'0'",
            "  AND (",
            "    (b.target_site_code IS NOT NULL AND b.target_site_code &lt;&gt; '' AND UPPER(b.target_site_code) = UPPER(#{siteCode}))",
            "    OR (UPPER(#{siteCode}) = 'SA' AND (",
            "      UPPER(COALESCE(b.target_store_code, '')) LIKE 'RUH%'",
            "      OR UPPER(COALESCE(b.target_store_code, '')) LIKE 'JED%'",
            "    ))",
            "    OR (UPPER(#{siteCode}) = 'AE' AND (",
            "      UPPER(COALESCE(b.target_store_code, '')) LIKE 'DB%'",
            "      OR UPPER(COALESCE(b.target_store_code, '')) LIKE 'DXB%'",
            "      OR UPPER(COALESCE(b.target_store_code, '')) LIKE 'AUH%'",
            "    ))",
            "  )",
            "  AND (",
            "    (UPPER(line.store_code) = UPPER(#{storeCode}) AND UPPER(line.site_code) = UPPER(#{siteCode}))",
            "    OR ((line.store_code IS NULL OR line.store_code = '')",
            "        AND (line.site_code IS NULL OR line.site_code = '' OR UPPER(line.site_code) = UPPER(#{siteCode})))",
            "  )",
            "  AND (",
            "    (line.psku IS NOT NULL AND line.psku &lt;&gt; '' AND (",
            "      UPPER(line.psku) = UPPER(COALESCE(pso.psku_code, ''))",
            "      OR UPPER(line.psku) = UPPER(COALESCE(pv.partner_sku, ''))",
            "      OR UPPER(line.psku) = UPPER(COALESCE(pv.child_sku, ''))",
            "      OR UPPER(line.psku) = UPPER(COALESCE(pm.sku_parent, ''))",
            "    ))",
            "    OR (line.sku IS NOT NULL AND line.sku &lt;&gt; '' AND (",
            "      UPPER(line.sku) = UPPER(COALESCE(pso.psku_code, ''))",
            "      OR UPPER(line.sku) = UPPER(COALESCE(pv.partner_sku, ''))",
            "      OR UPPER(line.sku) = UPPER(COALESCE(pv.child_sku, ''))",
            "      OR UPPER(line.sku) = UPPER(COALESCE(pm.sku_parent, ''))",
            "    ))",
            "    OR (line.msku IS NOT NULL AND line.msku &lt;&gt; '' AND (",
            "      UPPER(line.msku) = UPPER(COALESCE(pso.psku_code, ''))",
            "      OR UPPER(line.msku) = UPPER(COALESCE(pv.partner_sku, ''))",
            "      OR UPPER(line.msku) = UPPER(COALESCE(pv.child_sku, ''))",
            "      OR UPPER(line.msku) = UPPER(COALESCE(pm.sku_parent, ''))",
            "    ))",
            "  )",
            "  AND (",
            "    b.batch_status IN ('shipped', 'in_transit', 'customs_clearance', 'delivering', 'warehouse_received')",
            "    OR b.latest_node_status IN ('departed_origin', 'in_transit', 'arrived_port', 'customs_clearance', 'customs_released', 'delivering', 'warehouse_received')",
            "  )",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (b.batch_reference_no LIKE #{keywordLike}",
            "       OR b.tracking_no LIKE #{keywordLike}",
            "       OR b.external_shipment_no LIKE #{keywordLike}",
            "       OR b.raw_forwarder_name LIKE #{keywordLike}",
            "       OR f.forwarder_name LIKE #{keywordLike})",
            "</if>",
            "GROUP BY b.id, b.batch_reference_no, b.tracking_no, b.external_shipment_no, f.forwarder_name, b.raw_forwarder_name,",
            "         b.transport_mode, b.batch_status, b.latest_node_status, b.gmt_updated",
            "ORDER BY batchUsedByAsn ASC, b.gmt_updated DESC, b.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ShippingBatchCandidateRecord> listShippingBatchCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT NULL AS shippingBatchId,",
            "       COALESCE(NULLIF(b.batch_reference_no, ''), NULLIF(b.tracking_no, ''), NULLIF(b.external_shipment_no, ''), CAST(b.id AS CHAR)) AS shippingBatchNo,",
            "       b.batch_status AS status, NULL AS selectedOptionId, NULL AS shippingBatchSourceId,",
            "       b.id AS inTransitBatchId, b.batch_reference_no AS batchReferenceNo, b.tracking_no AS trackingNo,",
            "       b.external_shipment_no AS externalShipmentNo, COALESCE(f.forwarder_name, b.raw_forwarder_name) AS forwarderName,",
            "       b.transport_mode AS transportMode, b.latest_node_status AS latestNodeStatus, line.id AS inTransitGoodsLineId,",
            "       NULL AS fulfillmentBalanceId, COALESCE(NULLIF(line.store_code, ''), #{storeCode}) AS sourceStoreCode,",
            "       ls.project_name AS sourceStoreName,",
            "       purchase.purchaseBatchId AS purchaseOrderId, purchase.sourceId AS purchaseOrderNo,",
            "       NULL AS purchaseOrderTitle, NULL AS purchaseOrderItemId, NULL AS purchaseOrderItemSiteId,",
            "       pm.id AS productMasterId, pv.id AS productVariantId, COALESCE(NULLIF(pm.partner_sku, ''), pv.partner_sku) AS partnerSku, pm.sku_parent AS skuParent,",
            "       COALESCE(pm.title_cn_cache, pm.title_cache, pv.partner_sku, pm.sku_parent) AS titleCache,",
            "       pm.cover_image_url AS imageUrlCache, COALESCE(NULLIF(line.site_code, ''), #{siteCode}) AS siteCode,",
            "       b.transport_mode AS plannedTransportMode,",
            "       'FBN' AS fulfillmentType, COALESCE(f.forwarder_name, b.raw_forwarder_name) AS sourcePartyName,",
            "       GREATEST(GREATEST(COALESCE(line.shipped_quantity, 0), 0) - GREATEST(COALESCE(linked.scheduledAppointmentQuantity, 0), 0), 0) AS quantity",
            "FROM in_transit_batch b",
            "JOIN in_transit_goods_line line",
            "  ON line.batch_id = b.id",
            " AND line.owner_user_id = b.owner_user_id",
            " AND line.is_deleted = b'0'",
            "JOIN logical_store_site lss",
            "  ON UPPER(lss.store_code) = UPPER(#{storeCode})",
            " AND UPPER(lss.site) = UPPER(#{siteCode})",
            " AND lss.is_deleted = b'0'",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.owner_user_id = b.owner_user_id",
            " AND ls.is_deleted = b'0'",
            "JOIN product_site_offer pso",
            "  ON pso.site_id = lss.id",
            " AND pso.is_deleted = b'0'",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            " AND pv.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN in_transit_forwarder f",
            "  ON f.id = b.standard_forwarder_id",
            " AND f.owner_user_id = b.owner_user_id",
            " AND f.is_deleted = b'0'",
            "LEFT JOIN (",
            "  SELECT link.owner_user_id, link.in_transit_goods_line_id,",
            "         SUM(GREATEST(COALESCE(link.quantity, 0), 0)) AS linkedQuantity,",
            "         SUM(CASE WHEN EXISTS (",
            "           SELECT 1",
            "           FROM official_warehouse_appointment scheduledAppointment",
            "           WHERE scheduledAppointment.asn_id = link.asn_id",
            "             AND scheduledAppointment.owner_user_id = link.owner_user_id",
            "             AND scheduledAppointment.is_deleted = b'0'",
            "             AND scheduledAppointment.status = 'SCHEDULED'",
            "         ) THEN GREATEST(COALESCE(link.quantity, 0), 0) ELSE 0 END) AS scheduledAppointmentQuantity",
            "  FROM official_warehouse_asn_shipping_batch_link link",
            "  JOIN official_warehouse_asn linkedAsn",
            "    ON linkedAsn.id = link.asn_id",
            "   AND linkedAsn.owner_user_id = link.owner_user_id",
            "   AND linkedAsn.is_deleted = b'0'",
            "   AND UPPER(COALESCE(linkedAsn.status, '')) NOT IN ('FAILED', 'CANCELED', 'CANCELLED')",
            "  WHERE link.is_deleted = b'0'",
            "    AND link.in_transit_goods_line_id IS NOT NULL",
            "  GROUP BY link.owner_user_id, link.in_transit_goods_line_id",
            ") linked",
            "  ON linked.owner_user_id = b.owner_user_id",
            " AND linked.in_transit_goods_line_id = line.id",
            "LEFT JOIN (",
            "  SELECT owner_user_id, in_transit_goods_line_id,",
            "         MIN(purchase_batch_id) AS purchaseBatchId, MIN(source_id) AS sourceId",
            "  FROM procurement_logistics_shipment_allocation",
            "  WHERE is_deleted = b'0'",
            "    AND confirmation_status = 'CONFIRMED'",
            "  GROUP BY owner_user_id, in_transit_goods_line_id",
            ") purchase",
            "  ON purchase.owner_user_id = b.owner_user_id",
            " AND purchase.in_transit_goods_line_id = line.id",
            "WHERE b.owner_user_id = #{ownerUserId}",
            "  AND b.is_deleted = b'0'",
            "  AND (",
            "    (b.target_site_code IS NOT NULL AND b.target_site_code &lt;&gt; '' AND UPPER(b.target_site_code) = UPPER(#{siteCode}))",
            "    OR (UPPER(#{siteCode}) = 'SA' AND (",
            "      UPPER(COALESCE(b.target_store_code, '')) LIKE 'RUH%'",
            "      OR UPPER(COALESCE(b.target_store_code, '')) LIKE 'JED%'",
            "    ))",
            "    OR (UPPER(#{siteCode}) = 'AE' AND (",
            "      UPPER(COALESCE(b.target_store_code, '')) LIKE 'DB%'",
            "      OR UPPER(COALESCE(b.target_store_code, '')) LIKE 'DXB%'",
            "      OR UPPER(COALESCE(b.target_store_code, '')) LIKE 'AUH%'",
            "    ))",
            "  )",
            "  AND (",
            "    (UPPER(line.store_code) = UPPER(#{storeCode}) AND UPPER(line.site_code) = UPPER(#{siteCode}))",
            "    OR ((line.store_code IS NULL OR line.store_code = '')",
            "        AND (line.site_code IS NULL OR line.site_code = '' OR UPPER(line.site_code) = UPPER(#{siteCode})))",
            "  )",
            "  AND (",
            "    (line.psku IS NOT NULL AND line.psku &lt;&gt; '' AND (",
            "      UPPER(line.psku) = UPPER(COALESCE(pso.psku_code, ''))",
            "      OR UPPER(line.psku) = UPPER(COALESCE(pv.partner_sku, ''))",
            "      OR UPPER(line.psku) = UPPER(COALESCE(pv.child_sku, ''))",
            "      OR UPPER(line.psku) = UPPER(COALESCE(pm.sku_parent, ''))",
            "    ))",
            "    OR (line.sku IS NOT NULL AND line.sku &lt;&gt; '' AND (",
            "      UPPER(line.sku) = UPPER(COALESCE(pso.psku_code, ''))",
            "      OR UPPER(line.sku) = UPPER(COALESCE(pv.partner_sku, ''))",
            "      OR UPPER(line.sku) = UPPER(COALESCE(pv.child_sku, ''))",
            "      OR UPPER(line.sku) = UPPER(COALESCE(pm.sku_parent, ''))",
            "    ))",
            "    OR (line.msku IS NOT NULL AND line.msku &lt;&gt; '' AND (",
            "      UPPER(line.msku) = UPPER(COALESCE(pso.psku_code, ''))",
            "      OR UPPER(line.msku) = UPPER(COALESCE(pv.partner_sku, ''))",
            "      OR UPPER(line.msku) = UPPER(COALESCE(pv.child_sku, ''))",
            "      OR UPPER(line.msku) = UPPER(COALESCE(pm.sku_parent, ''))",
            "    ))",
            "  )",
            "  AND (",
            "    b.batch_status IN ('shipped', 'in_transit', 'customs_clearance', 'delivering', 'warehouse_received')",
            "    OR b.latest_node_status IN ('departed_origin', 'in_transit', 'arrived_port', 'customs_clearance', 'customs_released', 'delivering', 'warehouse_received')",
            "  )",
            "<if test='batchIds != null and batchIds.size() > 0'>",
            "  AND b.id IN",
            "  <foreach item='batchId' collection='batchIds' open='(' separator=',' close=')'>",
            "    #{batchId}",
            "  </foreach>",
            "</if>",
            "<if test='(partnerSkus != null and partnerSkus.size() > 0) or (variantIds != null and variantIds.size() > 0)'>",
            "  AND (",
            "    1 = 0",
            "    <if test='partnerSkus != null and partnerSkus.size() > 0'>",
            "      OR UPPER(COALESCE(NULLIF(pm.partner_sku, ''), pv.partner_sku)) IN",
            "      <foreach item='partnerSku' collection='partnerSkus' open='(' separator=',' close=')'>",
            "        UPPER(#{partnerSku})",
            "      </foreach>",
            "    </if>",
            "    <if test='variantIds != null and variantIds.size() > 0'>",
            "      OR pv.id IN",
            "      <foreach item='variantId' collection='variantIds' open='(' separator=',' close=')'>",
            "        #{variantId}",
            "      </foreach>",
            "    </if>",
            "  )",
            "</if>",
            "GROUP BY b.id, b.batch_reference_no, b.tracking_no, b.external_shipment_no, b.batch_status, b.transport_mode,",
            "         b.latest_node_status, f.forwarder_name, b.raw_forwarder_name, line.id, line.store_code, line.site_code,",
            "         ls.project_name, purchase.purchaseBatchId, purchase.sourceId, pm.id, pv.id, pv.partner_sku, pm.sku_parent, pm.title_cn_cache,",
            "         pm.title_cache, pm.cover_image_url, line.shipped_quantity, linked.scheduledAppointmentQuantity",
            "HAVING quantity &gt; 0",
            "ORDER BY b.gmt_updated DESC, b.id DESC, line.id ASC",
            "</script>"
    })
    List<ShippingBatchSourceAllocationRecord> listShippingBatchSourceAllocations(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("batchIds") Collection<Long> batchIds,
            @Param("variantIds") Collection<Long> variantIds,
            @Param("partnerSkus") Collection<String> partnerSkus
    );

    @Insert({
            "INSERT INTO official_warehouse_asn (",
            "id, owner_user_id, logical_store_id, store_code, store_name, site_code, project_code, partner_id,",
            "local_asn_no, source_type, status, product_count, total_quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.storeCode}, #{row.storeName}, #{row.siteCode},",
            "#{row.projectCode}, #{row.partnerId}, #{row.localAsnNo}, #{row.sourceType}, #{row.status},",
            "#{row.productCount}, #{row.totalQuantity}, b'0', #{row.operatorUserId}, #{row.operatorUserId}, NOW(), NOW())"
    })
    int insertAsn(@Param("row") AsnInsertRecord row);

    @Insert({
            "INSERT INTO official_warehouse_asn_line (",
            "id, asn_id, owner_user_id, store_code, site_code, product_master_id, product_variant_id, product_site_offer_id,",
            "sku_parent, partner_sku, child_sku, psku_code, noon_sku, title_cache, image_url_cache, qty,",
            "product_length_cm, product_width_cm, product_height_cm, product_weight_g, cubic_feet, storage_type_code,",
            "line_status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.asnId}, #{row.ownerUserId}, #{row.storeCode}, #{row.siteCode}, #{row.productMasterId},",
            "#{row.productVariantId}, #{row.productSiteOfferId}, #{row.skuParent}, #{row.partnerSku}, #{row.childSku},",
            "#{row.pskuCode}, #{row.noonSku}, #{row.titleCache}, #{row.imageUrlCache}, #{row.quantity},",
            "#{row.productLengthCm}, #{row.productWidthCm}, #{row.productHeightCm}, #{row.productWeightG},",
            "#{row.cubicFeet}, #{row.storageTypeCode}, #{row.lineStatus}, b'0', #{row.operatorUserId}, #{row.operatorUserId}, NOW(), NOW())"
    })
    int insertAsnLine(@Param("row") AsnLineInsertRecord row);

    @Insert({
            "INSERT INTO official_warehouse_asn_shipping_batch_link (",
            "id, asn_id, asn_line_id, owner_user_id, store_code, site_code, shipping_batch_id, shipping_batch_no,",
            "shipping_batch_source_id, in_transit_batch_id, batch_reference_no, tracking_no, external_shipment_no,",
            "forwarder_name, transport_mode, latest_node_status, in_transit_goods_line_id,",
            "fulfillment_balance_id, purchase_order_id, purchase_order_no, purchase_order_item_id,",
            "purchase_order_item_site_id, product_master_id, product_variant_id, partner_sku, psku_code, quantity,",
            "relation_status, relation_basis, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.asnId}, #{row.asnLineId}, #{row.ownerUserId}, #{row.storeCode}, #{row.siteCode},",
            "#{row.shippingBatchId}, #{row.shippingBatchNo}, #{row.shippingBatchSourceId},",
            "#{row.inTransitBatchId}, #{row.batchReferenceNo}, #{row.trackingNo}, #{row.externalShipmentNo},",
            "#{row.forwarderName}, #{row.transportMode}, #{row.latestNodeStatus}, #{row.inTransitGoodsLineId},",
            "#{row.fulfillmentBalanceId}, #{row.purchaseOrderId}, #{row.purchaseOrderNo}, #{row.purchaseOrderItemId}, #{row.purchaseOrderItemSiteId},",
            "#{row.productMasterId}, #{row.productVariantId}, #{row.partnerSku}, #{row.pskuCode}, #{row.quantity},",
            "#{row.relationStatus}, #{row.relationBasis}, b'0', #{row.operatorUserId}, #{row.operatorUserId}, NOW(), NOW())"
    })
    int insertAsnShippingBatchLink(@Param("row") AsnShippingBatchLinkInsertRecord row);

    @Update({
            "UPDATE official_warehouse_asn",
            "SET project_code = #{projectCode}, partner_id = #{partnerId},",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int updateAsnBinding(
            @Param("asnId") Long asnId,
            @Param("projectCode") String projectCode,
            @Param("partnerId") String partnerId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET status = 'ASN_CREATED', noon_asn_nr = #{asnNr}, noon_partner_asn_id = #{partnerAsnId},",
            "    noon_total_qty = #{totalQty}, noon_asn_status = #{noonAsnStatus}, submitted_at = NOW(),",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int markAsnCreated(
            @Param("asnId") Long asnId,
            @Param("asnNr") String asnNr,
            @Param("partnerAsnId") Long partnerAsnId,
            @Param("totalQty") Integer totalQty,
            @Param("noonAsnStatus") String noonAsnStatus,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET status = 'ROUTED', routing_response_json = #{routingResponseJson}, routing_is_transfer = #{routingIsTransfer},",
            "    selected_warehouse_partner_code = #{selectedWarehousePartnerCode}, selected_warehouse_code = #{selectedWarehouseCode},",
            "    selected_warehouse_name = #{selectedWarehouseName}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int markRouted(
            @Param("asnId") Long asnId,
            @Param("routingResponseJson") String routingResponseJson,
            @Param("routingIsTransfer") Boolean routingIsTransfer,
            @Param("selectedWarehousePartnerCode") String selectedWarehousePartnerCode,
            @Param("selectedWarehouseCode") String selectedWarehouseCode,
            @Param("selectedWarehouseName") String selectedWarehouseName,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET routing_response_json = #{routingResponseJson}, routing_is_transfer = #{routingIsTransfer},",
            "    selected_warehouse_partner_code = #{selectedWarehousePartnerCode}, selected_warehouse_code = #{selectedWarehouseCode},",
            "    selected_warehouse_name = #{selectedWarehouseName}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int updateAsnRoutingSnapshot(
            @Param("asnId") Long asnId,
            @Param("routingResponseJson") String routingResponseJson,
            @Param("routingIsTransfer") Boolean routingIsTransfer,
            @Param("selectedWarehousePartnerCode") String selectedWarehousePartnerCode,
            @Param("selectedWarehouseCode") String selectedWarehouseCode,
            @Param("selectedWarehouseName") String selectedWarehouseName,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET status = 'LINES_CREATED', finished_at = NOW(), error_stage = NULL, failure_type = NULL, error_message = NULL,",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int markLinesCreated(
            @Param("asnId") Long asnId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN official_warehouse_asn_line line",
            "  ON line.asn_id = #{asnId}",
            " AND line.is_deleted = b'0'",
            " AND line.line_status NOT IN ('PENDING', 'FAILED')",
            " AND line.partner_sku IS NOT NULL",
            " AND TRIM(line.partner_sku) <> ''",
            "JOIN official_warehouse_asn asn",
            "  ON asn.id = line.asn_id",
            " AND asn.owner_user_id = line.owner_user_id",
            " AND asn.is_deleted = b'0'",
            " AND asn.status NOT IN ('DRAFT', 'FAILED')",
            "JOIN logical_store_site lss",
            "  ON lss.is_deleted = b'0'",
            " AND CONVERT(UPPER(TRIM(lss.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "     = CONVERT(UPPER(TRIM(line.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.owner_user_id = line.owner_user_id",
            " AND ls.is_deleted = b'0'",
            "SET pso.logistics_has_history = b'1',",
            "    pso.logistics_first_flow_at = CASE",
            "        WHEN pso.logistics_first_flow_at IS NULL THEN COALESCE(asn.submitted_at, line.gmt_create, asn.gmt_create, NOW())",
            "        WHEN COALESCE(asn.submitted_at, line.gmt_create, asn.gmt_create) IS NULL THEN pso.logistics_first_flow_at",
            "        WHEN pso.logistics_first_flow_at > COALESCE(asn.submitted_at, line.gmt_create, asn.gmt_create) THEN COALESCE(asn.submitted_at, line.gmt_create, asn.gmt_create)",
            "        ELSE pso.logistics_first_flow_at",
            "    END,",
            "    pso.logistics_last_flow_at = CASE",
            "        WHEN pso.logistics_last_flow_at IS NULL THEN COALESCE(asn.finished_at, line.gmt_updated, asn.gmt_updated, NOW())",
            "        WHEN COALESCE(asn.finished_at, line.gmt_updated, asn.gmt_updated) IS NULL THEN pso.logistics_last_flow_at",
            "        WHEN pso.logistics_last_flow_at < COALESCE(asn.finished_at, line.gmt_updated, asn.gmt_updated) THEN COALESCE(asn.finished_at, line.gmt_updated, asn.gmt_updated)",
            "        ELSE pso.logistics_last_flow_at",
            "    END,",
            "    pso.logistics_history_source = 'OFFICIAL_WAREHOUSE_ASN',",
            "    pso.updated_by = #{operatorUserId},",
            "    pso.gmt_updated = NOW()",
            "WHERE pso.is_deleted = b'0'",
            "  AND pso.logical_store_id = lss.logical_store_id",
            "  AND pso.partner_sku IS NOT NULL",
            "  AND TRIM(pso.partner_sku) <> ''",
            "  AND (",
            "      CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "          = CONVERT(UPPER(TRIM(line.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      OR (",
            "          CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'",
            "          AND CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "              = CONVERT(UPPER(TRIM(line.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      )",
            "  )"
    })
    int markProductSiteOfferLogisticsHistoryByAsn(
            @Param("asnId") Long asnId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET noon_asn_status = #{noonAsnStatus}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int updateAsnNoonStatus(
            @Param("asnId") Long asnId,
            @Param("noonAsnStatus") String noonAsnStatus,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn_line",
            "SET line_status = 'CREATED', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE asn_id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int markAllLinesCreated(
            @Param("asnId") Long asnId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn_line",
            "SET noon_partner_asn_line_id = #{partnerAsnLineId}, noon_id_cluster = #{idCluster},",
            "    noon_id_storage_type = #{idStorageType}, noon_cluster_code = #{clusterCode}, noon_asn_status = #{asnStatus},",
            "    noon_country_code = #{countryCode}, is_labeled = #{labeled}, is_repl_tool_asn = #{replToolAsn},",
            "    line_status = 'CREATED', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE asn_id = #{asnId}",
            "  AND psku_code = #{pskuCode}",
            "  AND noon_sku = #{noonSku}",
            "  AND is_deleted = b'0'"
    })
    int updateLineFromNoon(
            @Param("asnId") Long asnId,
            @Param("pskuCode") String pskuCode,
            @Param("noonSku") String noonSku,
            @Param("partnerAsnLineId") Long partnerAsnLineId,
            @Param("idCluster") Integer idCluster,
            @Param("idStorageType") Integer idStorageType,
            @Param("clusterCode") String clusterCode,
            @Param("asnStatus") String asnStatus,
            @Param("countryCode") String countryCode,
            @Param("labeled") Boolean labeled,
            @Param("replToolAsn") Boolean replToolAsn,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET status = 'FAILED', error_stage = #{errorStage}, failure_type = #{failureType}, error_message = #{errorMessage},",
            "    finished_at = NOW(), updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int markAsnFailed(
            @Param("asnId") Long asnId,
            @Param("errorStage") String errorStage,
            @Param("failureType") String failureType,
            @Param("errorMessage") String errorMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn_line",
            "SET line_status = 'FAILED', error_message = #{errorMessage}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE asn_id = #{asnId}",
            "  AND is_deleted = b'0'",
            "  AND line_status != 'CREATED'"
    })
    int markPendingLinesFailed(
            @Param("asnId") Long asnId,
            @Param("errorMessage") String errorMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn_shipping_batch_link",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE asn_id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteAsnShippingBatchLinks(
            @Param("asnId") Long asnId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn_line",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE asn_id = #{asnId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteAsnLines(
            @Param("asnId") Long asnId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{asnId}",
            "  AND is_deleted = b'0'",
            "  AND (noon_asn_nr IS NULL OR TRIM(noon_asn_nr) = '')"
    })
    int softDeletePreSubmitAsn(
            @Param("asnId") Long asnId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, store_code AS storeCode,",
            "       store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, source_type AS sourceType, status, noon_asn_nr AS noonAsnNr,",
            "       noon_partner_asn_id AS noonPartnerAsnId, noon_total_qty AS noonTotalQty, noon_asn_status AS noonAsnStatus, noon_updated_at AS noonUpdatedAt,",
            "       routing_response_json AS routingResponseJson, routing_is_transfer AS routingIsTransfer,",
            "       selected_warehouse_partner_code AS selectedWarehousePartnerCode, selected_warehouse_code AS selectedWarehouseCode,",
            "       selected_warehouse_name AS selectedWarehouseName, product_count AS productCount, total_quantity AS totalQuantity,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') AS submittedAt,",
            "       DATE_FORMAT(finished_at, '%Y-%m-%d %H:%i:%s') AS finishedAt,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt,",
            "       DATE_FORMAT(COALESCE(noon_updated_at, gmt_updated), '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_asn",
            "WHERE is_deleted = b'0'",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND NOT (status = 'FAILED' AND (noon_asn_nr IS NULL OR TRIM(noon_asn_nr) = ''))",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(store_code) IN",
            "  <foreach item='storeCode' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCode})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (local_asn_no LIKE #{keywordLike} OR noon_asn_nr LIKE #{keywordLike} OR store_code LIKE #{keywordLike})",
            "</if>",
            "ORDER BY COALESCE(noon_updated_at, gmt_updated) DESC, id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<AsnRecord> listAsns(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keywordLike") String keywordLike,
            @Param("limit") int limit
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, store_code AS storeCode,",
            "       store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, source_type AS sourceType, status, noon_asn_nr AS noonAsnNr,",
            "       noon_partner_asn_id AS noonPartnerAsnId, noon_total_qty AS noonTotalQty, noon_asn_status AS noonAsnStatus, noon_updated_at AS noonUpdatedAt,",
            "       routing_response_json AS routingResponseJson, routing_is_transfer AS routingIsTransfer,",
            "       selected_warehouse_partner_code AS selectedWarehousePartnerCode, selected_warehouse_code AS selectedWarehouseCode,",
            "       selected_warehouse_name AS selectedWarehouseName, product_count AS productCount, total_quantity AS totalQuantity,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') AS submittedAt,",
            "       DATE_FORMAT(finished_at, '%Y-%m-%d %H:%i:%s') AS finishedAt,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt,",
            "       DATE_FORMAT(COALESCE(noon_updated_at, gmt_updated), '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_asn",
            "WHERE id = #{asnId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    AsnRecord selectAsn(
            @Param("ownerUserId") Long ownerUserId,
            @Param("asnId") Long asnId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, store_code AS storeCode,",
            "       store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, source_type AS sourceType, status, noon_asn_nr AS noonAsnNr,",
            "       noon_partner_asn_id AS noonPartnerAsnId, noon_total_qty AS noonTotalQty, noon_asn_status AS noonAsnStatus, noon_updated_at AS noonUpdatedAt,",
            "       routing_response_json AS routingResponseJson, routing_is_transfer AS routingIsTransfer,",
            "       selected_warehouse_partner_code AS selectedWarehousePartnerCode, selected_warehouse_code AS selectedWarehouseCode,",
            "       selected_warehouse_name AS selectedWarehouseName, product_count AS productCount, total_quantity AS totalQuantity,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') AS submittedAt,",
            "       DATE_FORMAT(finished_at, '%Y-%m-%d %H:%i:%s') AS finishedAt,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt,",
            "       DATE_FORMAT(COALESCE(noon_updated_at, gmt_updated), '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_asn",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND UPPER(store_code) = UPPER(#{storeCode})",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND noon_asn_nr = #{noonAsnNr}",
            "  AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    AsnRecord selectAsnByNoonAsnNr(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("noonAsnNr") String noonAsnNr
    );

    @Update({
            "UPDATE official_warehouse_asn",
            "SET project_code = COALESCE(NULLIF(#{row.projectCode}, ''), project_code),",
            "    partner_id = COALESCE(NULLIF(#{row.partnerId}, ''), partner_id),",
            "    status = #{row.status},",
            "    noon_asn_nr = #{row.noonAsnNr},",
            "    noon_partner_asn_id = CASE WHEN #{row.noonPartnerAsnId} IS NULL THEN noon_partner_asn_id ELSE #{row.noonPartnerAsnId} END,",
            "    noon_total_qty = CASE WHEN #{row.noonTotalQty} IS NULL THEN noon_total_qty ELSE #{row.noonTotalQty} END,",
            "    noon_asn_status = #{row.noonAsnStatus},",
            "    noon_updated_at = CASE WHEN #{row.noonUpdatedAt} IS NULL THEN noon_updated_at ELSE #{row.noonUpdatedAt} END,",
            "    selected_warehouse_partner_code = COALESCE(NULLIF(#{row.warehouseToPartnerCode}, ''), selected_warehouse_partner_code),",
            "    selected_warehouse_code = COALESCE(NULLIF(#{row.warehouseToCode}, ''), selected_warehouse_code),",
            "    selected_warehouse_name = COALESCE(NULLIF(#{row.warehouseName}, ''), selected_warehouse_name),",
            "    total_quantity = CASE WHEN #{row.noonTotalQty} IS NULL THEN total_quantity ELSE #{row.noonTotalQty} END,",
            "    submitted_at = COALESCE(submitted_at, NOW()),",
            "    finished_at = CASE WHEN #{row.status} IN ('LINES_CREATED', 'FAILED') THEN COALESCE(finished_at, NOW()) ELSE finished_at END,",
            "    error_stage = CASE WHEN #{row.status} = 'FAILED' THEN 'NOON_ASN_LIST_SYNC' ELSE NULL END,",
            "    failure_type = CASE WHEN #{row.status} = 'FAILED' THEN #{row.failureType} ELSE NULL END,",
            "    error_message = CASE WHEN #{row.status} = 'FAILED' THEN #{row.errorMessage} ELSE NULL END,",
            "    updated_by = #{row.operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{row.id}",
            "  AND owner_user_id = #{row.ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int syncAsnFromNoonList(@Param("row") AsnNoonListSyncRecord row);

    @Select({
            "SELECT awl.id, awl.asn_id AS asnId, awl.owner_user_id AS ownerUserId, awl.store_code AS storeCode, awl.site_code AS siteCode,",
            "       awl.product_master_id AS productMasterId, awl.product_variant_id AS productVariantId, awl.product_site_offer_id AS productSiteOfferId,",
            "       awl.sku_parent AS skuParent, awl.partner_sku AS partnerSku, awl.child_sku AS childSku, awl.psku_code AS pskuCode,",
            "       awl.noon_sku AS noonSku, awl.title_cache AS titleCache, COALESCE(NULLIF(pm.title_cache, ''), awl.title_cache) AS titleEn,",
            "       pm.brand_cache AS brandCache, awl.image_url_cache AS imageUrlCache, awl.qty,",
            "       awl.product_length_cm AS productLengthCm, awl.product_width_cm AS productWidthCm, awl.product_height_cm AS productHeightCm,",
            "       awl.product_weight_g AS productWeightG, awl.cubic_feet AS cubicFeet, awl.storage_type_code AS storageTypeCode,",
            "       awl.noon_partner_asn_line_id AS noonPartnerAsnLineId, awl.noon_id_cluster AS noonIdCluster,",
            "       awl.noon_id_storage_type AS noonIdStorageType, awl.noon_cluster_code AS noonClusterCode, awl.noon_asn_status AS noonAsnStatus,",
            "       awl.noon_country_code AS noonCountryCode, awl.is_labeled AS labeled, awl.is_repl_tool_asn AS replToolAsn,",
            "       awl.line_status AS lineStatus, awl.error_message AS errorMessage",
            "FROM official_warehouse_asn_line awl",
            "LEFT JOIN product_master pm ON pm.id = awl.product_master_id AND pm.is_deleted = b'0'",
            "WHERE awl.asn_id = #{asnId}",
            "  AND awl.is_deleted = b'0'",
            "ORDER BY awl.id ASC"
    })
    List<AsnLineRecord> listAsnLines(@Param("asnId") Long asnId);

    @Select({
            "SELECT id, asn_id AS asnId, asn_line_id AS asnLineId, owner_user_id AS ownerUserId,",
            "       store_code AS storeCode, site_code AS siteCode, shipping_batch_id AS shippingBatchId,",
            "       shipping_batch_no AS shippingBatchNo, shipping_batch_source_id AS shippingBatchSourceId,",
            "       in_transit_batch_id AS inTransitBatchId, batch_reference_no AS batchReferenceNo,",
            "       tracking_no AS trackingNo, external_shipment_no AS externalShipmentNo,",
            "       forwarder_name AS forwarderName, transport_mode AS transportMode, latest_node_status AS latestNodeStatus,",
            "       in_transit_goods_line_id AS inTransitGoodsLineId,",
            "       fulfillment_balance_id AS fulfillmentBalanceId, purchase_order_id AS purchaseOrderId,",
            "       purchase_order_no AS purchaseOrderNo, purchase_order_item_id AS purchaseOrderItemId,",
            "       purchase_order_item_site_id AS purchaseOrderItemSiteId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, partner_sku AS partnerSku, psku_code AS pskuCode,",
            "       quantity, relation_status AS relationStatus, relation_basis AS relationBasis,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt",
            "FROM official_warehouse_asn_shipping_batch_link",
            "WHERE asn_id = #{asnId}",
            "  AND is_deleted = b'0'",
            "ORDER BY asn_line_id ASC, shipping_batch_id ASC, id ASC"
    })
    List<AsnShippingBatchLinkRecord> listAsnShippingBatchLinks(@Param("asnId") Long asnId);

    @Insert({
            "INSERT INTO official_warehouse_appointment (",
            "id, asn_id, owner_user_id, logical_store_id, store_code, store_name, site_code, project_code, partner_id,",
            "local_asn_no, noon_asn_nr, total_units, warehouse_from, warehouse_to_partner_code, warehouse_to_code,",
            "ap_start_date, ap_end_date, ap_time_range, is_available_today, status, gate, docks, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.asnId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.storeCode}, #{row.storeName}, #{row.siteCode},",
            "#{row.projectCode}, #{row.partnerId}, #{row.localAsnNo}, #{row.noonAsnNr}, #{row.totalUnits},",
            "#{row.warehouseFrom}, #{row.warehouseToPartnerCode}, #{row.warehouseToCode},",
            "#{row.apStartDate}, #{row.apEndDate}, #{row.apTimeRange}, #{row.availableToday}, #{row.status}, #{row.gate}, #{row.docks},",
            "b'0', #{row.operatorUserId}, #{row.operatorUserId}, NOW(), NOW())"
    })
    int insertAppointment(@Param("row") AppointmentInsertRecord row);

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET warehouse_from = #{row.warehouseFrom}, warehouse_to_partner_code = #{row.warehouseToPartnerCode},",
            "    warehouse_to_code = #{row.warehouseToCode}, ap_start_date = #{row.apStartDate}, ap_end_date = #{row.apEndDate},",
            "    ap_time_range = #{row.apTimeRange}, is_available_today = #{row.availableToday}, status = #{row.status},",
            "    appointment_date = NULL, appointment_slot_id = NULL, appointment_time = NULL, gate = NULL, docks = NULL,",
            "    next_attempt_at = NULL, ap_success_time = NULL, error_stage = NULL, failure_type = NULL, error_message = NULL,",
            "    updated_by = #{row.operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{row.id}",
            "  AND owner_user_id = #{row.ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateAppointmentRequest(@Param("row") AppointmentInsertRecord row);

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET warehouse_from = #{warehouseFrom}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateAppointmentWarehouseFrom(
            @Param("ownerUserId") Long ownerUserId,
            @Param("appointmentId") Long appointmentId,
            @Param("warehouseFrom") String warehouseFrom,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET gate = COALESCE(NULLIF(#{gate}, ''), gate),",
            "    docks = COALESCE(NULLIF(#{docks}, ''), docks),",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateAppointmentGateDocks(
            @Param("ownerUserId") Long ownerUserId,
            @Param("appointmentId") Long appointmentId,
            @Param("gate") String gate,
            @Param("docks") String docks,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = 'RUNNING', attempt_count = attempt_count + 1, last_attempt_at = NOW(), next_attempt_at = NULL,",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND is_deleted = b'0'",
            "  AND status IN ('PENDING', 'FAILED')"
    })
    int markAppointmentRunning(
            @Param("appointmentId") Long appointmentId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = 'RUNNING', attempt_count = attempt_count + 1, last_attempt_at = NOW(), next_attempt_at = NULL,",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND is_deleted = b'0'",
            "  AND status = 'PENDING'",
            "  AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())"
    })
    int claimDueAppointmentForRun(
            @Param("appointmentId") Long appointmentId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = 'SCHEDULED', appointment_date = #{appointmentDate}, appointment_slot_id = #{slotId},",
            "    appointment_time = #{appointmentTime}, ap_success_time = NOW(), next_attempt_at = NULL, attempt_count = 0,",
            "    error_stage = NULL, failure_type = NULL, error_message = NULL, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND is_deleted = b'0'"
    })
    int markAppointmentScheduled(
            @Param("appointmentId") Long appointmentId,
            @Param("appointmentDate") java.time.LocalDate appointmentDate,
            @Param("slotId") Integer slotId,
            @Param("appointmentTime") String appointmentTime,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = 'PENDING', next_attempt_at = DATE_ADD(NOW(), INTERVAL #{retrySeconds} SECOND),",
            "    error_stage = #{errorStage}, failure_type = #{failureType}, error_message = #{errorMessage},",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND is_deleted = b'0'"
    })
    int markAppointmentPendingRetry(
            @Param("appointmentId") Long appointmentId,
            @Param("retrySeconds") int retrySeconds,
            @Param("errorStage") String errorStage,
            @Param("failureType") String failureType,
            @Param("errorMessage") String errorMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = 'PENDING', next_attempt_at = NOW(),",
            "    error_stage = 'SCHEDULE', failure_type = 'NO_CAPACITY',",
            "    error_message = COALESCE(NULLIF(error_message, ''), '没有匹配的 Noon 可约仓日期或时段。'),",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE is_deleted = b'0'",
            "  AND status = 'RUNNING'",
            "  AND failure_type = 'NO_CAPACITY'",
            "  AND gmt_updated <= DATE_SUB(NOW(), INTERVAL #{staleMinutes} MINUTE)"
    })
    int markStaleNoCapacityAppointmentsPending(
            @Param("staleMinutes") int staleMinutes,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = 'FAILED', next_attempt_at = NULL, error_stage = #{errorStage}, failure_type = #{failureType},",
            "    error_message = #{errorMessage}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND is_deleted = b'0'"
    })
    int markAppointmentFailed(
            @Param("appointmentId") Long appointmentId,
            @Param("errorStage") String errorStage,
            @Param("failureType") String failureType,
            @Param("errorMessage") String errorMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = #{status}, appointment_date = #{appointmentDate}, appointment_slot_id = #{slotId},",
            "    appointment_time = #{appointmentTime},",
            "    ap_success_time = CASE WHEN #{status} = 'SCHEDULED' THEN NOW() ELSE NULL END,",
            "    next_attempt_at = NULL, error_stage = #{errorStage}, failure_type = #{failureType},",
            "    error_message = #{errorMessage}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int correctAppointment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("appointmentId") Long appointmentId,
            @Param("status") String status,
            @Param("appointmentDate") java.time.LocalDate appointmentDate,
            @Param("slotId") Integer slotId,
            @Param("appointmentTime") String appointmentTime,
            @Param("failureType") String failureType,
            @Param("errorStage") String errorStage,
            @Param("errorMessage") String errorMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE official_warehouse_appointment",
            "SET status = 'CANCELED', next_attempt_at = NULL, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{appointmentId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int cancelAppointment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("appointmentId") Long appointmentId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, asn_id AS asnId, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       store_code AS storeCode, store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, noon_asn_nr AS noonAsnNr, total_units AS totalUnits,",
            "       warehouse_from AS warehouseFrom, warehouse_to_partner_code AS warehouseToPartnerCode, warehouse_to_code AS warehouseToCode,",
            "       ap_start_date AS apStartDateValue, ap_end_date AS apEndDateValue,",
            "       DATE_FORMAT(ap_start_date, '%Y-%m-%d') AS apStartDate, DATE_FORMAT(ap_end_date, '%Y-%m-%d') AS apEndDate,",
            "       ap_time_range AS apTimeRange, is_available_today AS availableToday, status,",
            "       DATE_FORMAT(appointment_date, '%Y-%m-%d') AS appointmentDate, appointment_slot_id AS appointmentSlotId, appointment_time AS appointmentTime, gate, docks,",
            "       attempt_count AS attemptCount, DATE_FORMAT(last_attempt_at, '%Y-%m-%d %H:%i:%s') AS lastAttemptAt,",
            "       DATE_FORMAT(next_attempt_at, '%Y-%m-%d %H:%i:%s') AS nextAttemptAt, DATE_FORMAT(ap_success_time, '%Y-%m-%d %H:%i:%s') AS apSuccessTime,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_appointment",
            "WHERE id = #{appointmentId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    AppointmentRecord selectAppointment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("appointmentId") Long appointmentId
    );

    @Select({
            "SELECT id, asn_id AS asnId, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       store_code AS storeCode, store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, noon_asn_nr AS noonAsnNr, total_units AS totalUnits,",
            "       warehouse_from AS warehouseFrom, warehouse_to_partner_code AS warehouseToPartnerCode, warehouse_to_code AS warehouseToCode,",
            "       ap_start_date AS apStartDateValue, ap_end_date AS apEndDateValue,",
            "       DATE_FORMAT(ap_start_date, '%Y-%m-%d') AS apStartDate, DATE_FORMAT(ap_end_date, '%Y-%m-%d') AS apEndDate,",
            "       ap_time_range AS apTimeRange, is_available_today AS availableToday, status,",
            "       DATE_FORMAT(appointment_date, '%Y-%m-%d') AS appointmentDate, appointment_slot_id AS appointmentSlotId, appointment_time AS appointmentTime, gate, docks,",
            "       attempt_count AS attemptCount, DATE_FORMAT(last_attempt_at, '%Y-%m-%d %H:%i:%s') AS lastAttemptAt,",
            "       DATE_FORMAT(next_attempt_at, '%Y-%m-%d %H:%i:%s') AS nextAttemptAt, DATE_FORMAT(ap_success_time, '%Y-%m-%d %H:%i:%s') AS apSuccessTime,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_appointment",
            "WHERE asn_id = #{asnId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    AppointmentRecord selectLatestAppointmentByAsn(
            @Param("ownerUserId") Long ownerUserId,
            @Param("asnId") Long asnId
    );

    @Select({
            "<script>",
            "SELECT id, asn_id AS asnId, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       store_code AS storeCode, store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, noon_asn_nr AS noonAsnNr, total_units AS totalUnits,",
            "       warehouse_from AS warehouseFrom, warehouse_to_partner_code AS warehouseToPartnerCode, warehouse_to_code AS warehouseToCode,",
            "       ap_start_date AS apStartDateValue, ap_end_date AS apEndDateValue,",
            "       DATE_FORMAT(ap_start_date, '%Y-%m-%d') AS apStartDate, DATE_FORMAT(ap_end_date, '%Y-%m-%d') AS apEndDate,",
            "       ap_time_range AS apTimeRange, is_available_today AS availableToday, status,",
            "       DATE_FORMAT(appointment_date, '%Y-%m-%d') AS appointmentDate, appointment_slot_id AS appointmentSlotId, appointment_time AS appointmentTime, gate, docks,",
            "       attempt_count AS attemptCount, DATE_FORMAT(last_attempt_at, '%Y-%m-%d %H:%i:%s') AS lastAttemptAt,",
            "       DATE_FORMAT(next_attempt_at, '%Y-%m-%d %H:%i:%s') AS nextAttemptAt, DATE_FORMAT(ap_success_time, '%Y-%m-%d %H:%i:%s') AS apSuccessTime,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_appointment",
            "WHERE is_deleted = b'0'",
            "  AND owner_user_id = #{ownerUserId}",
            "<if test='storeCodes != null and storeCodes.size() > 0'>",
            "  AND UPPER(store_code) IN",
            "  <foreach item='storeCode' collection='storeCodes' open='(' separator=',' close=')'>",
            "    UPPER(#{storeCode})",
            "  </foreach>",
            "</if>",
            "<if test='storeCode != null and storeCode != \"\"'>",
            "  AND UPPER(store_code) = UPPER(#{storeCode})",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "  AND status = #{status}",
            "</if>",
            "<if test='keywordLike != null and keywordLike != \"\"'>",
            "  AND (local_asn_no LIKE #{keywordLike} OR noon_asn_nr LIKE #{keywordLike} OR warehouse_to_partner_code LIKE #{keywordLike})",
            "</if>",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<AppointmentRecord> listAppointments(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("status") String status,
            @Param("keywordLike") String keywordLike,
            @Param("limit") int limit
    );

    @Select({
            "SELECT id, asn_id AS asnId, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       store_code AS storeCode, store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, noon_asn_nr AS noonAsnNr, total_units AS totalUnits,",
            "       warehouse_from AS warehouseFrom, warehouse_to_partner_code AS warehouseToPartnerCode, warehouse_to_code AS warehouseToCode,",
            "       ap_start_date AS apStartDateValue, ap_end_date AS apEndDateValue,",
            "       DATE_FORMAT(ap_start_date, '%Y-%m-%d') AS apStartDate, DATE_FORMAT(ap_end_date, '%Y-%m-%d') AS apEndDate,",
            "       ap_time_range AS apTimeRange, is_available_today AS availableToday, status,",
            "       DATE_FORMAT(appointment_date, '%Y-%m-%d') AS appointmentDate, appointment_slot_id AS appointmentSlotId, appointment_time AS appointmentTime, gate, docks,",
            "       attempt_count AS attemptCount, DATE_FORMAT(last_attempt_at, '%Y-%m-%d %H:%i:%s') AS lastAttemptAt,",
            "       DATE_FORMAT(next_attempt_at, '%Y-%m-%d %H:%i:%s') AS nextAttemptAt, DATE_FORMAT(ap_success_time, '%Y-%m-%d %H:%i:%s') AS apSuccessTime,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_appointment",
            "WHERE is_deleted = b'0'",
            "  AND status = 'PENDING'",
            "  AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())",
            "ORDER BY COALESCE(next_attempt_at, gmt_updated) ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<AppointmentRecord> listDueAppointments(@Param("limit") int limit);
}
