package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProcurementShippingQuoteChannelMapper {

    String SHIPPING_QUOTE_CHANNEL_MATCH = ""
            + " AND UPPER(COALESCE(quote.forwarder_code, '')) = UPPER(COALESCE(segment.forwarder_code, ''))"
            + " AND UPPER(COALESCE(quote.route_code, '')) = UPPER(COALESCE(segment.route_code, ''))"
            + " AND UPPER(COALESCE(quote.service_code, '')) = UPPER(COALESCE(segment.service_code, ''))";
    String SHIPPING_QUOTE_USABLE = " AND quote.unit_price > 0";
    String SHIPPING_QUOTE_SUBMITTABLE = ""
            + " AND NULLIF(TRIM(quote.forwarder_code), '') IS NOT NULL"
            + " AND NULLIF(TRIM(quote.route_code), '') IS NOT NULL"
            + " AND (quote.unit_price > 0"
            + " OR UPPER(TRIM(COALESCE(quote.forwarder_code, ''))) = 'ZD'"
            + " OR UPPER(TRIM(COALESCE(quote.route_code, ''))) LIKE 'ZD-%')";

    String CHANNEL_QUOTE_SELECT = ""
            + "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, "
            + "shipping_order_id AS shippingOrderId, shipping_order_no AS shippingOrderNo, "
            + "shipping_order_segment_id AS shippingOrderSegmentId, shipping_order_line_id AS shippingOrderLineId, "
            + "purchase_order_id AS purchaseOrderId, purchase_order_no AS purchaseOrderNo, "
            + "purchase_order_title AS purchaseOrderTitle, purchase_order_item_id AS purchaseOrderItemId, "
            + "purchase_order_item_site_id AS purchaseOrderItemSiteId, product_master_id AS productMasterId, "
            + "product_variant_id AS productVariantId, sku_parent AS skuParent, partner_sku AS partnerSku, "
            + "title_cache AS titleCache, site_code AS siteCode, psku_code AS pskuCode, yite_material AS yiteMaterial, "
            + "planned_transport_mode AS plannedTransportMode, quantity, fulfillment_type AS fulfillmentType, "
            + "is_new_product = b'1' AS isNewProduct, quote_status AS quoteStatus, "
            + "shipping_submit_status AS shippingSubmitStatus, forwarder_code AS forwarderCode, "
            + "forwarder_name AS forwarderName, route_code AS routeCode, route_name AS routeName, "
            + "service_code AS serviceCode, service_name AS serviceName, currency, unit_price AS unitPrice, "
            + "billing_unit AS billingUnit, estimated_amount AS estimatedAmount, remark, "
            + "DATE_FORMAT(exported_at, '%Y-%m-%d %H:%i') AS exportedAt, "
            + "DATE_FORMAT(confirmed_at, '%Y-%m-%d %H:%i') AS confirmedAt, "
            + "DATE_FORMAT(shipping_submitted_at, '%Y-%m-%d %H:%i') AS shippingSubmittedAt "
            + "FROM procurement_purchase_order_logistics_quote_line ";

    @Select(CHANNEL_QUOTE_SELECT
            + "WHERE shipping_order_id = #{shippingOrderId} "
            + "AND purchase_order_item_site_id = #{itemSiteId} "
            + "AND UPPER(COALESCE(forwarder_code, '')) = UPPER(COALESCE(#{forwarderCode}, '')) "
            + "AND UPPER(COALESCE(route_code, '')) = UPPER(COALESCE(#{routeCode}, '')) "
            + "AND UPPER(COALESCE(service_code, '')) = UPPER(COALESCE(#{serviceCode}, '')) "
            + "AND is_deleted = b'0' LIMIT 1 FOR UPDATE")
    PurchaseOrderLogisticsQuoteLineRecord selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("itemSiteId") Long itemSiteId,
            @Param("forwarderCode") String forwarderCode,
            @Param("routeCode") String routeCode,
            @Param("serviceCode") String serviceCode
    );

    @Select(CHANNEL_QUOTE_SELECT
            + "WHERE id = #{quoteLineId} "
            + "AND purchase_order_item_site_id = #{itemSiteId} "
            + "AND (shipping_order_id = #{documentId} "
            + "OR (shipping_order_id IS NULL AND purchase_order_id = #{documentId})) "
            + "AND is_deleted = b'0' LIMIT 1 FOR UPDATE")
    PurchaseOrderLogisticsQuoteLineRecord selectLogisticsQuoteLineByDocumentLineForUpdate(
            @Param("documentId") Long documentId,
            @Param("quoteLineId") Long quoteLineId,
            @Param("itemSiteId") Long itemSiteId
    );

    @Select(CHANNEL_QUOTE_SELECT
            + "WHERE shipping_order_id = #{shippingOrderId} "
            + "AND is_deleted = b'0' "
            + "ORDER BY purchase_order_item_site_id ASC, confirmed_at DESC, id DESC")
    List<PurchaseOrderLogisticsQuoteLineRecord> listLogisticsQuoteChannelSnapshotsByShippingOrder(
            @Param("shippingOrderId") Long shippingOrderId
    );

    @Select(CHANNEL_QUOTE_SELECT
            + "WHERE shipping_order_id = #{shippingOrderId} "
            + "AND unit_price > 0 AND is_deleted = b'0' "
            + "ORDER BY purchase_order_item_site_id ASC, confirmed_at DESC, id DESC")
    List<PurchaseOrderLogisticsQuoteLineRecord> listUsableLogisticsQuoteLinesByShippingOrder(
            @Param("shippingOrderId") Long shippingOrderId
    );
}
