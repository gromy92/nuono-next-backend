package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductArchiveRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductOfferRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderSeaRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsCostComponentInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsRecommendationInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688HistoryRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688PurchaseBatchRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreScopeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderSourcingRequirement;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface ProcurementPurchaseOrderMapper {

    String ORDER_SELECT = ""
            + "SELECT po.id, po.owner_user_id, po.logical_store_id, po.order_no, po.title, po.remark, "
            + "po.status, po.collection_status, po.progress_percent, po.site_codes_json, "
            + "po.project_code_cache, po.project_name_cache, po.anchor_store_code_cache, "
            + "po.item_count, po.total_quantity, po.collecting_item_count, po.abnormal_item_count, "
            + "po.created_by, po.updated_by, "
            + "DATE_FORMAT(po.gmt_create, '%Y-%m-%d %H:%i') AS created_at, "
            + "DATE_FORMAT(po.gmt_updated, '%Y-%m-%d %H:%i') AS updated_at "
            + "FROM procurement_purchase_order po ";

    String ITEM_SELECT = ""
            + "SELECT item.id, item.purchase_order_id, item.owner_user_id, item.logical_store_id, "
            + "item.product_master_id, item.product_variant_id, item.sku_parent, item.partner_sku, item.child_sku, "
            + "item.title_cache, item.image_url_cache, item.source_type, item.manual_selection_source_collection_id, "
            + "item.fulfillment_type, item.fulfillment_source_name, "
            + "pm.product_fulltype_cache AS productFulltypeCache, "
            + "item.sourcing_spec_text, item.sourcing_size_text, item.sourcing_color_text, "
            + "item.total_quantity, item.site_count, item.collection_status, item.progress_percent, "
            + "item.candidate_count, item.recommended_count, item.failure_code, item.failure_message, "
            + "item.latest_collection_link_id, DATE_FORMAT(item.last_collected_at, '%Y-%m-%d %H:%i') AS last_collected_at, "
            + "link.source_collection_id, source.collection_no AS source_collection_no, source.source_platform, "
            + "task.task_no AS ali_task_no, task.status AS ali_status, task.progress_percent AS ali_progress_percent, "
            + "task.candidate_count AS ali_candidate_count, task.recommended_count AS ali_recommended_count, "
            + "task.failure_code AS ali_failure_code, task.failure_message AS ali_failure_message, "
            + "DATE_FORMAT(task.finished_at, '%Y-%m-%d %H:%i') AS ali_finished_at "
            + "FROM procurement_purchase_order_item item "
            + "LEFT JOIN product_master pm "
            + "  ON pm.id = item.product_master_id "
            + " AND pm.is_deleted = b'0' "
            + "LEFT JOIN procurement_purchase_order_ali1688_collection link "
            + "  ON link.id = item.latest_collection_link_id "
            + " AND link.is_deleted = b'0' "
            + "LEFT JOIN product_selection_source_collection source "
            + "  ON source.id = link.source_collection_id "
            + " AND source.is_deleted = b'0' "
            + "LEFT JOIN product_selection_ali1688_collection_task task "
            + "  ON task.id = link.ali1688_task_id "
            + " AND task.is_deleted = b'0' ";

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("采购单 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextOrderId() {
        return nextId("procurement_purchase_order", 200000L);
    }

    default Long nextItemId() {
        return nextId("procurement_purchase_order_item", 210000L);
    }

    default Long nextItemSiteId() {
        return nextId("procurement_purchase_order_item_site", 220000L);
    }

    default Long nextCollectionLinkId() {
        return nextId("procurement_purchase_order_ali1688_collection", 230000L);
    }

    default Long nextOperationLogId() {
        return nextId("procurement_purchase_order_operation_log", 240000L);
    }

    default Long nextLogisticsPlanId() {
        return nextId("procurement_purchase_order_logistics_plan", 250000L);
    }

    default Long nextLogisticsRecommendationId() {
        return nextId("procurement_purchase_order_logistics_recommendation", 260000L);
    }

    default Long nextLogisticsCostComponentId() {
        return nextId("procurement_purchase_order_logistics_cost_component", 270000L);
    }

    @Select({
            "SELECT ls.owner_user_id, ls.id AS logical_store_id, ls.project_code, ls.project_name,",
            "       lss.store_code AS anchor_store_code, lss.site AS anchor_site",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND lss.store_code = #{storeCode}",
            "  AND lss.is_deleted = b'0'",
            "LIMIT 1"
    })
    StoreScopeRecord selectStoreScope(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT id AS site_id, site AS site_code, store_code",
            "FROM logical_store_site",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND is_deleted = b'0'",
            "ORDER BY is_reference_site DESC, id ASC"
    })
    List<StoreSiteRecord> listStoreSites(@Param("logicalStoreId") Long logicalStoreId);

    @Select({
            "<script>",
            "SELECT pm.id AS product_master_id, pv.id AS product_variant_id, pm.sku_parent, pv.partner_sku, pv.child_sku,",
            "       pv.size_en, pv.size_ar, pm.title_cache AS title, pm.cover_image_url AS image_url,",
            "       COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS product_length_cm,",
            "       COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS product_width_cm,",
            "       COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS product_height_cm,",
            "       COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS product_weight_g,",
            "       COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS carton_length_cm,",
            "       COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS carton_width_cm,",
            "       COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS carton_height_cm,",
            "       COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS carton_weight_kg,",
            "       COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS carton_quantity,",
            "       COALESCE(pvss.source_type, pvs.source_type) AS spec_source_type,",
            "       GROUP_CONCAT(DISTINCT lss.site ORDER BY lss.site SEPARATOR ',') AS available_site_codes_csv",
            "FROM product_master pm",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.is_deleted = b'0'",
            "JOIN logical_store_site lss ON lss.id = pso.site_id AND lss.logical_store_id = pm.logical_store_id AND lss.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs ON pvs.variant_id = pv.id AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source pvss ON pvss.id = pvs.effective_source_id AND pvss.variant_id = pv.id AND pvss.is_deleted = b'0'",
            "WHERE pm.logical_store_id = #{logicalStoreId}",
            "  AND pm.is_deleted = b'0'",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (pm.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.child_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pso.psku_code LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "GROUP BY pm.id, pv.id, pm.sku_parent, pv.partner_sku, pv.child_sku, pv.size_en, pv.size_ar,",
            "         pm.title_cache, pm.cover_image_url,",
            "         COALESCE(pvss.product_length_cm, pvs.product_length_cm),",
            "         COALESCE(pvss.product_width_cm, pvs.product_width_cm),",
            "         COALESCE(pvss.product_height_cm, pvs.product_height_cm),",
            "         COALESCE(pvss.product_weight_g, pvs.product_weight_g),",
            "         COALESCE(pvss.carton_length_cm, pvs.carton_length_cm),",
            "         COALESCE(pvss.carton_width_cm, pvs.carton_width_cm),",
            "         COALESCE(pvss.carton_height_cm, pvs.carton_height_cm),",
            "         COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg),",
            "         COALESCE(pvss.carton_quantity, pvs.carton_quantity),",
            "         COALESCE(pvss.source_type, pvs.source_type)",
            "ORDER BY pm.gmt_updated DESC, pv.variant_ix ASC, pv.id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductArchiveRecord> listProductOptions(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT pm.id AS product_master_id, pv.id AS product_variant_id, pm.sku_parent, pv.partner_sku, pv.child_sku,",
            "       pv.size_en, pv.size_ar, pm.title_cache AS title, pm.cover_image_url AS image_url,",
            "       COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS product_length_cm,",
            "       COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS product_width_cm,",
            "       COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS product_height_cm,",
            "       COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS product_weight_g,",
            "       COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS carton_length_cm,",
            "       COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS carton_width_cm,",
            "       COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS carton_height_cm,",
            "       COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS carton_weight_kg,",
            "       COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS carton_quantity,",
            "       COALESCE(pvss.source_type, pvs.source_type) AS spec_source_type,",
            "       GROUP_CONCAT(DISTINCT lss.site ORDER BY lss.site SEPARATOR ',') AS available_site_codes_csv",
            "FROM product_master pm",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.is_deleted = b'0'",
            "LEFT JOIN logical_store_site lss ON lss.id = pso.site_id AND lss.logical_store_id = pm.logical_store_id AND lss.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs ON pvs.variant_id = pv.id AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source pvss ON pvss.id = pvs.effective_source_id AND pvss.variant_id = pv.id AND pvss.is_deleted = b'0'",
            "WHERE pm.logical_store_id = #{logicalStoreId}",
            "  AND pm.is_deleted = b'0'",
            "  AND (pv.partner_sku = #{psku} OR pso.psku_code = #{psku})",
            "GROUP BY pm.id, pv.id, pm.sku_parent, pv.partner_sku, pv.child_sku, pv.size_en, pv.size_ar,",
            "         pm.title_cache, pm.cover_image_url,",
            "         COALESCE(pvss.product_length_cm, pvs.product_length_cm),",
            "         COALESCE(pvss.product_width_cm, pvs.product_width_cm),",
            "         COALESCE(pvss.product_height_cm, pvs.product_height_cm),",
            "         COALESCE(pvss.product_weight_g, pvs.product_weight_g),",
            "         COALESCE(pvss.carton_length_cm, pvs.carton_length_cm),",
            "         COALESCE(pvss.carton_width_cm, pvs.carton_width_cm),",
            "         COALESCE(pvss.carton_height_cm, pvs.carton_height_cm),",
            "         COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg),",
            "         COALESCE(pvss.carton_quantity, pvs.carton_quantity),",
            "         COALESCE(pvss.source_type, pvs.source_type)",
            "ORDER BY CASE WHEN pv.partner_sku = #{psku} THEN 0 ELSE 1 END, pv.id ASC",
            "LIMIT 2"
    })
    List<ProductArchiveRecord> listProductArchiveMatches(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("psku") String psku
    );

    @Select({
            "SELECT pm.id AS product_master_id, pv.id AS product_variant_id, pm.sku_parent, pv.partner_sku, pv.child_sku,",
            "       pv.size_en, pv.size_ar, pm.title_cache AS title, pm.cover_image_url AS image_url,",
            "       COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS product_length_cm,",
            "       COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS product_width_cm,",
            "       COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS product_height_cm,",
            "       COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS product_weight_g,",
            "       COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS carton_length_cm,",
            "       COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS carton_width_cm,",
            "       COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS carton_height_cm,",
            "       COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS carton_weight_kg,",
            "       COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS carton_quantity,",
            "       COALESCE(pvss.source_type, pvs.source_type) AS spec_source_type",
            "FROM product_master pm",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs ON pvs.variant_id = pv.id AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source pvss ON pvss.id = pvs.effective_source_id AND pvss.variant_id = pv.id AND pvss.is_deleted = b'0'",
            "WHERE pm.logical_store_id = #{logicalStoreId}",
            "  AND pm.is_deleted = b'0'",
            "  AND pv.id = #{variantId}",
            "LIMIT 1"
    })
    ProductArchiveRecord selectProductArchiveByVariant(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("variantId") Long variantId
    );

    @Select({
            "<script>",
            "SELECT",
            "  line.forwarder_code AS forwarderCode,",
            "  COALESCE(forwarder.name, line.forwarder_code) AS forwarderName,",
            "  line.service_code AS serviceCode,",
            "  line.service_name AS serviceName,",
            "  line.country AS country,",
            "  line.target_platform AS targetPlatform,",
            "  line.delivery_city AS deliveryCity,",
            "  line.destination_node AS destinationNode,",
            "  line.transport_mode AS transportMode,",
            "  line.delivery_scope AS deliveryScope,",
            "  line.transit_time_text AS transitTimeText,",
            "  line.transit_days_min AS transitDaysMin,",
            "  line.transit_days_max AS transitDaysMax,",
            "  COUNT(price.id) AS priceRuleCount,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.currency ORDER BY price.currency SEPARATOR ','), ',', 1) AS currency,",
            "  COALESCE(",
            "    MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END),",
            "    MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)",
            "  ) AS minUnitPrice,",
            "  CASE",
            "    WHEN MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END) IS NOT NULL THEN 'CBM'",
            "    WHEN MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END) IS NOT NULL THEN 'KG'",
            "    ELSE SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.billing_unit ORDER BY price.billing_unit SEPARATOR ','), ',', 1)",
            "  END AS billingUnit,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.billing_basis ORDER BY price.billing_basis SEPARATOR '；'), '；', 1) AS billingBasis,",
            "  MIN(price.min_billable_unit) AS minBillableUnit,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.min_billable_unit_type ORDER BY price.min_billable_unit_type SEPARATOR ','), ',', 1) AS minBillableUnitType,",
            "  MIN(price.min_charge) AS minCharge,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.volume_divisor END) AS volumeDivisor,",
            "  GROUP_CONCAT(DISTINCT price.cargo_category_name ORDER BY price.cargo_category_name SEPARATOR ',') AS cargoCategoryNamesCsv,",
            "  GROUP_CONCAT(DISTINCT price.price_status ORDER BY price.price_status SEPARATOR ',') AS priceStatusesCsv,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END) AS cbmMinUnitPrice,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END) AS kgMinUnitPrice",
            "FROM forwarder_quote_service_line line",
            "JOIN forwarder_quote_version version",
            "  ON version.id = line.quote_version_id",
            " AND version.status = 'PUBLISHED'",
            "JOIN forwarder",
            "  ON forwarder.id = version.forwarder_id",
            " AND forwarder.status = 'ACTIVE'",
            "LEFT JOIN forwarder_quote_base_price price",
            "  ON price.service_code = line.service_code",
            " AND price.quote_version_id = line.quote_version_id",
            "WHERE line.transport_mode = 'SEA'",
            "  AND line.active_for_mvp = b'1'",
            "<if test='countryNames != null and countryNames.size() > 0'>",
            "  AND line.country IN",
            "  <foreach collection='countryNames' item='countryName' open='(' separator=',' close=')'>",
            "    #{countryName}",
            "  </foreach>",
            "</if>",
            "GROUP BY line.forwarder_code, forwarder.name, line.service_code, line.service_name, line.country,",
            "         line.target_platform, line.delivery_city, line.destination_node, line.transport_mode,",
            "         line.delivery_scope, line.transit_time_text, line.transit_days_min, line.transit_days_max",
            "ORDER BY CASE WHEN COALESCE(MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END), MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)) IS NULL THEN 1 ELSE 0 END,",
            "         COALESCE(MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END), MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)),",
            "         forwarder.name, line.service_code",
            "</script>"
    })
    List<ForwarderSeaRecommendationRecord> listSeaForwarderRecommendationCandidates(
            @Param("countryNames") List<String> countryNames
    );

    @Select({
            "<script>",
            "SELECT",
            "  line.forwarder_code AS forwarderCode,",
            "  COALESCE(forwarder.name, line.forwarder_code) AS forwarderName,",
            "  line.service_code AS serviceCode,",
            "  line.service_name AS serviceName,",
            "  line.country AS country,",
            "  line.target_platform AS targetPlatform,",
            "  line.delivery_city AS deliveryCity,",
            "  line.destination_node AS destinationNode,",
            "  line.transport_mode AS transportMode,",
            "  line.delivery_scope AS deliveryScope,",
            "  line.transit_time_text AS transitTimeText,",
            "  line.transit_days_min AS transitDaysMin,",
            "  line.transit_days_max AS transitDaysMax,",
            "  COUNT(price.id) AS priceRuleCount,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.currency ORDER BY price.currency SEPARATOR ','), ',', 1) AS currency,",
            "  COALESCE(",
            "    MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END),",
            "    MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)",
            "  ) AS minUnitPrice,",
            "  CASE",
            "    WHEN MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END) IS NOT NULL THEN 'KG'",
            "    ELSE SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.billing_unit ORDER BY price.billing_unit SEPARATOR ','), ',', 1)",
            "  END AS billingUnit,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.billing_basis ORDER BY price.billing_basis SEPARATOR '；'), '；', 1) AS billingBasis,",
            "  MIN(price.min_billable_unit) AS minBillableUnit,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.min_billable_unit_type ORDER BY price.min_billable_unit_type SEPARATOR ','), ',', 1) AS minBillableUnitType,",
            "  MIN(price.min_charge) AS minCharge,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.volume_divisor END) AS volumeDivisor,",
            "  GROUP_CONCAT(DISTINCT price.cargo_category_name ORDER BY price.cargo_category_name SEPARATOR ',') AS cargoCategoryNamesCsv,",
            "  GROUP_CONCAT(DISTINCT price.price_status ORDER BY price.price_status SEPARATOR ',') AS priceStatusesCsv,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END) AS cbmMinUnitPrice,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END) AS kgMinUnitPrice",
            "FROM forwarder_quote_service_line line",
            "JOIN forwarder_quote_version version",
            "  ON version.id = line.quote_version_id",
            " AND version.status = 'PUBLISHED'",
            "JOIN forwarder",
            "  ON forwarder.id = version.forwarder_id",
            " AND forwarder.status = 'ACTIVE'",
            "LEFT JOIN forwarder_quote_base_price price",
            "  ON price.service_code = line.service_code",
            " AND price.quote_version_id = line.quote_version_id",
            "WHERE line.transport_mode = 'AIR'",
            "  AND line.active_for_mvp = b'1'",
            "<if test='countryNames != null and countryNames.size() > 0'>",
            "  AND line.country IN",
            "  <foreach collection='countryNames' item='countryName' open='(' separator=',' close=')'>",
            "    #{countryName}",
            "  </foreach>",
            "</if>",
            "GROUP BY line.forwarder_code, forwarder.name, line.service_code, line.service_name, line.country,",
            "         line.target_platform, line.delivery_city, line.destination_node, line.transport_mode,",
            "         line.delivery_scope, line.transit_time_text, line.transit_days_min, line.transit_days_max",
            "ORDER BY CASE WHEN COALESCE(MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END), MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)) IS NULL THEN 1 ELSE 0 END,",
            "         COALESCE(MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END), MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)),",
            "         forwarder.name, line.service_code",
            "</script>"
    })
    List<ForwarderSeaRecommendationRecord> listAirForwarderRecommendationCandidates(
            @Param("countryNames") List<String> countryNames
    );

    @Select({
            "<script>",
            "SELECT",
            "  route.route_code AS routeCode,",
            "  route.route_name AS routeName,",
            "  route.forwarder_code AS forwarderCode,",
            "  COALESCE(forwarder.name, route.forwarder_code) AS forwarderName,",
            "  line.service_code AS serviceCode,",
            "  line.service_name AS serviceName,",
            "  route.quote_version_code AS quoteVersionCode,",
            "  route.country AS country,",
            "  route.site_code AS siteCode,",
            "  route.target_platform AS targetPlatform,",
            "  route.delivery_city AS deliveryCity,",
            "  route.destination_node AS destinationNode,",
            "  route.transport_mode AS transportMode,",
            "  line.transit_time_text AS transitTimeText,",
            "  line.transit_days_min AS transitDaysMin,",
            "  line.transit_days_max AS transitDaysMax,",
            "  COUNT(price.id) AS priceRuleCount,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.currency ORDER BY price.currency SEPARATOR ','), ',', 1) AS currency,",
            "  COALESCE(",
            "    MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END),",
            "    MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END),",
            "    MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)",
            "  ) AS minUnitPrice,",
            "  CASE",
            "    WHEN MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END) IS NOT NULL THEN 'CBM'",
            "    WHEN MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END) IS NOT NULL THEN 'KG'",
            "    ELSE SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.billing_unit ORDER BY price.billing_unit SEPARATOR ','), ',', 1)",
            "  END AS billingUnit,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.billing_basis ORDER BY price.billing_basis SEPARATOR '；'), '；', 1) AS billingBasis,",
            "  MIN(price.min_billable_unit) AS minBillableUnit,",
            "  SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT price.min_billable_unit_type ORDER BY price.min_billable_unit_type SEPARATOR ','), ',', 1) AS minBillableUnitType,",
            "  MIN(price.min_charge) AS minCharge,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.volume_divisor END) AS volumeDivisor,",
            "  GROUP_CONCAT(DISTINCT price.cargo_category_name ORDER BY price.cargo_category_name SEPARATOR ',') AS cargoCategoryNamesCsv,",
            "  GROUP_CONCAT(DISTINCT price.price_status ORDER BY price.price_status SEPARATOR ',') AS priceStatusesCsv,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'CBM' THEN price.unit_price END) AS cbmMinUnitPrice,",
            "  MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) = 'KG' THEN price.unit_price END) AS kgMinUnitPrice",
            "FROM forwarder_quote_route_template route",
            "JOIN forwarder_quote_route_template_segment segment",
            "  ON segment.route_code = route.route_code",
            " AND segment.segment_role = 'HEADHAUL'",
            "JOIN forwarder_quote_service_line line",
            "  ON line.service_code = segment.service_code",
            "JOIN forwarder_quote_version version",
            "  ON version.id = line.quote_version_id",
            " AND version.status = 'PUBLISHED'",
            "JOIN forwarder",
            "  ON forwarder.id = version.forwarder_id",
            " AND forwarder.status = 'ACTIVE'",
            "LEFT JOIN forwarder_quote_base_price price",
            "  ON price.service_code = line.service_code",
            " AND price.quote_version_id = line.quote_version_id",
            "WHERE route.active_for_purchase_order = b'1'",
            "  AND route.transport_mode = #{transportMode}",
            "<if test='siteCodes != null and siteCodes.size() > 0'>",
            "  AND route.site_code IN",
            "  <foreach collection='siteCodes' item='siteCode' open='(' separator=',' close=')'>",
            "    #{siteCode}",
            "  </foreach>",
            "</if>",
            "GROUP BY route.route_code, route.route_name, route.forwarder_code, forwarder.name, line.service_code, line.service_name,",
            "         route.quote_version_code, route.country, route.site_code, route.target_platform, route.delivery_city, route.destination_node,",
            "         route.transport_mode, line.transit_time_text, line.transit_days_min, line.transit_days_max",
            "ORDER BY CASE WHEN COALESCE(MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) IN ('CBM', 'KG') THEN price.unit_price END), MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)) IS NULL THEN 1 ELSE 0 END,",
            "         COALESCE(MIN(CASE WHEN price.price_status = 'NORMAL' AND UPPER(COALESCE(price.billing_unit, '')) IN ('CBM', 'KG') THEN price.unit_price END), MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END)),",
            "         forwarder.name, route.route_code",
            "</script>"
    })
    List<ForwarderRouteRecommendationRecord> listRouteRecommendationCandidates(
            @Param("siteCodes") List<String> siteCodes,
            @Param("transportMode") String transportMode
    );

    @Select({
            "<script>",
            "SELECT route_code AS routeCode, segment_no AS segmentNo, segment_role AS segmentRole, service_code AS serviceCode,",
            "       cost_policy AS costPolicy, required AS required, display_name AS displayName, remark",
            "FROM forwarder_quote_route_template_segment",
            "WHERE route_code IN",
            "<foreach collection='routeCodes' item='routeCode' open='(' separator=',' close=')'>",
            "  #{routeCode}",
            "</foreach>",
            "ORDER BY route_code, segment_no",
            "</script>"
    })
    List<ForwarderRouteSegmentRecord> listRouteSegments(@Param("routeCodes") List<String> routeCodes);

    @Select({
            "<script>",
            "SELECT id, service_code AS serviceCode, price_rule_code AS priceRuleCode,",
            "       cargo_category_code AS cargoCategoryCode, cargo_category_name AS cargoCategoryName,",
            "       pricing_model AS pricingModel, currency, unit_price AS unitPrice, billing_unit AS billingUnit,",
            "       billing_basis AS billingBasis, volume_divisor AS volumeDivisor, min_billable_unit AS minBillableUnit,",
            "       min_billable_unit_type AS minBillableUnitType, min_charge AS minCharge,",
            "       target_platform AS targetPlatform, delivery_city AS deliveryCity, price_status AS priceStatus",
            "FROM forwarder_quote_base_price",
            "WHERE service_code IN",
            "<foreach collection='serviceCodes' item='serviceCode' open='(' separator=',' close=')'>",
            "  #{serviceCode}",
            "</foreach>",
            "ORDER BY service_code, price_status, id",
            "</script>"
    })
    List<ForwarderBasePriceRecord> listBasePricesByServiceCodes(@Param("serviceCodes") List<String> serviceCodes);

    @Select({
            "<script>",
            "SELECT id, service_code AS serviceCode, fee_name AS feeName, fee_type AS feeType, processing_scope AS processingScope,",
            "       pricing_model AS pricingModel, currency, amount, billing_unit AS billingUnit, condition_text AS conditionText,",
            "       min_charge AS minCharge, target_platform AS targetPlatform",
            "FROM forwarder_warehouse_processing_fee",
            "WHERE service_code IN",
            "<foreach collection='serviceCodes' item='serviceCode' open='(' separator=',' close=')'>",
            "  #{serviceCode}",
            "</foreach>",
            "ORDER BY service_code, fee_type, id",
            "</script>"
    })
    List<ForwarderWarehouseProcessingFeeRecord> listWarehouseProcessingFeesByServiceCodes(@Param("serviceCodes") List<String> serviceCodes);

    @Select({
            "<script>",
            "SELECT id, service_code AS serviceCode, fee_name AS feeName, fee_type AS feeType, target_platform AS targetPlatform,",
            "       delivery_city AS deliveryCity, trigger_condition AS triggerCondition, pricing_model AS pricingModel, currency,",
            "       amount, rate, billing_unit AS billingUnit, billing_basis AS billingBasis, min_charge AS minCharge,",
            "       min_billable_unit AS minBillableUnit, rounding_rule AS roundingRule, included_in_base_price AS includedInBasePrice",
            "FROM forwarder_quote_transport_fee",
            "WHERE service_code IN",
            "<foreach collection='serviceCodes' item='serviceCode' open='(' separator=',' close=')'>",
            "  #{serviceCode}",
            "</foreach>",
            "ORDER BY service_code, fee_type, id",
            "</script>"
    })
    List<ForwarderTransportFeeRecord> listTransportFeesByServiceCodes(@Param("serviceCodes") List<String> serviceCodes);

    @Select({
            "SELECT lss.id AS site_id, lss.site AS site_code, pso.id AS product_site_offer_id,",
            "       pso.psku_code, pso.offer_code",
            "FROM logical_store_site lss",
            "JOIN product_site_offer pso ON pso.site_id = lss.id AND pso.variant_id = #{variantId} AND pso.is_deleted = b'0'",
            "WHERE lss.logical_store_id = #{logicalStoreId}",
            "  AND lss.site = #{siteCode}",
            "  AND lss.is_deleted = b'0'",
            "LIMIT 1"
    })
    ProductOfferRecord selectProductOffer(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("variantId") Long variantId,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO procurement_purchase_order (",
            "id, owner_user_id, logical_store_id, order_no, title, remark, status, collection_status, progress_percent,",
            "site_codes_json, project_code_cache, project_name_cache, anchor_store_code_cache, item_count, total_quantity,",
            "collecting_item_count, abnormal_item_count, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.orderNo}, #{row.title}, #{row.remark},",
            "#{row.status}, #{row.collectionStatus}, #{row.progressPercent}, #{row.siteCodesJson}, #{row.projectCodeCache},",
            "#{row.projectNameCache}, #{row.anchorStoreCodeCache}, 0, 0, 0, 0, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertOrder(@Param("row") PurchaseOrderRecord row);

    @Update({
            "UPDATE procurement_purchase_order",
            "SET title = #{title},",
            "    remark = #{remark},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{orderId} AND is_deleted = b'0'"
    })
    int updateOrderHeader(
            @Param("orderId") Long orderId,
            @Param("title") String title,
            @Param("remark") String remark,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_purchase_order",
            "SET site_codes_json = #{siteCodesJson},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{orderId} AND is_deleted = b'0'"
    })
    int updateOrderSiteCodes(
            @Param("orderId") Long orderId,
            @Param("siteCodesJson") String siteCodesJson,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            ORDER_SELECT,
            "WHERE po.id = #{orderId} AND po.is_deleted = b'0' LIMIT 1"
    })
    PurchaseOrderRecord selectOrderById(@Param("orderId") Long orderId);

    @Select({
            "<script>",
            ORDER_SELECT,
            "WHERE po.logical_store_id = #{logicalStoreId}",
            "  AND po.is_deleted = b'0'",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (po.order_no LIKE CONCAT('%', #{keyword}, '%')",
            "       OR po.title LIKE CONCAT('%', #{keyword}, '%')",
            "       OR EXISTS (",
            "           SELECT 1 FROM procurement_purchase_order_item item",
            "           WHERE item.purchase_order_id = po.id",
            "             AND item.is_deleted = b'0'",
            "             AND (item.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "                  OR item.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "                  OR item.title_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "       ))",
            "</if>",
            "ORDER BY po.gmt_create DESC, po.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<PurchaseOrderRecord> listOrders(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit
    );

    @Select({
            ITEM_SELECT,
            "WHERE item.purchase_order_id = #{orderId} AND item.is_deleted = b'0'",
            "ORDER BY item.id ASC"
    })
    List<PurchaseOrderItemRecord> listItemsByOrder(@Param("orderId") Long orderId);

    @Select({
            ITEM_SELECT,
            "JOIN procurement_purchase_order po ON po.id = item.purchase_order_id AND po.is_deleted = b'0' ",
            "WHERE item.id = #{itemId} AND item.is_deleted = b'0' LIMIT 1"
    })
    PurchaseOrderItemRecord selectItemById(@Param("itemId") Long itemId);

    @Select({
            "SELECT * FROM procurement_purchase_order_item",
            "WHERE purchase_order_id = #{orderId}",
            "  AND product_variant_id = #{variantId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PurchaseOrderItemRecord selectItemByVariant(
            @Param("orderId") Long orderId,
            @Param("variantId") Long variantId
    );

    @Insert({
            "INSERT INTO procurement_purchase_order_item (",
            "id, purchase_order_id, owner_user_id, logical_store_id, product_master_id, product_variant_id,",
            "sku_parent, partner_sku, child_sku, title_cache, image_url_cache, source_type, fulfillment_type, fulfillment_source_name, total_quantity, site_count,",
            "collection_status, progress_percent, candidate_count, recommended_count, is_deleted, created_by, updated_by,",
            "gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.purchaseOrderId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.productMasterId}, #{row.productVariantId},",
            "#{row.skuParent}, #{row.partnerSku}, #{row.childSku}, #{row.titleCache}, #{row.imageUrlCache}, #{row.sourceType}, #{row.fulfillmentType}, #{row.fulfillmentSourceName}, 0, 0,",
            "'NOT_STARTED', 0, 0, 0, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertItem(@Param("row") PurchaseOrderItemRecord row);

    @Update({
            "UPDATE procurement_purchase_order_item",
            "SET fulfillment_type = #{fulfillmentType},",
            "    fulfillment_source_name = #{fulfillmentSourceName},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{itemId} AND is_deleted = b'0'"
    })
    int updateItemFulfillment(
            @Param("itemId") Long itemId,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("fulfillmentSourceName") String fulfillmentSourceName,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO procurement_purchase_order_item_site (",
            "id, purchase_order_id, purchase_order_item_id, owner_user_id, logical_store_id, site_id, site_code,",
            "product_site_offer_id, psku_code, offer_code, transport_mode, quantity, status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.purchaseOrderId}, #{row.purchaseOrderItemId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.siteId}, #{row.siteCode},",
            "#{row.productSiteOfferId}, #{row.pskuCode}, #{row.offerCode}, #{row.transportMode}, #{row.quantity}, 'ACTIVE', b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), status = 'ACTIVE', updated_by = VALUES(updated_by), gmt_updated = NOW()"
    })
    int upsertItemSite(@Param("row") PurchaseOrderItemSiteRecord row);

    @Update({
            "UPDATE procurement_purchase_order_item",
            "SET sourcing_spec_text = #{requirement.specText},",
            "    sourcing_size_text = #{requirement.sizeText},",
            "    sourcing_color_text = #{requirement.colorText},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{itemId} AND is_deleted = b'0'"
    })
    int updateItemSourcingRequirement(
            @Param("itemId") Long itemId,
            @Param("requirement") ProcurementPurchaseOrderSourcingRequirement requirement,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id, purchase_order_id, purchase_order_item_id, owner_user_id, logical_store_id, site_id, site_code,",
            "       product_site_offer_id, psku_code, offer_code, transport_mode, quantity, status",
            "FROM procurement_purchase_order_item_site",
            "WHERE purchase_order_id = #{orderId}",
            "  AND is_deleted = b'0'",
            "ORDER BY purchase_order_item_id ASC, site_code ASC, transport_mode ASC"
    })
    List<PurchaseOrderItemSiteRecord> listItemSitesByOrder(@Param("orderId") Long orderId);

    @Select({
            "<script>",
            "SELECT",
            "  link.order_id AS orderId,",
            "  link.item_id AS itemId,",
            "  link.assignment_id AS assignmentId,",
            "  link.target_store_code AS storeCode,",
            "  link.target_site_code AS siteCode,",
            "  link.sku_parent AS skuParent,",
            "  link.partner_sku AS partnerSku,",
            "  link.psku_code AS pskuCode,",
            "  link.product_title AS productTitle,",
            "  header.provider_order_no AS orderNo,",
            "  DATE_FORMAT(header.order_time, '%Y-%m-%d %H:%i:%s') AS orderTime,",
            "  header.supplier_name AS supplierName,",
            "  assignment.assigned_quantity AS assignedQuantity,",
            "  CASE",
            "    WHEN item.quantity IS NULL OR item.quantity = 0 THEN NULL",
            "    ELSE ROUND(",
            "      CAST(NULLIF(REPLACE(REPLACE(REPLACE(item.amount_text, '¥', ''), ',', ''), ' ', ''), '') AS DECIMAL(18, 4))",
            "      * assignment.assigned_quantity / item.quantity,",
            "      4",
            "    )",
            "  END AS allocatedCost,",
            "  CASE",
            "    WHEN item.quantity IS NULL OR item.quantity = 0 THEN NULL",
            "    ELSE ROUND(",
            "      CAST(NULLIF(REPLACE(REPLACE(REPLACE(item.amount_text, '¥', ''), ',', ''), ' ', ''), '') AS DECIMAL(18, 4))",
            "      / item.quantity,",
            "      4",
            "    )",
            "  END AS unitPrice",
            "FROM procurement_ali1688_order_item_product_link link",
            "JOIN procurement_ali1688_order_item_assignment assignment",
            "  ON assignment.id = link.assignment_id",
            " AND assignment.owner_user_id = link.owner_user_id",
            " AND assignment.target_type = 'STORE_SITE'",
            " AND assignment.status = 'active'",
            " AND assignment.is_deleted = b'0'",
            "JOIN procurement_ali1688_order_header header",
            "  ON header.id = link.order_id",
            " AND header.owner_user_id = link.owner_user_id",
            " AND header.is_deleted = b'0'",
            "JOIN procurement_ali1688_order_item item",
            "  ON item.id = link.item_id",
            " AND item.order_id = header.id",
            " AND item.is_deleted = b'0'",
            "WHERE link.owner_user_id = #{ownerUserId}",
            "  AND link.status = 'active'",
            "  AND link.is_deleted = b'0'",
            "  AND link.target_store_code = #{projectCode}",
            "  AND link.target_site_code IN",
            "  <foreach collection='siteCodes' item='siteCode' open='(' separator=',' close=')'>#{siteCode}</foreach>",
            "  AND (",
            "    <if test='partnerSkus != null and partnerSkus.size() &gt; 0'>",
            "      link.partner_sku IN",
            "      <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>#{partnerSku}</foreach>",
            "      OR link.psku_code IN",
            "      <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>#{partnerSku}</foreach>",
            "    </if>",
            "    <if test='partnerSkus != null and partnerSkus.size() &gt; 0 and skuParents != null and skuParents.size() &gt; 0'>",
            "      OR",
            "    </if>",
            "    <if test='skuParents != null and skuParents.size() &gt; 0'>",
            "      link.sku_parent IN",
            "      <foreach collection='skuParents' item='skuParent' open='(' separator=',' close=')'>#{skuParent}</foreach>",
            "    </if>",
            "  )",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM procurement_ali1688_order_sku_allocation allocation_guard",
            "    WHERE allocation_guard.owner_user_id = link.owner_user_id",
            "      AND allocation_guard.order_id = link.order_id",
            "      AND (allocation_guard.item_id = link.item_id OR allocation_guard.item_id IS NULL)",
            "      AND allocation_guard.target_store_code = link.target_store_code",
            "      AND COALESCE(allocation_guard.target_site_code, '*') = COALESCE(link.target_site_code, '*')",
            "      AND allocation_guard.sku_parent = link.sku_parent",
            "      AND allocation_guard.status = 'active'",
            "      AND allocation_guard.is_deleted = b'0'",
            "  )",
            "ORDER BY link.target_site_code ASC, link.partner_sku ASC, header.order_time DESC, link.id DESC",
            "</script>"
    })
    List<PurchaseOrderAli1688HistoryRow> listOrderAli1688HistoryRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("siteCodes") List<String> siteCodes,
            @Param("partnerSkus") List<String> partnerSkus,
            @Param("skuParents") List<String> skuParents
    );

    @Select({
            "<script>",
            "SELECT",
            "  allocation.id AS allocationId,",
            "  allocation.order_id AS orderId,",
            "  allocation.item_id AS itemId,",
            "  allocation.assignment_id AS assignmentId,",
            "  allocation.target_store_code AS storeCode,",
            "  allocation.target_site_code AS siteCode,",
            "  allocation.sku_parent AS skuParent,",
            "  allocation.partner_sku AS partnerSku,",
            "  allocation.psku_code AS pskuCode,",
            "  allocation.product_title AS productTitle,",
            "  header.provider_order_no AS orderNo,",
            "  DATE_FORMAT(header.order_time, '%Y-%m-%d %H:%i:%s') AS orderTime,",
            "  header.supplier_name AS supplierName,",
            "  CAST(ROUND(allocation.sku_quantity, 0) AS SIGNED) AS assignedQuantity,",
            "  allocation.allocated_cost AS allocatedCost,",
            "  CASE",
            "    WHEN allocation.sku_quantity IS NULL OR allocation.sku_quantity = 0 THEN NULL",
            "    ELSE ROUND(allocation.allocated_cost / allocation.sku_quantity, 4)",
            "  END AS unitPrice,",
            "  allocation.source_line_label AS sourceLineLabel,",
            "  allocation.allocation_basis AS allocationBasis,",
            "  allocation.evidence_text AS evidenceText",
            "FROM procurement_ali1688_order_sku_allocation allocation",
            "JOIN procurement_ali1688_order_header header",
            "  ON header.id = allocation.order_id",
            " AND header.owner_user_id = allocation.owner_user_id",
            " AND header.is_deleted = b'0'",
            "WHERE allocation.owner_user_id = #{ownerUserId}",
            "  AND allocation.status = 'active'",
            "  AND allocation.is_deleted = b'0'",
            "  AND allocation.target_store_code = #{projectCode}",
            "  AND allocation.target_site_code IN",
            "  <foreach collection='siteCodes' item='siteCode' open='(' separator=',' close=')'>#{siteCode}</foreach>",
            "  AND (",
            "    <if test='partnerSkus != null and partnerSkus.size() &gt; 0'>",
            "      allocation.partner_sku IN",
            "      <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>#{partnerSku}</foreach>",
            "      OR allocation.psku_code IN",
            "      <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>#{partnerSku}</foreach>",
            "    </if>",
            "    <if test='partnerSkus != null and partnerSkus.size() &gt; 0 and skuParents != null and skuParents.size() &gt; 0'>",
            "      OR",
            "    </if>",
            "    <if test='skuParents != null and skuParents.size() &gt; 0'>",
            "      allocation.sku_parent IN",
            "      <foreach collection='skuParents' item='skuParent' open='(' separator=',' close=')'>#{skuParent}</foreach>",
            "    </if>",
            "  )",
            "ORDER BY allocation.target_site_code ASC, allocation.partner_sku ASC, header.order_time DESC, allocation.id DESC",
            "</script>"
    })
    List<PurchaseOrderAli1688HistoryRow> listOrderAli1688AllocationHistoryRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("siteCodes") List<String> siteCodes,
            @Param("partnerSkus") List<String> partnerSkus,
            @Param("skuParents") List<String> skuParents
    );

    @Select({
            "<script>",
            "SELECT",
            "  batch.id AS id,",
            "  batch.target_store_code AS storeCode,",
            "  batch.target_site_code AS siteCode,",
            "  batch.sku_parent AS skuParent,",
            "  batch.partner_sku AS partnerSku,",
            "  batch.psku_code AS pskuCode,",
            "  batch.batch_label AS batchLabel,",
            "  batch.batch_sequence AS batchSequence,",
            "  batch.counted_quantity AS countedQuantity,",
            "  batch.counted_cost AS countedCost,",
            "  batch.note AS note,",
            "  source.order_id AS sourceOrderId,",
            "  source.item_id AS sourceItemId,",
            "  source.assignment_id AS sourceAssignmentId,",
            "  source.source_order_no AS orderNo,",
            "  DATE_FORMAT(source.source_order_time, '%Y-%m-%d %H:%i:%s') AS orderTime,",
            "  source.supplier_name AS supplierName",
            "FROM procurement_ali1688_sku_purchase_batch batch",
            "LEFT JOIN procurement_ali1688_sku_purchase_batch_source source",
            "  ON source.batch_id = batch.id",
            " AND source.owner_user_id = batch.owner_user_id",
            " AND source.status = 'active'",
            " AND source.is_deleted = b'0'",
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "  AND batch.target_store_code = #{projectCode}",
            "  AND batch.target_site_code IN",
            "  <foreach collection='siteCodes' item='siteCode' open='(' separator=',' close=')'>#{siteCode}</foreach>",
            "  AND batch.status = 'active'",
            "  AND batch.is_deleted = b'0'",
            "  AND (",
            "    <if test='partnerSkus != null and partnerSkus.size() &gt; 0'>",
            "      batch.partner_sku IN",
            "      <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>#{partnerSku}</foreach>",
            "      OR batch.psku_code IN",
            "      <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>#{partnerSku}</foreach>",
            "    </if>",
            "    <if test='partnerSkus != null and partnerSkus.size() &gt; 0 and skuParents != null and skuParents.size() &gt; 0'>",
            "      OR",
            "    </if>",
            "    <if test='skuParents != null and skuParents.size() &gt; 0'>",
            "      batch.sku_parent IN",
            "      <foreach collection='skuParents' item='skuParent' open='(' separator=',' close=')'>#{skuParent}</foreach>",
            "    </if>",
            "  )",
            "ORDER BY batch.target_site_code ASC, batch.partner_sku ASC, batch.batch_sequence ASC, batch.id ASC, source.id ASC",
            "</script>"
    })
    List<PurchaseOrderAli1688PurchaseBatchRow> listOrderAli1688PurchaseBatches(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("siteCodes") List<String> siteCodes,
            @Param("partnerSkus") List<String> partnerSkus,
            @Param("skuParents") List<String> skuParents
    );

    @Update({
            "UPDATE procurement_purchase_order_item item",
            "LEFT JOIN (",
            "  SELECT purchase_order_item_id, COUNT(*) AS site_count, COALESCE(SUM(quantity), 0) AS total_quantity",
            "  FROM procurement_purchase_order_item_site",
            "  WHERE purchase_order_item_id = #{itemId} AND is_deleted = b'0'",
            "  GROUP BY purchase_order_item_id",
            ") site ON site.purchase_order_item_id = item.id",
            "SET item.site_count = COALESCE(site.site_count, 0),",
            "    item.total_quantity = COALESCE(site.total_quantity, 0),",
            "    item.updated_by = #{updatedBy},",
            "    item.gmt_updated = NOW()",
            "WHERE item.id = #{itemId} AND item.is_deleted = b'0'"
    })
    int recalculateItemAggregates(@Param("itemId") Long itemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order po",
            "LEFT JOIN (",
            "  SELECT purchase_order_id, COUNT(*) AS item_count, COALESCE(SUM(total_quantity), 0) AS total_quantity,",
            "         COALESCE(ROUND(AVG(progress_percent)), 0) AS progress_percent,",
            "         SUM(CASE WHEN collection_status IN ('QUEUED', 'RUNNING') THEN 1 ELSE 0 END) AS collecting_count,",
            "         SUM(CASE WHEN collection_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,",
            "         SUM(CASE WHEN collection_status IN ('SUCCEEDED', 'PARTIAL_SUCCEEDED') THEN 1 ELSE 0 END) AS completed_count",
            "  FROM procurement_purchase_order_item",
            "  WHERE purchase_order_id = #{orderId} AND is_deleted = b'0'",
            "  GROUP BY purchase_order_id",
            ") agg ON agg.purchase_order_id = po.id",
            "SET po.item_count = COALESCE(agg.item_count, 0),",
            "    po.total_quantity = COALESCE(agg.total_quantity, 0),",
            "    po.progress_percent = COALESCE(agg.progress_percent, 0),",
            "    po.collecting_item_count = COALESCE(agg.collecting_count, 0),",
            "    po.abnormal_item_count = COALESCE(agg.failed_count, 0),",
            "    po.collection_status = CASE",
            "      WHEN COALESCE(agg.item_count, 0) = 0 THEN 'NOT_STARTED'",
            "      WHEN COALESCE(agg.collecting_count, 0) > 0 THEN 'RUNNING'",
            "      WHEN COALESCE(agg.failed_count, 0) > 0 THEN 'FAILED'",
            "      WHEN COALESCE(agg.completed_count, 0) = COALESCE(agg.item_count, 0) THEN 'SUCCEEDED'",
            "      WHEN COALESCE(agg.completed_count, 0) > 0 THEN 'PARTIAL_SUCCEEDED'",
            "      ELSE 'NOT_STARTED'",
            "    END,",
            "    po.status = CASE",
            "      WHEN COALESCE(agg.item_count, 0) = 0 THEN 'DRAFT'",
            "      WHEN COALESCE(agg.failed_count, 0) > 0 THEN 'ABNORMAL'",
            "      WHEN COALESCE(agg.collecting_count, 0) > 0 THEN 'COLLECTING'",
            "      WHEN COALESCE(agg.completed_count, 0) = COALESCE(agg.item_count, 0) THEN 'COMPLETED'",
            "      WHEN COALESCE(agg.completed_count, 0) > 0 THEN 'PARTIAL_DONE'",
            "      ELSE 'READY'",
            "    END,",
            "    po.updated_by = #{updatedBy},",
            "    po.gmt_updated = NOW()",
            "WHERE po.id = #{orderId} AND po.is_deleted = b'0'"
    })
    int recalculateOrderAggregates(@Param("orderId") Long orderId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order",
            "SET is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{orderId} AND is_deleted = b'0'"
    })
    int softDeleteOrder(@Param("orderId") Long orderId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order_item",
            "SET is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_id = #{orderId} AND is_deleted = b'0'"
    })
    int softDeleteItemsByOrder(@Param("orderId") Long orderId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order_item_site",
            "SET is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_id = #{orderId} AND is_deleted = b'0'"
    })
    int softDeleteItemSitesByOrder(@Param("orderId") Long orderId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order_ali1688_collection",
            "SET is_deleted = b'1', current_link_key = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_id = #{orderId} AND is_deleted = b'0'"
    })
    int softDeleteLinksByOrder(@Param("orderId") Long orderId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order_item",
            "SET is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{itemId} AND is_deleted = b'0'"
    })
    int softDeleteItem(@Param("itemId") Long itemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order_item_site",
            "SET is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_item_id = #{itemId} AND is_deleted = b'0'"
    })
    int softDeleteItemSitesByItem(@Param("itemId") Long itemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order_ali1688_collection",
            "SET is_deleted = b'1', current_link_key = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_item_id = #{itemId} AND is_deleted = b'0'"
    })
    int softDeleteLinksByItem(@Param("itemId") Long itemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_purchase_order_ali1688_collection",
            "SET is_deleted = b'1', current_link_key = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_item_id = #{itemId} AND current_link_key IS NOT NULL AND is_deleted = b'0'"
    })
    int supersedeCurrentLinksByItem(@Param("itemId") Long itemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_collection_task task",
            "JOIN procurement_purchase_order_ali1688_collection link",
            "  ON link.source_collection_id = task.source_collection_id",
            " AND link.ali1688_task_id = task.id",
            " AND link.is_deleted = b'0'",
            "SET task.current_task_key = NULL,",
            "    task.status = CASE",
            "      WHEN task.status IN ('waiting_source', 'queued', 'running', 'failed') THEN 'superseded'",
            "      ELSE task.status",
            "    END,",
            "    task.locked_at = NULL,",
            "    task.locked_by = NULL,",
            "    task.updated_by = #{updatedBy},",
            "    task.gmt_updated = NOW()",
            "WHERE link.purchase_order_item_id = #{itemId}",
            "  AND link.current_link_key IS NOT NULL",
            "  AND task.current_task_key IS NOT NULL",
            "  AND task.is_deleted = b'0'"
    })
    int supersedeCurrentAli1688TasksByItem(@Param("itemId") Long itemId, @Param("updatedBy") Long updatedBy);

    @Insert({
            "INSERT INTO procurement_purchase_order_ali1688_collection (",
            "id, purchase_order_id, purchase_order_item_id, owner_user_id, logical_store_id, source_collection_id, ali1688_task_id,",
            "current_link_key, status, progress_percent, candidate_count, recommended_count, failure_code, failure_message,",
            "source_snapshot_json, started_at, finished_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{orderId}, #{itemId}, #{ownerUserId}, #{logicalStoreId}, #{sourceCollectionId}, #{taskId},",
            "#{currentLinkKey}, #{status}, #{progressPercent}, #{candidateCount}, #{recommendedCount}, #{failureCode}, #{failureMessage},",
            "#{sourceSnapshotJson}, NULL, #{finishedAt}, b'0', #{updatedBy}, #{updatedBy}, NOW(), NOW())"
    })
    int insertCollectionLink(
            @Param("id") Long id,
            @Param("orderId") Long orderId,
            @Param("itemId") Long itemId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("sourceCollectionId") Long sourceCollectionId,
            @Param("taskId") Long taskId,
            @Param("currentLinkKey") String currentLinkKey,
            @Param("status") String status,
            @Param("progressPercent") Integer progressPercent,
            @Param("candidateCount") Integer candidateCount,
            @Param("recommendedCount") Integer recommendedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("sourceSnapshotJson") String sourceSnapshotJson,
            @Param("finishedAt") String finishedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_purchase_order_item",
            "SET latest_collection_link_id = #{linkId}, collection_status = #{status}, progress_percent = #{progressPercent},",
            "    candidate_count = #{candidateCount}, recommended_count = #{recommendedCount}, failure_code = #{failureCode},",
            "    failure_message = #{failureMessage}, last_collected_at = CASE WHEN #{finished} = 1 THEN NOW() ELSE last_collected_at END,",
            "    updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{itemId} AND is_deleted = b'0'"
    })
    int updateItemCollection(
            @Param("itemId") Long itemId,
            @Param("linkId") Long linkId,
            @Param("status") String status,
            @Param("progressPercent") Integer progressPercent,
            @Param("candidateCount") Integer candidateCount,
            @Param("recommendedCount") Integer recommendedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("finished") Integer finished,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_purchase_order_item",
            "SET collection_status = 'FAILED', progress_percent = 100, failure_code = #{failureCode}, failure_message = #{failureMessage},",
            "    updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{itemId} AND is_deleted = b'0'"
    })
    int markItemCollectionFailed(
            @Param("itemId") Long itemId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_plan",
            "SET status = 'SUPERSEDED', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_id = #{orderId}",
            "  AND status IN ('DRAFT', 'READY')",
            "  AND is_deleted = b'0'"
    })
    int supersedeCurrentLogisticsPlansByOrder(
            @Param("orderId") Long orderId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_recommendation",
            "SET is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE purchase_order_id = #{orderId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteLogisticsRecommendationsByOrder(
            @Param("orderId") Long orderId,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO procurement_purchase_order_logistics_plan (",
            "id, purchase_order_id, owner_user_id, logical_store_id, plan_no, status, transport_mode,",
            "item_count, sku_count, total_quantity, missing_item_count, site_summary_json, snapshot_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{orderId}, #{ownerUserId}, #{logicalStoreId}, #{planNo}, #{status}, #{transportMode},",
            "#{itemCount}, #{skuCount}, #{totalQuantity}, #{missingItemCount}, #{siteSummaryJson}, #{snapshotJson},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertLogisticsPlan(
            @Param("id") Long id,
            @Param("orderId") Long orderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("planNo") String planNo,
            @Param("status") String status,
            @Param("transportMode") String transportMode,
            @Param("itemCount") Integer itemCount,
            @Param("skuCount") Integer skuCount,
            @Param("totalQuantity") Integer totalQuantity,
            @Param("missingItemCount") Integer missingItemCount,
            @Param("siteSummaryJson") String siteSummaryJson,
            @Param("snapshotJson") String snapshotJson,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_purchase_order_logistics_recommendation (",
            "id, logistics_plan_id, purchase_order_id, route_code, forwarder_code, service_code, transport_mode, rank_no,",
            "recommended, estimate_status, currency, estimated_total_amount, recurring_amount_per_day, snapshot_json,",
            "created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.logisticsPlanId}, #{row.purchaseOrderId}, #{row.routeCode}, #{row.forwarderCode},",
            "#{row.serviceCode}, #{row.transportMode}, #{row.rankNo}, #{row.recommended}, #{row.estimateStatus},",
            "#{row.currency}, #{row.estimatedTotalAmount}, #{row.recurringAmountPerDay}, #{row.snapshotJson},",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertLogisticsRecommendation(
            @Param("row") LogisticsRecommendationInsertRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_purchase_order_logistics_cost_component (",
            "id, recommendation_id, logistics_plan_id, component_type, component_name, source_table, source_id,",
            "currency, unit_price, billing_unit, billable_quantity, amount, amount_status, included_in_total,",
            "formula_text, remark, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.recommendationId}, #{row.logisticsPlanId}, #{row.componentType}, #{row.componentName},",
            "#{row.sourceTable}, #{row.sourceId}, #{row.currency}, #{row.unitPrice}, #{row.billingUnit},",
            "#{row.billableQuantity}, #{row.amount}, #{row.amountStatus}, #{row.includedInTotal},",
            "#{row.formulaText}, #{row.remark}, #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertLogisticsCostComponent(
            @Param("row") LogisticsCostComponentInsertRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_purchase_order_operation_log (",
            "id, purchase_order_id, purchase_order_item_id, operation_type, operator_user_id, before_status, after_status, detail_json, gmt_create",
            ") VALUES (",
            "#{id}, #{orderId}, #{itemId}, #{operationType}, #{operatorUserId}, #{beforeStatus}, #{afterStatus}, #{detailJson}, NOW())"
    })
    int insertOperationLog(
            @Param("id") Long id,
            @Param("orderId") Long orderId,
            @Param("itemId") Long itemId,
            @Param("operationType") String operationType,
            @Param("operatorUserId") Long operatorUserId,
            @Param("beforeStatus") String beforeStatus,
            @Param("afterStatus") String afterStatus,
            @Param("detailJson") String detailJson
    );
}
