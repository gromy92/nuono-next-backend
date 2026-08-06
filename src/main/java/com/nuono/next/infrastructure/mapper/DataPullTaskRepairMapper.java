package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.persistence.DataPullTaskRepairCommand;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Single-row CAS reserved for preserving-checkpoint FAILED repair. */
public interface DataPullTaskRepairMapper {

    @Update({
            "UPDATE dp_pull_task target",
            "LEFT JOIN dp_pull_task successor",
            "  ON successor.operation_code = target.operation_code",
            " AND BINARY successor.scope_key = BINARY target.scope_key",
            " AND (successor.schedule_slot > target.schedule_slot",
            "      OR (successor.schedule_slot = target.schedule_slot AND successor.id > target.id))",
            " AND successor.state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED')",
            "SET target.state = 'QUEUED',",
            "    target.retry_not_before = NULL,",
            "    target.attempt = 0,",
            "    target.lease_owner = NULL,",
            "    target.lease_until = NULL,",
            "    target.sanitized_failure_code = NULL,",
            "    target.finished_at = NULL,",
            "    target.version_no = target.version_no + 1,",
            "    target.gmt_updated = #{now}",
            "WHERE target.id = #{command.taskId}",
            "  AND target.state = 'FAILED'",
            "  AND target.version_no = #{command.expectedVersion}",
            "  AND target.fence_epoch = #{command.expectedFenceEpoch}",
            "  AND BINARY target.sanitized_failure_code = BINARY #{command.expectedFailureCode}",
            "  AND target.lease_owner IS NULL",
            "  AND target.lease_until IS NULL",
            "  AND successor.id IS NULL"
    })
    int requeueFailed(
            @Param("command") DataPullTaskRepairCommand command,
            @Param("now") LocalDateTime now
    );
}
