package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskIdBlock;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

/** Fixed-call task-id, immutable insert and stable-key readback SQL. */
public interface DataPullScheduleTaskBatchMapper {
    @Update({
            "UPDATE noon_pull_id_sequence",
            "SET next_id = LAST_INSERT_ID(next_id + #{size}), gmt_updated = NOW()",
            "WHERE sequence_name = 'dp_pull_task'"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()", keyProperty = "lastAllocatedId",
            before = false, resultType = Long.class
    )
    int allocateTaskIdBlock(DataPullTaskIdBlock block);

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_task (",
            " id, operation_code, provider_channel, owner_user_id, logical_store_id,",
            " account_key, egress_key, project_code, store_code, site_code, scope_key,",
            " schedule_slot, scope_binding_id, scope_payload_type, scope_payload_sha256,",
            " scope_payload, scope_binding_effective_from_utc, business_window_key,",
            " state, step_code, remote_handle, checkpoint, retry_not_before, attempt,",
            " lease_owner, lease_until, fence_epoch, version_no, sanitized_failure_code,",
            " gmt_create, gmt_updated, finished_at)",
            "<foreach collection='tasks' item='task' separator=' UNION ALL '>",
            " SELECT #{task.id},#{task.operationCode},#{task.providerChannel},",
            " #{task.ownerUserId},#{task.logicalStoreId},#{task.accountKey},#{task.egressKey},",
            " #{task.projectCode},#{task.storeCode},#{task.siteCode},#{task.scopeKey},",
            " #{task.scheduleSlot},binding.binding_id,",
            " CASE WHEN #{task.operationCode} = 'DP08B' THEN #{task.scopePayloadType}",
            "      ELSE binding.payload_type END,",
            " CASE WHEN #{task.operationCode} = 'DP08B' THEN #{task.scopePayloadSha256}",
            "      ELSE binding.payload_sha256 END,",
            " CASE WHEN #{task.operationCode} = 'DP08B' THEN #{task.scopePayload}",
            "      ELSE binding.payload END,",
            " binding.effective_from_utc,#{task.businessWindowKey},#{task.state},",
            " #{task.stepCode},#{task.remoteHandle},#{task.checkpoint},#{task.retryNotBefore},",
            " #{task.attempt},#{task.leaseOwner},#{task.leaseUntil},#{task.fenceEpoch},",
            " #{task.version},#{task.sanitizedFailureCode},#{task.createdAt},",
            " #{task.updatedAt},#{task.finishedAt}",
            " FROM (SELECT 1 singleton) seed",
            " LEFT JOIN dp_pull_scope_binding_epoch binding",
            "  ON #{task.operationCode} IN ('DP08A','DP08B')",
            " AND binding.operation_code = #{task.operationCode}",
            " AND BINARY binding.scope_key = BINARY #{task.scopeKey}",
            " AND binding.effective_from_utc &lt;= #{task.scheduleSlot}",
            " AND (binding.effective_until_utc IS NULL",
            "      OR #{task.scheduleSlot} &lt; binding.effective_until_utc)",
            " WHERE #{task.operationCode} NOT IN ('DP08A','DP08B')",
            " OR (binding.binding_id IS NOT NULL AND NOT EXISTS (",
            "  SELECT 1 FROM dp_pull_scope_binding_epoch duplicate",
            "  WHERE duplicate.operation_code = #{task.operationCode}",
            "   AND BINARY duplicate.scope_key = BINARY #{task.scopeKey}",
            "   AND duplicate.effective_from_utc &lt;= #{task.scheduleSlot}",
            "   AND (duplicate.effective_until_utc IS NULL",
            "        OR #{task.scheduleSlot} &lt; duplicate.effective_until_utc)",
            "   AND BINARY duplicate.binding_id &lt;&gt; BINARY binding.binding_id))",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE id = id",
            "</script>"
    })
    int insertTasks(@Param("tasks") List<DataPullTask> tasks);

    @Select({
            "<script>",
            "SELECT", DataPullRuntimeMapper.COLUMNS, "FROM dp_pull_task",
            "WHERE",
            "<foreach collection='tasks' item='task' open='(' separator=' OR ' close=')'>",
            " (operation_code = #{task.operationCode}",
            "  AND BINARY scope_key = BINARY #{task.scopeKey}",
            "  AND BINARY business_window_key = BINARY #{task.businessWindowKey})",
            "</foreach>",
            "ORDER BY operation_code, BINARY scope_key, schedule_slot, id",
            "</script>"
    })
    List<DataPullTask> listByStableKeys(@Param("tasks") List<DataPullTask> tasks);
}
