package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.productlisting.ProductListingTaskRecord;
import java.time.LocalDateTime;
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
            "    AND task.failure_code IN ('noon_auth_required', 'noon_auth_recovered')",
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

    @Update({
            "UPDATE product_listing_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.failure_code = 'noon_auth_recovered',",
            "    task.failure_message = 'Noon 授权已恢复，原上架任务正在从安全检查点继续。',",
            "    task.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_domain = 'PRODUCT_LISTING'",
            "  AND item.status = 'PENDING'",
            "  AND task.mode = 'REAL_RUN'",
            "  AND task.status IN ('failed', 'written_verify_failed')",
            "  AND task.failure_code = 'noon_auth_required'",
            "  AND JSON_VALID(task.noon_result_json)",
            "  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(task.noon_result_json, '$.recoveryId')) AS UNSIGNED)",
            "      = item.recovery_id",
            "  AND ((",
            "    item.resume_policy = 'AUTO_RESUME'",
            "    AND COALESCE(JSON_EXTRACT(task.noon_result_json, '$.writeMayHaveOccurred'), FALSE) = FALSE",
            "  ) OR (",
            "    item.resume_policy = 'READBACK_REQUIRED'",
            "    AND JSON_EXTRACT(task.noon_result_json, '$.writeMayHaveOccurred') = TRUE",
            "  ))",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int markTaskAuthorizationRecovered(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE product_listing_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.failure_code = 'noon_auth_recovery_failed',",
            "    task.failure_message = #{diagnostic},",
            "    task.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_domain = 'PRODUCT_LISTING'",
            "  AND item.status = 'PENDING'",
            "  AND task.mode = 'REAL_RUN'",
            "  AND task.status IN ('failed', 'written_verify_failed')",
            "  AND task.failure_code = 'noon_auth_required'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int markTaskAuthorizationRecoveryFailed(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("diagnostic") String diagnostic,
            @Param("now") LocalDateTime now
    );
}
