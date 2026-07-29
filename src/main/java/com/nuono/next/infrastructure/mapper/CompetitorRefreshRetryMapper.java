package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CompetitorRefreshRetryMapper {
    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = 'QUEUED',",
            "    finished_at = NULL,",
            "    error_code = #{errorCode},",
            "    error_message = #{errorMessage},",
            "    gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND status = 'RUNNING'",
            "  AND is_deleted = b'0'"
    })
    int requeueSearchRun(
            @Param("taskId") Long taskId,
            @Param("runId") Long runId,
            @Param("watchProductId") Long watchProductId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );
}
