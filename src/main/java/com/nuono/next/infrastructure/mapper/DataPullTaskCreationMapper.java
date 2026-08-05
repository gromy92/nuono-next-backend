package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

/** Task-id allocation and immutable task creation SQL, isolated from worker mutation SQL. */
public interface DataPullTaskCreationMapper {
    String COLUMNS = ""
            + "id, operation_code AS operationCode, provider_channel AS providerChannel, "
            + "owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, "
            + "account_key AS accountKey, egress_key AS egressKey, "
            + "project_code AS projectCode, store_code AS storeCode, site_code AS siteCode, "
            + "scope_key AS scopeKey, scope_binding_id AS scopeBindingId, "
            + "scope_payload_type AS scopePayloadType, scope_payload_sha256 AS scopePayloadSha256, "
            + "scope_payload AS scopePayload, "
            + "scope_binding_effective_from_utc AS scopeBindingEffectiveFromUtc, "
            + "schedule_slot AS scheduleSlot, business_window_key AS businessWindowKey, "
            + "state, step_code AS stepCode, remote_handle AS remoteHandle, checkpoint, "
            + "retry_not_before AS retryNotBefore, attempt, lease_owner AS leaseOwner, "
            + "lease_until AS leaseUntil, fence_epoch AS fenceEpoch, version_no AS version, "
            + "sanitized_failure_code AS sanitizedFailureCode, "
            + "gmt_create AS createdAt, gmt_updated AS updatedAt, finished_at AS finishedAt";

    @Update({
            "UPDATE noon_pull_id_sequence",
            "SET next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()",
            "WHERE sequence_name = 'dp_pull_task'"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId",
            before = false, resultType = Long.class
    )
    int allocateTaskId(IdSequenceCommand command);

    @Insert({
            "INSERT INTO dp_pull_task (",
            "  id, operation_code, provider_channel, owner_user_id, logical_store_id,",
            "  account_key, egress_key, project_code, store_code, site_code, scope_key, schedule_slot,",
            "  scope_binding_id, scope_payload_type, scope_payload_sha256, scope_payload,",
            "  scope_binding_effective_from_utc, business_window_key, state, step_code,",
            "  remote_handle, checkpoint, retry_not_before, attempt, lease_owner, lease_until,",
            "  fence_epoch, version_no, sanitized_failure_code, gmt_create, gmt_updated, finished_at",
            ") SELECT",
            "  #{id}, #{operationCode}, #{providerChannel}, #{ownerUserId}, #{logicalStoreId},",
            "  #{accountKey}, #{egressKey}, #{projectCode}, #{storeCode}, #{siteCode},",
            "  #{scopeKey}, #{scheduleSlot}, binding.binding_id, binding.payload_type,",
            "  binding.payload_sha256, binding.payload, binding.effective_from_utc,",
            "  #{businessWindowKey}, #{state}, #{stepCode}, #{remoteHandle}, #{checkpoint},",
            "  #{retryNotBefore}, #{attempt}, #{leaseOwner}, #{leaseUntil}, #{fenceEpoch},",
            "  #{version}, #{sanitizedFailureCode}, #{createdAt}, #{updatedAt}, #{finishedAt}",
            "FROM (SELECT 1 AS singleton) seed",
            "LEFT JOIN dp_pull_scope_binding_epoch binding",
            "  ON #{operationCode} IN ('DP08A', 'DP08B')",
            " AND binding.operation_code = #{operationCode}",
            " AND BINARY binding.scope_key = BINARY #{scopeKey}",
            " AND binding.effective_from_utc <= #{scheduleSlot}",
            " AND (binding.effective_until_utc IS NULL OR #{scheduleSlot} < binding.effective_until_utc)",
            "WHERE #{operationCode} NOT IN ('DP08A', 'DP08B')",
            "   OR (binding.binding_id IS NOT NULL AND NOT EXISTS (",
            "     SELECT 1 FROM dp_pull_scope_binding_epoch duplicate",
            "     WHERE duplicate.operation_code = #{operationCode}",
            "       AND BINARY duplicate.scope_key = BINARY #{scopeKey}",
            "       AND duplicate.effective_from_utc <= #{scheduleSlot}",
            "       AND (duplicate.effective_until_utc IS NULL",
            "            OR #{scheduleSlot} < duplicate.effective_until_utc)",
            "       AND BINARY duplicate.binding_id <> BINARY binding.binding_id",
            "   )) ON DUPLICATE KEY UPDATE id = id"
    })
    int insertTaskIfAbsent(DataPullTask task);

    @Select({
            "SELECT", COLUMNS, "FROM dp_pull_task",
            "WHERE operation_code = #{operationCode}",
            "  AND BINARY scope_key = BINARY #{scopeKey}",
            "  AND BINARY business_window_key = BINARY #{businessWindowKey}",
            "LIMIT 1"
    })
    DataPullTask selectByStableKey(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey,
            @Param("businessWindowKey") String businessWindowKey
    );

    @Select({
            "SELECT MAX(schedule_slot) FROM dp_pull_task",
            "WHERE operation_code = #{operationCode}",
            "  AND BINARY scope_key = BINARY #{scopeKey}"
    })
    LocalDateTime selectLatestScheduleSlot(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey
    );
}
