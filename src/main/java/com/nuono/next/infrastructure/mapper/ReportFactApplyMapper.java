package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.report.ExportReportIntent;
import com.nuono.next.datapull.report.ReportApplyMarkerRow;
import com.nuono.next.datapull.report.ReportApplyTaskRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Fenced idempotency SQL for complete report fact imports. */
public interface ReportFactApplyMapper {
    @Select({
            "SELECT id AS taskId, operation_code AS operationCode, scope_key AS scopeKey,",
            "  business_window_key AS businessWindowKey, fence_epoch AS fenceEpoch,",
            "  state, lease_owner AS leaseOwner, lease_until AS leaseUntil",
            "FROM dp_pull_task",
            "WHERE id = #{taskId}",
            "FOR UPDATE"
    })
    ReportApplyTaskRow selectTaskForUpdate(@Param("taskId") long taskId);

    @Select({
            "SELECT task_id AS taskId, operation_code AS operationCode, scope_key AS scopeKey,",
            "  business_window_key AS businessWindowKey, applied_fence_epoch AS appliedFenceEpoch",
            "FROM dp_pull_report_apply",
            "WHERE task_id = #{taskId}",
            "LIMIT 1"
    })
    ReportApplyMarkerRow selectMarker(@Param("taskId") long taskId);

    @Insert({
            "INSERT INTO dp_pull_report_apply (",
            "  task_id, operation_code, scope_key, business_window_key, applied_fence_epoch,",
            "  applied_at, gmt_create",
            ")",
            "SELECT task.id, task.operation_code, task.scope_key, task.business_window_key,",
            "  task.fence_epoch, #{nowUtc}, #{nowUtc}",
            "FROM dp_pull_task task",
            "WHERE task.id = #{intent.taskId}",
            "  AND task.operation_code = #{intent.operationCode}",
            "  AND BINARY task.scope_key = BINARY #{intent.scopeKey}",
            "  AND BINARY task.business_window_key = BINARY #{intent.businessWindowKey}",
            "  AND task.state = 'RUNNING'",
            "  AND task.fence_epoch = #{intent.fenceEpoch}",
            "  AND BINARY task.lease_owner = BINARY #{intent.leaseOwner}",
            "  AND task.lease_until > #{nowUtc}"
    })
    int insertMarkerIfLive(
            @Param("intent") ExportReportIntent intent,
            @Param("nowUtc") LocalDateTime nowUtc
    );
}
