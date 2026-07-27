package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface NoonAuthRecoveryManualReauthenticationMapper {

    String PROJECT_BINDING_FINGERPRINT = "SHA2(CONCAT("
            + "'cookie#', CHAR_LENGTH(COALESCE(up.noon_partner_cookie, '')), ':', "
            + "COALESCE(up.noon_partner_cookie, ''), "
            + "'|user#', COALESCE(up.noon_partner_user, ''), "
            + "'|partner#', COALESCE(up.noon_partner_id, ''), "
            + "'|bind#', COALESCE(CAST(up.bind_status AS CHAR), 'NULL'), "
            + "'|authorized#', COALESCE(CAST(up.is_authorized AS CHAR), 'NULL'), "
            + "'|deleted#', COALESCE(CAST(up.is_deleted AS CHAR), 'NULL')"
            + "), 256)";

    @Update({
            "UPDATE noon_project_auth_state state",
            "JOIN user_project up",
            "  ON up.user_id = state.owner_user_id",
            " AND BINARY up.project_code = BINARY state.project_code",
            "SET state.status = 'HEALTHY',",
            "    state.active_recovery_id = NULL,",
            "    state.auth_version = state.auth_version + 1,",
            "    state.binding_fingerprint = " + PROJECT_BINDING_FINGERPRINT + ",",
            "    state.last_failure_code = NULL,",
            "    state.last_failure_task_id = NULL,",
            "    state.last_success_at = #{now},",
            "    state.manual_hold_reason = NULL,",
            "    state.gmt_updated = #{now}",
            "WHERE state.owner_user_id = #{ownerUserId}",
            "  AND BINARY state.project_code = BINARY #{projectCode}",
            "  AND state.auth_version = #{expectedAuthVersion}",
            "  AND state.active_recovery_id <=> #{expectedRecoveryId}",
            "  AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "  AND up.is_deleted = 0",
            "  AND up.bind_status = 1",
            "  AND up.is_authorized = 1"
    })
    int releaseProjectAfterManualReauthentication(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("expectedAuthVersion") Long expectedAuthVersion,
            @Param("expectedRecoveryId") Long expectedRecoveryId,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_pull_task task",
            "JOIN noon_auth_identity_recovery_item item",
            "  ON item.source_task_id = task.id",
            "SET task.status = 'QUEUED',",
            "    task.auth_recovery_id = NULL,",
            "    task.failure_type = NULL,",
            "    task.retry_action = NULL,",
            "    task.retryable = NULL,",
            "    task.requires_manual_action = NULL,",
            "    task.diagnostic_summary = 'project session verified by listing reauthentication',",
            "    task.readiness_state = 'manual_listing_reauthenticated',",
            "    task.locked_by = NULL,",
            "    task.queued_at = #{now},",
            "    task.started_at = NULL,",
            "    task.finished_at = NULL,",
            "    task.gmt_updated = #{now}",
            "WHERE item.recovery_id = #{recoveryId}",
            "  AND item.owner_user_id = #{ownerUserId}",
            "  AND BINARY item.project_code = BINARY #{projectCode}",
            "  AND item.status IN ('PENDING', 'VALIDATING', 'FAILED')",
            "  AND task.status = 'BLOCKED_AUTH'",
            "  AND task.auth_recovery_id = item.recovery_id",
            "  AND task.is_deleted = b'0'"
    })
    int requeueProjectPullTasksAfterManualReauthentication(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item",
            "SET status = 'RECOVERED',",
            "    failure_code = NULL,",
            "    diagnostic_summary = 'project session verified by listing reauthentication',",
            "    recovered_at = #{now},",
            "    gmt_updated = #{now}",
            "WHERE recovery_id = #{recoveryId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "  AND status IN ('PENDING', 'VALIDATING', 'FAILED')"
    })
    int recoverProjectItemsAfterManualReauthentication(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery recovery",
            "SET recovery.status = 'COMPLETED',",
            "    recovery.failure_code = NULL,",
            "    recovery.diagnostic_summary = 'drained by verified listing reauthentication',",
            "    recovery.completed_at = #{now},",
            "    recovery.lease_owner = NULL,",
            "    recovery.lease_token = NULL,",
            "    recovery.lease_until = NULL,",
            "    recovery.version_no = recovery.version_no + 1,",
            "    recovery.gmt_updated = #{now}",
            "WHERE recovery.id = #{recoveryId}",
            "  AND recovery.status NOT IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "  AND (recovery.lease_until IS NULL OR recovery.lease_until <= #{now})",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM noon_auth_identity_recovery_item item",
            "    WHERE item.recovery_id = recovery.id",
            "      AND item.status IN ('PENDING', 'VALIDATING')",
            "  )",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM noon_project_auth_state state",
            "    WHERE state.active_recovery_id = recovery.id",
            "      AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "  )"
    })
    int completeRecoveryAfterManualReauthenticationIfDrained(
            @Param("recoveryId") Long recoveryId,
            @Param("now") LocalDateTime now
    );
}
