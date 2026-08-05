package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Single-statement CAS transitions for DP tasks owned by Noon auth recovery items. */
public interface DataPullAuthWaitingTaskMapper {

    @Update({
            "UPDATE dp_pull_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.state = 'QUEUED',",
            "    task.retry_not_before = NULL,",
            "    task.sanitized_failure_code = NULL,",
            "    task.attempt = 0,",
            "    task.lease_owner = NULL,",
            "    task.lease_until = NULL,",
            "    task.finished_at = NULL,",
            "    task.version_no = task.version_no + 1,",
            "    task.gmt_updated = #{now}",
            "WHERE task.id = #{sourceTaskId}",
            "  AND item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_task_id = #{sourceTaskId}",
            "  AND BINARY item.source_domain = BINARY 'DP_RUNTIME'",
            "  AND BINARY item.source_checkpoint = BINARY #{expectedTaskVersionText}",
            "  AND item.resume_policy = 'AUTO_RESUME'",
            "  AND item.status = 'PENDING'",
            "  AND task.state = 'WAITING_AUTH'",
            "  AND task.version_no = #{expectedTaskVersion}",
            "  AND task.lease_owner IS NULL",
            "  AND task.lease_until IS NULL",
            "  AND task.owner_user_id = item.owner_user_id",
            "  AND BINARY task.project_code = BINARY item.project_code",
            "  AND BINARY task.store_code = BINARY item.store_code",
            "  AND BINARY COALESCE(task.site_code, '') = BINARY COALESCE(item.site_code, '')",
            "  AND recovery.id = #{recoveryId}",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND BINARY recovery.lease_token = BINARY #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int resumeAfterAuthorization(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("expectedTaskVersion") Long expectedTaskVersion,
            @Param("expectedTaskVersionText") String expectedTaskVersionText,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE dp_pull_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.retry_not_before = NULL,",
            "    task.sanitized_failure_code = #{sanitizedFailureCode},",
            "    task.gmt_updated = #{now}",
            "WHERE task.id = #{sourceTaskId}",
            "  AND item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_task_id = #{sourceTaskId}",
            "  AND BINARY item.source_domain = BINARY 'DP_RUNTIME'",
            "  AND BINARY item.source_checkpoint = BINARY #{expectedTaskVersionText}",
            "  AND item.resume_policy = 'AUTO_RESUME'",
            "  AND item.status = 'PENDING'",
            "  AND task.state = 'WAITING_AUTH'",
            "  AND task.version_no = #{expectedTaskVersion}",
            "  AND task.lease_owner IS NULL",
            "  AND task.lease_until IS NULL",
            "  AND task.owner_user_id = item.owner_user_id",
            "  AND BINARY task.project_code = BINARY item.project_code",
            "  AND BINARY task.store_code = BINARY item.store_code",
            "  AND BINARY COALESCE(task.site_code, '') = BINARY COALESCE(item.site_code, '')",
            "  AND recovery.id = #{recoveryId}",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND BINARY recovery.lease_token = BINARY #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int holdAuthorizationManualReview(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("expectedTaskVersion") Long expectedTaskVersion,
            @Param("expectedTaskVersionText") String expectedTaskVersionText,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("sanitizedFailureCode") String sanitizedFailureCode,
            @Param("now") LocalDateTime now
    );
}
