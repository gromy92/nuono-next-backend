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

public interface WarehouseOutboundMapper extends WarehouseShippingQueryMapper {

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
            "SELECT source.id, source.outbound_order_id AS outboundOrderId, source.outbound_order_line_id AS outboundOrderLineId,",
            "       source.batch_source_id AS batchSourceId, source.fulfillment_balance_id AS fulfillmentBalanceId,",
            "       balance.logical_store_id AS logicalStoreId,",
            "       COALESCE(batch_source.source_store_code, balance.source_store_code) AS sourceStoreCode,",
            "       COALESCE(batch_source.source_store_name, balance.source_store_name) AS sourceStoreName,",
            "       source.purchase_order_id AS purchaseOrderId, source.purchase_order_no AS purchaseOrderNo,",
            "       source.purchase_order_title AS purchaseOrderTitle, source.purchase_order_item_id AS purchaseOrderItemId,",
            "       source.purchase_order_item_site_id AS purchaseOrderItemSiteId,",
            "       source.planned_transport_mode AS plannedTransportMode, source.quantity",
            "FROM warehouse_outbound_order_line_source source",
            "LEFT JOIN warehouse_shipping_batch_source batch_source",
            "  ON batch_source.id = source.batch_source_id",
            " AND batch_source.is_deleted = b'0'",
            "LEFT JOIN procurement_fulfillment_balance balance",
            "  ON balance.id = source.fulfillment_balance_id",
            " AND balance.is_deleted = b'0'",
            "WHERE source.outbound_order_id = #{outboundOrderId}",
            "  AND source.is_deleted = b'0'",
            "ORDER BY source.outbound_order_line_id ASC, source.id ASC"
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
}
