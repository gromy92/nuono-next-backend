package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.checkpoint.DataPullScopeProgress;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressCommit;
import com.nuono.next.datapull.runtime.OperationCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** SQL seam for monotonic DP scope progress. */
public interface DataPullScopeProgressMapper {

    @Insert({
            "INSERT INTO dp_pull_scope_progress (",
            "  operation_code, scope_key, initial_full_completed,",
            "  official_modified_high_water_utc, last_applied_business_window_key,",
            "  version_no, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{operationCode}, #{scopeKey}, b'0', NULL, NULL, 0, #{createdAt}, #{updatedAt}",
            ") ON DUPLICATE KEY UPDATE operation_code = operation_code"
    })
    int insertIfAbsent(DataPullScopeProgress progress);

    @Select({
            "SELECT operation_code AS operationCode, scope_key AS scopeKey,",
            "  initial_full_completed AS initialFullCompleted,",
            "  official_modified_high_water_utc AS officialModifiedHighWaterUtc,",
            "  last_applied_business_window_key AS lastAppliedBusinessWindowKey,",
            "  version_no AS version, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM dp_pull_scope_progress",
            "WHERE operation_code = #{operationCode}",
            "  AND BINARY scope_key = BINARY #{scopeKey}",
            "LIMIT 1"
    })
    DataPullScopeProgress select(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey
    );

    @Update({
            "UPDATE dp_pull_scope_progress progress",
            "JOIN dp_pull_task task ON task.id = #{taskId}",
            "SET progress.initial_full_completed = CASE",
            "      WHEN progress.initial_full_completed = b'1' THEN b'1'",
            "      ELSE #{initialFullCompleted}",
            "    END,",
            "    progress.official_modified_high_water_utc = CASE",
            "      WHEN #{officialModifiedHighWaterUtc} IS NULL",
            "        THEN progress.official_modified_high_water_utc",
            "      WHEN progress.official_modified_high_water_utc IS NULL",
            "        THEN #{officialModifiedHighWaterUtc}",
            "      WHEN #{officialModifiedHighWaterUtc} > progress.official_modified_high_water_utc",
            "        THEN #{officialModifiedHighWaterUtc}",
            "      ELSE progress.official_modified_high_water_utc",
            "    END,",
            "    progress.last_applied_business_window_key = #{businessWindowKey},",
            "    progress.version_no = progress.version_no + 1,",
            "    progress.gmt_updated = #{nowUtc}",
            "WHERE progress.operation_code = #{operationCode}",
            "  AND BINARY progress.scope_key = BINARY #{scopeKey}",
            "  AND progress.version_no = #{expectedProgressVersion}",
            "  AND task.operation_code = progress.operation_code",
            "  AND BINARY task.scope_key = BINARY progress.scope_key",
            "  AND BINARY task.business_window_key = BINARY #{businessWindowKey}",
            "  AND task.state = 'RUNNING'",
            "  AND task.fence_epoch = #{taskFenceEpoch}",
            "  AND task.version_no = #{taskVersion}",
            "  AND BINARY task.lease_owner = BINARY #{leaseOwner}",
            "  AND task.lease_until > #{nowUtc}",
            "  AND (",
            "    #{officialModifiedHighWaterUtc} IS NULL",
            "    OR progress.official_modified_high_water_utc IS NULL",
            "    OR #{officialModifiedHighWaterUtc} >= progress.official_modified_high_water_utc",
            "  )"
    })
    int commitCompletedWindow(DataPullScopeProgressCommit commit);
}
