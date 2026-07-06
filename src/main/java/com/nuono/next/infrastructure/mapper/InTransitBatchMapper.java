package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchRecords.AirArrivalDurationSampleRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchAggregateRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchLatestNodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InTransitBatchMapper extends InTransitGoodsSequenceMapper {

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM in_transit_batch batch",
            BATCH_FILTER_CONDITIONS,
            "</script>"
    })
    int countBatches(@Param("query") InTransitBatchQuery query);

    @Select({
            "<script>",
            BATCH_SELECT,
            BATCH_FILTER_CONDITIONS,
            "<choose>",
            "<when test='query.sortField == \"etaDate\"'>",
            "ORDER BY batch.eta_date IS NULL ASC, batch.eta_date ${query.sortDirectionSql}, batch.id DESC",
            "</when>",
            "<when test='query.sortField == \"latestNodeHappenedAt\"'>",
            "ORDER BY batch.latest_node_happened_at IS NULL ASC, batch.latest_node_happened_at ${query.sortDirectionSql}, batch.id DESC",
            "</when>",
            "<when test='query.sortField == \"gmtUpdated\"'>",
            "ORDER BY batch.gmt_updated ${query.sortDirectionSql}, batch.id DESC",
            "</when>",
            "<when test='query.sortField == \"createdAt\"'>",
            "ORDER BY COALESCE(batch.source_created_at, batch.gmt_create) ${query.sortDirectionSql}, batch.id DESC",
            "</when>",
            "<otherwise>",
            "ORDER BY FIELD(batch.batch_status, 'exception', 'in_transit', 'customs_clearance', 'delivering', 'shipped', 'pending_shipment', 'draft', 'warehouse_received', 'completed', 'cancelled'),",
            "batch.eta_date IS NULL ASC, batch.eta_date ASC, batch.gmt_updated DESC, batch.id DESC",
            "</otherwise>",
            "</choose>",
            "LIMIT #{query.limit} OFFSET #{query.offset}",
            "</script>"
    })
    List<BatchRow> listBatches(@Param("query") InTransitBatchQuery query);

    @Select({BATCH_SELECT, "WHERE batch.owner_user_id = #{ownerUserId} AND batch.id = #{batchId} AND batch.is_deleted = b'0' LIMIT 1"})
    BatchRow selectBatchById(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Select({
            BATCH_SELECT,
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "AND NULLIF(TRIM(#{batchReferenceNo}), '') IS NOT NULL",
            "AND NULLIF(TRIM(batch.batch_reference_no), '') IS NOT NULL",
            "AND UPPER(TRIM(batch.batch_reference_no)) = UPPER(TRIM(#{batchReferenceNo}))",
            "AND batch.is_deleted = b'0'",
            "ORDER BY batch.gmt_updated DESC, batch.id DESC",
            "LIMIT 1"
    })
    BatchRow selectBatchByReferenceNo(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchReferenceNo") String batchReferenceNo
    );

    @Insert({
            "INSERT INTO in_transit_batch (",
            "id, owner_user_id, standard_forwarder_id, raw_forwarder_name, normalized_raw_forwarder_name, forwarder_quality_status,",
            "transport_mode, batch_status, target_store_code, target_site_code, target_warehouse_name, departure_date, eta_date,",
            "tracking_no, container_no, batch_reference_no, external_shipment_no, source_created_at, estimated_departure_at,",
            "estimated_arrival_at, estimated_arrival_source, estimated_arrival_source_detail, estimated_arrival_updated_at,",
            "estimated_arrival_updated_by, delivery_appointment_text, missing_fields_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.standardForwarderId}, #{row.rawForwarderName}, #{row.normalizedRawForwarderName}, #{row.forwarderQualityStatus},",
            "#{row.transportMode}, #{row.batchStatus}, #{row.targetStoreCode}, #{row.targetSiteCode}, #{row.targetWarehouseName}, #{row.departureDate}, #{row.etaDate},",
            "#{row.trackingNo}, #{row.containerNo}, #{row.batchReferenceNo}, #{row.externalShipmentNo}, #{row.sourceCreatedAt},",
            "#{row.estimatedDepartureAt}, #{row.estimatedArrivalAt}, #{row.estimatedArrivalSource}, #{row.estimatedArrivalSourceDetail},",
            "#{row.estimatedArrivalUpdatedAt}, #{row.estimatedArrivalUpdatedBy}, #{row.deliveryAppointmentText}, #{row.missingFieldsJson},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertBatch(@Param("row") BatchRow row);

    @Update({
            "UPDATE in_transit_batch",
            "SET standard_forwarder_id = #{row.standardForwarderId}, raw_forwarder_name = #{row.rawForwarderName},",
            "normalized_raw_forwarder_name = #{row.normalizedRawForwarderName}, forwarder_quality_status = #{row.forwarderQualityStatus},",
            "transport_mode = #{row.transportMode}, batch_status = #{row.batchStatus}, target_store_code = #{row.targetStoreCode},",
            "target_site_code = #{row.targetSiteCode}, target_warehouse_name = #{row.targetWarehouseName}, departure_date = #{row.departureDate},",
            "eta_date = #{row.etaDate}, tracking_no = #{row.trackingNo}, container_no = #{row.containerNo}, batch_reference_no = #{row.batchReferenceNo},",
            "external_shipment_no = #{row.externalShipmentNo}, source_created_at = #{row.sourceCreatedAt},",
            "estimated_departure_at = #{row.estimatedDepartureAt}, estimated_arrival_at = #{row.estimatedArrivalAt},",
            "estimated_arrival_source = #{row.estimatedArrivalSource}, estimated_arrival_source_detail = #{row.estimatedArrivalSourceDetail},",
            "estimated_arrival_updated_at = #{row.estimatedArrivalUpdatedAt}, estimated_arrival_updated_by = #{row.estimatedArrivalUpdatedBy},",
            "delivery_appointment_text = #{row.deliveryAppointmentText},",
            "missing_fields_json = #{row.missingFieldsJson}, updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateBatch(@Param("row") BatchRow row);

    @Update({
            "UPDATE in_transit_batch",
            "SET estimated_arrival_at = #{estimatedArrivalAt}, eta_date = #{etaDate},",
            "estimated_arrival_source = #{estimatedArrivalSource},",
            "estimated_arrival_source_detail = #{estimatedArrivalSourceDetail},",
            "estimated_arrival_updated_at = NOW(), estimated_arrival_updated_by = #{operatorUserId},",
            "updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{batchId} AND is_deleted = b'0'"
    })
    int updateEstimatedArrival(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("estimatedArrivalAt") LocalDateTime estimatedArrivalAt,
            @Param("etaDate") LocalDate etaDate,
            @Param("estimatedArrivalSource") String estimatedArrivalSource,
            @Param("estimatedArrivalSourceDetail") String estimatedArrivalSourceDetail,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "<script>",
            "SELECT TIMESTAMPDIFF(MINUTE,",
            "COALESCE(batch.estimated_departure_at, batch.source_created_at, TIMESTAMP(batch.departure_date)),",
            "received.node_happened_at) AS duration_minutes",
            "FROM in_transit_batch batch",
            "JOIN in_transit_logistics_node received ON received.owner_user_id = batch.owner_user_id",
            "AND received.batch_id = batch.id AND received.node_status = 'warehouse_received'",
            "AND received.is_deleted = b'0'",
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "AND batch.transport_mode = 'AIR'",
            "AND batch.is_deleted = b'0'",
            "AND COALESCE(batch.estimated_departure_at, batch.source_created_at, TIMESTAMP(batch.departure_date)) IS NOT NULL",
            "AND received.node_happened_at IS NOT NULL",
            "<if test='standardForwarderId != null'> AND batch.standard_forwarder_id = #{standardForwarderId}</if>",
            "<if test='targetStoreCode != null and targetStoreCode != \"\"'> AND batch.target_store_code = #{targetStoreCode}</if>",
            "<if test='targetSiteCode != null and targetSiteCode != \"\"'> AND batch.target_site_code = #{targetSiteCode}</if>",
            "AND TIMESTAMPDIFF(MINUTE,",
            "COALESCE(batch.estimated_departure_at, batch.source_created_at, TIMESTAMP(batch.departure_date)),",
            "received.node_happened_at) BETWEEN 60 AND 43200",
            "ORDER BY received.node_happened_at DESC, batch.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<AirArrivalDurationSampleRow> selectRecentAirArrivalDurations(
            @Param("ownerUserId") Long ownerUserId,
            @Param("standardForwarderId") Long standardForwarderId,
            @Param("targetStoreCode") String targetStoreCode,
            @Param("targetSiteCode") String targetSiteCode,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "CASE WHEN COUNT(*) = 0 THEN NULL ELSE COUNT(DISTINCT NULLIF(TRIM(psku), '')) END AS sku_count,",
            "CASE WHEN COUNT(*) = 0 THEN NULL ELSE COUNT(DISTINCT NULLIF(TRIM(box_no), '')) END AS box_count,",
            "SUM(shipped_quantity) AS shipped_quantity_total,",
            "SUM(received_quantity) AS received_quantity_total,",
            "SUM(remaining_quantity) AS remaining_quantity_total,",
            "SUM(carton_count) AS carton_count_total,",
            "COALESCE(",
            "(SELECT SUM(pkg.weight_kg) FROM in_transit_package pkg",
            "WHERE pkg.owner_user_id = #{ownerUserId} AND pkg.batch_id = #{batchId}",
            "AND pkg.is_deleted = b'0' AND pkg.weight_kg IS NOT NULL),",
            "SUM(CASE WHEN carton_weight_kg IS NULL THEN NULL ELSE carton_count * carton_weight_kg END)",
            ") AS total_weight_kg,",
            "COALESCE(",
            "(SELECT SUM(pkg.volume_cbm) FROM in_transit_package pkg",
            "WHERE pkg.owner_user_id = #{ownerUserId} AND pkg.batch_id = #{batchId}",
            "AND pkg.is_deleted = b'0' AND pkg.volume_cbm IS NOT NULL),",
            "SUM(CASE WHEN carton_volume_cbm IS NULL THEN NULL ELSE carton_count * carton_volume_cbm END)",
            ") AS total_volume_cbm",
            "FROM in_transit_goods_line",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'"
    })
    BatchAggregateRow aggregateLines(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Update({
            "UPDATE in_transit_batch",
            "SET box_count = #{aggregate.boxCount}, sku_count = #{aggregate.skuCount}, shipped_quantity_total = #{aggregate.shippedQuantityTotal},",
            "received_quantity_total = #{aggregate.receivedQuantityTotal}, remaining_quantity_total = #{aggregate.remainingQuantityTotal},",
            "carton_count_total = #{aggregate.cartonCountTotal},",
            "total_weight_kg = COALESCE(#{aggregate.totalWeightKg}, total_weight_kg),",
            "total_volume_cbm = COALESCE(#{aggregate.totalVolumeCbm}, total_volume_cbm),",
            "gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{batchId} AND is_deleted = b'0'"
    })
    int refreshBatchAggregate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("aggregate") BatchAggregateRow aggregate
    );
}
