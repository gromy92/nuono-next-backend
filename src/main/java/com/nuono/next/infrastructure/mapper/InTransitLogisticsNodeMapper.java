package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitBatchRecords.BatchLatestNodeRow;
import com.nuono.next.intransit.InTransitBatchRecords.NodeRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InTransitLogisticsNodeMapper extends InTransitGoodsSequenceMapper {

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

    @Select({
            NODE_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId}",
            "AND node_status = #{nodeStatus} AND node_happened_at = #{nodeHappenedAt}",
            "AND (description = #{description} OR (description IS NULL AND #{description} IS NULL)) AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    NodeRow selectNodeByStatusDescriptionAndHappenedAt(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("nodeStatus") String nodeStatus,
            @Param("nodeHappenedAt") java.time.LocalDateTime nodeHappenedAt,
            @Param("description") String description
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

    @Update({
            "UPDATE in_transit_logistics_node",
            "SET node_status = #{row.nodeStatus},",
            "node_happened_at = #{row.nodeHappenedAt},",
            "description = #{row.description},",
            "operator_name = #{row.operatorName},",
            "updated_by = #{row.updatedBy},",
            "gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND batch_id = #{row.batchId}",
            "AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateNode(@Param("row") NodeRow row);

    @Update({
            "UPDATE in_transit_logistics_node",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId}",
            "AND id = #{nodeId} AND is_deleted = b'0'"
    })
    int deleteNode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("nodeId") Long nodeId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT node_status AS latest_node_status, node_happened_at AS latest_node_happened_at, description AS latest_node_description",
            "FROM in_transit_logistics_node",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "ORDER BY CASE WHEN node_status IN ('warehouse_received', 'cancelled') THEN 0 ELSE 1 END,",
            "node_happened_at DESC, id DESC",
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
}
