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

public interface NoonAuthRecoveryBindingEpochMapper extends NoonAuthRecoveryMapperColumns {
    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET status = CASE",
            "      WHEN generation_no = 0",
            "       AND send_attempt_count = 0",
            "       AND first_send_at IS NULL",
            "       AND second_send_at IS NULL THEN 'COALESCING'",
            "      ELSE 'WAITING_COOLDOWN'",
            "    END,",
            "    config_fingerprint = #{configFingerprint},",
            "    coalesce_until = CASE",
            "      WHEN generation_no = 0",
            "       AND send_attempt_count = 0",
            "       AND first_send_at IS NULL",
            "       AND second_send_at IS NULL",
            "      THEN GREATEST(coalesce_until, #{coalesceUntil})",
            "      ELSE coalesce_until",
            "    END,",
            "    next_attempt_at = CASE",
            "      WHEN generation_no = 0",
            "       AND send_attempt_count = 0",
            "       AND first_send_at IS NULL",
            "       AND second_send_at IS NULL",
            "      THEN GREATEST(coalesce_until, #{coalesceUntil})",
            "      ELSE GREATEST(",
            "        #{cooldownAt},",
            "        TIMESTAMPADD(",
            "          SECOND,",
            "          GREATEST(0, TIMESTAMPDIFF(SECOND, #{now}, #{cooldownAt})),",
            "          COALESCE(second_send_at, first_send_at, #{now})",
            "        )",
            "      )",
            "    END,",
            "    send_budget_epoch = send_budget_epoch + 1,",
            "    generation_no = 0,",
            "    send_attempt_count = 0,",
            "    first_send_at = NULL,",
            "    second_send_at = NULL,",
            "    failure_code = NULL,",
            "    diagnostic_summary = NULL,",
            "    lease_owner = NULL,",
            "    lease_token = NULL,",
            "    lease_until = NULL,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND active_identity_slot IS NOT NULL"
    })
    int rebaseActiveRecoveryForBindingEpoch(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("configFingerprint") String configFingerprint,
            @Param("coalesceUntil") LocalDateTime coalesceUntil,
            @Param("cooldownAt") LocalDateTime cooldownAt,
            @Param("now") LocalDateTime now
    );

    @Insert({
            "INSERT INTO noon_project_auth_state (",
            "  owner_user_id, project_code, identity_key, status, active_recovery_id, auth_version,",
            "  binding_fingerprint, config_fingerprint, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{ownerUserId}, #{projectCode}, #{identityKey}, 'REAUTH_REQUIRED', #{recoveryId}, 1,",
            "  #{bindingFingerprint}, #{configFingerprint}, #{now}, #{now}",
            ") ON DUPLICATE KEY UPDATE",
            "  identity_key = VALUES(identity_key),",
            "  status = 'REAUTH_REQUIRED',",
            "  active_recovery_id = VALUES(active_recovery_id),",
            "  auth_version = auth_version + 1,",
            "  binding_fingerprint = VALUES(binding_fingerprint),",
            "  config_fingerprint = VALUES(config_fingerprint),",
            "  last_failure_code = NULL,",
            "  last_failure_task_id = NULL,",
            "  last_failure_at = NULL,",
            "  manual_hold_reason = NULL,",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    int rebaseProjectAuthStateForBindingEpoch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("identityKey") String identityKey,
            @Param("recoveryId") Long recoveryId,
            @Param("bindingFingerprint") String bindingFingerprint,
            @Param("configFingerprint") String configFingerprint,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT", ITEM_COLUMNS,
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "  AND source_task_id IS NULL",
            "LIMIT 1 FOR UPDATE"
    })
    NoonAuthRecoveryItemRecord selectSourceLessProjectRecoveryItemForUpdate(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "LEFT JOIN noon_pull_task task ON task.id = item.source_task_id",
            "SET item.status = 'PENDING',",
            "    item.expected_auth_version = #{expectedAuthVersion},",
            "    item.failure_code = NULL,",
            "    item.diagnostic_summary = NULL,",
            "    item.recovered_at = NULL,",
            "    item.gmt_updated = #{now}",
            "WHERE item.recovery_id = #{recoveryId}",
            "  AND item.owner_user_id = #{ownerUserId}",
            "  AND BINARY item.project_code = BINARY #{projectCode}",
            "  AND (",
            "    item.status IN ('PENDING', 'VALIDATING')",
            "    OR item.source_task_id IS NULL",
            "    OR (",
            "      task.status = 'BLOCKED_AUTH'",
            "      AND task.auth_recovery_id = item.recovery_id",
            "      AND task.is_deleted = b'0'",
            "    )",
            "  )"
    })
    int reopenProjectItemsForBindingEpoch(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("expectedAuthVersion") Long expectedAuthVersion,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "JOIN noon_pull_task task ON task.id = item.source_task_id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET item.status = 'STALE',",
            "    item.failure_code = 'BINDING_EPOCH_REQUEUED',",
            "    item.diagnostic_summary = 'terminal recovery task requeued for a new binding epoch',",
            "    item.gmt_updated = #{now}",
            "WHERE item.recovery_id = #{oldRecoveryId}",
            "  AND item.owner_user_id = #{ownerUserId}",
            "  AND BINARY item.project_code = BINARY #{projectCode}",
            "  AND recovery.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "  AND task.status = 'QUEUED'",
            "  AND task.auth_recovery_id IS NULL",
            "  AND task.readiness_state = 'binding_epoch_requeued'",
            "  AND task.is_deleted = b'0'"
    })
    int staleTerminalBlockedProjectItemsForBindingEpoch(
            @Param("oldRecoveryId") Long oldRecoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_pull_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.status = 'QUEUED',",
            "    task.auth_recovery_id = NULL,",
            "    task.failure_type = NULL,",
            "    task.retry_action = NULL,",
            "    task.retryable = NULL,",
            "    task.requires_manual_action = NULL,",
            "    task.diagnostic_summary = 'binding changed; terminal auth-recovery task requeued',",
            "    task.readiness_state = 'binding_epoch_requeued',",
            "    task.locked_by = NULL,",
            "    task.queued_at = #{now},",
            "    task.started_at = NULL,",
            "    task.finished_at = NULL,",
            "    task.gmt_updated = #{now}",
            "WHERE item.recovery_id = #{oldRecoveryId}",
            "  AND item.owner_user_id = #{ownerUserId}",
            "  AND BINARY item.project_code = BINARY #{projectCode}",
            "  AND recovery.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "  AND task.status = 'BLOCKED_AUTH'",
            "  AND task.auth_recovery_id = item.recovery_id",
            "  AND task.is_deleted = b'0'"
    })
    int requeueTerminalBlockedProjectTasksForBindingEpoch(
            @Param("oldRecoveryId") Long oldRecoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery recovery",
            "SET recovery.status = 'CANCELLED',",
            "    recovery.failure_code = 'EMPTY_ENQUEUE_REJECTED',",
            "    recovery.diagnostic_summary = 'enqueue eligibility changed before project lock',",
            "    recovery.completed_at = #{now},",
            "    recovery.lease_owner = NULL,",
            "    recovery.lease_token = NULL,",
            "    recovery.lease_until = NULL,",
            "    recovery.version_no = recovery.version_no + 1,",
            "    recovery.gmt_updated = #{now}",
            "WHERE recovery.id = #{recoveryId}",
            "  AND recovery.status = 'COALESCING'",
            "  AND recovery.generation_no = 0",
            "  AND recovery.send_attempt_count = 0",
            "  AND recovery.first_send_at IS NULL",
            "  AND recovery.second_send_at IS NULL",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM noon_auth_identity_recovery_item item",
            "    WHERE item.recovery_id = recovery.id",
            "  )"
    })
    int cancelEmptyRecoveryAfterRejectedEnqueue(
            @Param("recoveryId") Long recoveryId,
            @Param("now") LocalDateTime now
    );
}
