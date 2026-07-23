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

public interface WarehouseShippingWriteMapper extends WarehouseDispatchWriteMapper {

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
}
