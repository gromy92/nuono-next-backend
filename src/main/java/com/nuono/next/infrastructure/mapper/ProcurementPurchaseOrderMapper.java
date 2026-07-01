package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductArchiveRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductOfferRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderSeaRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsBillReconciliationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsCostComponentInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsExpectedBillComponentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsExpectedBillRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsRecommendationInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688HistoryRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688PurchaseBatchRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderChannelQuoteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderDeclarationAttributeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
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

    default Long nextLogisticsQuoteLineId() {
        return nextId("procurement_purchase_order_logistics_quote_line", 280000L);
    }

    default Long nextShippingOrderId() {
        return nextId("procurement_shipping_order", 290000L);
    }

    default Long nextShippingOrderSegmentId() {
        return nextId("procurement_shipping_order_segment", 292000L);
    }

    default Long nextShippingOrderLineId() {
        return nextId("procurement_shipping_order_line", 300000L);
    }

    default Long nextProductForwarderDeclarationAttributeId() {
        return nextId("product_forwarder_declaration_attribute", 310000L);
    }

    default Long nextProductForwarderChannelQuoteId() {
        return nextId("product_forwarder_channel_quote", 320000L);
    }

    default Long nextLogisticsExpectedBillId() {
        return nextId("logistics_expected_bill", 330000L);
    }

    default Long nextLogisticsExpectedBillComponentId() {
        return nextId("logistics_expected_bill_component", 340000L);
    }

    default Long nextLogisticsBillReconciliationId() {
        return nextId("logistics_bill_reconciliation", 360000L);
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
            "       pv.size_en, pv.size_ar, COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title, pm.cover_image_url AS image_url,",
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
            "       OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cn_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "GROUP BY pm.id, pv.id, pm.sku_parent, pv.partner_sku, pv.child_sku, pv.size_en, pv.size_ar,",
            "         pm.title_cn_cache, pm.title_cache, pm.cover_image_url,",
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
            "       pv.size_en, pv.size_ar, COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title, pm.cover_image_url AS image_url,",
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
            "  AND pv.partner_sku = #{psku}",
            "GROUP BY pm.id, pv.id, pm.sku_parent, pv.partner_sku, pv.child_sku, pv.size_en, pv.size_ar,",
            "         pm.title_cn_cache, pm.title_cache, pm.cover_image_url,",
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
            "ORDER BY pv.id ASC",
            "LIMIT 2"
    })
    List<ProductArchiveRecord> listProductArchiveMatches(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("psku") String psku
    );

    @Select({
            "SELECT pm.id AS product_master_id, pv.id AS product_variant_id, pm.sku_parent, pv.partner_sku, pv.child_sku,",
            "       pv.size_en, pv.size_ar, COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title, pm.cover_image_url AS image_url,",
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
            "<script>",
            "SELECT lss.id AS site_id, lss.site AS site_code, pso.id AS product_site_offer_id,",
            "       pso.psku_code, pso.offer_code",
            "FROM logical_store_site lss",
            "JOIN product_site_offer pso ON pso.site_id = lss.id AND pso.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.id = pso.variant_id AND pv.is_deleted = b'0'",
            "JOIN product_master pm ON pm.id = pv.product_master_id AND pm.logical_store_id = lss.logical_store_id AND pm.is_deleted = b'0'",
            "WHERE lss.logical_store_id = #{logicalStoreId}",
            "  AND lss.site = #{siteCode}",
            "  AND lss.is_deleted = b'0'",
            "<choose>",
            "  <when test='partnerSku != null and partnerSku != \"\"'>",
            "    AND UPPER(COALESCE(NULLIF(pso.partner_sku, ''), NULLIF(pm.partner_sku, ''), pv.partner_sku)) = UPPER(#{partnerSku})",
            "  </when>",
            "  <otherwise>",
            "    AND pso.variant_id = #{variantId}",
            "  </otherwise>",
            "</choose>",
            "LIMIT 1",
            "</script>"
    })
    ProductOfferRecord selectProductOffer(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
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
            "SET status = 'SUBMITTED',",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{orderId}",
            "  AND status <> 'SUBMITTED'",
            "  AND is_deleted = b'0'"
    })
    int submitOrder(@Param("orderId") Long orderId, @Param("updatedBy") Long updatedBy);

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
            "<if test='submittedOnly != null and submittedOnly'>",
            "  AND po.status = 'SUBMITTED'",
            "</if>",
            "<if test='shippingAvailableOnly != null and shippingAvailableOnly'>",
            "  AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM procurement_shipping_order_line sol",
            "      WHERE sol.purchase_order_id = po.id",
            "        AND sol.is_deleted = b'0'",
            "  )",
            "</if>",
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
            @Param("submittedOnly") Boolean submittedOnly,
            @Param("shippingAvailableOnly") Boolean shippingAvailableOnly,
            @Param("limit") Integer limit
    );

    @Select({
            "<script>",
            ORDER_SELECT,
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND po.is_deleted = b'0'",
            "<if test='submittedOnly != null and submittedOnly'>",
            "  AND po.status = 'SUBMITTED'",
            "</if>",
            "<if test='shippingAvailableOnly != null and shippingAvailableOnly'>",
            "  AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM procurement_shipping_order_line sol",
            "      WHERE sol.purchase_order_id = po.id",
            "        AND sol.is_deleted = b'0'",
            "  )",
            "</if>",
            "<if test='storeCodes != null and storeCodes.size() &gt; 0'>",
            "  AND po.anchor_store_code_cache IN",
            "  <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>#{storeCode}</foreach>",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (po.order_no LIKE CONCAT('%', #{keyword}, '%')",
            "       OR po.title LIKE CONCAT('%', #{keyword}, '%')",
            "       OR po.project_name_cache LIKE CONCAT('%', #{keyword}, '%')",
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
    List<PurchaseOrderRecord> listOrdersByOwner(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") java.util.Collection<String> storeCodes,
            @Param("keyword") String keyword,
            @Param("submittedOnly") Boolean submittedOnly,
            @Param("shippingAvailableOnly") Boolean shippingAvailableOnly,
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
            "  AND UPPER(partner_sku) = UPPER(#{partnerSku})",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PurchaseOrderItemRecord selectItemByPartnerSku(
            @Param("orderId") Long orderId,
            @Param("partnerSku") String partnerSku
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
            "SELECT COUNT(1)",
            "FROM procurement_shipping_order_line",
            "WHERE purchase_order_item_site_id IN",
            "  <foreach collection='itemSiteIds' item='itemSiteId' open='(' separator=',' close=')'>#{itemSiteId}</foreach>",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int countActiveShippingOrderLinesByItemSites(@Param("itemSiteIds") List<Long> itemSiteIds);

    @Insert({
            "INSERT INTO procurement_shipping_order (",
            "id, owner_user_id, shipping_order_no, title, status, purchase_order_count, line_count, sku_count, total_quantity,",
            "store_summary_json, site_summary_json, transport_summary_json, quote_status, shipping_submit_status, remark,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.shippingOrderNo}, #{row.title}, #{row.status},",
            "#{row.purchaseOrderCount}, #{row.lineCount}, #{row.skuCount}, #{row.totalQuantity},",
            "#{row.storeSummaryJson}, #{row.siteSummaryJson}, #{row.transportSummaryJson}, #{row.quoteStatus},",
            "#{row.shippingSubmitStatus}, #{row.remark}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingOrder(
            @Param("row") ShippingOrderRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_shipping_order_segment (",
            "id, shipping_order_id, owner_user_id, segment_no, site_code, transport_mode,",
            "quote_status, shipping_submit_status, line_count, sku_count, total_quantity, missing_yite_material_count,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.shippingOrderId}, #{row.ownerUserId}, #{row.segmentNo}, #{row.siteCode}, #{row.transportMode},",
            "#{row.quoteStatus}, #{row.shippingSubmitStatus}, #{row.lineCount}, #{row.skuCount}, #{row.totalQuantity}, #{row.missingYiteMaterialCount},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingOrderSegment(
            @Param("row") ShippingOrderSegmentRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order",
            "SET title = #{title},",
            "    remark = #{remark},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{shippingOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateShippingOrderHeader(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("title") String title,
            @Param("remark") String remark,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_shipping_order_line (",
            "id, shipping_order_id, shipping_order_segment_id, owner_user_id, logical_store_id, source_store_code, source_store_name,",
            "purchase_order_id, purchase_order_no, purchase_order_title, purchase_order_item_id, purchase_order_item_site_id,",
            "product_master_id, product_variant_id, sku_parent, partner_sku, title_cache, image_url_cache,",
            "site_code, psku_code, yite_material, planned_transport_mode, quantity, fulfillment_type, quote_line_id,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.shippingOrderId}, #{row.shippingOrderSegmentId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.sourceStoreCode},",
            "#{row.sourceStoreName}, #{row.purchaseOrderId}, #{row.purchaseOrderNo}, #{row.purchaseOrderTitle},",
            "#{row.purchaseOrderItemId}, #{row.purchaseOrderItemSiteId}, #{row.productMasterId}, #{row.productVariantId},",
            "#{row.skuParent}, #{row.partnerSku}, #{row.titleCache}, #{row.imageUrlCache}, #{row.siteCode},",
            "#{row.pskuCode}, #{row.yiteMaterial}, #{row.plannedTransportMode}, #{row.quantity}, #{row.fulfillmentType}, #{row.quoteLineId},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingOrderLine(
            @Param("row") ShippingOrderLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order_line",
            "SET quote_line_id = #{quoteLineId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE shipping_order_id = #{shippingOrderId}",
            "  AND purchase_order_item_site_id = #{itemSiteId}",
            "  AND is_deleted = b'0'"
    })
    int updateShippingOrderLineQuoteLine(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("itemSiteId") Long itemSiteId,
            @Param("quoteLineId") Long quoteLineId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order_line",
            "SET yite_material = #{yiteMaterial},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{shippingOrderLineId}",
            "  AND shipping_order_id = #{shippingOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateShippingOrderLineYiteMaterial(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("shippingOrderLineId") Long shippingOrderLineId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("yiteMaterial") String yiteMaterial,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_quote_line quote",
            "JOIN procurement_shipping_order_line sol",
            "  ON sol.shipping_order_id = quote.shipping_order_id",
            " AND sol.purchase_order_item_site_id = quote.purchase_order_item_site_id",
            " AND sol.is_deleted = b'0'",
            "SET quote.yite_material = #{yiteMaterial},",
            "    quote.updated_by = #{operatorUserId},",
            "    quote.gmt_updated = NOW()",
            "WHERE sol.id = #{shippingOrderLineId}",
            "  AND sol.shipping_order_id = #{shippingOrderId}",
            "  AND sol.owner_user_id = #{ownerUserId}",
            "  AND quote.is_deleted = b'0'"
    })
    int updateShippingOrderQuoteLineYiteMaterial(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("shippingOrderLineId") Long shippingOrderLineId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("yiteMaterial") String yiteMaterial,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, logical_store_id AS logicalStoreId,",
            "       source_store_code AS sourceStoreCode, partner_sku AS partnerSku,",
            "       barcode, forwarder_code AS forwarderCode,",
            "       attribute_code AS attributeCode, attribute_value AS attributeValue,",
            "       source_shipping_order_id AS sourceShippingOrderId,",
            "       source_shipping_order_line_id AS sourceShippingOrderLineId,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM product_forwarder_declaration_attribute",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND forwarder_code = #{forwarderCode}",
            "  AND attribute_code = #{attributeCode}",
            "  AND is_deleted = b'0'",
            "<choose>",
            "  <when test='partnerSkus != null and partnerSkus.size() &gt; 0'>",
            "    AND (",
            "      UPPER(partner_sku) IN",
            "      <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>",
            "        UPPER(#{partnerSku})",
            "      </foreach>",
            "      <if test='productVariantIds != null and productVariantIds.size() &gt; 0'>",
            "        OR product_variant_id IN",
            "        <foreach collection='productVariantIds' item='productVariantId' open='(' separator=',' close=')'>",
            "          #{productVariantId}",
            "        </foreach>",
            "      </if>",
            "    )",
            "  </when>",
            "  <when test='productVariantIds != null and productVariantIds.size() &gt; 0'>",
            "    AND product_variant_id IN",
            "    <foreach collection='productVariantIds' item='productVariantId' open='(' separator=',' close=')'>",
            "      #{productVariantId}",
            "    </foreach>",
            "  </when>",
            "</choose>",
            "</script>"
    })
    List<ProductForwarderDeclarationAttributeRecord> listProductForwarderDeclarationAttributes(
            @Param("ownerUserId") Long ownerUserId,
            @Param("forwarderCode") String forwarderCode,
            @Param("attributeCode") String attributeCode,
            @Param("productVariantIds") List<Long> productVariantIds,
            @Param("partnerSkus") List<String> partnerSkus
    );

    @Insert({
            "INSERT INTO product_forwarder_declaration_attribute (",
            "id, owner_user_id, product_master_id, product_variant_id, logical_store_id, source_store_code, partner_sku, barcode,",
            "forwarder_code, attribute_code, attribute_value, source_shipping_order_id, source_shipping_order_line_id,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.productMasterId}, #{row.productVariantId}, #{row.logicalStoreId}, #{row.sourceStoreCode}, #{row.partnerSku}, #{row.barcode},",
            "#{row.forwarderCode}, #{row.attributeCode}, #{row.attributeValue}, #{row.sourceShippingOrderId}, #{row.sourceShippingOrderLineId},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  product_master_id = VALUES(product_master_id),",
            "  logical_store_id = VALUES(logical_store_id),",
            "  source_store_code = VALUES(source_store_code),",
            "  partner_sku = VALUES(partner_sku),",
            "  barcode = VALUES(barcode),",
            "  attribute_value = VALUES(attribute_value),",
            "  source_shipping_order_id = VALUES(source_shipping_order_id),",
            "  source_shipping_order_line_id = VALUES(source_shipping_order_line_id),",
            "  is_deleted = b'0',",
            "  updated_by = #{operatorUserId},",
            "  gmt_updated = NOW()"
    })
    int upsertProductForwarderDeclarationAttribute(
            @Param("row") ProductForwarderDeclarationAttributeRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE product_forwarder_declaration_attribute",
            "SET is_deleted = b'1',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND forwarder_code = #{forwarderCode}",
            "  AND attribute_code = #{attributeCode}",
            "<choose>",
            "  <when test='partnerSku != null and partnerSku != \"\"'>",
            "    AND UPPER(partner_sku) = UPPER(#{partnerSku})",
            "    <if test='logicalStoreId != null'>",
            "      AND (logical_store_id IS NULL OR logical_store_id = #{logicalStoreId})",
            "    </if>",
            "    <if test='sourceStoreCode != null and sourceStoreCode != \"\"'>",
            "      AND (source_store_code IS NULL OR TRIM(source_store_code) = '' OR UPPER(source_store_code) = UPPER(#{sourceStoreCode}))",
            "    </if>",
            "  </when>",
            "  <otherwise>",
            "    AND product_variant_id = #{productVariantId}",
            "  </otherwise>",
            "</choose>",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int softDeleteProductForwarderDeclarationAttribute(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceStoreCode") String sourceStoreCode,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("productVariantId") Long productVariantId,
            @Param("forwarderCode") String forwarderCode,
            @Param("attributeCode") String attributeCode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE product_forwarder_channel_quote",
            "SET effective_status = 'HISTORICAL',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND forwarder_code = #{forwarderCode}",
            "<choose>",
            "  <when test='partnerSku != null and partnerSku != \"\"'>",
            "    AND UPPER(partner_sku) = UPPER(#{partnerSku})",
            "    <if test='logicalStoreId != null'>",
            "      AND (logical_store_id IS NULL OR logical_store_id = #{logicalStoreId})",
            "    </if>",
            "    <if test='sourceStoreCode != null and sourceStoreCode != \"\"'>",
            "      AND (source_store_code IS NULL OR TRIM(source_store_code) = '' OR UPPER(source_store_code) = UPPER(#{sourceStoreCode}))",
            "    </if>",
            "  </when>",
            "  <otherwise>",
            "    AND product_variant_id = #{productVariantId}",
            "  </otherwise>",
            "</choose>",
            "  AND COALESCE(site_code, '') = COALESCE(#{siteCode}, '')",
            "  AND COALESCE(route_code, '') = COALESCE(#{routeCode}, '')",
            "  AND COALESCE(service_code, '') = COALESCE(#{serviceCode}, '')",
            "  AND COALESCE(billing_unit, '') = COALESCE(#{billingUnit}, '')",
            "  AND effective_status = 'CURRENT'",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int markHistoricalProductForwarderChannelQuote(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceStoreCode") String sourceStoreCode,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("productVariantId") Long productVariantId,
            @Param("forwarderCode") String forwarderCode,
            @Param("siteCode") String siteCode,
            @Param("routeCode") String routeCode,
            @Param("serviceCode") String serviceCode,
            @Param("billingUnit") String billingUnit,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO product_forwarder_channel_quote (",
            "id, owner_user_id, product_master_id, product_variant_id, logical_store_id, source_store_code, partner_sku, barcode,",
            "forwarder_code, forwarder_name, route_code, route_name, service_code, service_name,",
            "site_code, transport_mode, target_platform, delivery_city, currency, unit_price, billing_unit, estimated_amount,",
            "source_type, source_shipping_order_id, source_shipping_order_line_id, source_quote_line_id,",
            "source_actual_bill_id, source_actual_component_id, source_filename, effective_status, raw_snapshot_json,",
            "is_deleted, confirmed_at, confirmed_by, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.productMasterId}, #{row.productVariantId}, #{row.logicalStoreId}, #{row.sourceStoreCode}, #{row.partnerSku}, #{row.barcode},",
            "#{row.forwarderCode}, #{row.forwarderName}, #{row.routeCode}, #{row.routeName}, #{row.serviceCode}, #{row.serviceName},",
            "#{row.siteCode}, #{row.transportMode}, #{row.targetPlatform}, #{row.deliveryCity}, #{row.currency}, #{row.unitPrice}, #{row.billingUnit}, #{row.estimatedAmount},",
            "#{row.sourceType}, #{row.sourceShippingOrderId}, #{row.sourceShippingOrderLineId}, #{row.sourceQuoteLineId},",
            "#{row.sourceActualBillId}, #{row.sourceActualComponentId}, #{row.sourceFilename}, #{row.effectiveStatus}, #{row.rawSnapshotJson},",
            "b'0', NOW(), #{operatorUserId}, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertProductForwarderChannelQuote(
            @Param("row") ProductForwarderChannelQuoteRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, logical_store_id AS logicalStoreId,",
            "       source_store_code AS sourceStoreCode, partner_sku AS partnerSku, barcode,",
            "       forwarder_code AS forwarderCode, forwarder_name AS forwarderName,",
            "       route_code AS routeCode, route_name AS routeName,",
            "       service_code AS serviceCode, service_name AS serviceName,",
            "       site_code AS siteCode, transport_mode AS transportMode,",
            "       target_platform AS targetPlatform, delivery_city AS deliveryCity,",
            "       currency, unit_price AS unitPrice, billing_unit AS billingUnit, estimated_amount AS estimatedAmount,",
            "       source_type AS sourceType, source_shipping_order_id AS sourceShippingOrderId,",
            "       source_shipping_order_line_id AS sourceShippingOrderLineId, source_quote_line_id AS sourceQuoteLineId,",
            "       source_actual_bill_id AS sourceActualBillId, source_actual_component_id AS sourceActualComponentId,",
            "       source_filename AS sourceFilename, effective_status AS effectiveStatus, raw_snapshot_json AS rawSnapshotJson",
            "FROM product_forwarder_channel_quote",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND forwarder_code = #{forwarderCode}",
            "<choose>",
            "  <when test='partnerSku != null and partnerSku != \"\"'>",
            "    AND UPPER(partner_sku) = UPPER(#{partnerSku})",
            "    <if test='logicalStoreId != null'>",
            "      AND (logical_store_id IS NULL OR logical_store_id = #{logicalStoreId})",
            "    </if>",
            "    <if test='sourceStoreCode != null and sourceStoreCode != \"\"'>",
            "      AND (source_store_code IS NULL OR TRIM(source_store_code) = '' OR UPPER(source_store_code) = UPPER(#{sourceStoreCode}))",
            "    </if>",
            "  </when>",
            "  <otherwise>",
            "    AND product_variant_id = #{productVariantId}",
            "  </otherwise>",
            "</choose>",
            "  AND COALESCE(site_code, '') = COALESCE(#{siteCode}, '')",
            "  AND COALESCE(route_code, '') = COALESCE(#{routeCode}, '')",
            "  AND COALESCE(service_code, '') = COALESCE(#{serviceCode}, '')",
            "  AND effective_status = 'CURRENT'",
            "  AND is_deleted = b'0'",
            "ORDER BY CASE",
            "           WHEN source_store_code IS NOT NULL",
            "            AND TRIM(source_store_code) != ''",
            "            AND UPPER(source_store_code) = UPPER(#{sourceStoreCode}) THEN 0",
            "           ELSE 1",
            "         END,",
            "         confirmed_at DESC, id DESC",
            "LIMIT 1",
            "</script>"
    })
    ProductForwarderChannelQuoteRecord selectCurrentProductForwarderChannelQuote(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceStoreCode") String sourceStoreCode,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("productVariantId") Long productVariantId,
            @Param("forwarderCode") String forwarderCode,
            @Param("siteCode") String siteCode,
            @Param("routeCode") String routeCode,
            @Param("serviceCode") String serviceCode
    );

    @Update({
            "UPDATE logistics_expected_bill",
            "SET bill_status = 'CANCELLED',",
            "    cancelled_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND shipping_order_id = #{shippingOrderId}",
            "  AND ((#{shippingOrderSegmentId} IS NULL AND shipping_order_segment_id IS NULL)",
            "       OR shipping_order_segment_id = #{shippingOrderSegmentId})",
            "  AND bill_status IN ('DRAFT', 'GENERATED')",
            "  AND is_deleted = b'0'"
    })
    int cancelOpenLogisticsExpectedBills(
            @Param("ownerUserId") Long ownerUserId,
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("shippingOrderSegmentId") Long shippingOrderSegmentId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE logistics_bill_reconciliation",
            "SET reconciliation_status = 'CANCELLED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND shipping_order_id = #{shippingOrderId}",
            "  AND ((#{shippingOrderSegmentId} IS NULL AND shipping_order_segment_id IS NULL)",
            "       OR shipping_order_segment_id = #{shippingOrderSegmentId})",
            "  AND reconciliation_status IN ('PENDING_ACTUAL_BILL', 'MATCHED', 'DIFF_FOUND')",
            "  AND is_deleted = b'0'"
    })
    int cancelOpenLogisticsBillReconciliations(
            @Param("ownerUserId") Long ownerUserId,
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("shippingOrderSegmentId") Long shippingOrderSegmentId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_expected_bill (",
            "id, owner_user_id, expected_bill_no, shipping_order_id, shipping_order_no, shipping_order_segment_id, shipping_order_segment_no,",
            "forwarder_code, forwarder_name, route_code, route_name, service_code, service_name, transport_mode,",
            "currency, exchange_rate_to_cny, expected_total_amount, expected_total_cny, component_count,",
            "bill_status, generated_from, generated_at, raw_snapshot_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.expectedBillNo}, #{row.shippingOrderId}, #{row.shippingOrderNo}, #{row.shippingOrderSegmentId}, #{row.shippingOrderSegmentNo},",
            "#{row.forwarderCode}, #{row.forwarderName}, #{row.routeCode}, #{row.routeName}, #{row.serviceCode}, #{row.serviceName}, #{row.transportMode},",
            "#{row.currency}, #{row.exchangeRateToCny}, #{row.expectedTotalAmount}, #{row.expectedTotalCny}, #{row.componentCount},",
            "#{row.billStatus}, #{row.generatedFrom}, NOW(), #{row.rawSnapshotJson},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertLogisticsExpectedBill(
            @Param("row") LogisticsExpectedBillRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_expected_bill_component (",
            "id, owner_user_id, expected_bill_id, shipping_order_id, shipping_order_segment_id, shipping_order_line_id, quote_line_id,",
            "product_master_id, product_variant_id, barcode, psku_code, site_code, box_no, fee_type, raw_fee_name,",
            "quantity, charge_quantity, charge_unit, unit_price, currency, exchange_rate_to_cny,",
            "expected_amount, expected_amount_cny, allocation_basis, raw_snapshot_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.expectedBillId}, #{row.shippingOrderId}, #{row.shippingOrderSegmentId}, #{row.shippingOrderLineId}, #{row.quoteLineId},",
            "#{row.productMasterId}, #{row.productVariantId}, #{row.barcode}, #{row.pskuCode}, #{row.siteCode}, #{row.boxNo}, #{row.feeType}, #{row.rawFeeName},",
            "#{row.quantity}, #{row.chargeQuantity}, #{row.chargeUnit}, #{row.unitPrice}, #{row.currency}, #{row.exchangeRateToCny},",
            "#{row.expectedAmount}, #{row.expectedAmountCny}, #{row.allocationBasis}, #{row.rawSnapshotJson},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertLogisticsExpectedBillComponent(
            @Param("row") LogisticsExpectedBillComponentRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_bill_reconciliation (",
            "id, owner_user_id, shipping_order_id, shipping_order_segment_id, expected_bill_id, actual_bill_id, reconciliation_no,",
            "reconciliation_status, expected_total_cny, actual_total_cny, diff_amount_cny, diff_rate,",
            "matched_component_count, unmatched_expected_count, unmatched_actual_count, summary_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.shippingOrderId}, #{row.shippingOrderSegmentId}, #{row.expectedBillId}, #{row.actualBillId}, #{row.reconciliationNo},",
            "#{row.reconciliationStatus}, #{row.expectedTotalCny}, #{row.actualTotalCny}, #{row.diffAmountCny}, #{row.diffRate},",
            "#{row.matchedComponentCount}, #{row.unmatchedExpectedCount}, #{row.unmatchedActualCount}, #{row.summaryJson},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertLogisticsBillReconciliation(
            @Param("row") LogisticsBillReconciliationRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "<script>",
            "SELECT bill.id, bill.owner_user_id AS ownerUserId, bill.expected_bill_no AS expectedBillNo,",
            "       bill.shipping_order_id AS shippingOrderId, bill.shipping_order_no AS shippingOrderNo,",
            "       COALESCE(so.title, bill.shipping_order_no) AS shippingOrderTitle,",
            "       bill.shipping_order_segment_id AS shippingOrderSegmentId, bill.shipping_order_segment_no AS shippingOrderSegmentNo,",
            "       bill.forwarder_code AS forwarderCode, bill.forwarder_name AS forwarderName,",
            "       bill.route_code AS routeCode, bill.route_name AS routeName,",
            "       bill.service_code AS serviceCode, bill.service_name AS serviceName,",
            "       bill.transport_mode AS transportMode, bill.currency, bill.exchange_rate_to_cny AS exchangeRateToCny,",
            "       bill.expected_total_amount AS expectedTotalAmount, bill.expected_total_cny AS expectedTotalCny,",
            "       bill.component_count AS componentCount, bill.bill_status AS billStatus, bill.generated_from AS generatedFrom,",
            "       reconciliation.reconciliation_status AS reconciliationStatus,",
            "       reconciliation.actual_total_cny AS actualTotalCny, reconciliation.diff_amount_cny AS diffAmountCny,",
            "       DATE_FORMAT(bill.gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(bill.gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM logistics_expected_bill bill",
            "LEFT JOIN procurement_shipping_order so",
            "  ON so.id = bill.shipping_order_id",
            " AND so.owner_user_id = bill.owner_user_id",
            " AND so.is_deleted = b'0'",
            "LEFT JOIN logistics_bill_reconciliation reconciliation",
            "  ON reconciliation.expected_bill_id = bill.id",
            " AND reconciliation.owner_user_id = bill.owner_user_id",
            " AND reconciliation.is_deleted = b'0'",
            " AND reconciliation.reconciliation_status != 'CANCELLED'",
            "WHERE bill.owner_user_id = #{ownerUserId}",
            "  AND bill.is_deleted = b'0'",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (bill.expected_bill_no LIKE CONCAT('%', #{keyword}, '%')",
            "       OR bill.shipping_order_no LIKE CONCAT('%', #{keyword}, '%')",
            "       OR bill.shipping_order_segment_no LIKE CONCAT('%', #{keyword}, '%')",
            "       OR bill.forwarder_name LIKE CONCAT('%', #{keyword}, '%')",
            "       OR bill.route_name LIKE CONCAT('%', #{keyword}, '%')",
            "       OR EXISTS (",
            "           SELECT 1 FROM logistics_expected_bill_component component",
            "           WHERE component.expected_bill_id = bill.id",
            "             AND component.is_deleted = b'0'",
            "             AND (component.barcode LIKE CONCAT('%', #{keyword}, '%')",
            "                  OR component.psku_code LIKE CONCAT('%', #{keyword}, '%'))",
            "       ))",
            "</if>",
            "ORDER BY bill.gmt_create DESC, bill.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<LogisticsExpectedBillRecord> listLogisticsBills(
            @Param("ownerUserId") Long ownerUserId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT bill.id, bill.owner_user_id AS ownerUserId, bill.expected_bill_no AS expectedBillNo,",
            "       bill.shipping_order_id AS shippingOrderId, bill.shipping_order_no AS shippingOrderNo,",
            "       COALESCE(so.title, bill.shipping_order_no) AS shippingOrderTitle,",
            "       bill.shipping_order_segment_id AS shippingOrderSegmentId, bill.shipping_order_segment_no AS shippingOrderSegmentNo,",
            "       bill.forwarder_code AS forwarderCode, bill.forwarder_name AS forwarderName,",
            "       bill.route_code AS routeCode, bill.route_name AS routeName,",
            "       bill.service_code AS serviceCode, bill.service_name AS serviceName,",
            "       bill.transport_mode AS transportMode, bill.currency, bill.exchange_rate_to_cny AS exchangeRateToCny,",
            "       bill.expected_total_amount AS expectedTotalAmount, bill.expected_total_cny AS expectedTotalCny,",
            "       bill.component_count AS componentCount, bill.bill_status AS billStatus, bill.generated_from AS generatedFrom,",
            "       reconciliation.reconciliation_status AS reconciliationStatus,",
            "       reconciliation.actual_total_cny AS actualTotalCny, reconciliation.diff_amount_cny AS diffAmountCny,",
            "       DATE_FORMAT(bill.gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(bill.gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM logistics_expected_bill bill",
            "LEFT JOIN procurement_shipping_order so",
            "  ON so.id = bill.shipping_order_id",
            " AND so.owner_user_id = bill.owner_user_id",
            " AND so.is_deleted = b'0'",
            "LEFT JOIN logistics_bill_reconciliation reconciliation",
            "  ON reconciliation.expected_bill_id = bill.id",
            " AND reconciliation.owner_user_id = bill.owner_user_id",
            " AND reconciliation.is_deleted = b'0'",
            " AND reconciliation.reconciliation_status != 'CANCELLED'",
            "WHERE bill.owner_user_id = #{ownerUserId}",
            "  AND bill.id = #{expectedBillId}",
            "  AND bill.is_deleted = b'0'",
            "LIMIT 1"
    })
    LogisticsExpectedBillRecord selectLogisticsBillById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedBillId") Long expectedBillId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, expected_bill_id AS expectedBillId,",
            "       shipping_order_id AS shippingOrderId, shipping_order_segment_id AS shippingOrderSegmentId, shipping_order_line_id AS shippingOrderLineId,",
            "       quote_line_id AS quoteLineId, product_master_id AS productMasterId, product_variant_id AS productVariantId,",
            "       barcode, psku_code AS pskuCode, site_code AS siteCode, box_no AS boxNo, fee_type AS feeType, raw_fee_name AS rawFeeName,",
            "       quantity, charge_quantity AS chargeQuantity, charge_unit AS chargeUnit, unit_price AS unitPrice,",
            "       currency, exchange_rate_to_cny AS exchangeRateToCny, expected_amount AS expectedAmount, expected_amount_cny AS expectedAmountCny,",
            "       allocation_basis AS allocationBasis",
            "FROM logistics_expected_bill_component",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND expected_bill_id = #{expectedBillId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<LogisticsExpectedBillComponentRecord> listLogisticsBillComponents(
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedBillId") Long expectedBillId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, shipping_order_no AS shippingOrderNo, title, status,",
            "       purchase_order_count AS purchaseOrderCount, line_count AS lineCount, sku_count AS skuCount,",
            "       total_quantity AS totalQuantity, store_summary_json AS storeSummaryJson, site_summary_json AS siteSummaryJson,",
            "       (",
            "         SELECT COUNT(1)",
            "         FROM procurement_shipping_order_line sol",
            "         LEFT JOIN procurement_shipping_order_segment segment",
            "           ON segment.id = sol.shipping_order_segment_id",
            "          AND segment.is_deleted = b'0'",
            "         LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "           ON quote.id = sol.quote_line_id",
            "          AND quote.is_deleted = b'0'",
            "         WHERE sol.shipping_order_id = procurement_shipping_order.id",
            "           AND sol.is_deleted = b'0'",
            "           AND UPPER(COALESCE(quote.forwarder_code, segment.forwarder_code, procurement_shipping_order.forwarder_code, '')) = 'YT'",
            "           AND (sol.yite_material IS NULL OR TRIM(sol.yite_material) = '')",
            "       ) AS missingYiteMaterialCount,",
            "       transport_summary_json AS transportSummaryJson, quote_status AS quoteStatus,",
            "       shipping_submit_status AS shippingSubmitStatus, forwarder_code AS forwarderCode, forwarder_name AS forwarderName,",
            "       route_code AS routeCode, route_name AS routeName, service_code AS serviceCode, service_name AS serviceName,",
            "       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i') AS submittedAt, remark,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM procurement_shipping_order",
            "WHERE id = #{shippingOrderId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ShippingOrderRecord selectShippingOrderById(@Param("shippingOrderId") Long shippingOrderId);

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, shipping_order_no AS shippingOrderNo, title, status,",
            "       purchase_order_count AS purchaseOrderCount, line_count AS lineCount, sku_count AS skuCount,",
            "       total_quantity AS totalQuantity, store_summary_json AS storeSummaryJson, site_summary_json AS siteSummaryJson,",
            "       (",
            "         SELECT COUNT(1)",
            "         FROM procurement_shipping_order_line sol",
            "         LEFT JOIN procurement_shipping_order_segment segment",
            "           ON segment.id = sol.shipping_order_segment_id",
            "          AND segment.is_deleted = b'0'",
            "         LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "           ON quote.id = sol.quote_line_id",
            "          AND quote.is_deleted = b'0'",
            "         WHERE sol.shipping_order_id = procurement_shipping_order.id",
            "           AND sol.is_deleted = b'0'",
            "           AND UPPER(COALESCE(quote.forwarder_code, segment.forwarder_code, procurement_shipping_order.forwarder_code, '')) = 'YT'",
            "           AND (sol.yite_material IS NULL OR TRIM(sol.yite_material) = '')",
            "       ) AS missingYiteMaterialCount,",
            "       transport_summary_json AS transportSummaryJson, quote_status AS quoteStatus,",
            "       shipping_submit_status AS shippingSubmitStatus, forwarder_code AS forwarderCode, forwarder_name AS forwarderName,",
            "       route_code AS routeCode, route_name AS routeName, service_code AS serviceCode, service_name AS serviceName,",
            "       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i') AS submittedAt, remark,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM procurement_shipping_order",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (shipping_order_no LIKE CONCAT('%', #{keyword}, '%') OR title LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY gmt_create DESC, id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ShippingOrderRecord> listShippingOrders(
            @Param("ownerUserId") Long ownerUserId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT id, shipping_order_id AS shippingOrderId, owner_user_id AS ownerUserId, segment_no AS segmentNo,",
            "       site_code AS siteCode, transport_mode AS transportMode,",
            "       forwarder_code AS forwarderCode, forwarder_name AS forwarderName,",
            "       route_code AS routeCode, route_name AS routeName, service_code AS serviceCode, service_name AS serviceName,",
            "       quote_status AS quoteStatus, shipping_submit_status AS shippingSubmitStatus,",
            "       line_count AS lineCount, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       missing_yite_material_count AS missingYiteMaterialCount,",
            "       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i') AS submittedAt,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM procurement_shipping_order_segment",
            "WHERE shipping_order_id = #{shippingOrderId}",
            "  AND is_deleted = b'0'",
            "ORDER BY site_code ASC, transport_mode ASC, id ASC"
    })
    List<ShippingOrderSegmentRecord> listShippingOrderSegments(@Param("shippingOrderId") Long shippingOrderId);

    @Select({
            "SELECT sol.id, sol.shipping_order_id AS shippingOrderId, sol.shipping_order_segment_id AS shippingOrderSegmentId,",
            "       segment.segment_no AS shippingOrderSegmentNo, sol.owner_user_id AS ownerUserId, sol.logical_store_id AS logicalStoreId,",
            "       COALESCE(source_site.store_code, sol.source_store_code) AS sourceStoreCode, sol.source_store_name AS sourceStoreName,",
            "       sol.purchase_order_id AS purchaseOrderId, sol.purchase_order_no AS purchaseOrderNo, sol.purchase_order_title AS purchaseOrderTitle,",
            "       sol.purchase_order_item_id AS purchaseOrderItemId, sol.purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       sol.product_master_id AS productMasterId, sol.product_variant_id AS productVariantId, sol.sku_parent AS skuParent,",
            "       sol.partner_sku AS partnerSku,",
            "       (",
            "         SELECT COALESCE(MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END), MAX(pb.barcode))",
            "         FROM product_barcode pb",
            "         WHERE pb.variant_id = sol.product_variant_id",
            "           AND pb.is_deleted = b'0'",
            "       ) AS barcode,",
            "       COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(sol.title_cache, ''), NULLIF(pm.title_cache, ''), NULLIF(public_detail.title_en, '')) AS titleCache,",
            "       COALESCE(NULLIF(public_detail.title_en, ''), NULLIF(pm.title_cache, ''), NULLIF(sol.title_cache, '')) AS titleEn,",
            "       COALESCE(NULLIF(public_detail.main_image_url, ''), NULLIF(pm.cover_image_url, ''), NULLIF(sol.image_url_cache, '')) AS imageUrlCache,",
            "       sol.site_code AS siteCode, sol.psku_code AS pskuCode, sol.yite_material AS yiteMaterial, sol.planned_transport_mode AS plannedTransportMode,",
            "       sol.quantity, sol.fulfillment_type AS fulfillmentType, sol.quote_line_id AS quoteLineId,",
            "       quote.currency AS currency, quote.unit_price AS unitPrice, quote.billing_unit AS billingUnit,",
            "       COALESCE(quote.quote_status, 'PENDING_QUOTE') AS quoteStatus,",
            "       COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS shippingSubmitStatus",
            "FROM procurement_shipping_order_line sol",
            "LEFT JOIN procurement_shipping_order_segment segment",
            "  ON segment.id = sol.shipping_order_segment_id",
            " AND segment.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_item_site item_site",
            "  ON item_site.id = sol.purchase_order_item_site_id",
            " AND item_site.is_deleted = b'0'",
            "LEFT JOIN logical_store_site source_site",
            "  ON source_site.id = item_site.site_id",
            " AND source_site.is_deleted = b'0'",
            "LEFT JOIN product_master pm",
            "  ON pm.id = sol.product_master_id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_public_detail_snapshot public_detail",
            "  ON public_detail.product_variant_id = sol.product_variant_id",
            " AND public_detail.site_code = sol.site_code",
            " AND public_detail.source_platform = 'NOON'",
            " AND public_detail.is_latest = b'1'",
            " AND public_detail.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.shipping_order_id = sol.shipping_order_id",
            " AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE sol.shipping_order_id = #{shippingOrderId}",
            "  AND sol.id = #{shippingOrderLineId}",
            "  AND sol.owner_user_id = #{ownerUserId}",
            "  AND sol.is_deleted = b'0'",
            "LIMIT 1"
    })
    ShippingOrderLineRecord selectShippingOrderLineById(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("shippingOrderLineId") Long shippingOrderLineId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT sol.id, sol.shipping_order_id AS shippingOrderId, sol.shipping_order_segment_id AS shippingOrderSegmentId,",
            "       segment.segment_no AS shippingOrderSegmentNo, sol.owner_user_id AS ownerUserId, sol.logical_store_id AS logicalStoreId,",
            "       COALESCE(source_site.store_code, sol.source_store_code) AS sourceStoreCode, sol.source_store_name AS sourceStoreName,",
            "       sol.purchase_order_id AS purchaseOrderId, sol.purchase_order_no AS purchaseOrderNo, sol.purchase_order_title AS purchaseOrderTitle,",
            "       sol.purchase_order_item_id AS purchaseOrderItemId, sol.purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       sol.product_master_id AS productMasterId, sol.product_variant_id AS productVariantId, sol.sku_parent AS skuParent,",
            "       sol.partner_sku AS partnerSku,",
            "       (",
            "         SELECT COALESCE(MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END), MAX(pb.barcode))",
            "         FROM product_barcode pb",
            "         WHERE pb.variant_id = sol.product_variant_id",
            "           AND pb.is_deleted = b'0'",
            "       ) AS barcode,",
            "       COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(sol.title_cache, ''), NULLIF(pm.title_cache, ''), NULLIF(public_detail.title_en, '')) AS titleCache,",
            "       COALESCE(NULLIF(public_detail.title_en, ''), NULLIF(pm.title_cache, ''), NULLIF(sol.title_cache, '')) AS titleEn,",
            "       COALESCE(NULLIF(public_detail.main_image_url, ''), NULLIF(pm.cover_image_url, ''), NULLIF(sol.image_url_cache, '')) AS imageUrlCache,",
            "       sol.site_code AS siteCode, sol.psku_code AS pskuCode, sol.yite_material AS yiteMaterial, sol.planned_transport_mode AS plannedTransportMode,",
            "       sol.quantity, sol.fulfillment_type AS fulfillmentType, sol.quote_line_id AS quoteLineId,",
            "       quote.currency AS currency, quote.unit_price AS unitPrice, quote.billing_unit AS billingUnit,",
            "       COALESCE(quote.quote_status, 'PENDING_QUOTE') AS quoteStatus,",
            "       COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS shippingSubmitStatus",
            "FROM procurement_shipping_order_line sol",
            "LEFT JOIN procurement_shipping_order_segment segment",
            "  ON segment.id = sol.shipping_order_segment_id",
            " AND segment.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_item_site item_site",
            "  ON item_site.id = sol.purchase_order_item_site_id",
            " AND item_site.is_deleted = b'0'",
            "LEFT JOIN logical_store_site source_site",
            "  ON source_site.id = item_site.site_id",
            " AND source_site.is_deleted = b'0'",
            "LEFT JOIN product_master pm",
            "  ON pm.id = sol.product_master_id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_public_detail_snapshot public_detail",
            "  ON public_detail.product_variant_id = sol.product_variant_id",
            " AND public_detail.site_code = sol.site_code",
            " AND public_detail.source_platform = 'NOON'",
            " AND public_detail.is_latest = b'1'",
            " AND public_detail.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.shipping_order_id = sol.shipping_order_id",
            " AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE sol.shipping_order_id = #{shippingOrderId}",
            "  AND sol.is_deleted = b'0'",
            "ORDER BY segment.site_code ASC, segment.transport_mode ASC, sol.source_store_code ASC, sol.purchase_order_id ASC, sol.partner_sku ASC, sol.site_code ASC, sol.id ASC"
    })
    List<ShippingOrderLineRecord> listShippingOrderLines(@Param("shippingOrderId") Long shippingOrderId);

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
            "      WHEN po.status = 'SUBMITTED' THEN 'SUBMITTED'",
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

    @Select({
            "SELECT quote.id, site.owner_user_id AS ownerUserId, site.logical_store_id AS logicalStoreId,",
            "       source_site.store_code AS sourceStoreCode, po.project_name_cache AS sourceStoreName,",
            "       quote.shipping_order_id AS shippingOrderId, quote.shipping_order_no AS shippingOrderNo,",
            "       site.purchase_order_id AS purchaseOrderId, po.order_no AS purchaseOrderNo, po.title AS purchaseOrderTitle,",
            "       site.purchase_order_item_id AS purchaseOrderItemId, site.id AS purchaseOrderItemSiteId,",
            "       item.product_master_id AS productMasterId, item.product_variant_id AS productVariantId,",
            "       item.sku_parent AS skuParent, item.partner_sku AS partnerSku,",
            "       (",
            "         SELECT COALESCE(MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END), MAX(pb.barcode))",
            "         FROM product_barcode pb",
            "         WHERE pb.variant_id = item.product_variant_id",
            "           AND pb.is_deleted = b'0'",
            "       ) AS barcode,",
            "       COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(item.title_cache, ''), NULLIF(pm.title_cache, ''), NULLIF(public_detail.title_en, '')) AS titleCache,",
            "       COALESCE(NULLIF(public_detail.title_en, ''), NULLIF(pm.title_cache, ''), NULLIF(item.title_cache, '')) AS titleEn,",
            "       COALESCE(NULLIF(public_detail.main_image_url, ''), NULLIF(pm.cover_image_url, ''), NULLIF(item.image_url_cache, '')) AS imageUrlCache,",
            "       COALESCE(public_detail.brand, pm.brand_cache) AS brandName,",
            "       site.site_code AS siteCode, site.psku_code AS pskuCode, quote.yite_material AS yiteMaterial, site.transport_mode AS plannedTransportMode,",
            "       site.quantity, COALESCE(item.fulfillment_type, 'WAREHOUSE_RECEIPT') AS fulfillmentType,",
            "       COALESCE(balance.is_new_product, b'0') = b'1' AS isNewProduct,",
            "       COALESCE(quote.quote_status, 'PENDING_QUOTE') AS quoteStatus,",
            "       COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS shippingSubmitStatus,",
            "       quote.forwarder_code AS forwarderCode, quote.forwarder_name AS forwarderName,",
            "       quote.route_code AS routeCode, quote.route_name AS routeName,",
            "       quote.service_code AS serviceCode, quote.service_name AS serviceName,",
            "       quote.currency, quote.unit_price AS unitPrice, quote.billing_unit AS billingUnit,",
            "       quote.estimated_amount AS estimatedAmount, quote.remark,",
            "       COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS productLengthCm,",
            "       COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS productWidthCm,",
            "       COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS productHeightCm,",
            "       COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS productWeightG,",
            "       COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS cartonLengthCm,",
            "       COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS cartonWidthCm,",
            "       COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS cartonHeightCm,",
            "       COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS cartonWeightKg,",
            "       COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS cartonQuantity,",
            "       DATE_FORMAT(quote.exported_at, '%Y-%m-%d %H:%i') AS exportedAt,",
            "       DATE_FORMAT(quote.confirmed_at, '%Y-%m-%d %H:%i') AS confirmedAt,",
            "       DATE_FORMAT(quote.shipping_submitted_at, '%Y-%m-%d %H:%i') AS shippingSubmittedAt",
            "FROM procurement_purchase_order_item_site site",
            "JOIN procurement_purchase_order_item item",
            "  ON item.id = site.purchase_order_item_id",
            " AND item.is_deleted = b'0'",
            "JOIN procurement_purchase_order po",
            "  ON po.id = site.purchase_order_id",
            " AND po.is_deleted = b'0'",
            "LEFT JOIN logical_store_site source_site",
            "  ON source_site.id = site.site_id",
            " AND source_site.is_deleted = b'0'",
            "LEFT JOIN product_master pm",
            "  ON pm.id = item.product_master_id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = item.product_variant_id",
            " AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source pvss",
            "  ON pvss.id = pvs.effective_source_id",
            " AND pvss.variant_id = item.product_variant_id",
            " AND pvss.is_deleted = b'0'",
            "LEFT JOIN product_public_detail_snapshot public_detail",
            "  ON public_detail.product_variant_id = item.product_variant_id",
            " AND public_detail.site_code = site.site_code",
            " AND public_detail.source_platform = 'NOON'",
            " AND public_detail.is_latest = b'1'",
            " AND public_detail.is_deleted = b'0'",
            "LEFT JOIN procurement_fulfillment_balance balance",
            "  ON balance.purchase_order_item_site_id = site.id",
            " AND balance.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.purchase_order_item_site_id = site.id",
            " AND quote.shipping_order_id IS NULL",
            " AND quote.is_deleted = b'0'",
            "WHERE site.purchase_order_id = #{orderId}",
            "  AND site.is_deleted = b'0'",
            "ORDER BY site.site_code ASC, site.transport_mode ASC, item.partner_sku ASC, site.id ASC"
    })
    List<PurchaseOrderLogisticsQuoteLineRecord> listLogisticsQuoteCandidatesByOrder(@Param("orderId") Long orderId);

    @Select({
            "SELECT quote.id, sol.owner_user_id AS ownerUserId, sol.logical_store_id AS logicalStoreId,",
            "       COALESCE(source_site.store_code, sol.source_store_code) AS sourceStoreCode, sol.source_store_name AS sourceStoreName,",
            "       sol.shipping_order_id AS shippingOrderId, so.shipping_order_no AS shippingOrderNo,",
            "       sol.shipping_order_segment_id AS shippingOrderSegmentId, segment.segment_no AS shippingOrderSegmentNo,",
            "       sol.id AS shippingOrderLineId,",
            "       sol.purchase_order_id AS purchaseOrderId, sol.purchase_order_no AS purchaseOrderNo, sol.purchase_order_title AS purchaseOrderTitle,",
            "       sol.purchase_order_item_id AS purchaseOrderItemId, sol.purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       sol.product_master_id AS productMasterId, sol.product_variant_id AS productVariantId,",
            "       sol.sku_parent AS skuParent, sol.partner_sku AS partnerSku,",
            "       (",
            "         SELECT COALESCE(MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END), MAX(pb.barcode))",
            "         FROM product_barcode pb",
            "         WHERE pb.variant_id = sol.product_variant_id",
            "           AND pb.is_deleted = b'0'",
            "       ) AS barcode,",
            "       COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(sol.title_cache, ''), NULLIF(pm.title_cache, ''), NULLIF(public_detail.title_en, '')) AS titleCache,",
            "       COALESCE(NULLIF(public_detail.title_en, ''), NULLIF(pm.title_cache, ''), NULLIF(sol.title_cache, '')) AS titleEn,",
            "       COALESCE(NULLIF(public_detail.main_image_url, ''), NULLIF(pm.cover_image_url, ''), NULLIF(sol.image_url_cache, '')) AS imageUrlCache,",
            "       COALESCE(public_detail.brand, pm.brand_cache) AS brandName,",
            "       sol.site_code AS siteCode, sol.psku_code AS pskuCode, COALESCE(sol.yite_material, quote.yite_material) AS yiteMaterial, sol.planned_transport_mode AS plannedTransportMode,",
            "       sol.quantity, sol.fulfillment_type AS fulfillmentType,",
            "       COALESCE(balance.is_new_product, b'0') = b'1' AS isNewProduct,",
            "       COALESCE(quote.quote_status, 'PENDING_QUOTE') AS quoteStatus,",
            "       COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS shippingSubmitStatus,",
            "       quote.forwarder_code AS forwarderCode, quote.forwarder_name AS forwarderName,",
            "       quote.route_code AS routeCode, quote.route_name AS routeName,",
            "       quote.service_code AS serviceCode, quote.service_name AS serviceName,",
            "       quote.currency, quote.unit_price AS unitPrice, quote.billing_unit AS billingUnit,",
            "       quote.estimated_amount AS estimatedAmount, quote.remark,",
            "       COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS productLengthCm,",
            "       COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS productWidthCm,",
            "       COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS productHeightCm,",
            "       COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS productWeightG,",
            "       COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS cartonLengthCm,",
            "       COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS cartonWidthCm,",
            "       COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS cartonHeightCm,",
            "       COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS cartonWeightKg,",
            "       COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS cartonQuantity,",
            "       DATE_FORMAT(quote.exported_at, '%Y-%m-%d %H:%i') AS exportedAt,",
            "       DATE_FORMAT(quote.confirmed_at, '%Y-%m-%d %H:%i') AS confirmedAt,",
            "       DATE_FORMAT(quote.shipping_submitted_at, '%Y-%m-%d %H:%i') AS shippingSubmittedAt",
            "FROM procurement_shipping_order_line sol",
            "JOIN procurement_shipping_order so",
            "  ON so.id = sol.shipping_order_id",
            " AND so.is_deleted = b'0'",
            "LEFT JOIN procurement_shipping_order_segment segment",
            "  ON segment.id = sol.shipping_order_segment_id",
            " AND segment.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_item_site item_site",
            "  ON item_site.id = sol.purchase_order_item_site_id",
            " AND item_site.is_deleted = b'0'",
            "LEFT JOIN logical_store_site source_site",
            "  ON source_site.id = item_site.site_id",
            " AND source_site.is_deleted = b'0'",
            "LEFT JOIN product_master pm",
            "  ON pm.id = sol.product_master_id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = sol.product_variant_id",
            " AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source pvss",
            "  ON pvss.id = pvs.effective_source_id",
            " AND pvss.variant_id = sol.product_variant_id",
            " AND pvss.is_deleted = b'0'",
            "LEFT JOIN product_public_detail_snapshot public_detail",
            "  ON public_detail.product_variant_id = sol.product_variant_id",
            " AND public_detail.site_code = sol.site_code",
            " AND public_detail.source_platform = 'NOON'",
            " AND public_detail.is_latest = b'1'",
            " AND public_detail.is_deleted = b'0'",
            "LEFT JOIN procurement_fulfillment_balance balance",
            "  ON balance.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND balance.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.shipping_order_id = sol.shipping_order_id",
            " AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE sol.shipping_order_id = #{shippingOrderId}",
            "  AND sol.is_deleted = b'0'",
            "ORDER BY sol.site_code ASC, sol.planned_transport_mode ASC, sol.partner_sku ASC, sol.id ASC"
    })
    List<PurchaseOrderLogisticsQuoteLineRecord> listLogisticsQuoteCandidatesByShippingOrder(
            @Param("shippingOrderId") Long shippingOrderId
    );

    @Select({
            "<script>",
            "SELECT quote.id, sol.owner_user_id AS ownerUserId, sol.logical_store_id AS logicalStoreId,",
            "       COALESCE(source_site.store_code, sol.source_store_code) AS sourceStoreCode, sol.source_store_name AS sourceStoreName,",
            "       sol.shipping_order_id AS shippingOrderId, so.shipping_order_no AS shippingOrderNo,",
            "       sol.shipping_order_segment_id AS shippingOrderSegmentId, segment.segment_no AS shippingOrderSegmentNo,",
            "       sol.id AS shippingOrderLineId,",
            "       sol.purchase_order_id AS purchaseOrderId, sol.purchase_order_no AS purchaseOrderNo, sol.purchase_order_title AS purchaseOrderTitle,",
            "       sol.purchase_order_item_id AS purchaseOrderItemId, sol.purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       sol.product_master_id AS productMasterId, sol.product_variant_id AS productVariantId,",
            "       sol.sku_parent AS skuParent, sol.partner_sku AS partnerSku,",
            "       (",
            "         SELECT COALESCE(MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END), MAX(pb.barcode))",
            "         FROM product_barcode pb",
            "         WHERE pb.variant_id = sol.product_variant_id",
            "           AND pb.is_deleted = b'0'",
            "       ) AS barcode,",
            "       COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(sol.title_cache, ''), NULLIF(pm.title_cache, ''), NULLIF(public_detail.title_en, '')) AS titleCache,",
            "       COALESCE(NULLIF(public_detail.title_en, ''), NULLIF(pm.title_cache, ''), NULLIF(sol.title_cache, '')) AS titleEn,",
            "       COALESCE(NULLIF(public_detail.main_image_url, ''), NULLIF(pm.cover_image_url, ''), NULLIF(sol.image_url_cache, '')) AS imageUrlCache,",
            "       COALESCE(public_detail.brand, pm.brand_cache) AS brandName,",
            "       sol.site_code AS siteCode, sol.psku_code AS pskuCode, COALESCE(sol.yite_material, quote.yite_material) AS yiteMaterial, sol.planned_transport_mode AS plannedTransportMode,",
            "       sol.quantity, sol.fulfillment_type AS fulfillmentType,",
            "       COALESCE(balance.is_new_product, b'0') = b'1' AS isNewProduct,",
            "       COALESCE(quote.quote_status, 'PENDING_QUOTE') AS quoteStatus,",
            "       COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS shippingSubmitStatus,",
            "       quote.forwarder_code AS forwarderCode, quote.forwarder_name AS forwarderName,",
            "       quote.route_code AS routeCode, quote.route_name AS routeName,",
            "       quote.service_code AS serviceCode, quote.service_name AS serviceName,",
            "       quote.currency, quote.unit_price AS unitPrice, quote.billing_unit AS billingUnit,",
            "       quote.estimated_amount AS estimatedAmount, quote.remark,",
            "       COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS productLengthCm,",
            "       COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS productWidthCm,",
            "       COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS productHeightCm,",
            "       COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS productWeightG,",
            "       COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS cartonLengthCm,",
            "       COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS cartonWidthCm,",
            "       COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS cartonHeightCm,",
            "       COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS cartonWeightKg,",
            "       COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS cartonQuantity,",
            "       DATE_FORMAT(quote.exported_at, '%Y-%m-%d %H:%i') AS exportedAt,",
            "       DATE_FORMAT(quote.confirmed_at, '%Y-%m-%d %H:%i') AS confirmedAt,",
            "       DATE_FORMAT(quote.shipping_submitted_at, '%Y-%m-%d %H:%i') AS shippingSubmittedAt",
            "FROM procurement_shipping_order_line sol",
            "JOIN procurement_shipping_order so",
            "  ON so.id = sol.shipping_order_id",
            " AND so.is_deleted = b'0'",
            "LEFT JOIN procurement_shipping_order_segment segment",
            "  ON segment.id = sol.shipping_order_segment_id",
            " AND segment.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_item_site item_site",
            "  ON item_site.id = sol.purchase_order_item_site_id",
            " AND item_site.is_deleted = b'0'",
            "LEFT JOIN logical_store_site source_site",
            "  ON source_site.id = item_site.site_id",
            " AND source_site.is_deleted = b'0'",
            "LEFT JOIN product_master pm",
            "  ON pm.id = sol.product_master_id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = sol.product_variant_id",
            " AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source pvss",
            "  ON pvss.id = pvs.effective_source_id",
            " AND pvss.variant_id = sol.product_variant_id",
            " AND pvss.is_deleted = b'0'",
            "LEFT JOIN product_public_detail_snapshot public_detail",
            "  ON public_detail.product_variant_id = sol.product_variant_id",
            " AND public_detail.site_code = sol.site_code",
            " AND public_detail.source_platform = 'NOON'",
            " AND public_detail.is_latest = b'1'",
            " AND public_detail.is_deleted = b'0'",
            "LEFT JOIN procurement_fulfillment_balance balance",
            "  ON balance.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND balance.is_deleted = b'0'",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.shipping_order_id = sol.shipping_order_id",
            " AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE sol.shipping_order_id = #{shippingOrderId}",
            "  AND sol.shipping_order_segment_id IN",
            "  <foreach collection='segmentIds' item='segmentId' open='(' separator=',' close=')'>#{segmentId}</foreach>",
            "  AND sol.is_deleted = b'0'",
            "ORDER BY sol.site_code ASC, sol.planned_transport_mode ASC, sol.partner_sku ASC, sol.id ASC",
            "</script>"
    })
    List<PurchaseOrderLogisticsQuoteLineRecord> listLogisticsQuoteCandidatesByShippingOrderSegments(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("segmentIds") List<Long> segmentIds
    );

    @Insert({
            "INSERT INTO procurement_purchase_order_logistics_quote_line (",
            "id, owner_user_id, logical_store_id, shipping_order_id, shipping_order_no, shipping_order_segment_id, shipping_order_line_id,",
            "purchase_order_id, purchase_order_no, purchase_order_title,",
            "purchase_order_item_id, purchase_order_item_site_id, product_master_id, product_variant_id,",
            "sku_parent, partner_sku, title_cache, site_code, psku_code, yite_material, planned_transport_mode, quantity, fulfillment_type,",
            "is_new_product, quote_status, shipping_submit_status, forwarder_code, forwarder_name, route_code, route_name,",
            "service_code, service_name, currency, unit_price, billing_unit, estimated_amount, remark,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.shippingOrderId}, #{row.shippingOrderNo},",
            "#{row.shippingOrderSegmentId}, #{row.shippingOrderLineId},",
            "#{row.purchaseOrderId}, #{row.purchaseOrderNo}, #{row.purchaseOrderTitle},",
            "#{row.purchaseOrderItemId}, #{row.purchaseOrderItemSiteId}, #{row.productMasterId}, #{row.productVariantId},",
            "#{row.skuParent}, #{row.partnerSku}, #{row.titleCache}, #{row.siteCode}, #{row.pskuCode}, #{row.yiteMaterial}, #{row.plannedTransportMode},",
            "#{row.quantity}, #{row.fulfillmentType}, CASE WHEN #{row.isNewProduct} THEN b'1' ELSE b'0' END,",
            "#{row.quoteStatus}, #{row.shippingSubmitStatus}, #{row.forwarderCode}, #{row.forwarderName}, #{row.routeCode}, #{row.routeName},",
            "#{row.serviceCode}, #{row.serviceName}, #{row.currency}, #{row.unitPrice}, #{row.billingUnit}, #{row.estimatedAmount}, #{row.remark},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertLogisticsQuoteLine(
            @Param("row") PurchaseOrderLogisticsQuoteLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_quote_line",
            "SET shipping_order_id = #{row.shippingOrderId},",
            "    shipping_order_no = #{row.shippingOrderNo},",
            "    shipping_order_segment_id = #{row.shippingOrderSegmentId},",
            "    shipping_order_line_id = #{row.shippingOrderLineId},",
            "    purchase_order_no = #{row.purchaseOrderNo},",
            "    purchase_order_title = #{row.purchaseOrderTitle},",
            "    product_master_id = #{row.productMasterId},",
            "    product_variant_id = #{row.productVariantId},",
            "    sku_parent = #{row.skuParent},",
            "    partner_sku = #{row.partnerSku},",
            "    title_cache = #{row.titleCache},",
            "    site_code = #{row.siteCode},",
            "    psku_code = #{row.pskuCode},",
            "    yite_material = #{row.yiteMaterial},",
            "    planned_transport_mode = #{row.plannedTransportMode},",
            "    quantity = #{row.quantity},",
            "    fulfillment_type = #{row.fulfillmentType},",
            "    is_new_product = CASE WHEN #{row.isNewProduct} THEN b'1' ELSE b'0' END,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{row.id}",
            "  AND is_deleted = b'0'"
    })
    int refreshLogisticsQuoteLineSnapshot(
            @Param("row") PurchaseOrderLogisticsQuoteLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_quote_line",
            "SET forwarder_code = #{row.forwarderCode},",
            "    forwarder_name = #{row.forwarderName},",
            "    route_code = #{row.routeCode},",
            "    route_name = #{row.routeName},",
            "    service_code = #{row.serviceCode},",
            "    service_name = #{row.serviceName},",
            "    currency = #{row.currency},",
            "    billing_unit = #{row.billingUnit},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{row.id}",
            "  AND is_deleted = b'0'",
            "  AND quote_status != 'CONFIRMED'"
    })
    int assignLogisticsQuoteLineChannel(
            @Param("row") PurchaseOrderLogisticsQuoteLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       shipping_order_id AS shippingOrderId, shipping_order_no AS shippingOrderNo,",
            "       shipping_order_segment_id AS shippingOrderSegmentId, shipping_order_line_id AS shippingOrderLineId,",
            "       purchase_order_id AS purchaseOrderId, purchase_order_no AS purchaseOrderNo, purchase_order_title AS purchaseOrderTitle,",
            "       purchase_order_item_id AS purchaseOrderItemId, purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       product_master_id AS productMasterId, product_variant_id AS productVariantId, sku_parent AS skuParent,",
            "       partner_sku AS partnerSku, title_cache AS titleCache, site_code AS siteCode, psku_code AS pskuCode, yite_material AS yiteMaterial,",
            "       planned_transport_mode AS plannedTransportMode, quantity, fulfillment_type AS fulfillmentType,",
            "       is_new_product = b'1' AS isNewProduct, quote_status AS quoteStatus, shipping_submit_status AS shippingSubmitStatus,",
            "       forwarder_code AS forwarderCode, forwarder_name AS forwarderName, route_code AS routeCode, route_name AS routeName,",
            "       service_code AS serviceCode, service_name AS serviceName, currency, unit_price AS unitPrice,",
            "       billing_unit AS billingUnit, estimated_amount AS estimatedAmount, remark,",
            "       DATE_FORMAT(exported_at, '%Y-%m-%d %H:%i') AS exportedAt,",
            "       DATE_FORMAT(confirmed_at, '%Y-%m-%d %H:%i') AS confirmedAt,",
            "       DATE_FORMAT(shipping_submitted_at, '%Y-%m-%d %H:%i') AS shippingSubmittedAt",
            "FROM procurement_purchase_order_logistics_quote_line",
            "WHERE purchase_order_id = #{orderId}",
            "  AND purchase_order_item_site_id = #{itemSiteId}",
            "  AND shipping_order_id IS NULL",
            "  AND is_deleted = b'0'",
            "LIMIT 1",
            "FOR UPDATE"
    })
    PurchaseOrderLogisticsQuoteLineRecord selectLogisticsQuoteLineByItemSiteForUpdate(
            @Param("orderId") Long orderId,
            @Param("itemSiteId") Long itemSiteId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       shipping_order_id AS shippingOrderId, shipping_order_no AS shippingOrderNo,",
            "       shipping_order_segment_id AS shippingOrderSegmentId, shipping_order_line_id AS shippingOrderLineId,",
            "       purchase_order_id AS purchaseOrderId, purchase_order_no AS purchaseOrderNo, purchase_order_title AS purchaseOrderTitle,",
            "       purchase_order_item_id AS purchaseOrderItemId, purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       product_master_id AS productMasterId, product_variant_id AS productVariantId, sku_parent AS skuParent,",
            "       partner_sku AS partnerSku, title_cache AS titleCache, site_code AS siteCode, psku_code AS pskuCode, yite_material AS yiteMaterial,",
            "       planned_transport_mode AS plannedTransportMode, quantity, fulfillment_type AS fulfillmentType,",
            "       is_new_product = b'1' AS isNewProduct, quote_status AS quoteStatus, shipping_submit_status AS shippingSubmitStatus,",
            "       forwarder_code AS forwarderCode, forwarder_name AS forwarderName, route_code AS routeCode, route_name AS routeName,",
            "       service_code AS serviceCode, service_name AS serviceName, currency, unit_price AS unitPrice,",
            "       billing_unit AS billingUnit, estimated_amount AS estimatedAmount, remark,",
            "       DATE_FORMAT(exported_at, '%Y-%m-%d %H:%i') AS exportedAt,",
            "       DATE_FORMAT(confirmed_at, '%Y-%m-%d %H:%i') AS confirmedAt,",
            "       DATE_FORMAT(shipping_submitted_at, '%Y-%m-%d %H:%i') AS shippingSubmittedAt",
            "FROM procurement_purchase_order_logistics_quote_line",
            "WHERE shipping_order_id = #{shippingOrderId}",
            "  AND purchase_order_item_site_id = #{itemSiteId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1",
            "FOR UPDATE"
    })
    PurchaseOrderLogisticsQuoteLineRecord selectLogisticsQuoteLineByShippingOrderItemSiteForUpdate(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("itemSiteId") Long itemSiteId
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_quote_line",
            "SET quote_status = 'CONFIRMED',",
            "    shipping_submit_status = COALESCE(#{row.shippingSubmitStatus}, shipping_submit_status, 'NOT_SUBMITTED'),",
            "    forwarder_code = #{row.forwarderCode},",
            "    forwarder_name = #{row.forwarderName},",
            "    route_code = #{row.routeCode},",
            "    route_name = #{row.routeName},",
            "    service_code = #{row.serviceCode},",
            "    service_name = #{row.serviceName},",
            "    currency = #{row.currency},",
            "    unit_price = #{row.unitPrice},",
            "    billing_unit = #{row.billingUnit},",
            "    estimated_amount = #{row.estimatedAmount},",
            "    remark = #{row.remark},",
            "    confirmed_at = NOW(),",
            "    confirmed_by = #{operatorUserId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{row.id}",
            "  AND is_deleted = b'0'"
    })
    int confirmLogisticsQuoteLine(
            @Param("row") PurchaseOrderLogisticsQuoteLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE procurement_purchase_order_logistics_quote_line",
            "SET exported_at = NOW(),",
            "    exported_by = #{operatorUserId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE purchase_order_id = #{orderId}",
            "  AND id IN",
            "  <foreach collection='lineIds' item='lineId' open='(' separator=',' close=')'>#{lineId}</foreach>",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int markLogisticsQuoteLinesExported(
            @Param("orderId") Long orderId,
            @Param("lineIds") List<Long> lineIds,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE procurement_purchase_order_logistics_quote_line",
            "SET exported_at = NOW(),",
            "    exported_by = #{operatorUserId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE shipping_order_id = #{shippingOrderId}",
            "  AND id IN",
            "  <foreach collection='lineIds' item='lineId' open='(' separator=',' close=')'>#{lineId}</foreach>",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int markShippingOrderLogisticsQuoteLinesExported(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("lineIds") List<Long> lineIds,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM procurement_purchase_order_item_site site",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.purchase_order_item_site_id = site.id",
            " AND quote.shipping_order_id IS NULL",
            " AND quote.is_deleted = b'0'",
            "WHERE site.purchase_order_id = #{orderId}",
            "  AND site.is_deleted = b'0'",
            "  AND (quote.id IS NULL",
            "       OR quote.quote_status != 'CONFIRMED')"
    })
    int countUnconfirmedLogisticsQuoteLines(@Param("orderId") Long orderId);

    @Select({
            "SELECT COUNT(1)",
            "FROM procurement_shipping_order_line sol",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.shipping_order_id = sol.shipping_order_id",
            " AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE sol.shipping_order_id = #{shippingOrderId}",
            "  AND sol.is_deleted = b'0'",
            "  AND (quote.id IS NULL OR quote.quote_status != 'CONFIRMED')"
    })
    int countUnconfirmedLogisticsQuoteLinesByShippingOrder(@Param("shippingOrderId") Long shippingOrderId);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM procurement_shipping_order_line sol",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.shipping_order_id = sol.shipping_order_id",
            " AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE sol.shipping_order_id = #{shippingOrderId}",
            "  AND sol.shipping_order_segment_id IN",
            "  <foreach collection='segmentIds' item='segmentId' open='(' separator=',' close=')'>#{segmentId}</foreach>",
            "  AND sol.is_deleted = b'0'",
            "  AND (quote.id IS NULL OR quote.quote_status != 'CONFIRMED')",
            "</script>"
    })
    int countUnconfirmedLogisticsQuoteLinesByShippingOrderSegments(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("segmentIds") List<Long> segmentIds
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_quote_line",
            "SET shipping_submit_status = 'SUBMITTED',",
            "    shipping_submitted_at = NOW(),",
            "    shipping_submitted_by = #{operatorUserId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE purchase_order_id = #{orderId}",
            "  AND quote_status = 'CONFIRMED'",
            "  AND is_deleted = b'0'"
    })
    int submitLogisticsQuoteLinesForShipping(
            @Param("orderId") Long orderId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_purchase_order_logistics_quote_line",
            "SET shipping_submit_status = 'SUBMITTED',",
            "    shipping_submitted_at = NOW(),",
            "    shipping_submitted_by = #{operatorUserId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE shipping_order_id = #{shippingOrderId}",
            "  AND is_deleted = b'0'"
    })
    int submitLogisticsQuoteLinesForShippingOrder(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE procurement_purchase_order_logistics_quote_line quote",
            "JOIN procurement_shipping_order_line sol",
            "  ON sol.shipping_order_id = quote.shipping_order_id",
            " AND sol.purchase_order_item_site_id = quote.purchase_order_item_site_id",
            " AND sol.is_deleted = b'0'",
            "SET quote.shipping_submit_status = 'SUBMITTED',",
            "    quote.shipping_submitted_at = NOW(),",
            "    quote.shipping_submitted_by = #{operatorUserId},",
            "    quote.updated_by = #{operatorUserId},",
            "    quote.gmt_updated = NOW()",
            "WHERE quote.shipping_order_id = #{shippingOrderId}",
            "  AND sol.shipping_order_segment_id IN",
            "  <foreach collection='segmentIds' item='segmentId' open='(' separator=',' close=')'>#{segmentId}</foreach>",
            "  AND quote.is_deleted = b'0'",
            "</script>"
    })
    int submitLogisticsQuoteLinesForShippingOrderSegments(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("segmentIds") List<Long> segmentIds,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order",
            "SET shipping_submit_status = 'SUBMITTED',",
            "    submitted_at = NOW(),",
            "    submitted_by = #{operatorUserId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{shippingOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int markShippingOrderSubmitted(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE procurement_shipping_order_segment segment",
            "SET quote_status = CASE",
            "      WHEN EXISTS (",
            "        SELECT 1 FROM procurement_shipping_order_line sol",
            "        LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "          ON quote.shipping_order_id = sol.shipping_order_id",
            "         AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            "         AND quote.is_deleted = b'0'",
            "        WHERE sol.shipping_order_segment_id = segment.id",
            "          AND sol.is_deleted = b'0'",
            "          AND (quote.id IS NULL OR quote.quote_status != 'CONFIRMED')",
            "      ) THEN 'PENDING_QUOTE' ELSE 'CONFIRMED' END,",
            "    shipping_submit_status = CASE",
            "      WHEN NOT EXISTS (",
            "        SELECT 1 FROM procurement_shipping_order_line sol",
            "        JOIN procurement_purchase_order_logistics_quote_line quote",
            "          ON quote.shipping_order_id = sol.shipping_order_id",
            "         AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            "         AND quote.is_deleted = b'0'",
            "        WHERE sol.shipping_order_segment_id = segment.id",
            "          AND sol.is_deleted = b'0'",
            "          AND quote.shipping_submit_status = 'SUBMITTED'",
            "      ) THEN 'NOT_SUBMITTED'",
            "      WHEN EXISTS (",
            "        SELECT 1 FROM procurement_shipping_order_line sol",
            "        LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "          ON quote.shipping_order_id = sol.shipping_order_id",
            "         AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            "         AND quote.is_deleted = b'0'",
            "        WHERE sol.shipping_order_segment_id = segment.id",
            "          AND sol.is_deleted = b'0'",
            "          AND (quote.id IS NULL OR quote.shipping_submit_status != 'SUBMITTED')",
            "      ) THEN 'PARTIAL_SUBMITTED' ELSE 'SUBMITTED' END,",
            "    forwarder_code = #{row.forwarderCode},",
            "    forwarder_name = #{row.forwarderName},",
            "    route_code = #{row.routeCode},",
            "    route_name = #{row.routeName},",
            "    service_code = #{row.serviceCode},",
            "    service_name = #{row.serviceName},",
            "    missing_yite_material_count = (",
            "      SELECT COUNT(1) FROM procurement_shipping_order_line sol",
            "      WHERE sol.shipping_order_segment_id = segment.id",
            "        AND sol.is_deleted = b'0'",
            "        AND UPPER(COALESCE(segment.forwarder_code, '')) = 'YT'",
            "        AND (sol.yite_material IS NULL OR TRIM(sol.yite_material) = '')",
            "    ),",
            "    submitted_at = CASE",
            "      WHEN segment.shipping_submit_status = 'SUBMITTED' THEN COALESCE(segment.submitted_at, NOW())",
            "      ELSE segment.submitted_at END,",
            "    submitted_by = CASE",
            "      WHEN segment.shipping_submit_status = 'SUBMITTED' THEN COALESCE(segment.submitted_by, #{operatorUserId})",
            "      ELSE segment.submitted_by END,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE segment.shipping_order_id = #{shippingOrderId}",
            "  AND segment.id IN",
            "  <foreach collection='segmentIds' item='segmentId' open='(' separator=',' close=')'>#{segmentId}</foreach>",
            "  AND segment.is_deleted = b'0'",
            "</script>"
    })
    int refreshShippingOrderSegmentState(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("segmentIds") List<Long> segmentIds,
            @Param("row") PurchaseOrderLogisticsQuoteLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order so",
            "SET quote_status = CASE",
            "      WHEN EXISTS (",
            "        SELECT 1 FROM procurement_shipping_order_segment segment",
            "        WHERE segment.shipping_order_id = so.id",
            "          AND segment.is_deleted = b'0'",
            "          AND segment.quote_status != 'CONFIRMED'",
            "      ) THEN 'PENDING_QUOTE' ELSE 'CONFIRMED' END,",
            "    shipping_submit_status = CASE",
            "      WHEN NOT EXISTS (",
            "        SELECT 1 FROM procurement_shipping_order_segment segment",
            "        WHERE segment.shipping_order_id = so.id",
            "          AND segment.is_deleted = b'0'",
            "          AND segment.shipping_submit_status IN ('SUBMITTED', 'PARTIAL_SUBMITTED')",
            "      ) THEN 'NOT_SUBMITTED'",
            "      WHEN EXISTS (",
            "        SELECT 1 FROM procurement_shipping_order_segment segment",
            "        WHERE segment.shipping_order_id = so.id",
            "          AND segment.is_deleted = b'0'",
            "          AND segment.shipping_submit_status != 'SUBMITTED'",
            "      ) THEN 'PARTIAL_SUBMITTED' ELSE 'SUBMITTED' END,",
            "    submitted_at = CASE",
            "      WHEN so.shipping_submit_status = 'SUBMITTED' THEN COALESCE(so.submitted_at, NOW())",
            "      ELSE so.submitted_at END,",
            "    submitted_by = CASE",
            "      WHEN so.shipping_submit_status = 'SUBMITTED' THEN COALESCE(so.submitted_by, #{operatorUserId})",
            "      ELSE so.submitted_by END,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE so.id = #{shippingOrderId}",
            "  AND so.owner_user_id = #{ownerUserId}",
            "  AND so.is_deleted = b'0'"
    })
    int refreshShippingOrderHeaderState(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order",
            "SET quote_status = CASE",
            "      WHEN EXISTS (",
            "        SELECT 1 FROM procurement_shipping_order_line sol",
            "        LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "          ON quote.shipping_order_id = sol.shipping_order_id",
            "         AND quote.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            "         AND quote.is_deleted = b'0'",
            "        WHERE sol.shipping_order_id = #{shippingOrderId}",
            "          AND sol.is_deleted = b'0'",
            "          AND (quote.id IS NULL OR quote.quote_status != 'CONFIRMED')",
            "      ) THEN 'PENDING_QUOTE' ELSE 'CONFIRMED' END,",
            "    forwarder_code = #{row.forwarderCode},",
            "    forwarder_name = #{row.forwarderName},",
            "    route_code = #{row.routeCode},",
            "    route_name = #{row.routeName},",
            "    service_code = #{row.serviceCode},",
            "    service_name = #{row.serviceName},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{shippingOrderId}",
            "  AND is_deleted = b'0'"
    })
    int refreshShippingOrderQuoteState(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("row") PurchaseOrderLogisticsQuoteLineRecord row,
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
