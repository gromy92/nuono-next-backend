package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlisting.ProductListingTaskRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductListingTaskLeaseMapper {

    @Update({
            "UPDATE product_listing_task",
            "SET status = 'written_verify_failed',",
            "    failure_category = 'recovery',",
            "    failure_code = 'real_run_interrupted',",
            "    failure_message = '真实上架任务执行中断，系统不会自动重放 Noon 写入；请人工核对 Noon 后继续。',",
            "    completed_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at IS NOT NULL",
            "  AND gmt_updated < #{staleBefore}"
    })
    int recoverStaleRunningRealRunTasks(@Param("staleBefore") LocalDateTime staleBefore);

    @Update({
            "UPDATE product_listing_task",
            "SET status = 'running',",
            "    started_at = #{startedAt},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'submitted'"
    })
    int markTaskRunning(
            @Param("taskId") Long taskId,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update({
            "UPDATE product_listing_task",
            "SET status = 'running',",
            "    started_at = #{startedAt},",
            "    completed_at = NULL,",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = #{expectedStatus}"
    })
    int markTaskRecoveryRunning(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedStatus") String expectedStatus,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update({
            "UPDATE product_listing_task",
            "SET gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at = #{startedAt}"
    })
    int heartbeatRunningRealRunTask(
            @Param("taskId") Long taskId,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update({
            "UPDATE product_listing_task",
            "SET gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at = #{startedAt}"
    })
    int heartbeatRunningTask(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update({
            "UPDATE product_listing_task",
            "SET noon_result_json = #{noonResultJson},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at = #{startedAt}"
    })
    int checkpointRunningTaskNoonResult(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("noonResultJson") String noonResultJson,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update({
            "UPDATE product_listing_task",
            "SET status = #{task.status},",
            "    noon_result_json = #{task.noonResultJson},",
            "    failure_category = #{task.failureCategory},",
            "    failure_code = #{task.failureCode},",
            "    failure_message = #{task.failureMessage},",
            "    completed_at = #{task.completedAt},",
            "    gmt_updated = NOW()",
            "WHERE id = #{task.id}",
            "  AND owner_user_id = #{task.ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at = #{task.startedAt}"
    })
    int updateRunningTaskResult(@Param("task") ProductListingTaskRecord task);

    @Update({
            "UPDATE product_listing_task",
            "SET status = #{task.status},",
            "    noon_result_json = #{task.noonResultJson},",
            "    failure_category = #{task.failureCategory},",
            "    failure_code = #{task.failureCode},",
            "    failure_message = #{task.failureMessage},",
            "    completed_at = #{task.completedAt},",
            "    gmt_updated = NOW()",
            "WHERE id = #{task.id}",
            "  AND owner_user_id = #{task.ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at = #{expectedStartedAt}"
    })
    int completeRunningTaskResult(
            @Param("task") ProductListingTaskRecord task,
            @Param("expectedStartedAt") LocalDateTime expectedStartedAt
    );
}
