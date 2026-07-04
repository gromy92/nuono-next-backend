package com.nuono.next.infrastructure.mapper;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceQuantityDelta;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ForwarderRouteQuoteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationLineInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseReceiptRow;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface WarehouseDispatchMapper {

    String BALANCE_SELECT = ""
            + "SELECT balance.id, balance.owner_user_id AS ownerUserId, balance.logical_store_id AS logicalStoreId, "
            + "balance.source_store_code AS sourceStoreCode, balance.source_store_name AS sourceStoreName, "
            + "balance.purchase_order_id AS purchaseOrderId, balance.purchase_order_no AS purchaseOrderNo, "
            + "balance.purchase_order_title AS purchaseOrderTitle, balance.purchase_order_item_id AS purchaseOrderItemId, "
            + "balance.purchase_order_item_site_id AS purchaseOrderItemSiteId, balance.product_master_id AS productMasterId, "
            + "balance.product_variant_id AS productVariantId, balance.partner_sku AS partnerSku, balance.sku_parent AS skuParent, "
            + "balance.title_cache AS titleCache, balance.image_url_cache AS imageUrlCache, balance.site_code AS siteCode, "
            + "balance.is_new_product = b'1' AS isNewProduct, "
            + "balance.planned_transport_mode AS plannedTransportMode, balance.fulfillment_type AS fulfillmentType, "
            + "balance.planned_quantity AS plannedQuantity, balance.confirmed_quantity AS confirmedQuantity, "
            + "balance.abnormal_quantity AS abnormalQuantity, balance.reserved_quantity AS reservedQuantity, "
            + "balance.logistics_handoff_quantity AS logisticsHandoffQuantity, balance.available_quantity AS availableQuantity, "
            + "CASE WHEN COALESCE(pvss.product_length_cm, pvs.product_length_cm) IS NULL "
            + "       OR COALESCE(pvss.product_width_cm, pvs.product_width_cm) IS NULL "
            + "       OR COALESCE(pvss.product_height_cm, pvs.product_height_cm) IS NULL "
            + "       OR COALESCE(pvss.product_weight_g, pvs.product_weight_g) IS NULL "
            + "     THEN 'SPEC_MISSING' ELSE COALESCE(balance.spec_status, 'READY') END AS specStatus, "
            + "COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS productLengthCm, "
            + "COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS productWidthCm, "
            + "COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS productHeightCm, "
            + "COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS productWeightG, "
            + "pvlp.profile_status AS logisticsProfileStatus, pvlp.battery_type AS batteryType, "
            + "pvlp.magnetic_type AS magneticType, pvlp.liquid_powder_type AS liquidPowderType, "
            + "pvlp.electric_type AS electricType, pvlp.blade_weapon_type AS bladeWeaponType, "
            + "pvlp.sensitive_tags_json AS sensitiveTagsJson, pvlp.manual_confirm_required = b'1' AS manualConfirmRequired, "
            + "COALESCE(quote.quote_status, 'PENDING_QUOTE') AS logisticsQuoteStatus, "
            + "COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS logisticsShippingSubmitStatus, "
            + "(quote.id IS NULL OR quote.quote_status != 'CONFIRMED' OR quote.shipping_submit_status != 'SUBMITTED') AS logisticsQuoteBlocking, "
            + "balance.status "
            + "FROM procurement_fulfillment_balance balance "
            + "LEFT JOIN product_variant_spec pvs "
            + "  ON pvs.variant_id = balance.product_variant_id "
            + " AND pvs.is_deleted = b'0' "
            + "LEFT JOIN product_variant_spec_source pvss "
            + "  ON pvss.id = pvs.effective_source_id "
            + " AND pvss.variant_id = balance.product_variant_id "
            + " AND pvss.is_deleted = b'0' "
            + "LEFT JOIN product_variant_logistics_profile pvlp "
            + "  ON pvlp.variant_id = balance.product_variant_id "
            + " AND pvlp.is_deleted = b'0' "
            + "LEFT JOIN procurement_purchase_order_logistics_quote_line quote "
            + "  ON quote.purchase_order_item_site_id = balance.purchase_order_item_site_id "
            + " AND quote.is_deleted = b'0' ";

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
            throw new IllegalStateException("仓库发运 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextConfirmationId() {
        return nextId("procurement_fulfillment_confirmation", 320000L);
    }

    default Long nextConfirmationLineId() {
        return nextId("procurement_fulfillment_confirmation_line", 330000L);
    }

    default Long nextDispatchPlanId() {
        return nextId("procurement_dispatch_plan", 340000L);
    }

    default Long nextDispatchLineId() {
        return nextId("procurement_dispatch_plan_line", 350000L);
    }

    default Long nextDispatchSourceId() {
        return nextId("procurement_dispatch_plan_line_source", 360000L);
    }

    default Long nextOperationLogId() {
        return nextId("procurement_dispatch_plan_operation_log", 390000L);
    }

    default Long nextShippingBatchId() {
        return nextId("warehouse_shipping_batch", 700000L);
    }

    default Long nextShippingBatchSourceId() {
        return nextId("warehouse_shipping_batch_source", 710000L);
    }

    default Long nextShippingSuggestionOptionId() {
        return nextId("warehouse_shipping_suggestion_option", 720000L);
    }

    default Long nextShippingSuggestionLineId() {
        return nextId("warehouse_shipping_suggestion_line", 730000L);
    }

    default Long nextShippingSuggestionLineSourceId() {
        return nextId("warehouse_shipping_suggestion_line_source", 740000L);
    }

    default Long nextOutboundOrderId() {
        return nextId("warehouse_outbound_order", 800000L);
    }

    default Long nextOutboundOrderLineId() {
        return nextId("warehouse_outbound_order_line", 810000L);
    }

    default Long nextOutboundOrderLineSourceId() {
        return nextId("warehouse_outbound_order_line_source", 820000L);
    }

    default Long nextPackingListId() {
        return nextId("warehouse_packing_list", 830000L);
    }

    default Long nextPackingBoxId() {
        return nextId("warehouse_packing_box", 840000L);
    }

    default Long nextPackingBoxItemId() {
        return nextId("warehouse_packing_box_item", 850000L);
    }

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, order_no AS orderNo,",
            "       title, anchor_store_code_cache AS anchorStoreCodeCache, project_code_cache AS projectCodeCache,",
            "       project_name_cache AS projectNameCache",
            "FROM procurement_purchase_order",
            "WHERE id = #{purchaseOrderId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PurchaseOrderAccessRecord selectOrderAccess(@Param("purchaseOrderId") Long purchaseOrderId);

    @Select({
            "SELECT id, purchase_order_id AS purchaseOrderId, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       product_master_id AS productMasterId, product_variant_id AS productVariantId, partner_sku AS partnerSku,",
            "       sku_parent AS skuParent, title_cache AS titleCache, image_url_cache AS imageUrlCache,",
            "       fulfillment_type AS fulfillmentType, fulfillment_source_name AS fulfillmentSourceName, total_quantity AS totalQuantity",
            "FROM procurement_purchase_order_item",
            "WHERE id = #{purchaseOrderItemId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PurchaseOrderItemRecord selectPurchaseOrderItem(@Param("purchaseOrderItemId") Long purchaseOrderItemId);

    @Select({
            "SELECT id, purchase_order_id AS purchaseOrderId, purchase_order_item_id AS purchaseOrderItemId,",
            "       owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, site_code AS siteCode,",
            "       transport_mode AS transportMode, quantity",
            "FROM procurement_purchase_order_item_site",
            "WHERE purchase_order_item_id = #{purchaseOrderItemId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<PurchaseOrderItemSiteRecord> listItemSitesForBalance(@Param("purchaseOrderItemId") Long purchaseOrderItemId);

    @Insert({
            "INSERT INTO procurement_fulfillment_balance (",
            "id, owner_user_id, logical_store_id, source_store_code, source_store_name, purchase_order_id, purchase_order_no,",
            "purchase_order_title, purchase_order_item_id, purchase_order_item_site_id, product_master_id, product_variant_id,",
            "partner_sku, sku_parent, title_cache, image_url_cache, site_code, planned_transport_mode, fulfillment_type,",
            "planned_quantity, confirmed_quantity, abnormal_quantity, reserved_quantity, logistics_handoff_quantity,",
            "available_quantity, is_new_product, spec_status, status, is_deleted, created_by, updated_by, gmt_create, gmt_updated)",
            "SELECT site.id + 900000000, site.owner_user_id, site.logical_store_id, po.anchor_store_code_cache, po.project_name_cache,",
            "       site.purchase_order_id, po.order_no, po.title, site.purchase_order_item_id, site.id, item.product_master_id,",
            "       item.product_variant_id, item.partner_sku, item.sku_parent, item.title_cache, item.image_url_cache,",
            "       site.site_code, site.transport_mode, #{fulfillmentType}, site.quantity, 0, 0, 0, 0, 0,",
            "       CASE WHEN COALESCE(pso_logistics.logistics_has_history, 0) = 1 THEN b'0' ELSE b'1' END,",
            "       'READY', 'OPEN', b'0', #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            "FROM procurement_purchase_order_item_site site",
            "JOIN procurement_purchase_order_item item ON item.id = site.purchase_order_item_id AND item.is_deleted = b'0'",
            "JOIN procurement_purchase_order po ON po.id = site.purchase_order_id AND po.is_deleted = b'0'",
            "LEFT JOIN (",
            "  SELECT logical_store_id, UPPER(TRIM(partner_sku)) AS partner_sku_key,",
            "         MAX(CASE WHEN logistics_has_history = b'1' THEN 1 ELSE 0 END) AS logistics_has_history",
            "  FROM product_site_offer",
            "  WHERE is_deleted = b'0'",
            "    AND partner_sku IS NOT NULL",
            "    AND TRIM(partner_sku) <> ''",
            "  GROUP BY logical_store_id, UPPER(TRIM(partner_sku))",
            ") pso_logistics",
            "  ON pso_logistics.logical_store_id = site.logical_store_id",
            " AND pso_logistics.partner_sku_key = UPPER(TRIM(item.partner_sku))",
            "WHERE site.id = #{purchaseOrderItemSiteId}",
            "  AND site.is_deleted = b'0'",
            "ON DUPLICATE KEY UPDATE planned_quantity = VALUES(planned_quantity), source_store_code = VALUES(source_store_code),",
            "    source_store_name = VALUES(source_store_name), purchase_order_no = VALUES(purchase_order_no),",
            "    purchase_order_title = VALUES(purchase_order_title), partner_sku = VALUES(partner_sku), sku_parent = VALUES(sku_parent),",
            "    title_cache = VALUES(title_cache), image_url_cache = VALUES(image_url_cache),",
            "    planned_transport_mode = VALUES(planned_transport_mode), fulfillment_type = VALUES(fulfillment_type),",
            "    is_new_product = CASE",
            "      WHEN confirmed_quantity = 0 AND abnormal_quantity = 0 AND reserved_quantity = 0 AND logistics_handoff_quantity = 0",
            "      THEN VALUES(is_new_product) ELSE is_new_product END,",
            "    updated_by = VALUES(updated_by), gmt_updated = NOW()"
    })
    int upsertBalanceFromItemSite(
            @Param("purchaseOrderItemSiteId") Long purchaseOrderItemSiteId,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM procurement_fulfillment_balance",
            "WHERE purchase_order_item_id = #{purchaseOrderItemId}",
            "  AND is_deleted = b'0'",
            "  AND (confirmed_quantity <> 0 OR abnormal_quantity <> 0 OR reserved_quantity <> 0 OR logistics_handoff_quantity <> 0)"
    })
    int countItemFulfillmentActivity(@Param("purchaseOrderItemId") Long purchaseOrderItemId);

    @Update({
            "UPDATE procurement_purchase_order_item",
            "SET fulfillment_type = #{fulfillmentType},",
            "    fulfillment_source_name = #{sourceName},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{purchaseOrderItemId}",
            "  AND is_deleted = b'0'"
    })
    int updatePurchaseOrderItemFulfillment(
            @Param("purchaseOrderItemId") Long purchaseOrderItemId,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("sourceName") String sourceName,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_fulfillment_balance",
            "SET fulfillment_type = #{fulfillmentType},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE purchase_order_item_id = #{purchaseOrderItemId}",
            "  AND is_deleted = b'0'",
            "  AND confirmed_quantity = 0",
            "  AND abnormal_quantity = 0",
            "  AND reserved_quantity = 0",
            "  AND logistics_handoff_quantity = 0"
    })
    int updateActiveBalancesFulfillment(
            @Param("purchaseOrderItemId") Long purchaseOrderItemId,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO procurement_fulfillment_confirmation (",
            "id, owner_user_id, logical_store_id, purchase_order_id, confirmation_no, confirmation_type, status,",
            "source_party_name, related_confirmation_id, relation_type, operator_user_id, confirmed_at, expected_quantity,",
            "confirmed_quantity_delta, abnormal_quantity_delta, remark, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.purchaseOrderId}, #{row.confirmationNo},",
            "#{row.confirmationType}, #{row.status}, #{row.sourcePartyName}, #{row.relatedConfirmationId}, #{row.relationType},",
            "#{row.operatorUserId}, NOW(), #{row.expectedQuantity}, #{row.confirmedQuantityDelta}, #{row.abnormalQuantityDelta},",
            "#{row.remark}, b'0', #{row.operatorUserId}, #{row.operatorUserId}, NOW(), NOW())"
    })
    int insertConfirmation(@Param("row") FulfillmentConfirmationInsertRecord row);

    @Insert({
            "INSERT INTO procurement_fulfillment_confirmation_line (",
            "id, confirmation_id, owner_user_id, logical_store_id, purchase_order_id, purchase_order_item_id, product_master_id,",
            "product_variant_id, partner_sku, sku_parent, title_cache, image_url_cache, fulfillment_type, expected_quantity,",
            "confirmed_quantity_delta, abnormal_quantity_delta, related_confirmation_line_id, exception_reason, snapshot_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.confirmationId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.purchaseOrderId},",
            "#{row.purchaseOrderItemId}, #{row.productMasterId}, #{row.productVariantId}, #{row.partnerSku}, #{row.skuParent},",
            "#{row.titleCache}, #{row.imageUrlCache}, #{row.fulfillmentType}, #{row.expectedQuantity},",
            "#{row.confirmedQuantityDelta}, #{row.abnormalQuantityDelta}, #{row.relatedConfirmationLineId},",
            "#{row.exceptionReason}, #{row.snapshotJson}, b'0', #{row.operatorUserId}, #{row.operatorUserId}, NOW(), NOW())"
    })
    int insertConfirmationLine(@Param("row") FulfillmentConfirmationLineInsertRecord row);

    @Select({
            BALANCE_SELECT,
            "WHERE balance.purchase_order_item_id = #{purchaseOrderItemId}",
            "  AND balance.is_deleted = b'0'",
            "ORDER BY balance.purchase_order_item_site_id ASC",
            "FOR UPDATE"
    })
    List<FulfillmentBalanceRecord> listBalancesForItemForUpdate(@Param("purchaseOrderItemId") Long purchaseOrderItemId);

    @Update({
            "UPDATE procurement_fulfillment_balance",
            "SET available_quantity = confirmed_quantity + #{row.confirmedDelta}",
            "        - abnormal_quantity - #{row.abnormalDelta}",
            "        - reserved_quantity - logistics_handoff_quantity,",
            "    confirmed_quantity = confirmed_quantity + #{row.confirmedDelta},",
            "    abnormal_quantity = abnormal_quantity + #{row.abnormalDelta},",
            "    updated_by = #{row.operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{row.balanceId}",
            "  AND is_deleted = b'0'"
    })
    int updateBalanceQuantities(@Param("row") BalanceQuantityDelta row);

    @Select({
            "<script>",
            BALANCE_SELECT,
            "WHERE balance.owner_user_id = #{ownerUserId}",
            "  AND balance.is_deleted = b'0'",
            "  AND balance.available_quantity > 0",
            "<if test='storeCodes != null and storeCodes.size() &gt; 0'>",
            "  AND balance.source_store_code IN",
            "  <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>#{storeCode}</foreach>",
            "</if>",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND balance.site_code = #{siteCode}",
            "</if>",
            "<if test='fulfillmentType != null and fulfillmentType != \"\"'>",
            "  AND balance.fulfillment_type = #{fulfillmentType}",
            "</if>",
            "ORDER BY balance.product_variant_id ASC, balance.site_code ASC, balance.fulfillment_type ASC, balance.purchase_order_id ASC, balance.id ASC",
            "</script>"
    })
    List<FulfillmentBalanceRecord> listReadyBalances(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("siteCode") String siteCode,
            @Param("fulfillmentType") String fulfillmentType
    );

    @Select({
            "<script>",
            BALANCE_SELECT,
            "WHERE balance.owner_user_id = #{ownerUserId}",
            "  AND balance.is_deleted = b'0'",
            "  AND balance.planned_quantity > 0",
            "<if test='storeCodes != null and storeCodes.size() &gt; 0'>",
            "  AND balance.source_store_code IN",
            "  <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>#{storeCode}</foreach>",
            "</if>",
            "ORDER BY balance.purchase_order_id DESC, balance.product_variant_id ASC, balance.site_code ASC, balance.fulfillment_type ASC, balance.id ASC",
            "</script>"
    })
    List<FulfillmentBalanceRecord> listPurchasePlanBalances(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes
    );

    @Select({
            "<script>",
            "SELECT so.id AS receiptSourceId, so.shipping_order_no AS receiptSourceNo, so.title AS receiptSourceTitle,",
            "       GROUP_CONCAT(DISTINCT COALESCE(sol.source_store_name, sol.source_store_code) ORDER BY sol.source_store_code SEPARATOR ' / ') AS receiptSourceStoreName,",
            "       GROUP_CONCAT(DISTINCT sol.source_store_code ORDER BY sol.source_store_code SEPARATOR ',') AS receiptSourceStoreCode,",
            "       DATE_FORMAT(so.gmt_create, '%Y-%m-%d') AS receiptSourceCreatedAt,",
            "       po.id AS orderId, po.order_no AS orderNo, po.title AS orderTitle,",
            "       po.project_name_cache AS storeName, po.anchor_store_code_cache AS sourceStoreCode,",
            "       DATE_FORMAT(po.gmt_create, '%Y-%m-%d') AS createdAt,",
            "       item.id AS itemId, item.product_variant_id AS productVariantId, item.partner_sku AS partnerSku,",
            "       item.sku_parent AS skuParent, item.title_cache AS titleCache, item.image_url_cache AS imageUrlCache,",
            "       SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT sol.site_code ORDER BY sol.id SEPARATOR ','), ',', 1) AS siteCode,",
            "       SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT sol.planned_transport_mode ORDER BY sol.id SEPARATOR ','), ',', 1) AS transportMode,",
            "       COALESCE(SUM(sol.quantity), 0) AS expectedQuantity,",
            "       COALESCE(SUM(balance.confirmed_quantity - balance.abnormal_quantity), 0) AS receivedQuantity,",
            "       COALESCE(SUM(balance.reserved_quantity + balance.logistics_handoff_quantity), 0) AS plannedQuantity,",
            "       CASE WHEN COALESCE(pvss.product_length_cm, pvs.product_length_cm) IS NULL",
            "              OR COALESCE(pvss.product_width_cm, pvs.product_width_cm) IS NULL",
            "              OR COALESCE(pvss.product_height_cm, pvs.product_height_cm) IS NULL",
            "              OR COALESCE(pvss.product_weight_g, pvs.product_weight_g) IS NULL",
            "            THEN 'SPEC_MISSING' ELSE 'READY' END AS specStatus,",
            "       COALESCE(item.fulfillment_type, 'WAREHOUSE_RECEIPT') AS fulfillmentType,",
            "       item.fulfillment_source_name AS fulfillmentSourceName",
            "FROM procurement_shipping_order so",
            "JOIN procurement_shipping_order_line sol",
            "  ON sol.shipping_order_id = so.id",
            " AND sol.is_deleted = b'0'",
            "JOIN procurement_purchase_order po",
            "  ON po.id = sol.purchase_order_id",
            " AND po.is_deleted = b'0'",
            "JOIN procurement_purchase_order_item item",
            "  ON item.purchase_order_id = po.id",
            " AND item.id = sol.purchase_order_item_id",
            " AND item.is_deleted = b'0'",
            "LEFT JOIN procurement_fulfillment_balance balance",
            "  ON balance.purchase_order_item_site_id = sol.purchase_order_item_site_id",
            " AND balance.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = item.product_variant_id",
            " AND pvs.is_deleted = b'0'",
            "LEFT JOIN product_variant_spec_source pvss",
            "  ON pvss.id = pvs.effective_source_id",
            " AND pvss.variant_id = item.product_variant_id",
            " AND pvss.is_deleted = b'0'",
            "WHERE so.owner_user_id = #{ownerUserId}",
            "  AND so.is_deleted = b'0'",
            "  AND so.shipping_submit_status IN ('SUBMITTED', 'PARTIAL_SUBMITTED')",
            "<if test='storeCodes != null and storeCodes.size() &gt; 0'>",
            "  AND sol.source_store_code IN",
            "  <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>#{storeCode}</foreach>",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (so.shipping_order_no LIKE CONCAT('%', #{keyword}, '%')",
            "       OR so.title LIKE CONCAT('%', #{keyword}, '%')",
            "       OR po.order_no LIKE CONCAT('%', #{keyword}, '%')",
            "       OR po.title LIKE CONCAT('%', #{keyword}, '%')",
            "       OR item.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR item.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR item.title_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "GROUP BY so.id, so.shipping_order_no, so.title, so.gmt_create,",
            "         po.id, po.order_no, po.title, po.project_name_cache, po.anchor_store_code_cache, po.gmt_create,",
            "         item.id, item.product_variant_id, item.partner_sku, item.sku_parent, item.title_cache, item.image_url_cache,",
            "         COALESCE(pvss.product_length_cm, pvs.product_length_cm),",
            "         COALESCE(pvss.product_width_cm, pvs.product_width_cm),",
            "         COALESCE(pvss.product_height_cm, pvs.product_height_cm),",
            "         COALESCE(pvss.product_weight_g, pvs.product_weight_g), item.fulfillment_type, item.fulfillment_source_name",
            "ORDER BY so.gmt_create DESC, so.id DESC, po.id ASC, item.id ASC",
            "</script>"
    })
    List<PurchaseReceiptRow> listReceiptRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("keyword") String keyword
    );

    @Select({
            "<script>",
            BALANCE_SELECT,
            "WHERE balance.id IN",
            "<foreach collection='balanceIds' item='balanceId' open='(' separator=',' close=')'>#{balanceId}</foreach>",
            "  AND balance.is_deleted = b'0'",
            "ORDER BY balance.id ASC",
            "FOR UPDATE",
            "</script>"
    })
    List<FulfillmentBalanceRecord> selectBalancesForUpdate(@Param("balanceIds") List<Long> balanceIds);

    @Insert({
            "INSERT INTO procurement_dispatch_plan (",
            "id, owner_user_id, plan_no, status, item_count, sku_count, total_quantity, site_summary_json, transport_summary_json,",
            "remark, handoff_generation_no, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.planNo}, #{row.status}, #{row.itemCount}, #{row.skuCount}, #{row.totalQuantity},",
            "#{row.siteSummaryJson}, #{row.transportSummaryJson}, #{row.remark}, #{row.handoffGenerationNo}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertDispatchPlan(
            @Param("row") DispatchPlanRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_dispatch_plan_line (",
            "id, dispatch_plan_id, owner_user_id, product_master_id, product_variant_id, partner_sku, sku_parent, title_cache,",
            "image_url_cache, site_code, actual_transport_mode, fulfillment_type, spec_status, quantity, source_count,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.dispatchPlanId}, #{row.ownerUserId}, #{row.productMasterId}, #{row.productVariantId},",
            "#{row.partnerSku}, #{row.skuParent}, #{row.titleCache}, #{row.imageUrlCache}, #{row.siteCode},",
            "#{row.actualTransportMode}, #{row.fulfillmentType}, #{row.specStatus}, #{row.quantity}, #{row.sourceCount},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertDispatchPlanLine(
            @Param("row") DispatchPlanLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_dispatch_plan_line_source (",
            "id, dispatch_plan_id, dispatch_plan_line_id, owner_user_id, fulfillment_balance_id, source_store_code, source_store_name,",
            "purchase_order_id, purchase_order_no, purchase_order_item_id, purchase_order_item_site_id, planned_transport_mode,",
            "fulfillment_type, quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.dispatchPlanId}, #{row.dispatchPlanLineId}, #{row.ownerUserId}, #{row.fulfillmentBalanceId},",
            "#{row.sourceStoreCode}, #{row.sourceStoreName}, #{row.purchaseOrderId}, #{row.purchaseOrderNo},",
            "#{row.purchaseOrderItemId}, #{row.purchaseOrderItemSiteId}, #{row.plannedTransportMode}, #{row.fulfillmentType},",
            "#{row.quantity}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertDispatchPlanLineSource(
            @Param("row") DispatchPlanLineSourceRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_fulfillment_balance",
            "SET reserved_quantity = reserved_quantity + #{quantity},",
            "    available_quantity = available_quantity - #{quantity},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{balanceId}",
            "  AND is_deleted = b'0'",
            "  AND available_quantity >= #{quantity}"
    })
    int reserveBalance(
            @Param("balanceId") Long balanceId,
            @Param("quantity") Integer quantity,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_shipping_batch (",
            "id, owner_user_id, batch_no, status, selected_option_id, source_count, sku_count, total_quantity,",
            "store_summary_json, site_summary_json, transport_summary_json, origin_summary_json, remark,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchNo}, #{row.status}, #{row.selectedOptionId},",
            "#{row.sourceCount}, #{row.skuCount}, #{row.totalQuantity}, #{row.storeSummaryJson},",
            "#{row.siteSummaryJson}, #{row.transportSummaryJson}, #{row.originSummaryJson}, #{row.remark},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingBatch(
            @Param("row") ShippingBatchRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_shipping_batch_source (",
            "id, batch_id, owner_user_id, fulfillment_balance_id, source_store_code, source_store_name,",
            "purchase_order_id, purchase_order_no, purchase_order_title, purchase_order_item_id, purchase_order_item_site_id,",
            "product_master_id, product_variant_id, partner_sku, sku_parent, title_cache, image_url_cache, site_code,",
            "planned_transport_mode, fulfillment_type, source_party_name, spec_status, product_length_cm, product_width_cm,",
            "product_height_cm, product_weight_g, logistics_profile_status, sensitive_flag, sensitive_reason_json, reserved_quantity,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.batchId}, #{row.ownerUserId}, #{row.fulfillmentBalanceId}, #{row.sourceStoreCode},",
            "#{row.sourceStoreName}, #{row.purchaseOrderId}, #{row.purchaseOrderNo}, #{row.purchaseOrderTitle},",
            "#{row.purchaseOrderItemId}, #{row.purchaseOrderItemSiteId}, #{row.productMasterId}, #{row.productVariantId},",
            "#{row.partnerSku}, #{row.skuParent}, #{row.titleCache}, #{row.imageUrlCache}, #{row.siteCode},",
            "#{row.plannedTransportMode}, #{row.fulfillmentType}, #{row.sourcePartyName}, #{row.specStatus},",
            "#{row.productLengthCm}, #{row.productWidthCm}, #{row.productHeightCm}, #{row.productWeightG},",
            "#{row.logisticsProfileStatus}, CASE WHEN #{row.sensitiveFlag} THEN b'1' ELSE b'0' END,",
            "#{row.sensitiveReasonJson}, #{row.reservedQuantity}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingBatchSource(
            @Param("row") ShippingBatchSourceRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_shipping_suggestion_option (",
            "id, batch_id, owner_user_id, option_type, option_name, status, selected_flag, score, sku_count,",
            "total_quantity, air_quantity, sea_quantity, spec_missing_count, warning_count, forwarder_plan_type,",
            "auto_recommended, target_forwarder_codes_json, target_forwarder_names_json, route_codes_json, evaluation_status,",
            "blocked_reasons_json, actual_weight_kg, volume_cbm, chargeable_weight_kg, estimated_total_amount,",
            "avg_unit_amount, currency, cost_snapshot_json, summary_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.batchId}, #{row.ownerUserId}, #{row.optionType}, #{row.optionName}, #{row.status},",
            "CASE WHEN #{row.selectedFlag} THEN b'1' ELSE b'0' END, #{row.score}, #{row.skuCount},",
            "#{row.totalQuantity}, #{row.airQuantity}, #{row.seaQuantity}, #{row.specMissingCount},",
            "#{row.warningCount}, #{row.forwarderPlanType}, CASE WHEN #{row.autoRecommended} THEN b'1' ELSE b'0' END,",
            "#{row.targetForwarderCodesJson}, #{row.targetForwarderNamesJson}, #{row.routeCodesJson}, #{row.evaluationStatus},",
            "#{row.blockedReasonsJson}, #{row.actualWeightKg}, #{row.volumeCbm}, #{row.chargeableWeightKg},",
            "#{row.estimatedTotalAmount}, #{row.avgUnitAmount}, #{row.currency}, #{row.costSnapshotJson}, #{row.summaryJson},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingSuggestionOption(
            @Param("row") ShippingSuggestionOptionRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_shipping_suggestion_line (",
            "id, option_id, batch_id, owner_user_id, product_master_id, product_variant_id, partner_sku, sku_parent,",
            "title_cache, image_url_cache, site_code, actual_transport_mode, fulfillment_type, source_party_name,",
            "spec_status, target_forwarder_code, target_forwarder_name, route_code, route_name, actual_weight_kg,",
            "volume_cbm, chargeable_weight_kg, estimated_amount, currency, quantity, source_count, warning_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.optionId}, #{row.batchId}, #{row.ownerUserId}, #{row.productMasterId},",
            "#{row.productVariantId}, #{row.partnerSku}, #{row.skuParent}, #{row.titleCache}, #{row.imageUrlCache},",
            "#{row.siteCode}, #{row.actualTransportMode}, #{row.fulfillmentType}, #{row.sourcePartyName},",
            "#{row.specStatus}, #{row.targetForwarderCode}, #{row.targetForwarderName}, #{row.routeCode}, #{row.routeName},",
            "#{row.actualWeightKg}, #{row.volumeCbm}, #{row.chargeableWeightKg}, #{row.estimatedAmount}, #{row.currency},",
            "#{row.quantity}, #{row.sourceCount}, #{row.warningJson}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingSuggestionLine(
            @Param("row") ShippingSuggestionLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_shipping_suggestion_line_source (",
            "id, option_id, line_id, batch_id, batch_source_id, fulfillment_balance_id, planned_transport_mode, quantity,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.optionId}, #{row.lineId}, #{row.batchId}, #{row.batchSourceId},",
            "#{row.fulfillmentBalanceId}, #{row.plannedTransportMode}, #{row.quantity}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertShippingSuggestionLineSource(
            @Param("row") ShippingSuggestionLineSourceRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, batch_no AS batchNo, status, selected_option_id AS selectedOptionId,",
            "       source_count AS sourceCount, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       store_summary_json AS storeSummaryJson, site_summary_json AS siteSummaryJson,",
            "       transport_summary_json AS transportSummaryJson, origin_summary_json AS originSummaryJson, remark,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_batch",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT #{limit}"
    })
    List<ShippingBatchRecord> listShippingBatches(
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, batch_no AS batchNo, status, selected_option_id AS selectedOptionId,",
            "       source_count AS sourceCount, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       store_summary_json AS storeSummaryJson, site_summary_json AS siteSummaryJson,",
            "       transport_summary_json AS transportSummaryJson, origin_summary_json AS originSummaryJson, remark,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_batch",
            "WHERE id = #{batchId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ShippingBatchRecord selectShippingBatchById(@Param("batchId") Long batchId);

    @Select({
            "SELECT source.id, source.batch_id AS batchId, source.owner_user_id AS ownerUserId,",
            "       source.fulfillment_balance_id AS fulfillmentBalanceId,",
            "       source.source_store_code AS sourceStoreCode, source.source_store_name AS sourceStoreName,",
            "       source.purchase_order_id AS purchaseOrderId, source.purchase_order_no AS purchaseOrderNo,",
            "       source.purchase_order_title AS purchaseOrderTitle, source.purchase_order_item_id AS purchaseOrderItemId,",
            "       source.purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       source.product_master_id AS productMasterId, source.product_variant_id AS productVariantId,",
            "       source.partner_sku AS partnerSku, source.sku_parent AS skuParent, source.title_cache AS titleCache,",
            "       source.image_url_cache AS imageUrlCache, source.site_code AS siteCode,",
            "       source.planned_transport_mode AS plannedTransportMode, source.fulfillment_type AS fulfillmentType,",
            "       source.source_party_name AS sourcePartyName, source.spec_status AS specStatus,",
            "       source.product_length_cm AS productLengthCm, source.product_width_cm AS productWidthCm,",
            "       source.product_height_cm AS productHeightCm, source.product_weight_g AS productWeightG,",
            "       source.logistics_profile_status AS logisticsProfileStatus, source.sensitive_flag = b'1' AS sensitiveFlag,",
            "       source.sensitive_reason_json AS sensitiveReasonJson,",
            "       COALESCE(quote.quote_status, 'PENDING_QUOTE') AS logisticsQuoteStatus,",
            "       COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS logisticsShippingSubmitStatus,",
            "       (quote.id IS NULL OR quote.quote_status != 'CONFIRMED' OR quote.shipping_submit_status != 'SUBMITTED') AS logisticsQuoteBlocking,",
            "       source.reserved_quantity AS reservedQuantity",
            "FROM warehouse_shipping_batch_source source",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.purchase_order_item_site_id = source.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE source.batch_id = #{batchId}",
            "  AND source.is_deleted = b'0'",
            "ORDER BY source.id ASC"
    })
    List<ShippingBatchSourceRecord> listShippingBatchSources(@Param("batchId") Long batchId);

    @Select({
            "SELECT id, batch_id AS batchId, owner_user_id AS ownerUserId, option_type AS optionType, option_name AS optionName,",
            "       status, selected_flag = b'1' AS selectedFlag, score, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       air_quantity AS airQuantity, sea_quantity AS seaQuantity, spec_missing_count AS specMissingCount,",
            "       warning_count AS warningCount, forwarder_plan_type AS forwarderPlanType, auto_recommended = b'1' AS autoRecommended,",
            "       target_forwarder_codes_json AS targetForwarderCodesJson, target_forwarder_names_json AS targetForwarderNamesJson,",
            "       route_codes_json AS routeCodesJson, evaluation_status AS evaluationStatus, blocked_reasons_json AS blockedReasonsJson,",
            "       actual_weight_kg AS actualWeightKg, volume_cbm AS volumeCbm, chargeable_weight_kg AS chargeableWeightKg,",
            "       estimated_total_amount AS estimatedTotalAmount, avg_unit_amount AS avgUnitAmount, currency,",
            "       cost_snapshot_json AS costSnapshotJson, summary_json AS summaryJson,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_suggestion_option",
            "WHERE batch_id = #{batchId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<ShippingSuggestionOptionRecord> listShippingSuggestionOptions(@Param("batchId") Long batchId);

    @Select({
            "SELECT id, batch_id AS batchId, owner_user_id AS ownerUserId, option_type AS optionType, option_name AS optionName,",
            "       status, selected_flag = b'1' AS selectedFlag, score, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       air_quantity AS airQuantity, sea_quantity AS seaQuantity, spec_missing_count AS specMissingCount,",
            "       warning_count AS warningCount, forwarder_plan_type AS forwarderPlanType, auto_recommended = b'1' AS autoRecommended,",
            "       target_forwarder_codes_json AS targetForwarderCodesJson, target_forwarder_names_json AS targetForwarderNamesJson,",
            "       route_codes_json AS routeCodesJson, evaluation_status AS evaluationStatus, blocked_reasons_json AS blockedReasonsJson,",
            "       actual_weight_kg AS actualWeightKg, volume_cbm AS volumeCbm, chargeable_weight_kg AS chargeableWeightKg,",
            "       estimated_total_amount AS estimatedTotalAmount, avg_unit_amount AS avgUnitAmount, currency,",
            "       cost_snapshot_json AS costSnapshotJson, summary_json AS summaryJson,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_suggestion_option",
            "WHERE id = #{optionId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ShippingSuggestionOptionRecord selectShippingSuggestionOptionById(@Param("optionId") Long optionId);

    @Update({
            "UPDATE warehouse_shipping_suggestion_option",
            "SET selected_flag = b'0',",
            "    status = 'CANDIDATE',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE batch_id = #{batchId}",
            "  AND selected_flag = b'1'",
            "  AND is_deleted = b'0'"
    })
    int clearSelectedShippingOptions(
            @Param("batchId") Long batchId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE warehouse_shipping_suggestion_option",
            "SET selected_flag = b'1',",
            "    status = 'SELECTED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE batch_id = #{batchId}",
            "  AND id = #{optionId}",
            "  AND is_deleted = b'0'"
    })
    int selectShippingSuggestionOption(
            @Param("batchId") Long batchId,
            @Param("optionId") Long optionId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE warehouse_shipping_batch",
            "SET selected_option_id = #{optionId},",
            "    status = 'OPTION_SELECTED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{batchId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('DRAFT', 'OPTION_SELECTED')",
            "  AND is_deleted = b'0'"
    })
    int updateShippingBatchSelectedOption(
            @Param("batchId") Long batchId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("optionId") Long optionId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, option_id AS optionId, batch_id AS batchId, owner_user_id AS ownerUserId,",
            "       product_master_id AS productMasterId, product_variant_id AS productVariantId, partner_sku AS partnerSku,",
            "       sku_parent AS skuParent, title_cache AS titleCache, image_url_cache AS imageUrlCache, site_code AS siteCode,",
            "       actual_transport_mode AS actualTransportMode, fulfillment_type AS fulfillmentType, source_party_name AS sourcePartyName,",
            "       spec_status AS specStatus, target_forwarder_code AS targetForwarderCode, target_forwarder_name AS targetForwarderName,",
            "       route_code AS routeCode, route_name AS routeName, actual_weight_kg AS actualWeightKg, volume_cbm AS volumeCbm,",
            "       chargeable_weight_kg AS chargeableWeightKg, estimated_amount AS estimatedAmount, currency,",
            "       quantity, source_count AS sourceCount, warning_json AS warningJson",
            "FROM warehouse_shipping_suggestion_line",
            "WHERE batch_id = #{batchId}",
            "  AND is_deleted = b'0'",
            "ORDER BY option_id ASC, id ASC"
    })
    List<ShippingSuggestionLineRecord> listShippingSuggestionLines(@Param("batchId") Long batchId);

    @Select({
            "SELECT id, option_id AS optionId, line_id AS lineId, batch_id AS batchId, batch_source_id AS batchSourceId,",
            "       fulfillment_balance_id AS fulfillmentBalanceId, planned_transport_mode AS plannedTransportMode, quantity",
            "FROM warehouse_shipping_suggestion_line_source",
            "WHERE batch_id = #{batchId}",
            "  AND is_deleted = b'0'",
            "ORDER BY option_id ASC, line_id ASC, id ASC"
    })
    List<ShippingSuggestionLineSourceRecord> listShippingSuggestionLineSources(@Param("batchId") Long batchId);

    @Select({
            "<script>",
            "SELECT route.route_code AS routeCode, route.route_name AS routeName, route.forwarder_code AS forwarderCode,",
            "       COALESCE(forwarder.name, route.forwarder_code) AS forwarderName, route.transport_mode AS transportMode,",
            "       price.cargo_category_code AS cargoCategoryCode, price.cargo_category_name AS cargoCategoryName,",
            "       price.currency, price.unit_price AS minUnitPrice, price.billing_unit AS billingUnit,",
            "       price.min_billable_unit AS minBillableUnit, price.min_billable_unit_type AS minBillableUnitType,",
            "       price.min_charge AS minCharge, price.volume_divisor AS volumeDivisor",
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
            "JOIN forwarder_quote_base_price price",
            "  ON price.service_code = line.service_code",
            " AND price.quote_version_id = line.quote_version_id",
            " AND price.price_status IN ('NORMAL', 'STARTING_PRICE', 'ESTIMATE')",
            " AND price.unit_price IS NOT NULL",
            "WHERE route.active_for_purchase_order = b'1'",
            "  AND route.route_code IN",
            "  <foreach collection='routeCodes' item='routeCode' open='(' separator=',' close=')'>#{routeCode}</foreach>",
            "ORDER BY route.route_code ASC, price.cargo_category_code ASC, price.billing_unit ASC, price.unit_price ASC",
            "</script>"
    })
    List<ForwarderRouteQuoteRecord> listForwarderRouteQuotes(@Param("routeCodes") Collection<String> routeCodes);

    @Insert({
            "INSERT INTO warehouse_outbound_order (",
            "id, batch_id, option_id, owner_user_id, outbound_no, status, origin_type, origin_name, sku_count, total_quantity,",
            "site_summary_json, transport_summary_json, remark, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.batchId}, #{row.optionId}, #{row.ownerUserId}, #{row.outboundNo}, #{row.status},",
            "#{row.originType}, #{row.originName}, #{row.skuCount}, #{row.totalQuantity}, #{row.siteSummaryJson},",
            "#{row.transportSummaryJson}, #{row.remark}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertOutboundOrder(
            @Param("row") OutboundOrderRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_outbound_order_line (",
            "id, outbound_order_id, batch_id, option_line_id, owner_user_id, product_master_id, product_variant_id,",
            "partner_sku, sku_parent, title_cache, image_url_cache, site_code, actual_transport_mode, fulfillment_type,",
            "source_party_name, spec_status, quantity, packed_quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.outboundOrderId}, #{row.batchId}, #{row.optionLineId}, #{row.ownerUserId},",
            "#{row.productMasterId}, #{row.productVariantId}, #{row.partnerSku}, #{row.skuParent}, #{row.titleCache},",
            "#{row.imageUrlCache}, #{row.siteCode}, #{row.actualTransportMode}, #{row.fulfillmentType},",
            "#{row.sourcePartyName}, #{row.specStatus}, #{row.quantity}, #{row.packedQuantity}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertOutboundOrderLine(
            @Param("row") OutboundOrderLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_outbound_order_line_source (",
            "id, outbound_order_id, outbound_order_line_id, batch_source_id, fulfillment_balance_id, purchase_order_id,",
            "purchase_order_no, purchase_order_title, purchase_order_item_id, purchase_order_item_site_id,",
            "planned_transport_mode, quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.outboundOrderId}, #{row.outboundOrderLineId}, #{row.batchSourceId}, #{row.fulfillmentBalanceId},",
            "#{row.purchaseOrderId}, #{row.purchaseOrderNo}, #{row.purchaseOrderTitle}, #{row.purchaseOrderItemId},",
            "#{row.purchaseOrderItemSiteId}, #{row.plannedTransportMode}, #{row.quantity}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertOutboundOrderLineSource(
            @Param("row") OutboundOrderLineSourceRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE warehouse_shipping_batch",
            "SET status = 'OUTBOUND_CREATED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{batchId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status = 'OPTION_SELECTED'",
            "  AND is_deleted = b'0'"
    })
    int updateShippingBatchOutboundCreated(
            @Param("batchId") Long batchId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, batch_id AS batchId, option_id AS optionId, owner_user_id AS ownerUserId, outbound_no AS outboundNo,",
            "       status, origin_type AS originType, origin_name AS originName, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       site_summary_json AS siteSummaryJson, transport_summary_json AS transportSummaryJson, remark,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_outbound_order",
            "WHERE id = #{outboundOrderId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    OutboundOrderRecord selectOutboundOrderById(@Param("outboundOrderId") Long outboundOrderId);

    @Select({
            "SELECT id, batch_id AS batchId, option_id AS optionId, owner_user_id AS ownerUserId, outbound_no AS outboundNo,",
            "       status, origin_type AS originType, origin_name AS originName, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       site_summary_json AS siteSummaryJson, transport_summary_json AS transportSummaryJson, remark,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_outbound_order",
            "WHERE batch_id = #{batchId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<OutboundOrderRecord> listOutboundOrdersByBatch(@Param("batchId") Long batchId);

    @Select({
            "SELECT id, outbound_order_id AS outboundOrderId, batch_id AS batchId, option_line_id AS optionLineId,",
            "       owner_user_id AS ownerUserId, product_master_id AS productMasterId, product_variant_id AS productVariantId,",
            "       partner_sku AS partnerSku, sku_parent AS skuParent, title_cache AS titleCache, image_url_cache AS imageUrlCache,",
            "       site_code AS siteCode, actual_transport_mode AS actualTransportMode, fulfillment_type AS fulfillmentType,",
            "       source_party_name AS sourcePartyName, spec_status AS specStatus, quantity, packed_quantity AS packedQuantity",
            "FROM warehouse_outbound_order_line",
            "WHERE outbound_order_id = #{outboundOrderId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<OutboundOrderLineRecord> listOutboundOrderLines(@Param("outboundOrderId") Long outboundOrderId);

    @Select({
            "SELECT id, outbound_order_id AS outboundOrderId, outbound_order_line_id AS outboundOrderLineId,",
            "       batch_source_id AS batchSourceId, fulfillment_balance_id AS fulfillmentBalanceId,",
            "       purchase_order_id AS purchaseOrderId, purchase_order_no AS purchaseOrderNo, purchase_order_title AS purchaseOrderTitle,",
            "       purchase_order_item_id AS purchaseOrderItemId, purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       planned_transport_mode AS plannedTransportMode, quantity",
            "FROM warehouse_outbound_order_line_source",
            "WHERE outbound_order_id = #{outboundOrderId}",
            "  AND is_deleted = b'0'",
            "ORDER BY outbound_order_line_id ASC, id ASC"
    })
    List<OutboundOrderLineSourceRecord> listOutboundOrderLineSources(@Param("outboundOrderId") Long outboundOrderId);

    @Select({
            "SELECT COUNT(1)",
            "FROM warehouse_outbound_order_line_source source",
            "LEFT JOIN procurement_purchase_order_logistics_quote_line quote",
            "  ON quote.purchase_order_item_site_id = source.purchase_order_item_site_id",
            " AND quote.is_deleted = b'0'",
            "WHERE source.outbound_order_id = #{outboundOrderId}",
            "  AND source.is_deleted = b'0'",
            "  AND (quote.id IS NULL",
            "       OR quote.quote_status != 'CONFIRMED'",
            "       OR quote.shipping_submit_status != 'SUBMITTED')"
    })
    int countBlockingOutboundOrderLogisticsQuotes(@Param("outboundOrderId") Long outboundOrderId);

    @Insert({
            "INSERT INTO warehouse_packing_list (",
            "id, outbound_order_id, owner_user_id, packing_no, status, box_count, packed_quantity, gross_weight_kg, volume_cbm,",
            "remark, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.outboundOrderId}, #{row.ownerUserId}, #{row.packingNo}, #{row.status}, #{row.boxCount},",
            "#{row.packedQuantity}, #{row.grossWeightKg}, #{row.volumeCbm}, #{row.remark}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertPackingList(
            @Param("row") PackingListRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE warehouse_outbound_order",
            "SET status = 'PACKING',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{outboundOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('DRAFT', 'PACKING')",
            "  AND is_deleted = b'0'"
    })
    int markOutboundOrderPacking(
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, outbound_order_id AS outboundOrderId, owner_user_id AS ownerUserId, packing_no AS packingNo, status,",
            "       box_count AS boxCount, packed_quantity AS packedQuantity, gross_weight_kg AS grossWeightKg, volume_cbm AS volumeCbm,",
            "       remark, DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_packing_list",
            "WHERE id = #{packingListId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PackingListRecord selectPackingListById(@Param("packingListId") Long packingListId);

    @Select({
            "SELECT id, outbound_order_id AS outboundOrderId, owner_user_id AS ownerUserId, packing_no AS packingNo, status,",
            "       box_count AS boxCount, packed_quantity AS packedQuantity, gross_weight_kg AS grossWeightKg, volume_cbm AS volumeCbm,",
            "       remark, DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_packing_list",
            "WHERE outbound_order_id = #{outboundOrderId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<PackingListRecord> listPackingListsByOutboundOrder(@Param("outboundOrderId") Long outboundOrderId);

    @Update({
            "UPDATE warehouse_packing_box_item",
            "SET is_deleted = b'1',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE packing_list_id = #{packingListId}",
            "  AND is_deleted = b'0'"
    })
    int deletePackingBoxItems(
            @Param("packingListId") Long packingListId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE warehouse_packing_box",
            "SET is_deleted = b'1',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE packing_list_id = #{packingListId}",
            "  AND is_deleted = b'0'"
    })
    int deletePackingBoxes(
            @Param("packingListId") Long packingListId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_packing_box (",
            "id, packing_list_id, outbound_order_id, owner_user_id, box_no, length_cm, width_cm, height_cm, gross_weight_kg,",
            "quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.packingListId}, #{row.outboundOrderId}, #{row.ownerUserId}, #{row.boxNo}, #{row.lengthCm},",
            "#{row.widthCm}, #{row.heightCm}, #{row.grossWeightKg}, #{row.quantity}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertPackingBox(
            @Param("row") PackingBoxRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO warehouse_packing_box_item (",
            "id, packing_list_id, packing_box_id, outbound_order_id, outbound_order_line_id, owner_user_id, product_variant_id,",
            "partner_sku, site_code, actual_transport_mode, quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.packingListId}, #{row.packingBoxId}, #{row.outboundOrderId}, #{row.outboundOrderLineId},",
            "#{row.ownerUserId}, #{row.productVariantId}, #{row.partnerSku}, #{row.siteCode}, #{row.actualTransportMode},",
            "#{row.quantity}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertPackingBoxItem(
            @Param("row") PackingBoxItemRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE warehouse_packing_list",
            "SET box_count = #{boxCount},",
            "    packed_quantity = #{packedQuantity},",
            "    gross_weight_kg = #{grossWeightKg},",
            "    volume_cbm = #{volumeCbm},",
            "    remark = #{remark},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{packingListId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status = 'DRAFT'",
            "  AND is_deleted = b'0'"
    })
    int updatePackingListTotals(
            @Param("packingListId") Long packingListId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("boxCount") Integer boxCount,
            @Param("packedQuantity") Integer packedQuantity,
            @Param("grossWeightKg") java.math.BigDecimal grossWeightKg,
            @Param("volumeCbm") java.math.BigDecimal volumeCbm,
            @Param("remark") String remark,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, packing_list_id AS packingListId, outbound_order_id AS outboundOrderId, owner_user_id AS ownerUserId,",
            "       box_no AS boxNo, length_cm AS lengthCm, width_cm AS widthCm, height_cm AS heightCm,",
            "       gross_weight_kg AS grossWeightKg, quantity",
            "FROM warehouse_packing_box",
            "WHERE packing_list_id = #{packingListId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<PackingBoxRecord> listPackingBoxes(@Param("packingListId") Long packingListId);

    @Select({
            "SELECT id, packing_list_id AS packingListId, packing_box_id AS packingBoxId, outbound_order_id AS outboundOrderId,",
            "       outbound_order_line_id AS outboundOrderLineId, owner_user_id AS ownerUserId, product_variant_id AS productVariantId,",
            "       partner_sku AS partnerSku, site_code AS siteCode, actual_transport_mode AS actualTransportMode, quantity",
            "FROM warehouse_packing_box_item",
            "WHERE packing_list_id = #{packingListId}",
            "  AND is_deleted = b'0'",
            "ORDER BY packing_box_id ASC, id ASC"
    })
    List<PackingBoxItemRecord> listPackingBoxItems(@Param("packingListId") Long packingListId);

    @Update({
            "UPDATE warehouse_packing_list",
            "SET status = 'CONFIRMED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{packingListId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status = 'DRAFT'",
            "  AND is_deleted = b'0'"
    })
    int confirmPackingList(
            @Param("packingListId") Long packingListId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE warehouse_outbound_order",
            "SET status = 'PACKED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{outboundOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('DRAFT', 'PACKING')",
            "  AND is_deleted = b'0'"
    })
    int markOutboundOrderPacked(
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, plan_no AS planNo, status, item_count AS itemCount, sku_count AS skuCount,",
            "       total_quantity AS totalQuantity, site_summary_json AS siteSummaryJson, transport_summary_json AS transportSummaryJson,",
            "       remark, handoff_generation_no AS handoffGenerationNo, handoff_request_no AS handoffRequestNo,",
            "       handoff_error_message AS handoffErrorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM procurement_dispatch_plan",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT #{limit}"
    })
    List<DispatchPlanRecord> listDispatchPlans(
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, plan_no AS planNo, status, item_count AS itemCount, sku_count AS skuCount,",
            "       total_quantity AS totalQuantity, site_summary_json AS siteSummaryJson, transport_summary_json AS transportSummaryJson,",
            "       remark, handoff_generation_no AS handoffGenerationNo, handoff_request_no AS handoffRequestNo,",
            "       handoff_error_message AS handoffErrorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM procurement_dispatch_plan",
            "WHERE id = #{dispatchPlanId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    DispatchPlanRecord selectDispatchPlanById(@Param("dispatchPlanId") Long dispatchPlanId);

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, plan_no AS planNo, status, item_count AS itemCount, sku_count AS skuCount,",
            "       total_quantity AS totalQuantity, site_summary_json AS siteSummaryJson, transport_summary_json AS transportSummaryJson,",
            "       remark, handoff_generation_no AS handoffGenerationNo, handoff_request_no AS handoffRequestNo,",
            "       handoff_error_message AS handoffErrorMessage",
            "FROM procurement_dispatch_plan",
            "WHERE handoff_request_no = #{handoffRequestNo}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    DispatchPlanRecord selectDispatchPlanByHandoffRequest(@Param("handoffRequestNo") String handoffRequestNo);

    @Update({
            "UPDATE procurement_dispatch_plan",
            "SET status = 'READY_FOR_LOGISTICS',",
            "    handoff_generation_no = #{handoffGenerationNo},",
            "    handoff_request_no = #{handoffRequestNo},",
            "    ready_for_logistics_at = NOW(),",
            "    handoff_failed_at = NULL,",
            "    handoff_error_message = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{dispatchPlanId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('DRAFT', 'HANDOFF_FAILED')",
            "  AND is_deleted = b'0'"
    })
    int updateDispatchPlanReady(
            @Param("dispatchPlanId") Long dispatchPlanId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("handoffGenerationNo") Integer handoffGenerationNo,
            @Param("handoffRequestNo") String handoffRequestNo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_dispatch_plan",
            "SET status = 'LOGISTICS_REQUESTED',",
            "    logistics_requested_at = NOW(),",
            "    handoff_confirmed_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE handoff_request_no = #{handoffRequestNo}",
            "  AND status IN ('READY_FOR_LOGISTICS', 'HANDOFF_FAILED')",
            "  AND is_deleted = b'0'"
    })
    int markDispatchPlanHandoffSuccess(
            @Param("handoffRequestNo") String handoffRequestNo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_dispatch_plan",
            "SET status = 'HANDOFF_FAILED',",
            "    handoff_failed_at = NOW(),",
            "    handoff_error_message = #{errorMessage},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE handoff_request_no = #{handoffRequestNo}",
            "  AND status = 'READY_FOR_LOGISTICS'",
            "  AND is_deleted = b'0'"
    })
    int markDispatchPlanHandoffFailed(
            @Param("handoffRequestNo") String handoffRequestNo,
            @Param("errorMessage") String errorMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_dispatch_plan",
            "SET status = 'DRAFT',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{dispatchPlanId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('READY_FOR_LOGISTICS', 'HANDOFF_FAILED')",
            "  AND handoff_confirmed_at IS NULL",
            "  AND is_deleted = b'0'"
    })
    int reopenDispatchPlanDraft(
            @Param("dispatchPlanId") Long dispatchPlanId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, dispatch_plan_id AS dispatchPlanId, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, partner_sku AS partnerSku, sku_parent AS skuParent, title_cache AS titleCache,",
            "       image_url_cache AS imageUrlCache, site_code AS siteCode, actual_transport_mode AS actualTransportMode,",
            "       fulfillment_type AS fulfillmentType, spec_status AS specStatus, quantity, source_count AS sourceCount",
            "FROM procurement_dispatch_plan_line",
            "WHERE dispatch_plan_id = #{dispatchPlanId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<DispatchPlanLineRecord> listDispatchPlanLines(@Param("dispatchPlanId") Long dispatchPlanId);

    @Select({
            "SELECT id, dispatch_plan_id AS dispatchPlanId, dispatch_plan_line_id AS dispatchPlanLineId, owner_user_id AS ownerUserId,",
            "       fulfillment_balance_id AS fulfillmentBalanceId, source_store_code AS sourceStoreCode, source_store_name AS sourceStoreName,",
            "       purchase_order_id AS purchaseOrderId, purchase_order_no AS purchaseOrderNo, purchase_order_item_id AS purchaseOrderItemId,",
            "       purchase_order_item_site_id AS purchaseOrderItemSiteId, planned_transport_mode AS plannedTransportMode,",
            "       fulfillment_type AS fulfillmentType, quantity",
            "FROM procurement_dispatch_plan_line_source",
            "WHERE dispatch_plan_id = #{dispatchPlanId}",
            "  AND is_deleted = b'0'",
            "ORDER BY dispatch_plan_line_id ASC, id ASC"
    })
    List<DispatchPlanLineSourceRecord> listDispatchLineSources(@Param("dispatchPlanId") Long dispatchPlanId);

    @Update({
            "UPDATE procurement_fulfillment_balance",
            "SET reserved_quantity = reserved_quantity - #{quantity},",
            "    logistics_handoff_quantity = logistics_handoff_quantity + #{quantity},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{balanceId}",
            "  AND reserved_quantity >= #{quantity}",
            "  AND is_deleted = b'0'"
    })
    void moveReservedToLogisticsHandoff(
            @Param("balanceId") Long balanceId,
            @Param("quantity") Integer quantity,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_dispatch_plan_operation_log (",
            "id, dispatch_plan_id, operation_type, operator_user_id, before_status, after_status, detail_json, gmt_create",
            ") VALUES (",
            "#{id}, #{dispatchPlanId}, #{operationType}, #{operatorUserId}, #{beforeStatus}, #{afterStatus}, #{detailJson}, NOW())"
    })
    int insertOperationLog(
            @Param("id") Long id,
            @Param("dispatchPlanId") Long dispatchPlanId,
            @Param("operationType") String operationType,
            @Param("operatorUserId") Long operatorUserId,
            @Param("beforeStatus") String beforeStatus,
            @Param("afterStatus") String afterStatus,
            @Param("detailJson") String detailJson
    );
}
