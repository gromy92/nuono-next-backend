package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskTransition;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis statements for the fenced DP runtime task ledger. */
public interface DataPullRuntimeMapper extends DataPullTaskCreationMapper {

    @Select({
            "<script>",
            "SELECT", COLUMNS,
            "FROM dp_pull_task candidate",
            "WHERE candidate.schedule_slot &lt;= #{now}",
            "  <if test='afterScheduleSlot != null'>",
            "    AND (",
            "      candidate.schedule_slot &gt; #{afterScheduleSlot}",
            "      OR (",
            "        candidate.schedule_slot = #{afterScheduleSlot}",
            "        AND candidate.id &gt; #{afterTaskId}",
            "      )",
            "    )",
            "  </if>",
            "  AND (candidate.lease_until IS NULL OR candidate.lease_until &lt;= #{now})",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM dp_pull_backoff_hold hold",
            "    WHERE hold.blocked_until &gt; #{now}",
            "      AND BINARY hold.provider_channel = BINARY candidate.provider_channel",
            "      AND (",
            "        (",
            "          hold.share_level = 'EXACT'",
            "          AND BINARY hold.account_key = BINARY candidate.account_key",
            "          AND hold.operation_code = candidate.operation_code",
            "          AND BINARY hold.scope_key = BINARY candidate.scope_key",
            "        )",
            "        OR (",
            "          hold.share_level = 'ACCOUNT'",
            "          AND BINARY hold.account_key = BINARY candidate.account_key",
            "        )",
            "        OR (",
            "          hold.share_level = 'EXIT'",
            "          AND candidate.egress_key IS NOT NULL",
            "          AND BINARY hold.egress_key = BINARY candidate.egress_key",
            "        )",
            "      )",
            "  )",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM dp_pull_task predecessor",
            "    WHERE predecessor.operation_code = candidate.operation_code",
            "      AND BINARY predecessor.scope_key = BINARY candidate.scope_key",
            "      AND (",
            "        predecessor.schedule_slot &lt; candidate.schedule_slot",
            "        OR (",
            "          predecessor.schedule_slot = candidate.schedule_slot",
            "          AND predecessor.id &lt; candidate.id",
            "        )",
            "      )",
            "      AND predecessor.state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED')",
            "  )",
            "  AND (candidate.state &lt;&gt; 'WAITING_AUTH' OR NOT EXISTS (",
            "    SELECT 1",
            "    FROM noon_auth_identity_recovery_item auth_item",
            "    WHERE auth_item.source_task_id = candidate.id",
            "      AND BINARY auth_item.source_domain = BINARY 'DP_RUNTIME'",
            "      AND auth_item.status = 'PENDING'",
            "  ))",
            "  AND (",
            "    candidate.state = 'QUEUED'",
            "    OR (",
            "      candidate.state = 'RUNNING'",
            "      AND candidate.lease_until IS NOT NULL",
            "      AND candidate.lease_until &lt;= #{now}",
            "    )",
            "    OR (",
            "      candidate.state IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH')",
            "      AND candidate.retry_not_before IS NOT NULL",
            "      AND candidate.retry_not_before &lt;= #{now}",
            "    )",
            "  )",
            "ORDER BY candidate.schedule_slot ASC, candidate.id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<DataPullTask> selectDueCandidatesAfter(
            @Param("now") LocalDateTime now,
            @Param("afterScheduleSlot") LocalDateTime afterScheduleSlot,
            @Param("afterTaskId") Long afterTaskId,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE dp_pull_task candidate",
            "INNER JOIN dp_pull_runtime_leader runtime_leader",
            "  ON runtime_leader.runtime_name = 'daily_pull'",
            " AND BINARY runtime_leader.leader_owner = BINARY #{leaderLease.owner}",
            " AND runtime_leader.leader_epoch = #{leaderLease.epoch}",
            " AND runtime_leader.lease_until > NOW(3)",
            "LEFT JOIN dp_pull_task predecessor",
            "  ON predecessor.operation_code = candidate.operation_code",
            " AND BINARY predecessor.scope_key = BINARY candidate.scope_key",
            " AND (",
            "      predecessor.schedule_slot < candidate.schedule_slot",
            "      OR (predecessor.schedule_slot = candidate.schedule_slot",
            "          AND predecessor.id < candidate.id)",
            " )",
            " AND predecessor.state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED')",
            "LEFT JOIN dp_pull_backoff_hold hold",
            "  ON hold.blocked_until > #{now}",
            " AND BINARY hold.provider_channel = BINARY candidate.provider_channel",
            " AND (",
            "      (hold.share_level = 'EXACT'",
            "       AND BINARY hold.account_key = BINARY candidate.account_key",
            "       AND hold.operation_code = candidate.operation_code",
            "       AND BINARY hold.scope_key = BINARY candidate.scope_key)",
            "      OR (hold.share_level = 'ACCOUNT'",
            "          AND BINARY hold.account_key = BINARY candidate.account_key)",
            "      OR (hold.share_level = 'EXIT'",
            "          AND candidate.egress_key IS NOT NULL",
            "          AND BINARY hold.egress_key = BINARY candidate.egress_key)",
            " )",
            "LEFT JOIN dp_pull_emergency_claim_hold emergency_hold",
            "  ON emergency_hold.blocked_until > #{now}",
            " AND (emergency_hold.hold_scope = 'GLOBAL'",
            "      OR (emergency_hold.hold_scope = 'OPERATION' AND emergency_hold.operation_code = candidate.operation_code)",
            "      OR (emergency_hold.hold_scope = 'SCOPE' AND emergency_hold.operation_code = candidate.operation_code",
            "          AND BINARY emergency_hold.scope_key = BINARY candidate.scope_key))",
            "SET candidate.state = 'RUNNING',",
            "    candidate.lease_owner = #{leaseOwner},",
            "    candidate.lease_until = #{leaseUntil},",
            "    candidate.retry_not_before = NULL,",
            "    candidate.fence_epoch = candidate.fence_epoch + 1,",
            "    candidate.version_no = candidate.version_no + 1,",
            "    candidate.finished_at = NULL,",
            "    candidate.gmt_updated = #{now}",
            "WHERE candidate.id = #{taskId}",
            "  AND candidate.version_no = #{expectedVersion}",
            "  AND candidate.schedule_slot <= #{now}",
            "  AND #{leaseUntil} > #{now}",
            "  AND (candidate.lease_until IS NULL OR candidate.lease_until <= #{now})",
            "  AND predecessor.id IS NULL",
            "  AND hold.hold_key IS NULL",
            "  AND emergency_hold.hold_key IS NULL",
            "  AND (candidate.state <> 'WAITING_AUTH' OR NOT EXISTS (",
            "    SELECT 1",
            "    FROM noon_auth_identity_recovery_item auth_item",
            "    WHERE auth_item.source_task_id = candidate.id",
            "      AND BINARY auth_item.source_domain = BINARY 'DP_RUNTIME'",
            "      AND auth_item.status = 'PENDING'",
            "  ))",
            "  AND (",
            "    candidate.state = 'QUEUED'",
            "    OR (candidate.state = 'RUNNING'",
            "        AND candidate.lease_until IS NOT NULL",
            "        AND candidate.lease_until <= #{now})",
            "    OR (",
            "      candidate.state IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH')",
            "      AND candidate.retry_not_before IS NOT NULL",
            "      AND candidate.retry_not_before <= #{now}",
            "    )",
            "  )"
    })
    int tryClaim(
            @Param("taskId") long taskId,
            @Param("expectedVersion") long expectedVersion,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now,
            @Param("leaderLease") DataPullRuntimeLeaderLease leaderLease
    );

    @Options(timeout = com.nuono.next.datapull.orchestration.DataPullRuntimeProperties
            .DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    @Update({
            "UPDATE dp_pull_task",
            "SET state = 'QUEUED',",
            "    lease_owner = NULL,",
            "    lease_until = NULL,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{taskId}",
            "  AND state = 'RUNNING'",
            "  AND fence_epoch = #{expectedFenceEpoch}",
            "  AND version_no = #{expectedVersion}",
            "  AND BINARY lease_owner = BINARY #{leaseOwner}",
            "  AND lease_until > #{now}"
    })
    int releaseUnstartedClaim(
            @Param("taskId") long taskId,
            @Param("expectedFenceEpoch") long expectedFenceEpoch,
            @Param("expectedVersion") long expectedVersion,
            @Param("leaseOwner") String leaseOwner,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE dp_pull_task",
            "SET state = #{transition.nextState},",
            "    step_code = #{transition.stepCode},",
            "    remote_handle = #{transition.remoteHandle},",
            "    checkpoint = #{transition.checkpoint},",
            "    retry_not_before = #{transition.retryNotBefore},",
            "    sanitized_failure_code = #{transition.sanitizedFailureCode},",
            "    finished_at = #{transition.finishedAt},",
            "    attempt = CASE",
            "      WHEN #{transition.nextState} IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH')",
            "        THEN CASE WHEN attempt < 2147483647 THEN attempt + 1 ELSE attempt END",
            "      WHEN #{transition.nextState} IN ('QUEUED', 'SUCCEEDED') THEN 0",
            "      ELSE attempt",
            "    END,",
            "    lease_owner = NULL,",
            "    lease_until = NULL,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{transition.now}",
            "WHERE id = #{transition.taskId}",
            "  AND state = 'RUNNING'",
            "  AND fence_epoch = #{transition.expectedFenceEpoch}",
            "  AND version_no = #{transition.expectedVersion}",
            "  AND BINARY lease_owner = BINARY #{transition.leaseOwner}",
            "  AND lease_until > #{transition.now}",
            "  AND #{transition.nextState} IN (",
            "    'QUEUED', 'WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH', 'SUCCEEDED', 'FAILED'",
            "  )"
    })
    int transitionTask(@Param("transition") DataPullTaskTransition transition);

    @Update({
            "UPDATE dp_pull_task",
            "SET lease_until = #{leaseUntil},",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{taskId}",
            "  AND state = 'RUNNING'",
            "  AND fence_epoch = #{expectedFenceEpoch}",
            "  AND version_no = #{expectedVersion}",
            "  AND BINARY lease_owner = BINARY #{leaseOwner}",
            "  AND lease_until > #{now}",
            "  AND #{leaseUntil} > lease_until"
    })
    int heartbeat(
            @Param("taskId") long taskId,
            @Param("expectedFenceEpoch") long expectedFenceEpoch,
            @Param("expectedVersion") long expectedVersion,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT", COLUMNS,
            "FROM dp_pull_task",
            "WHERE id = #{taskId}",
            "LIMIT 1"
    })
    DataPullTask selectById(@Param("taskId") long taskId);
}
