package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WarehouseHandoffReceiptMapper {

    @Select({
            "SELECT id FROM procurement_dispatch_plan_operation_log",
            "WHERE operation_type = 'INVENTORY_HANDOFF_COMPLETED'",
            "  AND after_status = 'SHIPPED'",
            "  AND dispatch_plan_id <=> #{dispatchPlanId}",
            "  AND JSON_VALID(detail_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "        CASE WHEN JSON_VALID(detail_json) THEN detail_json ELSE '{}' END,",
            "        '$.shippingBatchId')) = CAST(#{shippingBatchId} AS CHAR)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "        CASE WHEN JSON_VALID(detail_json) THEN detail_json ELSE '{}' END,",
            "        '$.outboundOrderId')) = CAST(#{outboundOrderId} AS CHAR)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "        CASE WHEN JSON_VALID(detail_json) THEN detail_json ELSE '{}' END,",
            "        '$.packingListId')) = CAST(#{packingListId} AS CHAR)",
            "ORDER BY id DESC LIMIT 1"
    })
    Long selectInventoryHandoffCompletionReceiptId(
            @Param("dispatchPlanId") Long dispatchPlanId,
            @Param("shippingBatchId") Long shippingBatchId,
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("packingListId") Long packingListId
    );

    @Select({
            "SELECT id FROM procurement_dispatch_plan_operation_log",
            "WHERE operation_type = 'HANDOFF_SUCCESS'",
            "  AND after_status = 'LOGISTICS_REQUESTED'",
            "  AND dispatch_plan_id = #{dispatchPlanId}",
            "  AND JSON_VALID(detail_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "        CASE WHEN JSON_VALID(detail_json) THEN detail_json ELSE '{}' END,",
            "        '$.detail')) = #{handoffRequestNo}",
            "ORDER BY id DESC LIMIT 1"
    })
    Long selectLegacyDispatchPlanHandoffReceiptId(
            @Param("dispatchPlanId") Long dispatchPlanId,
            @Param("handoffRequestNo") String handoffRequestNo
    );
}
