package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface NoonAuthProductTaskMapper {

    @Update({
            "UPDATE product_publish_task task",
            "JOIN noon_auth_identity_recovery_item item",
            "  ON item.source_task_id = task.id",
            " AND item.source_domain IN ('PRODUCT_DELETE', 'PRODUCT_PUBLISH')",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.status = CASE",
            "      WHEN item.source_domain = 'PRODUCT_DELETE' THEN 'product_delete_queued'",
            "      ELSE 'queued'",
            "    END,",
            "    task.error_code = NULL,",
            "    task.error_message = NULL,",
            "    task.next_run_at = #{now},",
            "    task.finished_at = NULL,",
            "    task.active_lock_key = CONCAT('product:', task.product_master_id),",
            "    task.locked_by = NULL,",
            "    task.locked_at = NULL,",
            "    task.version_no = task.version_no + 1,",
            "    task.updated_by = task.owner_user_id,",
            "    task.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.resume_policy = 'AUTO_RESUME'",
            "  AND item.status = 'PENDING'",
            "  AND task.status = 'pending_manual_check'",
            "  AND task.error_code = 'noon_auth_recovery_pending'",
            "  AND JSON_VALID(task.result_json)",
            "  AND JSON_TYPE(task.result_json) = 'OBJECT'",
            "  AND JSON_TYPE(JSON_EXTRACT(task.result_json, '$.writeMayHaveOccurred')) = 'BOOLEAN'",
            "  AND LOWER(JSON_UNQUOTE(JSON_EXTRACT(task.result_json, '$.writeMayHaveOccurred'))) = 'false'",
            "  AND task.is_deleted = b'0'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int resumeSafeProductTask(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE product_publish_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.error_code = 'noon_auth_recovery_failed',",
            "    task.error_message = #{diagnostic},",
            "    task.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_domain IN ('PRODUCT_DELETE', 'PRODUCT_PUBLISH')",
            "  AND task.status = 'pending_manual_check'",
            "  AND task.error_code = 'noon_auth_recovery_pending'",
            "  AND task.is_deleted = b'0'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int failProductTask(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("diagnostic") String diagnostic,
            @Param("now") LocalDateTime now
    );
}
