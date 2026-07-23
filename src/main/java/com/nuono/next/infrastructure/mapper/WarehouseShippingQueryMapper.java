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

public interface WarehouseShippingQueryMapper extends WarehouseShippingWriteMapper {

    String SHIPPING_BATCH_EXECUTION_STATUS = "CASE "
            + "WHEN EXISTS (SELECT 1 FROM warehouse_outbound_order shipped_order "
            + "WHERE shipped_order.batch_id = batch.id AND shipped_order.is_deleted = b'0') "
            + "AND NOT EXISTS (SELECT 1 FROM warehouse_outbound_order pending_order "
            + "WHERE pending_order.batch_id = batch.id AND pending_order.is_deleted = b'0' "
            + "AND pending_order.status != 'SHIPPED') THEN 'SHIPPED' "
            + "WHEN EXISTS (SELECT 1 FROM warehouse_outbound_order packed_order "
            + "WHERE packed_order.batch_id = batch.id AND packed_order.is_deleted = b'0') "
            + "AND NOT EXISTS (SELECT 1 FROM warehouse_outbound_order unpacked_order "
            + "WHERE unpacked_order.batch_id = batch.id AND unpacked_order.is_deleted = b'0' "
            + "AND unpacked_order.status NOT IN ('PACKED', 'SHIPPED')) THEN 'PACKED' "
            + "WHEN EXISTS (SELECT 1 FROM warehouse_outbound_order packing_order "
            + "WHERE packing_order.batch_id = batch.id AND packing_order.is_deleted = b'0' "
            + "AND packing_order.status = 'PACKING') THEN 'PACKING' "
            + "ELSE batch.status END AS status,";

@Select({
            "SELECT batch.id, batch.owner_user_id AS ownerUserId, batch.batch_no AS batchNo,",
            SHIPPING_BATCH_EXECUTION_STATUS,
            "       batch.selected_option_id AS selectedOptionId,",
            "       batch.source_count AS sourceCount, batch.sku_count AS skuCount, batch.total_quantity AS totalQuantity,",
            "       COALESCE(plan.optionCount, 0) AS optionCount, COALESCE(packing.packingListCount, 0) AS packingListCount,",
            "       COALESCE(packing.boxCount, 0) AS boxCount, COALESCE(packing.packedQuantity, 0) AS packedQuantity,",
            "       COALESCE(packing.grossWeightKg, 0) AS grossWeightKg, COALESCE(packing.volumeCbm, 0) AS volumeCbm,",
            "       batch.store_summary_json AS storeSummaryJson, batch.site_summary_json AS siteSummaryJson,",
            "       batch.transport_summary_json AS transportSummaryJson, batch.origin_summary_json AS originSummaryJson, batch.remark,",
            "       DATE_FORMAT(batch.gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(batch.gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_batch batch",
            "LEFT JOIN (SELECT batch_id, COUNT(*) AS optionCount FROM warehouse_shipping_suggestion_option",
            "           WHERE is_deleted = b'0' GROUP BY batch_id) plan ON plan.batch_id = batch.id",
            "LEFT JOIN (SELECT outbound.batch_id, COUNT(list.id) AS packingListCount,",
            "                  COALESCE(SUM(list.box_count), 0) AS boxCount,",
            "                  COALESCE(SUM(list.packed_quantity), 0) AS packedQuantity,",
            "                  COALESCE(SUM(list.gross_weight_kg), 0) AS grossWeightKg,",
            "                  COALESCE(SUM(list.volume_cbm), 0) AS volumeCbm",
            "           FROM warehouse_outbound_order outbound",
            "           LEFT JOIN warehouse_packing_list list ON list.outbound_order_id = outbound.id AND list.is_deleted = b'0'",
            "           WHERE outbound.is_deleted = b'0' GROUP BY outbound.batch_id) packing ON packing.batch_id = batch.id",
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "  AND batch.is_deleted = b'0'",
            "ORDER BY batch.gmt_updated DESC, batch.id DESC"
    })
    List<ShippingBatchRecord> listShippingBatches(@Param("ownerUserId") Long ownerUserId);

@Select({
            "SELECT id, owner_user_id AS ownerUserId, batch_no AS batchNo,",
            SHIPPING_BATCH_EXECUTION_STATUS,
            "       selected_option_id AS selectedOptionId,",
            "       source_count AS sourceCount, sku_count AS skuCount, total_quantity AS totalQuantity,",
            "       store_summary_json AS storeSummaryJson, site_summary_json AS siteSummaryJson,",
            "       transport_summary_json AS transportSummaryJson, origin_summary_json AS originSummaryJson, remark,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_batch batch",
            "WHERE batch.id = #{batchId}",
            "  AND batch.is_deleted = b'0'",
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
}
