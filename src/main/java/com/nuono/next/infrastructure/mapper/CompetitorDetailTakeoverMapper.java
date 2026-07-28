package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorDetailTakeoverCandidateRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CompetitorDetailTakeoverMapper {
    @Select({
            "SELECT task.id AS taskId, run.id AS runId,",
            "       task.status AS taskStatus, run.status AS runStatus,",
            "       task.payload_json AS payloadJson",
            "FROM operational_task task",
            "JOIN operations_competitor_search_run run",
            "  ON run.task_id = task.id",
            " AND run.is_deleted = b'0'",
            "WHERE run.watch_product_id = #{watchProductId}",
            "  AND run.trigger_mode = 'SCHEDULED_DETAIL_MONITOR'",
            "  AND task.task_type = 'OPERATIONS_COMPETITOR_REFRESH'",
            "  AND (",
            "       (task.status IN ('QUEUED', 'RUNNING')",
            "        AND run.status IN ('QUEUED', 'RUNNING'))",
            "    OR (task.status = 'SUCCEEDED'",
            "        AND run.status IN ('SUCCEEDED', 'PARTIAL_FAILED'))",
            "    OR (task.status = 'FAILED'",
            "        AND run.status IN ('FAILED', 'PARTIAL_FAILED'))",
            "  )",
            "  AND task.id <> #{excludedTaskId}",
            "  AND run.id <> #{excludedRunId}",
            "  AND task.is_deleted = b'0'",
            "ORDER BY run.id ASC"
    })
    List<CompetitorDetailTakeoverCandidateRow> listScheduledDetailOwnershipCandidates(
            @Param("watchProductId") Long watchProductId,
            @Param("excludedTaskId") Long excludedTaskId,
            @Param("excludedRunId") Long excludedRunId
    );

    @Select({
            "SELECT status",
            "FROM operational_task",
            "WHERE id = #{taskId}",
            "  AND task_type = 'OPERATIONS_COMPETITOR_REFRESH'",
            "  AND status IN ('QUEUED', 'RUNNING')",
            "  AND is_deleted = b'0'",
            "FOR UPDATE"
    })
    String lockActiveScheduledDetailTask(@Param("taskId") Long taskId);

    @Select({
            "SELECT status",
            "FROM operations_competitor_search_run",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND trigger_mode = 'SCHEDULED_DETAIL_MONITOR'",
            "  AND status IN ('QUEUED', 'RUNNING')",
            "  AND is_deleted = b'0'",
            "FOR UPDATE"
    })
    String lockActiveScheduledDetailRun(
            @Param("taskId") Long taskId,
            @Param("runId") Long runId,
            @Param("watchProductId") Long watchProductId
    );

    @Update({
            "UPDATE operational_task",
            "SET status = 'SUCCEEDED',",
            "    progress_percent = 100,",
            "    result_json = #{resultJson},",
            "    message = #{message},",
            "    error_code = NULL,",
            "    finished_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND task_type = 'OPERATIONS_COMPETITOR_REFRESH'",
            "  AND status = #{expectedStatus}",
            "  AND is_deleted = b'0'"
    })
    int supersedeActiveScheduledDetailTask(
            @Param("taskId") Long taskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("resultJson") String resultJson,
            @Param("message") String message
    );

    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = 'SUCCEEDED',",
            "    finished_at = NOW(),",
            "    error_code = NULL,",
            "    error_message = NULL,",
            "    gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND trigger_mode = 'SCHEDULED_DETAIL_MONITOR'",
            "  AND status = #{expectedStatus}",
            "  AND is_deleted = b'0'"
    })
    int supersedeActiveScheduledDetailRun(
            @Param("taskId") Long taskId,
            @Param("runId") Long runId,
            @Param("watchProductId") Long watchProductId,
            @Param("expectedStatus") String expectedStatus
    );
}
