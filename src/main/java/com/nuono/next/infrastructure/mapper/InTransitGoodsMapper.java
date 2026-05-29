package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasRow;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderRow;
import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchRecords.BatchAggregateRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchLatestNodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.ImportBatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.NodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.OperationAuditRow;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface InTransitGoodsMapper {

    String FORWARDER_SELECT = ""
            + "SELECT id, owner_user_id, forwarder_code, forwarder_name, status, created_by, updated_by "
            + "FROM in_transit_forwarder ";

    String ALIAS_SELECT = ""
            + "SELECT alias.id, alias.owner_user_id, alias.standard_forwarder_id, alias.raw_forwarder_name, "
            + "alias.normalized_raw_forwarder_name, alias.status, alias.created_by, alias.updated_by, "
            + "forwarder.forwarder_code AS standard_forwarder_code, forwarder.forwarder_name AS standard_forwarder_name "
            + "FROM in_transit_forwarder_alias alias "
            + "LEFT JOIN in_transit_forwarder forwarder ON forwarder.id = alias.standard_forwarder_id "
            + "AND forwarder.owner_user_id = alias.owner_user_id AND forwarder.is_deleted = b'0' ";

    String BATCH_SELECT = ""
            + "SELECT batch.id, batch.owner_user_id, batch.standard_forwarder_id, "
            + "forwarder.forwarder_code AS standard_forwarder_code, forwarder.forwarder_name AS standard_forwarder_name, "
            + "batch.raw_forwarder_name, batch.normalized_raw_forwarder_name, batch.forwarder_quality_status, "
            + "batch.transport_mode, batch.batch_status, batch.target_store_code, batch.target_site_code, batch.target_warehouse_name, "
            + "batch.departure_date, batch.eta_date, batch.tracking_no, batch.container_no, batch.batch_reference_no, batch.remark, "
            + "batch.missing_fields_json, batch.sku_count, batch.shipped_quantity_total, batch.received_quantity_total, "
            + "batch.remaining_quantity_total, batch.carton_count_total, batch.total_weight_kg, batch.total_volume_cbm, "
            + "batch.latest_node_status, batch.latest_node_happened_at, batch.latest_node_description, "
            + "batch.created_by, batch.updated_by "
            + "FROM in_transit_batch batch "
            + "LEFT JOIN in_transit_forwarder forwarder ON forwarder.id = batch.standard_forwarder_id "
            + "AND forwarder.owner_user_id = batch.owner_user_id AND forwarder.is_deleted = b'0' ";

    String LINE_SELECT = ""
            + "SELECT id, owner_user_id, batch_id, package_id, box_no, sku, msku, psku, product_name, store_code, site_code, "
            + "shipped_quantity, received_quantity, remaining_quantity, carton_count, units_per_carton, "
            + "carton_weight_kg, carton_volume_cbm, remark, created_by, updated_by "
            + "FROM in_transit_goods_line ";

    String PACKAGE_SELECT = ""
            + "SELECT id, owner_user_id, batch_id, box_no, tracking_no, remark, created_by, updated_by "
            + "FROM in_transit_package ";

    String NODE_SELECT = ""
            + "SELECT id, owner_user_id, batch_id, node_status, node_happened_at, description, operator_name, created_by, updated_by "
            + "FROM in_transit_logistics_node ";

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextForwarderId() {
        return nextId("in_transit_forwarder", 51000L);
    }

    default Long nextForwarderAliasId() {
        return nextId("in_transit_forwarder_alias", 52000L);
    }

    default Long nextBatchId() {
        return nextId("in_transit_batch", 53000L);
    }

    default Long nextLineId() {
        return nextId("in_transit_goods_line", 54000L);
    }

    default Long nextPackageId() {
        return nextId("in_transit_package", 58000L);
    }

    default Long nextNodeId() {
        return nextId("in_transit_logistics_node", 55000L);
    }

    default Long nextImportBatchId() {
        return nextId("in_transit_import_batch", 56000L);
    }

    default Long nextOperationAuditId() {
        return nextId("in_transit_operation_audit", 57000L);
    }

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("在途商品 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }

    @Select({FORWARDER_SELECT, "WHERE owner_user_id = #{ownerUserId} AND id = #{forwarderId} AND is_deleted = b'0' LIMIT 1"})
    ForwarderRow selectForwarderById(@Param("ownerUserId") Long ownerUserId, @Param("forwarderId") Long forwarderId);

    @Select({FORWARDER_SELECT, "WHERE owner_user_id = #{ownerUserId} AND forwarder_code = #{forwarderCode} AND is_deleted = b'0' LIMIT 1"})
    ForwarderRow selectForwarderByOwnerAndCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("forwarderCode") String forwarderCode
    );

    @Select({FORWARDER_SELECT, "WHERE owner_user_id = #{ownerUserId} AND is_deleted = b'0' ORDER BY forwarder_name ASC, id ASC"})
    List<ForwarderRow> listForwarders(@Param("ownerUserId") Long ownerUserId);

    @Insert({
            "INSERT INTO in_transit_forwarder (",
            "id, owner_user_id, forwarder_code, forwarder_name, status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.forwarderCode}, #{row.forwarderName}, #{row.status}, b'0',",
            "#{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertForwarder(@Param("row") ForwarderRow row);

    @Update({
            "UPDATE in_transit_forwarder",
            "SET forwarder_name = #{row.forwarderName}, status = #{row.status}, updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateForwarder(@Param("row") ForwarderRow row);

    @Select({ALIAS_SELECT, "WHERE alias.owner_user_id = #{ownerUserId} AND alias.id = #{aliasId} AND alias.is_deleted = b'0' LIMIT 1"})
    ForwarderAliasRow selectAliasById(@Param("ownerUserId") Long ownerUserId, @Param("aliasId") Long aliasId);

    @Select({
            ALIAS_SELECT,
            "WHERE alias.owner_user_id = #{ownerUserId}",
            "AND alias.normalized_raw_forwarder_name = #{normalizedRawForwarderName}",
            "AND alias.is_deleted = b'0' LIMIT 1"
    })
    ForwarderAliasRow selectAliasByOwnerAndNormalized(
            @Param("ownerUserId") Long ownerUserId,
            @Param("normalizedRawForwarderName") String normalizedRawForwarderName
    );

    @Select({
            ALIAS_SELECT,
            "WHERE alias.owner_user_id = #{ownerUserId}",
            "AND alias.normalized_raw_forwarder_name = #{normalizedRawForwarderName}",
            "AND alias.status = 'ACTIVE'",
            "AND alias.is_deleted = b'0' LIMIT 1"
    })
    ForwarderAliasRow selectActiveAliasByOwnerAndNormalized(
            @Param("ownerUserId") Long ownerUserId,
            @Param("normalizedRawForwarderName") String normalizedRawForwarderName
    );

    @Insert({
            "INSERT INTO in_transit_forwarder_alias (",
            "id, owner_user_id, standard_forwarder_id, raw_forwarder_name, normalized_raw_forwarder_name,",
            "status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.standardForwarderId}, #{row.rawForwarderName},",
            "#{row.normalizedRawForwarderName}, #{row.status}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertForwarderAlias(@Param("row") ForwarderAliasRow row);

    @Update({
            "UPDATE in_transit_forwarder_alias",
            "SET standard_forwarder_id = #{row.standardForwarderId}, raw_forwarder_name = #{row.rawForwarderName},",
            "status = #{row.status}, updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateForwarderAlias(@Param("row") ForwarderAliasRow row);

    @Select({
            "<script>",
            BATCH_SELECT,
            "WHERE batch.owner_user_id = #{query.ownerUserId} AND batch.is_deleted = b'0'",
            "<if test='query.standardForwarderId != null'> AND batch.standard_forwarder_id = #{query.standardForwarderId}</if>",
            "<if test='query.rawForwarderName != null and query.rawForwarderName != \"\"'> AND batch.raw_forwarder_name LIKE CONCAT('%', #{query.rawForwarderName}, '%')</if>",
            "<if test='query.transportMode != null and query.transportMode != \"\"'> AND batch.transport_mode = #{query.transportMode}</if>",
            "<if test='query.targetStoreCode != null and query.targetStoreCode != \"\"'> AND batch.target_store_code = #{query.targetStoreCode}</if>",
            "<if test='query.targetSiteCode != null and query.targetSiteCode != \"\"'> AND batch.target_site_code = #{query.targetSiteCode}</if>",
            "<if test='query.accessScopeRestricted'>",
            "AND (",
            "(batch.target_store_code IS NULL OR batch.target_store_code = '' OR batch.target_site_code IS NULL OR batch.target_site_code = '')",
            "<if test='query.allowedStoreSites != null and query.allowedStoreSites.size() > 0'>",
            "OR",
            "<foreach collection='query.allowedStoreSites' item='scope' separator=' OR '>",
            "(batch.target_store_code = #{scope.storeCode} AND batch.target_site_code = #{scope.siteCode})",
            "</foreach>",
            "</if>",
            ")",
            "</if>",
            "<if test='query.targetWarehouseName != null and query.targetWarehouseName != \"\"'> AND batch.target_warehouse_name LIKE CONCAT('%', #{query.targetWarehouseName}, '%')</if>",
            "<if test='query.batchStatus != null and query.batchStatus != \"\"'> AND batch.batch_status = #{query.batchStatus}</if>",
            "<if test='(query.batchStatus == null or query.batchStatus == \"\") and query.statusScope == \"active\"'>",
            "AND batch.batch_status NOT IN ('warehouse_received', 'completed', 'cancelled')",
            "</if>",
            "<if test='(query.batchStatus == null or query.batchStatus == \"\") and query.statusScope == \"completed\"'>",
            "AND batch.batch_status IN ('warehouse_received', 'completed', 'cancelled')",
            "</if>",
            "<if test='query.skuKeyword != null and query.skuKeyword != \"\"'>",
            "AND EXISTS (",
            "SELECT 1 FROM in_transit_goods_line line",
            "WHERE line.owner_user_id = batch.owner_user_id AND line.batch_id = batch.id AND line.is_deleted = b'0'",
            "AND (line.sku LIKE CONCAT('%', #{query.skuKeyword}, '%')",
            "OR line.msku LIKE CONCAT('%', #{query.skuKeyword}, '%')",
            "OR line.psku LIKE CONCAT('%', #{query.skuKeyword}, '%'))",
            ")",
            "</if>",
            "<if test='query.etaFrom != null'> AND batch.eta_date &gt;= #{query.etaFrom}</if>",
            "<if test='query.etaTo != null'> AND batch.eta_date &lt;= #{query.etaTo}</if>",
            "ORDER BY FIELD(batch.batch_status, 'exception', 'in_transit', 'customs_clearance', 'delivering', 'shipped', 'pending_shipment', 'draft', 'warehouse_received', 'completed', 'cancelled'),",
            "batch.eta_date IS NULL ASC, batch.eta_date ASC, batch.gmt_updated DESC",
            "LIMIT #{query.limit}",
            "</script>"
    })
    List<BatchRow> listBatches(@Param("query") InTransitBatchQuery query);

    @Select({BATCH_SELECT, "WHERE batch.owner_user_id = #{ownerUserId} AND batch.id = #{batchId} AND batch.is_deleted = b'0' LIMIT 1"})
    BatchRow selectBatchById(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Select({
            BATCH_SELECT,
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "AND batch.batch_reference_no = #{batchReferenceNo}",
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
            "tracking_no, container_no, batch_reference_no, remark, missing_fields_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.standardForwarderId}, #{row.rawForwarderName}, #{row.normalizedRawForwarderName}, #{row.forwarderQualityStatus},",
            "#{row.transportMode}, #{row.batchStatus}, #{row.targetStoreCode}, #{row.targetSiteCode}, #{row.targetWarehouseName}, #{row.departureDate}, #{row.etaDate},",
            "#{row.trackingNo}, #{row.containerNo}, #{row.batchReferenceNo}, #{row.remark}, #{row.missingFieldsJson}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertBatch(@Param("row") BatchRow row);

    @Update({
            "UPDATE in_transit_batch",
            "SET standard_forwarder_id = #{row.standardForwarderId}, raw_forwarder_name = #{row.rawForwarderName},",
            "normalized_raw_forwarder_name = #{row.normalizedRawForwarderName}, forwarder_quality_status = #{row.forwarderQualityStatus},",
            "transport_mode = #{row.transportMode}, batch_status = #{row.batchStatus}, target_store_code = #{row.targetStoreCode},",
            "target_site_code = #{row.targetSiteCode}, target_warehouse_name = #{row.targetWarehouseName}, departure_date = #{row.departureDate},",
            "eta_date = #{row.etaDate}, tracking_no = #{row.trackingNo}, container_no = #{row.containerNo}, batch_reference_no = #{row.batchReferenceNo},",
            "remark = #{row.remark}, missing_fields_json = #{row.missingFieldsJson}, updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateBatch(@Param("row") BatchRow row);

    @Select({
            LINE_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "ORDER BY box_no IS NULL ASC, box_no ASC, id ASC"
    })
    List<LineRow> listLines(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Select({
            LINE_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND id = #{lineId} AND is_deleted = b'0' LIMIT 1"
    })
    LineRow selectLineById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("lineId") Long lineId
    );

    @Select({
            LINE_SELECT,
            "WHERE owner_user_id = #{ownerUserId}",
            "AND batch_id = #{batchId}",
            "AND box_no = #{boxNo}",
            "AND sku = #{sku}",
            "AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    LineRow selectLineByBoxNoAndSku(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNo") String boxNo,
            @Param("sku") String sku
    );

    @Select({
            PACKAGE_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND box_no = #{boxNo} AND is_deleted = b'0' LIMIT 1"
    })
    PackageRow selectPackageByBoxNo(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNo") String boxNo
    );

    @Insert({
            "INSERT INTO in_transit_package (",
            "id, owner_user_id, batch_id, box_no, tracking_no, remark, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.boxNo}, #{row.trackingNo}, #{row.remark},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertPackage(@Param("row") PackageRow row);

    @Insert({
            "INSERT INTO in_transit_goods_line (",
            "id, owner_user_id, batch_id, package_id, box_no, sku, msku, psku, product_name, store_code, site_code,",
            "shipped_quantity, received_quantity, remaining_quantity, carton_count, units_per_carton,",
            "carton_weight_kg, carton_volume_cbm, remark, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.packageId}, #{row.boxNo}, #{row.sku}, #{row.msku}, #{row.psku}, #{row.productName},",
            "#{row.storeCode}, #{row.siteCode}, #{row.shippedQuantity}, #{row.receivedQuantity}, #{row.remainingQuantity},",
            "#{row.cartonCount}, #{row.unitsPerCarton}, #{row.cartonWeightKg}, #{row.cartonVolumeCbm}, #{row.remark},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertLine(@Param("row") LineRow row);

    @Update({
            "UPDATE in_transit_goods_line",
            "SET package_id = #{row.packageId}, box_no = #{row.boxNo}, sku = #{row.sku}, msku = #{row.msku}, psku = #{row.psku}, product_name = #{row.productName},",
            "store_code = #{row.storeCode}, site_code = #{row.siteCode}, shipped_quantity = #{row.shippedQuantity},",
            "received_quantity = #{row.receivedQuantity}, remaining_quantity = #{row.remainingQuantity},",
            "carton_count = #{row.cartonCount}, units_per_carton = #{row.unitsPerCarton},",
            "carton_weight_kg = #{row.cartonWeightKg}, carton_volume_cbm = #{row.cartonVolumeCbm},",
            "remark = #{row.remark}, updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND batch_id = #{row.batchId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateLine(@Param("row") LineRow row);

    @Update({
            "UPDATE in_transit_goods_line",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND id = #{lineId} AND is_deleted = b'0'"
    })
    int deleteLine(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("lineId") Long lineId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT",
            "CASE WHEN COUNT(*) = 0 THEN NULL ELSE COUNT(DISTINCT NULLIF(TRIM(sku), '')) END AS sku_count,",
            "SUM(shipped_quantity) AS shipped_quantity_total,",
            "SUM(received_quantity) AS received_quantity_total,",
            "SUM(remaining_quantity) AS remaining_quantity_total,",
            "SUM(carton_count) AS carton_count_total,",
            "SUM(CASE WHEN carton_weight_kg IS NULL THEN NULL ELSE carton_count * carton_weight_kg END) AS total_weight_kg,",
            "SUM(CASE WHEN carton_volume_cbm IS NULL THEN NULL ELSE carton_count * carton_volume_cbm END) AS total_volume_cbm",
            "FROM in_transit_goods_line",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'"
    })
    BatchAggregateRow aggregateLines(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Update({
            "UPDATE in_transit_batch",
            "SET sku_count = #{aggregate.skuCount}, shipped_quantity_total = #{aggregate.shippedQuantityTotal},",
            "received_quantity_total = #{aggregate.receivedQuantityTotal}, remaining_quantity_total = #{aggregate.remainingQuantityTotal},",
            "carton_count_total = #{aggregate.cartonCountTotal}, total_weight_kg = #{aggregate.totalWeightKg},",
            "total_volume_cbm = #{aggregate.totalVolumeCbm}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{batchId} AND is_deleted = b'0'"
    })
    int refreshBatchAggregate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("aggregate") BatchAggregateRow aggregate
    );

    @Select({
            NODE_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "ORDER BY node_happened_at DESC, id DESC"
    })
    List<NodeRow> listNodes(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Select({
            NODE_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND id = #{nodeId} AND is_deleted = b'0' LIMIT 1"
    })
    NodeRow selectNodeById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("nodeId") Long nodeId
    );

    @Insert({
            "INSERT INTO in_transit_logistics_node (",
            "id, owner_user_id, batch_id, node_status, node_happened_at, description, operator_name,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.nodeStatus}, #{row.nodeHappenedAt},",
            "#{row.description}, #{row.operatorName}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertNode(@Param("row") NodeRow row);

    @Select({
            "SELECT node_status AS latest_node_status, node_happened_at AS latest_node_happened_at, description AS latest_node_description",
            "FROM in_transit_logistics_node",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "ORDER BY node_happened_at DESC, id DESC",
            "LIMIT 1"
    })
    BatchLatestNodeRow selectLatestNode(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Update({
            "UPDATE in_transit_batch",
            "SET latest_node_status = #{latest.latestNodeStatus},",
            "latest_node_happened_at = #{latest.latestNodeHappenedAt},",
            "latest_node_description = #{latest.latestNodeDescription},",
            "batch_status = #{latest.derivedBatchStatus},",
            "gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{batchId} AND is_deleted = b'0'"
    })
    int refreshBatchLatestNode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("latest") BatchLatestNodeRow latest
    );

    @Insert({
            "INSERT INTO in_transit_import_batch (",
            "id, owner_user_id, file_name, source_type, status, total_row_count, valid_row_count, error_count, warning_count,",
            "summary_json, raw_preview_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.fileName}, #{row.sourceType}, #{row.status}, #{row.totalRowCount},",
            "#{row.validRowCount}, #{row.errorCount}, #{row.warningCount}, #{row.summaryJson}, #{row.rawPreviewJson},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertImportBatch(@Param("row") ImportBatchRow row);

    @Select({
            "SELECT id, owner_user_id, file_name, source_type, status, total_row_count, valid_row_count, error_count, warning_count,",
            "summary_json, raw_preview_json, created_by, updated_by",
            "FROM in_transit_import_batch",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{importBatchId} AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ImportBatchRow selectImportBatchById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("importBatchId") Long importBatchId
    );

    @Update({
            "UPDATE in_transit_import_batch",
            "SET status = 'imported', summary_json = #{summaryJson}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{importBatchId} AND is_deleted = b'0'"
    })
    int markImportBatchImported(
            @Param("ownerUserId") Long ownerUserId,
            @Param("importBatchId") Long importBatchId,
            @Param("operatorUserId") Long operatorUserId,
            @Param("summaryJson") String summaryJson
    );

    @Insert({
            "INSERT INTO in_transit_operation_audit (",
            "id, owner_user_id, operator_user_id, operation_type, target_type, target_id, batch_id,",
            "store_code, site_code, summary, detail_json, created_by, gmt_create",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.operatorUserId}, #{row.operationType}, #{row.targetType}, #{row.targetId}, #{row.batchId},",
            "#{row.storeCode}, #{row.siteCode}, #{row.summary}, #{row.detailJson}, #{row.createdBy}, NOW())"
    })
    int insertOperationAudit(@Param("row") OperationAuditRow row);
}
