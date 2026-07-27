package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlisting.ProductListingTaskRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductListingAuthRecoveryMapper {
    @Select({
            "SELECT",
            "  pending.id, pending.draft_id, pending.owner_user_id, pending.store_code,",
            "  pending.task_no, pending.mode, pending.status, pending.source_task_id,",
            "  pending.input_snapshot_json, pending.validation_json,",
            "  pending.confirmation_json, pending.noon_result_json,",
            "  pending.failure_category, pending.failure_code, pending.failure_message,",
            "  pending.submitted_by, pending.submitted_at, pending.started_at,",
            "  pending.completed_at, pending.gmt_create, pending.gmt_updated",
            "FROM (",
            "  SELECT",
            "    task.*,",
            "    ROW_NUMBER() OVER (",
            "      PARTITION BY task.owner_user_id, BINARY task.store_code",
            "      ORDER BY task.completed_at, task.id",
            "    ) AS store_recovery_rank",
            "  FROM product_listing_task task",
            "  WHERE task.mode = 'REAL_RUN'",
            "    AND task.status IN ('failed', 'written_verify_failed')",
            "    AND task.failure_code = 'noon_auth_required'",
            "    AND JSON_VALID(task.noon_result_json)",
            "    AND NULLIF(JSON_UNQUOTE(JSON_EXTRACT(",
            "        task.noon_result_json, '$.recoveryId')), 'null') IS NOT NULL",
            ") pending",
            "WHERE pending.store_recovery_rank = 1",
            "ORDER BY",
            "  CASE WHEN pending.id > #{afterTaskId} THEN 0 ELSE 1 END,",
            "  pending.id",
            "LIMIT #{limit}"
    })
    List<ProductListingTaskRecord> selectPendingAuthRecoveryTasks(
            @Param("afterTaskId") long afterTaskId,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE product_listing_task",
            "SET failure_category = 'manual_recovery',",
            "    failure_code = 'listing_auth_recovery_manual_review',",
            "    failure_message = #{failureMessage},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'written_verify_failed'",
            "  AND failure_code = 'noon_auth_required'",
            "  AND JSON_VALID(noon_result_json)",
            "  AND noon_result_json = #{expectedNoonResultJson}",
            "  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(noon_result_json, '$.recoveryId')) AS UNSIGNED)",
            "      = #{recoveryId}"
    })
    int markAuthRecoveryManualReview(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedNoonResultJson") String expectedNoonResultJson,
            @Param("recoveryId") Long recoveryId,
            @Param("failureMessage") String failureMessage
    );

    @Update({
            "UPDATE product_listing_task",
            "SET failure_category = 'authorization',",
            "    failure_code = 'listing_auth_recovery_superseded',",
            "    failure_message = #{failureMessage},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'failed'",
            "  AND failure_code = 'noon_auth_required'",
            "  AND JSON_VALID(noon_result_json)",
            "  AND noon_result_json = #{expectedNoonResultJson}",
            "  AND COALESCE(JSON_EXTRACT(noon_result_json, '$.writeMayHaveOccurred'), FALSE) = FALSE",
            "  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(noon_result_json, '$.recoveryId')) AS UNSIGNED)",
            "      = #{recoveryId}"
    })
    int markPreWriteAuthRecoverySuperseded(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedNoonResultJson") String expectedNoonResultJson,
            @Param("recoveryId") Long recoveryId,
            @Param("failureMessage") String failureMessage
    );
}
