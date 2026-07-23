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
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface WarehouseReceiptMapper extends WarehouseProcurementMapper {

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
            "       CASE WHEN warehouseSpec.product_length_cm IS NULL",
            "              OR warehouseSpec.product_width_cm IS NULL",
            "              OR warehouseSpec.product_height_cm IS NULL",
            "              OR warehouseSpec.product_weight_g IS NULL",
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
            "LEFT JOIN product_variant_spec_source warehouseSpec",
            "  ON warehouseSpec.variant_id = item.product_variant_id",
            " AND warehouseSpec.source_type = 'warehouse'",
            " AND warehouseSpec.is_deleted = b'0'",
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
            "         warehouseSpec.product_length_cm, warehouseSpec.product_width_cm,",
            "         warehouseSpec.product_height_cm, warehouseSpec.product_weight_g,",
            "         item.fulfillment_type, item.fulfillment_source_name",
            "ORDER BY so.gmt_create DESC, so.id DESC, po.id ASC, item.id ASC",
            "</script>"
    })
    List<PurchaseReceiptRow> listReceiptRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes,
            @Param("keyword") String keyword
    );
}
