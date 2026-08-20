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

public interface NoonAuthRecoveryConfigEpochMapper extends NoonAuthRecoveryMapperColumns {
    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET status = CASE",
            "      WHEN generation_no = 0",
            "       AND send_attempt_count = 0",
            "       AND first_send_at IS NULL",
            "       AND second_send_at IS NULL THEN 'COALESCING'",
            "      ELSE 'WAITING_COOLDOWN'",
            "    END,",
            "    config_fingerprint = #{newConfigFingerprint},",
            "    next_attempt_at = CASE",
            "      WHEN generation_no = 0",
            "       AND send_attempt_count = 0",
            "       AND first_send_at IS NULL",
            "       AND second_send_at IS NULL THEN GREATEST(coalesce_until, #{now})",
            "      ELSE GREATEST(",
            "        #{nextAttemptAt},",
            "        TIMESTAMPADD(",
            "          SECOND,",
            "          GREATEST(0, TIMESTAMPDIFF(SECOND, #{now}, #{nextAttemptAt})),",
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
            "WHERE identity_key = #{identityKey}",
            "  AND status = 'MANUAL_HOLD'",
            "  AND config_fingerprint <=> #{expectedConfigFingerprint}",
            "  AND NOT (config_fingerprint <=> #{newConfigFingerprint})",
            "  AND active_identity_slot IS NOT NULL"
    })
    int releaseManualHoldOnConfigChange(
            @Param("identityKey") String identityKey,
            @Param("expectedConfigFingerprint") String expectedConfigFingerprint,
            @Param("newConfigFingerprint") String newConfigFingerprint,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET status = CASE",
            "      WHEN generation_no = 0",
            "       AND send_attempt_count = 0",
            "       AND first_send_at IS NULL",
            "       AND second_send_at IS NULL THEN 'COALESCING'",
            "      ELSE 'WAITING_COOLDOWN'",
            "    END,",
            "    config_fingerprint = #{newConfigFingerprint},",
            "    next_attempt_at = CASE",
            "      WHEN generation_no = 0",
            "       AND send_attempt_count = 0",
            "       AND first_send_at IS NULL",
            "       AND second_send_at IS NULL THEN GREATEST(coalesce_until, #{now})",
            "      ELSE GREATEST(",
            "        #{nextAttemptAt},",
            "        TIMESTAMPADD(",
            "          SECOND,",
            "          GREATEST(0, TIMESTAMPDIFF(SECOND, #{now}, #{nextAttemptAt})),",
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
            "WHERE identity_key = #{identityKey}",
            "  AND NOT (config_fingerprint <=> #{newConfigFingerprint})",
            "  AND active_identity_slot IS NOT NULL"
    })
    int releaseChangedManualHolds(
            @Param("identityKey") String identityKey,
            @Param("newConfigFingerprint") String newConfigFingerprint,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_pull_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "JOIN noon_project_auth_state state",
            "  ON state.owner_user_id = item.owner_user_id",
            " AND BINARY state.project_code = BINARY item.project_code",
            " AND state.active_recovery_id = recovery.id",
            "SET task.status = 'QUEUED',",
            "    task.auth_recovery_id = NULL,",
            "    task.failure_type = NULL,",
            "    task.retry_action = NULL,",
            "    task.retryable = NULL,",
            "    task.requires_manual_action = NULL,",
            "    task.diagnostic_summary = 'auth configuration changed; terminal recovery task requeued',",
            "    task.readiness_state = 'config_epoch_requeued',",
            "    task.locked_by = NULL,",
            "    task.queued_at = #{now},",
            "    task.started_at = NULL,",
            "    task.finished_at = NULL,",
            "    task.gmt_updated = #{now}",
            "WHERE recovery.identity_key = #{identityKey}",
            "  AND recovery.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "  AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "  AND NOT (state.config_fingerprint <=> #{newConfigFingerprint})",
            "  AND task.status = 'BLOCKED_AUTH'",
            "  AND task.auth_recovery_id = recovery.id",
            "  AND task.is_deleted = b'0'"
    })
    int requeueTerminalBlockedTasksOnConfigChange(
            @Param("identityKey") String identityKey,
            @Param("newConfigFingerprint") String newConfigFingerprint,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "JOIN noon_pull_task task ON task.id = item.source_task_id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "JOIN noon_project_auth_state state",
            "  ON state.owner_user_id = item.owner_user_id",
            " AND BINARY state.project_code = BINARY item.project_code",
            " AND state.active_recovery_id = recovery.id",
            "SET item.status = 'STALE',",
            "    item.failure_code = 'CONFIG_EPOCH_REQUEUED',",
            "    item.diagnostic_summary = 'terminal recovery task requeued after auth configuration change',",
            "    item.gmt_updated = #{now}",
            "WHERE recovery.identity_key = #{identityKey}",
            "  AND recovery.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "  AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "  AND NOT (state.config_fingerprint <=> #{newConfigFingerprint})",
            "  AND task.status = 'QUEUED'",
            "  AND task.auth_recovery_id IS NULL",
            "  AND task.readiness_state = 'config_epoch_requeued'",
            "  AND task.is_deleted = b'0'"
    })
    int staleTerminalItemsOnConfigChange(
            @Param("identityKey") String identityKey,
            @Param("newConfigFingerprint") String newConfigFingerprint,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_project_auth_state state",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = state.active_recovery_id",
            "SET state.status = 'RECOVERY_DISABLED',",
            "    state.active_recovery_id = NULL,",
            "    state.auth_version = state.auth_version + 1,",
            "    state.config_fingerprint = #{newConfigFingerprint},",
            "    state.last_failure_code = 'IDENTITY_CONFIG_CHANGED',",
            "    state.manual_hold_reason = NULL,",
            "    state.gmt_updated = #{now}",
            "WHERE state.identity_key = #{identityKey}",
            "  AND state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "  AND recovery.status IN ('CANCELLED', 'COMPLETED', 'FAILED_FINAL')",
            "  AND NOT (state.config_fingerprint <=> #{newConfigFingerprint})"
    })
    int releaseTerminalProjectHoldsOnConfigChange(
            @Param("identityKey") String identityKey,
            @Param("newConfigFingerprint") String newConfigFingerprint,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_project_auth_state",
            "SET status = 'REAUTH_REQUIRED',",
            "    config_fingerprint = #{newConfigFingerprint},",
            "    manual_hold_reason = NULL,",
            "    last_failure_code = NULL,",
            "    last_failure_task_id = NULL,",
            "    last_failure_at = NULL,",
            "    gmt_updated = #{now}",
            "WHERE active_recovery_id = #{recoveryId}",
            "  AND status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')"
    })
    int releaseProjectManualHolds(
            @Param("recoveryId") Long recoveryId,
            @Param("newConfigFingerprint") String newConfigFingerprint,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item",
            "SET status = 'PENDING',",
            "    failure_code = NULL,",
            "    diagnostic_summary = NULL,",
            "    recovered_at = NULL,",
            "    gmt_updated = #{now}",
            "WHERE recovery_id = #{recoveryId}",
            "  AND status IN ('FAILED', 'VALIDATING')"
    })
    int reopenFailedRecoveryItems(
            @Param("recoveryId") Long recoveryId,
            @Param("now") LocalDateTime now
    );
}
