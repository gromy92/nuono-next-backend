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
}
