package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthIdentityRecoveryRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryItemStatus;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonProjectAuthStateRecord;
import com.nuono.next.noonauth.NoonProjectAuthStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonAuthRecoveryProjectStateMapper extends NoonAuthRecoveryMapperColumns {
    @Insert({
            "INSERT INTO noon_project_auth_state (",
            "  owner_user_id, project_code, identity_key, status, active_recovery_id, auth_version,",
            "  binding_fingerprint, config_fingerprint,",
            "  last_failure_code, last_failure_task_id, last_failure_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{ownerUserId}, #{projectCode}, #{identityKey}, 'REAUTH_REQUIRED', #{recoveryId}, 1,",
            "  #{bindingFingerprint}, #{configFingerprint},",
            "  #{failureCode}, #{sourceTaskId}, #{now}, #{now}, #{now}",
            ") ON DUPLICATE KEY UPDATE",
            "  auth_version = CASE",
            "    WHEN active_recovery_id <=> VALUES(active_recovery_id)",
            "     AND status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD') THEN auth_version",
            "    ELSE auth_version + 1",
            "  END,",
            "  binding_fingerprint = CASE",
            "    WHEN active_recovery_id <=> VALUES(active_recovery_id)",
            "     AND status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD') THEN binding_fingerprint",
            "    ELSE VALUES(binding_fingerprint)",
            "  END,",
            "  config_fingerprint = CASE",
            "    WHEN active_recovery_id <=> VALUES(active_recovery_id)",
            "     AND status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD') THEN config_fingerprint",
            "    ELSE VALUES(config_fingerprint)",
            "  END,",
            "  status = CASE",
            "    WHEN active_recovery_id <=> VALUES(active_recovery_id) AND status = 'MANUAL_HOLD' THEN 'MANUAL_HOLD'",
            "    WHEN active_recovery_id <=> VALUES(active_recovery_id) AND status = 'RECOVERING' THEN 'RECOVERING'",
            "    ELSE 'REAUTH_REQUIRED'",
            "  END,",
            "  identity_key = VALUES(identity_key),",
            "  active_recovery_id = VALUES(active_recovery_id),",
            "  last_failure_code = VALUES(last_failure_code),",
            "  last_failure_task_id = VALUES(last_failure_task_id),",
            "  last_failure_at = VALUES(last_failure_at),",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    int upsertProjectAuthRequired(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("identityKey") String identityKey,
            @Param("recoveryId") Long recoveryId,
            @Param("bindingFingerprint") String bindingFingerprint,
            @Param("configFingerprint") String configFingerprint,
            @Param("failureCode") String failureCode,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT " + NoonAuthRecoverySql.PROJECT_BINDING_FINGERPRINT,
            "FROM user_project up",
            "WHERE up.user_id = #{ownerUserId}",
            "  AND BINARY up.project_code = BINARY #{projectCode}",
            "  AND up.is_deleted = 0",
            "  AND up.bind_status = 1",
            "  AND up.is_authorized = 1",
            "LIMIT 1"
    })
    String selectProjectBindingFingerprint(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Select({
            "SELECT", PROJECT_STATE_COLUMNS,
            "FROM noon_project_auth_state",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "LIMIT 1"
    })
    NoonProjectAuthStateRecord selectProjectAuthState(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Select({
            "SELECT", PROJECT_STATE_COLUMNS,
            "FROM noon_project_auth_state",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "LIMIT 1 FOR UPDATE"
    })
    NoonProjectAuthStateRecord selectProjectAuthStateForUpdate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Update({
            "UPDATE noon_project_auth_state state",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = state.active_recovery_id",
            "SET state.status = 'RECOVERING',",
            "    state.gmt_updated = #{now}",
            "WHERE state.owner_user_id = #{ownerUserId}",
            "  AND BINARY state.project_code = BINARY #{projectCode}",
            "  AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "  AND state.active_recovery_id = #{recoveryId}",
            "  AND state.auth_version = #{expectedAuthVersion}",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int markProjectRecovering(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedAuthVersion") Long expectedAuthVersion,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_project_auth_state state",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = state.active_recovery_id",
            "SET state.status = #{targetStatus},",
            "    state.last_failure_code = #{failureCode},",
            "    state.manual_hold_reason = #{manualHoldReason},",
            "    state.gmt_updated = #{now}",
            "WHERE state.owner_user_id = #{ownerUserId}",
            "  AND BINARY state.project_code = BINARY #{projectCode}",
            "  AND state.active_recovery_id = #{recoveryId}",
            "  AND state.auth_version = #{expectedAuthVersion}",
            "  AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int markProjectRecoveryFailed(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedAuthVersion") Long expectedAuthVersion,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("targetStatus") NoonProjectAuthStatus targetStatus,
            @Param("failureCode") String failureCode,
            @Param("manualHoldReason") String manualHoldReason,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE user_project up",
            "JOIN noon_project_auth_state state",
            "  ON state.owner_user_id = up.user_id",
            " AND BINARY state.project_code = BINARY up.project_code",
            "JOIN noon_auth_identity_recovery recovery",
            "  ON recovery.id = state.active_recovery_id",
            "SET up.noon_partner_cookie = #{cookie}, up.noon_partner_user_code = #{userCode},",
            "    up.cookie_generate_time = #{now},",
            "    up.bind_status = 1,",
            "    up.is_authorized = 1,",
            "    up.updated_by = #{updatedBy},",
            "    up.gmt_updated = #{now},",
            "    state.status = 'HEALTHY',",
            "    state.active_recovery_id = NULL,",
            "    state.auth_version = state.auth_version + 1,",
            "    state.last_failure_code = NULL,",
            "    state.last_failure_task_id = NULL,",
            "    state.last_success_at = #{now},",
            "    state.manual_hold_reason = NULL,",
            "    state.gmt_updated = #{now}",
            "WHERE up.user_id = #{ownerUserId}",
            "  AND BINARY up.project_code = BINARY #{projectCode}",
            "  AND up.is_deleted = 0",
            "  AND up.bind_status = 1",
            "  AND up.is_authorized = 1",
            "  AND state.active_recovery_id = #{recoveryId}",
            "  AND state.auth_version = #{expectedAuthVersion}",
            "  AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING')",
            "  AND state.binding_fingerprint = " + NoonAuthRecoverySql.PROJECT_BINDING_FINGERPRINT,
            "  AND recovery.id = #{recoveryId}",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int persistRecoveredProjectCookieCas(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedAuthVersion") Long expectedAuthVersion,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("cookie") String cookie, @Param("userCode") String userCode,
            @Param("updatedBy") Long updatedBy,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_pull_task task",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = task.auth_recovery_id",
            "SET task.status = 'QUEUED',",
            "    task.failure_type = NULL,",
            "    task.retry_action = NULL,",
            "    task.retryable = NULL,",
            "    task.requires_manual_action = NULL,",
            "    task.diagnostic_summary = 'auth recovery completed; original pull task requeued',",
            "    task.readiness_state = 'auth_recovered',",
            "    task.locked_by = NULL,",
            "    task.queued_at = #{now},",
            "    task.started_at = NULL,",
            "    task.finished_at = NULL,",
            "    task.gmt_updated = #{now}",
            "WHERE task.id = #{taskId}",
            "  AND task.status = 'BLOCKED_AUTH'",
            "  AND task.auth_recovery_id = #{recoveryId}",
            "  AND task.is_deleted = b'0'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int requeueBlockedTaskAfterRecoveryCas(
            @Param("taskId") Long taskId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("now") LocalDateTime now
    );
}
