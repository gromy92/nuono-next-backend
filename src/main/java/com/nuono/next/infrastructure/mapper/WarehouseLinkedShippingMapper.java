package com.nuono.next.infrastructure.mapper;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WarehouseLinkedShippingMapper extends WarehouseShippingQueryMapper {

    @Select({
            "SELECT batch.id, batch.owner_user_id AS ownerUserId,",
            "       batch.dispatch_plan_id AS dispatchPlanId,",
            "       batch.client_request_id AS clientRequestId,",
            "       batch.request_fingerprint AS requestFingerprint,",
            "       batch.batch_no AS batchNo,",
            SHIPPING_BATCH_EXECUTION_STATUS,
            "       batch.selected_option_id AS selectedOptionId,",
            "       batch.source_count AS sourceCount, batch.sku_count AS skuCount,",
            "       batch.total_quantity AS totalQuantity,",
            "       batch.store_summary_json AS storeSummaryJson,",
            "       batch.site_summary_json AS siteSummaryJson,",
            "       batch.transport_summary_json AS transportSummaryJson,",
            "       batch.origin_summary_json AS originSummaryJson, batch.remark,",
            "       DATE_FORMAT(batch.gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(batch.gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_batch batch",
            "WHERE batch.dispatch_plan_id = #{dispatchPlanId}",
            "  AND batch.is_deleted = b'0'",
            "ORDER BY batch.gmt_updated DESC, batch.id DESC",
            "LIMIT 1"
    })
    ShippingBatchRecord selectLatestShippingBatchByDispatchPlan(
            @Param("dispatchPlanId") Long dispatchPlanId
    );

    @Select({
            "<script>",
            "SELECT batch.id, batch.owner_user_id AS ownerUserId,",
            "       batch.dispatch_plan_id AS dispatchPlanId, batch.batch_no AS batchNo,",
            SHIPPING_BATCH_EXECUTION_STATUS,
            "       batch.selected_option_id AS selectedOptionId,",
            "       batch.source_count AS sourceCount, batch.sku_count AS skuCount,",
            "       batch.total_quantity AS totalQuantity, COALESCE(options.optionCount, 0) AS optionCount,",
            "       sourceSummary.volumeCbm,",
            "       batch.store_summary_json AS storeSummaryJson, batch.site_summary_json AS siteSummaryJson,",
            "       batch.transport_summary_json AS transportSummaryJson,",
            "       batch.origin_summary_json AS originSummaryJson, batch.remark,",
            "       DATE_FORMAT(batch.gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(batch.gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_shipping_batch batch",
            "JOIN procurement_dispatch_plan dispatchPlan",
            "  ON dispatchPlan.id = batch.dispatch_plan_id",
            " AND dispatchPlan.owner_user_id = batch.owner_user_id",
            " AND dispatchPlan.is_deleted = b'0'",
            "LEFT JOIN (",
            "    SELECT batch_id, COUNT(*) AS optionCount",
            "    FROM warehouse_shipping_suggestion_option",
            "    WHERE is_deleted = b'0' GROUP BY batch_id",
            ") options ON options.batch_id = batch.id",
            "LEFT JOIN (",
            "    SELECT source.batch_id,",
            "      CASE WHEN SUM(source.product_length_cm IS NULL OR source.product_length_cm &lt;= 0",
            "                     OR source.product_width_cm IS NULL OR source.product_width_cm &lt;= 0",
            "                     OR source.product_height_cm IS NULL OR source.product_height_cm &lt;= 0) &gt; 0 THEN NULL",
            "           ELSE ROUND(SUM(source.product_length_cm * source.product_width_cm",
            "                     * source.product_height_cm * source.reserved_quantity) / 1000000, 4) END AS volumeCbm",
            "    FROM warehouse_shipping_batch_source source",
            "    WHERE source.is_deleted = b'0' GROUP BY source.batch_id",
            ") sourceSummary ON sourceSummary.batch_id = batch.id",
            "WHERE batch.dispatch_plan_id IN",
            "<foreach collection='dispatchPlanIds' item='dispatchPlanId' open='(' separator=',' close=')'>",
            "  #{dispatchPlanId}",
            "</foreach>",
            "  AND batch.is_deleted = b'0'",
            WarehouseAggregateSourceScopeMapper.SHIPPING_BATCH_SOURCE_SCOPE,
            "  AND batch.id = (",
            "      SELECT latest.id FROM warehouse_shipping_batch latest",
            "      WHERE latest.dispatch_plan_id = batch.dispatch_plan_id",
            "        AND latest.is_deleted = b'0'",
            "      ORDER BY latest.gmt_updated DESC, latest.id DESC LIMIT 1",
            "  )",
            "ORDER BY batch.dispatch_plan_id ASC",
            "</script>"
    })
    List<ShippingBatchRecord> listLatestShippingBatchSummariesByDispatchPlanIds(
            @Param("dispatchPlanIds") Collection<Long> dispatchPlanIds,
            @Param("storeOwnerUserIds") Map<String, Long> storeOwnerUserIds
    );
}
