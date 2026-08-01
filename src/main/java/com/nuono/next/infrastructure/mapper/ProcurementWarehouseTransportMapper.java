package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurementorder.ProductForwarderTransportEligibilityRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProcurementWarehouseTransportMapper
        extends ProcurementShippingQuoteChannelMapper, ProductForwarderEligibilityLockMapper {

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

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, logical_store_id AS logicalStoreId,",
            "       source_store_code AS sourceStoreCode, partner_sku AS partnerSku,",
            "       site_code AS siteCode, forwarder_code AS forwarderCode, transport_mode AS transportMode,",
            "       eligibility_status AS eligibilityStatus, effective_from AS effectiveFrom,",
            "       effective_to AS effectiveTo, version,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM product_forwarder_transport_eligibility",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND product_variant_id IN",
            "  <foreach collection='productVariantIds' item='variantId' open='(' separator=',' close=')'>#{variantId}</foreach>",
            "  AND effective_to IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY effective_from DESC, version DESC, id DESC",
            "</script>"
    })
    List<ProductForwarderTransportEligibilityRecord> listCurrentProductForwarderTransportEligibilities(
            @Param("ownerUserId") Long ownerUserId,
            @Param("productVariantIds") List<Long> productVariantIds
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, logical_store_id AS logicalStoreId,",
            "       source_store_code AS sourceStoreCode, partner_sku AS partnerSku,",
            "       site_code AS siteCode, forwarder_code AS forwarderCode, transport_mode AS transportMode,",
            "       eligibility_status AS eligibilityStatus, effective_from AS effectiveFrom,",
            "       effective_to AS effectiveTo, version",
            "FROM product_forwarder_transport_eligibility",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND product_variant_id = #{productVariantId}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND UPPER(forwarder_code) = UPPER(#{forwarderCode})",
            "  AND UPPER(transport_mode) = UPPER(#{transportMode})",
            "  AND effective_to IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY version DESC, id DESC",
            "LIMIT 1 FOR UPDATE"
    })
    ProductForwarderTransportEligibilityRecord selectActiveProductForwarderTransportEligibilityForUpdate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("productVariantId") Long productVariantId,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );

    @Select({
            "SELECT version FROM product_forwarder_transport_eligibility",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND product_variant_id = #{productVariantId}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND UPPER(forwarder_code) = UPPER(#{forwarderCode})",
            "  AND UPPER(transport_mode) = UPPER(#{transportMode})",
            "ORDER BY version DESC, id DESC",
            "LIMIT 1 FOR UPDATE"
    })
    Integer selectLatestProductForwarderTransportEligibilityVersionForUpdate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("productVariantId") Long productVariantId,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );

    @Update({
            "UPDATE product_forwarder_transport_eligibility",
            "SET effective_to = #{effectiveTo}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND version = #{version}",
            "  AND effective_to IS NULL",
            "  AND is_deleted = b'0'"
    })
    int closeProductForwarderTransportEligibility(
            @Param("id") Long id,
            @Param("version") Integer version,
            @Param("effectiveTo") java.time.LocalDate effectiveTo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO product_forwarder_transport_eligibility (",
            "id, owner_user_id, product_master_id, product_variant_id, logical_store_id,",
            "source_store_code, partner_sku, site_code, forwarder_code, transport_mode,",
            "eligibility_status, effective_from, effective_to, version,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.productMasterId}, #{row.productVariantId}, #{row.logicalStoreId},",
            "#{row.sourceStoreCode}, #{row.partnerSku}, #{row.siteCode}, #{row.forwarderCode}, #{row.transportMode},",
            "#{row.eligibilityStatus}, #{row.effectiveFrom}, NULL, #{row.version},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertProductForwarderTransportEligibility(
            @Param("row") ProductForwarderTransportEligibilityRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, shipping_order_no AS shippingOrderNo, title, status,",
            "       shipping_submit_status AS shippingSubmitStatus",
            "FROM procurement_shipping_order",
            "WHERE id = #{shippingOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1 FOR UPDATE"
    })
    ShippingOrderRecord selectShippingOrderByIdForUpdate(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Update({
            "<script>",
            "UPDATE procurement_shipping_order_line",
            "SET shipping_order_segment_id = #{targetSegmentId},",
            "    planned_transport_mode = #{targetTransportMode},",
            "    quote_line_id = NULL,",
            "    eligibility_status_snapshot = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE shipping_order_id = #{shippingOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND id IN",
            "  <foreach collection='lineIds' item='lineId' open='(' separator=',' close=')'>#{lineId}</foreach>",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int reassignShippingOrderLines(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("lineIds") List<Long> lineIds,
            @Param("targetSegmentId") Long targetSegmentId,
            @Param("targetTransportMode") String targetTransportMode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE procurement_shipping_order_segment",
            "SET forwarder_code = NULL, forwarder_name = NULL,",
            "    route_code = NULL, route_name = NULL,",
            "    service_code = NULL, service_name = NULL,",
            "    quote_status = 'PENDING_QUOTE',",
            "    submitted_at = NULL, submitted_by = NULL,",
            "    updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE shipping_order_id = #{shippingOrderId}",
            "  AND id IN",
            "  <foreach collection='segmentIds' item='segmentId' open='(' separator=',' close=')'>#{segmentId}</foreach>",
            "  AND shipping_submit_status = 'NOT_SUBMITTED'",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int resetShippingOrderSegmentsAfterReassignment(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("segmentIds") List<Long> segmentIds,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order_segment segment",
            "SET line_count = (",
            "      SELECT COUNT(1) FROM procurement_shipping_order_line line",
            "      WHERE line.shipping_order_segment_id = segment.id AND line.is_deleted = b'0'",
            "    ),",
            "    sku_count = (",
            "      SELECT COUNT(DISTINCT CONCAT(COALESCE(line.source_store_code, ''), ':',",
            "             COALESCE(line.partner_sku, ''), ':', COALESCE(line.product_variant_id, 0)))",
            "      FROM procurement_shipping_order_line line",
            "      WHERE line.shipping_order_segment_id = segment.id AND line.is_deleted = b'0'",
            "    ),",
            "    total_quantity = (",
            "      SELECT COALESCE(SUM(line.quantity), 0) FROM procurement_shipping_order_line line",
            "      WHERE line.shipping_order_segment_id = segment.id AND line.is_deleted = b'0'",
            "    ),",
            "    missing_yite_material_count = CASE",
            "      WHEN UPPER(COALESCE(segment.forwarder_code, '')) IN ('YT', 'YITE') THEN (",
            "        SELECT COUNT(1) FROM procurement_shipping_order_line line",
            "        WHERE line.shipping_order_segment_id = segment.id",
            "          AND line.is_deleted = b'0'",
            "          AND (line.yite_material IS NULL OR TRIM(line.yite_material) = '')",
            "      ) ELSE 0 END,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE segment.shipping_order_id = #{shippingOrderId}",
            "  AND segment.is_deleted = b'0'"
    })
    int recalculateShippingOrderSegmentAggregates(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order_segment segment",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE segment.shipping_order_id = #{shippingOrderId}",
            "  AND segment.shipping_submit_status != 'SUBMITTED'",
            "  AND segment.is_deleted = b'0'",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM procurement_shipping_order_line line",
            "    WHERE line.shipping_order_segment_id = segment.id AND line.is_deleted = b'0'",
            "  )"
    })
    int softDeleteEmptyShippingOrderSegments(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order",
            "SET transport_summary_json = #{transportSummaryJson},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{shippingOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateShippingOrderTransportSummary(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("transportSummaryJson") String transportSummaryJson,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_shipping_order_line",
            "SET eligibility_status_snapshot = #{row.eligibilityStatus},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{row.shippingOrderLineId}",
            "  AND shipping_order_id = #{shippingOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int snapshotShippingOrderLineEligibility(
            @Param("shippingOrderId") Long shippingOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("row") PurchaseOrderLogisticsQuoteLineRecord row,
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
}
