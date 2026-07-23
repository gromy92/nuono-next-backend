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

public interface WarehouseProcurementMapper extends WarehouseSequenceMapper {

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
            "available_quantity, spec_status, status, is_deleted, created_by, updated_by, gmt_create, gmt_updated)",
            "SELECT site.id + 900000000, site.owner_user_id, site.logical_store_id, po.anchor_store_code_cache, po.project_name_cache,",
            "       site.purchase_order_id, po.order_no, po.title, site.purchase_order_item_id, site.id, item.product_master_id,",
            "       item.product_variant_id, item.partner_sku, item.sku_parent, item.title_cache, item.image_url_cache,",
            "       site.site_code, site.transport_mode, #{fulfillmentType}, site.quantity, 0, 0, 0, 0, 0, 'READY', 'OPEN', b'0',",
            "       #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            "FROM procurement_purchase_order_item_site site",
            "JOIN procurement_purchase_order_item item ON item.id = site.purchase_order_item_id AND item.is_deleted = b'0'",
            "JOIN procurement_purchase_order po ON po.id = site.purchase_order_id AND po.is_deleted = b'0'",
            "WHERE site.id = #{purchaseOrderItemSiteId}",
            "  AND site.is_deleted = b'0'",
            "ON DUPLICATE KEY UPDATE planned_quantity = VALUES(planned_quantity), source_store_code = VALUES(source_store_code),",
            "    source_store_name = VALUES(source_store_name), purchase_order_no = VALUES(purchase_order_no),",
            "    purchase_order_title = VALUES(purchase_order_title), partner_sku = VALUES(partner_sku), sku_parent = VALUES(sku_parent),",
            "    title_cache = VALUES(title_cache), image_url_cache = VALUES(image_url_cache),",
            "    planned_transport_mode = VALUES(planned_transport_mode), fulfillment_type = VALUES(fulfillment_type),",
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
}
