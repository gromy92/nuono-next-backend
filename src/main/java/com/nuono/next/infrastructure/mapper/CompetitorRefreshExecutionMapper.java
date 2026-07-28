package com.nuono.next.infrastructure.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CompetitorRefreshExecutionMapper {
    @Select({
            "SELECT id FROM operational_task",
            "WHERE id = #{taskId}",
            "  AND task_type = 'OPERATIONS_COMPETITOR_REFRESH'",
            "  AND status = 'RUNNING'",
            "  AND is_deleted = b'0'",
            "FOR UPDATE"
    })
    Long lockRunningRefreshTask(@Param("taskId") Long taskId);

    @Update({
            "UPDATE operational_task SET gmt_updated = #{updatedAt}",
            "WHERE id = #{taskId}",
            "  AND task_type = 'OPERATIONS_COMPETITOR_REFRESH'",
            "  AND status = 'RUNNING'",
            "  AND is_deleted = b'0'"
    })
    int heartbeatRunningRefreshTask(
            @Param("taskId") Long taskId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Select({
            "SELECT id FROM operations_competitor_search_run",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND status = 'RUNNING'",
            "  AND is_deleted = b'0'",
            "FOR UPDATE"
    })
    Long lockRunningRefreshRun(
            @Param("taskId") Long taskId,
            @Param("runId") Long runId,
            @Param("watchProductId") Long watchProductId
    );

    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = 'QUEUED', error_code = #{errorCode},",
            "    error_message = #{errorMessage}, finished_at = NULL, gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND status = 'RUNNING'",
            "  AND is_deleted = b'0'"
    })
    int requeueRunningRefreshRun(
            @Param("taskId") Long taskId,
            @Param("runId") Long runId,
            @Param("watchProductId") Long watchProductId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = #{status}, finished_at = NOW(),",
            "    keyword_success = #{keywordSuccess}, keyword_failed = #{keywordFailed},",
            "    candidate_upserted_count = #{candidateUpsertedCount},",
            "    rank_fact_written_count = #{rankFactWrittenCount},",
            "    error_code = #{errorCode}, error_message = #{errorMessage},",
            "    updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND status = 'RUNNING'",
            "  AND is_deleted = b'0'"
    })
    int completeRunningRefreshRun(
            @Param("taskId") Long taskId,
            @Param("runId") Long runId,
            @Param("watchProductId") Long watchProductId,
            @Param("status") String status,
            @Param("keywordSuccess") int keywordSuccess,
            @Param("keywordFailed") int keywordFailed,
            @Param("candidateUpsertedCount") int candidateUpsertedCount,
            @Param("rankFactWrittenCount") int rankFactWrittenCount,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("actorUserId") Long actorUserId
    );

    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = 'FAILED', finished_at = NOW(),",
            "    error_code = #{errorCode}, error_message = #{errorMessage},",
            "    updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND task_id = #{taskId}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND status = 'RUNNING'",
            "  AND is_deleted = b'0'"
    })
    int failRunningRefreshRun(
            @Param("taskId") Long taskId,
            @Param("runId") Long runId,
            @Param("watchProductId") Long watchProductId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("actorUserId") Long actorUserId
    );

    @Update({
            "UPDATE operations_competitor_watch_product",
            "SET latest_run_id = #{runId}, latest_run_status = #{runStatus},",
            "    latest_run_at = NOW(), updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{watchProductId}",
            "  AND (latest_run_id IS NULL OR latest_run_id <= #{runId})",
            "  AND is_deleted = b'0'"
    })
    int updateLatestRefreshRunIfNotOlder(
            @Param("watchProductId") Long watchProductId,
            @Param("runId") Long runId,
            @Param("runStatus") String runStatus,
            @Param("actorUserId") Long actorUserId
    );
}
