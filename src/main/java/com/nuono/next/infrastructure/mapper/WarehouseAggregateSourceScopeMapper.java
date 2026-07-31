package com.nuono.next.infrastructure.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WarehouseAggregateSourceScopeMapper {

    String EXACT_SOURCE_STORE_PAIR = ""
            + "<foreach collection='storeOwnerUserIds' index='storeCode' item='ownerUserId'"
            + " open='(' separator=' OR ' close=')'>"
            + " (source.owner_user_id = #{ownerUserId}"
            + "  AND source.source_store_code = #{storeCode})"
            + "</foreach>";

    String DISPATCH_PLAN_SOURCE_SCOPE = ""
            + " AND plan.owner_user_id IS NOT NULL"
            + " AND EXISTS ("
            + "   SELECT 1 FROM procurement_dispatch_plan_line_source source"
            + "   WHERE source.dispatch_plan_id = plan.id"
            + "     AND source.is_deleted = b'0'"
            + " )"
            + "<choose>"
            + "<when test='storeOwnerUserIds != null and storeOwnerUserIds.size() &gt; 0'>"
            + " AND plan.owner_user_id IN"
            + " <foreach collection='storeOwnerUserIds' item='ownerUserId'"
            + "  open='(' separator=',' close=')'>#{ownerUserId}</foreach>"
            + " AND NOT EXISTS ("
            + "   SELECT 1 FROM procurement_dispatch_plan_line_source source"
            + "   WHERE source.dispatch_plan_id = plan.id"
            + "     AND source.is_deleted = b'0'"
            + "     AND (source.owner_user_id IS NULL"
            + "       OR source.source_store_code IS NULL"
            + "       OR TRIM(source.source_store_code) = ''"
            + "       OR source.owner_user_id != plan.owner_user_id"
            + "       OR NOT (source.owner_user_id = plan.owner_user_id AND "
            + EXACT_SOURCE_STORE_PAIR
            + "       )"
            + "     )"
            + " )"
            + "</when>"
            + "<otherwise> AND 1 = 0 </otherwise>"
            + "</choose>";

    String SHIPPING_BATCH_SOURCE_SCOPE = ""
            + " AND batch.owner_user_id IS NOT NULL"
            + " AND EXISTS ("
            + "   SELECT 1 FROM warehouse_shipping_batch_source source"
            + "   WHERE source.batch_id = batch.id"
            + "     AND source.is_deleted = b'0'"
            + " )"
            + "<choose>"
            + "<when test='storeOwnerUserIds != null and storeOwnerUserIds.size() &gt; 0'>"
            + " AND batch.owner_user_id IN"
            + " <foreach collection='storeOwnerUserIds' item='ownerUserId'"
            + "  open='(' separator=',' close=')'>#{ownerUserId}</foreach>"
            + " AND NOT EXISTS ("
            + "   SELECT 1 FROM warehouse_shipping_batch_source source"
            + "   WHERE source.batch_id = batch.id"
            + "     AND source.is_deleted = b'0'"
            + "     AND (source.owner_user_id IS NULL"
            + "       OR source.source_store_code IS NULL"
            + "       OR TRIM(source.source_store_code) = ''"
            + "       OR source.owner_user_id != batch.owner_user_id"
            + "       OR NOT (source.owner_user_id = batch.owner_user_id AND "
            + EXACT_SOURCE_STORE_PAIR
            + "       )"
            + "     )"
            + " )"
            + "</when>"
            + "<otherwise> AND 1 = 0 </otherwise>"
            + "</choose>";

    String RESOLVED_OUTBOUND_SOURCE_STORE = ""
            + "COALESCE("
            + "NULLIF(TRIM(source.source_store_code), ''),"
            + "NULLIF(TRIM(batch_source.source_store_code), ''),"
            + "NULLIF(TRIM(balance.source_store_code), '')"
            + ")";

    String OUTBOUND_SOURCE_STORE_PAIR = ""
            + "<foreach collection='storeOwnerUserIds' index='storeCode' item='ownerUserId'"
            + " open='(' separator=' OR ' close=')'>"
            + " (outbound.owner_user_id = #{ownerUserId}"
            + "  AND (batch_source.id IS NULL"
            + "       OR (batch_source.batch_id = outbound.batch_id"
            + "           AND batch_source.owner_user_id = outbound.owner_user_id))"
            + "  AND (balance.id IS NULL OR balance.owner_user_id = outbound.owner_user_id)"
            + "  AND "
            + RESOLVED_OUTBOUND_SOURCE_STORE
            + " = #{storeCode})"
            + "</foreach>";

    String OUTBOUND_SOURCE_SCOPE = ""
            + " AND outbound.owner_user_id IS NOT NULL"
            + " AND EXISTS ("
            + "   SELECT 1 FROM warehouse_outbound_order_line_source source"
            + "   WHERE source.outbound_order_id = outbound.id"
            + "     AND source.is_deleted = b'0'"
            + " )"
            + "<choose>"
            + "<when test='storeOwnerUserIds != null and storeOwnerUserIds.size() &gt; 0'>"
            + " AND NOT EXISTS ("
            + "   SELECT 1 FROM warehouse_outbound_order_line_source source"
            + "   LEFT JOIN warehouse_shipping_batch_source batch_source"
            + "     ON batch_source.id = source.batch_source_id"
            + "    AND batch_source.is_deleted = b'0'"
            + "   LEFT JOIN procurement_fulfillment_balance balance"
            + "     ON balance.id = source.fulfillment_balance_id"
            + "    AND balance.is_deleted = b'0'"
            + "   WHERE source.outbound_order_id = outbound.id"
            + "     AND source.is_deleted = b'0'"
            + "     AND ("
            + "       "
            + RESOLVED_OUTBOUND_SOURCE_STORE
            + " IS NULL"
            + "       OR (batch_source.id IS NOT NULL"
            + "           AND (batch_source.batch_id IS NULL"
            + "             OR batch_source.batch_id != outbound.batch_id"
            + "             OR batch_source.owner_user_id IS NULL"
            + "             OR batch_source.owner_user_id != outbound.owner_user_id))"
            + "       OR (balance.id IS NOT NULL"
            + "           AND (balance.owner_user_id IS NULL"
            + "             OR balance.owner_user_id != outbound.owner_user_id))"
            + "       OR NOT "
            + OUTBOUND_SOURCE_STORE_PAIR
            + "     )"
            + " )"
            + "</when>"
            + "<otherwise> AND 1 = 0 </otherwise>"
            + "</choose>";

    String PACKING_SOURCE_STORE_PAIR = ""
            + "<foreach collection='storeOwnerUserIds' index='storeCode' item='ownerUserId'"
            + " open='(' separator=' OR ' close=')'>"
            + " (packing.owner_user_id = #{ownerUserId}"
            + "  AND packing.owner_user_id = outbound.owner_user_id"
            + "  AND (batch_source.id IS NULL"
            + "       OR (batch_source.batch_id = outbound.batch_id"
            + "           AND batch_source.owner_user_id = packing.owner_user_id))"
            + "  AND (balance.id IS NULL OR balance.owner_user_id = packing.owner_user_id)"
            + "  AND "
            + RESOLVED_OUTBOUND_SOURCE_STORE
            + " = #{storeCode})"
            + "</foreach>";

    String PACKING_SOURCE_SCOPE = ""
            + " AND packing.owner_user_id IS NOT NULL"
            + " AND packing.owner_user_id = outbound.owner_user_id"
            + " AND EXISTS ("
            + "   SELECT 1 FROM warehouse_outbound_order_line_source source"
            + "   WHERE source.outbound_order_id = outbound.id"
            + "     AND source.is_deleted = b'0'"
            + " )"
            + "<choose>"
            + "<when test='storeOwnerUserIds != null and storeOwnerUserIds.size() &gt; 0'>"
            + " AND NOT EXISTS ("
            + "   SELECT 1 FROM warehouse_outbound_order_line_source source"
            + "   LEFT JOIN warehouse_shipping_batch_source batch_source"
            + "     ON batch_source.id = source.batch_source_id"
            + "    AND batch_source.is_deleted = b'0'"
            + "   LEFT JOIN procurement_fulfillment_balance balance"
            + "     ON balance.id = source.fulfillment_balance_id"
            + "    AND balance.is_deleted = b'0'"
            + "   WHERE source.outbound_order_id = outbound.id"
            + "     AND source.is_deleted = b'0'"
            + "     AND ("
            + "       "
            + RESOLVED_OUTBOUND_SOURCE_STORE
            + " IS NULL"
            + "       OR (batch_source.id IS NOT NULL"
            + "           AND (batch_source.batch_id IS NULL"
            + "             OR batch_source.batch_id != outbound.batch_id"
            + "             OR batch_source.owner_user_id IS NULL"
            + "             OR batch_source.owner_user_id != packing.owner_user_id))"
            + "       OR (balance.id IS NOT NULL"
            + "           AND (balance.owner_user_id IS NULL"
            + "             OR balance.owner_user_id != packing.owner_user_id))"
            + "       OR NOT "
            + PACKING_SOURCE_STORE_PAIR
            + "     )"
            + " )"
            + "</when>"
            + "<otherwise> AND 1 = 0 </otherwise>"
            + "</choose>";

    @Select({
            "<script>",
            "SELECT EXISTS (",
            "  SELECT 1 FROM procurement_dispatch_plan plan",
            "  WHERE plan.id = #{dispatchPlanId}",
            "    AND plan.is_deleted = b'0'",
            DISPATCH_PLAN_SOURCE_SCOPE,
            ")",
            "</script>"
    })
    boolean isDispatchPlanSourceScopeAuthorized(
            @Param("dispatchPlanId") Long dispatchPlanId,
            @Param("storeOwnerUserIds") Map<String, Long> storeOwnerUserIds
    );

    @Select({
            "<script>",
            "SELECT EXISTS (",
            "  SELECT 1 FROM warehouse_shipping_batch batch",
            "  WHERE batch.id = #{batchId}",
            "    AND batch.is_deleted = b'0'",
            SHIPPING_BATCH_SOURCE_SCOPE,
            ")",
            "</script>"
    })
    boolean isShippingBatchSourceScopeAuthorized(
            @Param("batchId") Long batchId,
            @Param("storeOwnerUserIds") Map<String, Long> storeOwnerUserIds
    );

    @Select({
            "<script>",
            "SELECT EXISTS (",
            "  SELECT 1 FROM warehouse_outbound_order outbound",
            "  WHERE outbound.id = #{outboundOrderId}",
            "    AND outbound.is_deleted = b'0'",
            OUTBOUND_SOURCE_SCOPE,
            ")",
            "</script>"
    })
    boolean isOutboundOrderSourceScopeAuthorized(
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("storeOwnerUserIds") Map<String, Long> storeOwnerUserIds
    );

    @Select({
            "<script>",
            "SELECT EXISTS (",
            "  SELECT 1 FROM warehouse_packing_list packing",
            "  JOIN warehouse_outbound_order outbound",
            "    ON outbound.id = packing.outbound_order_id",
            "   AND outbound.is_deleted = b'0'",
            "  WHERE packing.id = #{packingListId}",
            "    AND packing.is_deleted = b'0'",
            PACKING_SOURCE_SCOPE,
            ")",
            "</script>"
    })
    boolean isPackingListSourceScopeAuthorized(
            @Param("packingListId") Long packingListId,
            @Param("storeOwnerUserIds") Map<String, Long> storeOwnerUserIds
    );
}
