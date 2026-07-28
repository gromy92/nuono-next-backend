package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CompetitorRefreshRecoveryMapper {
    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = 'FAILED',",
            "    finished_at = NOW(),",
            "    error_code = #{errorCode},",
            "    error_message = #{errorMessage},",
            "    gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND status IN ('QUEUED', 'RUNNING')",
            "  AND is_deleted = b'0'"
    })
    int markActiveSearchRunFailedForTask(
            @Param("runId") Long runId,
            @Param("taskId") Long taskId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    @Update({
            "UPDATE operational_task",
            "SET status = #{taskStatus},",
            "    progress_percent = CASE WHEN #{taskStatus} = 'SUCCEEDED' THEN 100 ELSE progress_percent END,",
            "    message = #{message},",
            "    error_code = #{errorCode},",
            "    finished_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND task_type = 'OPERATIONS_COMPETITOR_REFRESH'",
            "  AND status = 'FAILED'",
            "  AND error_code = #{claimedErrorCode}",
            "  AND is_deleted = b'0'"
    })
    int alignFailedStaleTaskToTerminalRun(
            @Param("taskId") Long taskId,
            @Param("claimedErrorCode") String claimedErrorCode,
            @Param("taskStatus") String taskStatus,
            @Param("errorCode") String errorCode,
            @Param("message") String message
    );
}
