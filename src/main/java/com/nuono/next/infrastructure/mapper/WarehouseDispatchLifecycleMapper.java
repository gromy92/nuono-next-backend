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

public interface WarehouseDispatchLifecycleMapper extends WarehousePackingMapper {

@Select({
            "SELECT id, owner_user_id AS ownerUserId, plan_no AS planNo, status, item_count AS itemCount, sku_count AS skuCount,",
            "       total_quantity AS totalQuantity, site_summary_json AS siteSummaryJson, transport_summary_json AS transportSummaryJson,",
            "       remark, handoff_generation_no AS handoffGenerationNo, handoff_request_no AS handoffRequestNo,",
            "       handoff_error_message AS handoffErrorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM procurement_dispatch_plan",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<DispatchPlanRecord> listDispatchPlans(@Param("ownerUserId") Long ownerUserId);

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
