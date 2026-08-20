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

public interface NoonAuthRecoveryQueueMapper extends NoonAuthRecoveryMapperColumns {
    @Insert({
            "INSERT INTO noon_auth_identity_recovery (",
            "  identity_key, status, generation_no, send_attempt_count, coalesce_until, next_attempt_at,",
            "  version_no, config_fingerprint, requested_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{identityKey}, 'COALESCING', 0, 0, #{coalesceUntil}, #{coalesceUntil},",
            "  0, #{configFingerprint}, #{requestedAt}, #{requestedAt}, #{requestedAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  id = LAST_INSERT_ID(id)"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "id",
            before = false,
            resultType = Long.class
    )
    int coalesceActiveRecovery(NoonAuthIdentityRecoveryRecord recovery);

    @Select({
            "SELECT id FROM noon_auth_owner_scope_manifest",
            "WHERE active_identity_slot = #{identityKey}",
            "LIMIT 1 FOR UPDATE"
    })
    Long selectActiveLegacyOwnerScopeManifestForUpdate(@Param("identityKey") String identityKey);

    @Select({
            "SELECT", RECOVERY_COLUMNS,
            "FROM noon_auth_identity_recovery",
            "WHERE id = #{recoveryId}",
            "LIMIT 1"
    })
    NoonAuthIdentityRecoveryRecord selectRecovery(@Param("recoveryId") Long recoveryId);

    @Select({
            "SELECT", RECOVERY_COLUMNS,
            "FROM noon_auth_identity_recovery",
            "WHERE id = #{recoveryId}",
            "LIMIT 1 FOR UPDATE"
    })
    NoonAuthIdentityRecoveryRecord selectRecoveryForUpdate(@Param("recoveryId") Long recoveryId);

    @Select({
            "SELECT", RECOVERY_COLUMNS,
            "FROM noon_auth_identity_recovery",
            "WHERE identity_key = #{identityKey}",
            "  AND active_identity_slot IS NOT NULL",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    NoonAuthIdentityRecoveryRecord selectActiveRecovery(@Param("identityKey") String identityKey);

    @Select({
            "SELECT", RECOVERY_COLUMNS,
            "FROM noon_auth_identity_recovery",
            "WHERE identity_key = #{identityKey}",
            "  AND active_identity_slot IS NOT NULL",
            "ORDER BY id DESC",
            "LIMIT 1 FOR UPDATE"
    })
    NoonAuthIdentityRecoveryRecord selectActiveRecoveryForUpdate(@Param("identityKey") String identityKey);

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET status = 'FAILED_FINAL',",
            "    failure_code = 'SUPERSEDED_BY_RENEWAL',",
            "    diagnostic_summary = 'historical held recovery retired before a fresh identity renewal',",
            "    completed_at = COALESCE(completed_at, #{now}),",
            "    lease_owner = NULL,",
            "    lease_token = NULL,",
            "    lease_until = NULL,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = 'MANUAL_HOLD'",
            "  AND version_no = #{expectedVersion}",
            "  AND active_identity_slot IS NOT NULL"
    })
    int retireLegacyManualHold(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT", RECOVERY_COLUMNS,
            "FROM noon_auth_identity_recovery",
            "WHERE active_identity_slot IS NOT NULL",
            "  AND status != 'MANUAL_HOLD'",
            "  AND next_attempt_at <= #{now}",
            "  AND (lease_until IS NULL OR lease_until <= #{now})",
            "ORDER BY next_attempt_at ASC, requested_at ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<NoonAuthIdentityRecoveryRecord> listDueRecoveries(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    @Select({
            "SELECT DISTINCT recovery.identity_key",
            "FROM noon_auth_identity_recovery recovery",
            "LEFT JOIN noon_project_auth_state state ON state.active_recovery_id = recovery.id",
            "WHERE recovery.identity_key <> #{identityKey}",
            "  AND (",
            "    recovery.status IN (",
            "      'WAITING_PREDECESSOR', 'COALESCING', 'AUTHENTICATING', 'WAITING_EMAIL',",
            "      'VALIDATING', 'APPLYING_PROJECTS', 'RECOVERING_PULLS', 'WAITING_COOLDOWN', 'MANUAL_HOLD'",
            "    )",
            "    OR (",
            "      state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')",
            "      AND state.active_recovery_id IS NOT NULL",
            "    )",
            "  )"
    })
    List<String> listUndrainedIdentityKeysExcept(@Param("identityKey") String identityKey);

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET status = 'CANCELLED',",
            "    failure_code = #{failureCode},",
            "    diagnostic_summary = #{diagnosticSummary},",
            "    completed_at = COALESCE(completed_at, #{now}),",
            "    lease_owner = NULL,",
            "    lease_token = NULL,",
            "    lease_until = NULL,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE status IN (",
            "  'WAITING_PREDECESSOR', 'COALESCING', 'AUTHENTICATING', 'WAITING_EMAIL',",
            "  'VALIDATING', 'APPLYING_PROJECTS', 'RECOVERING_PULLS', 'WAITING_COOLDOWN', 'MANUAL_HOLD'",
            ")",
            "  AND (#{identityKey} IS NULL OR identity_key = #{identityKey})"
    })
    int cancelRecoveriesForDrain(
            @Param("identityKey") String identityKey,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET item.status = 'SKIPPED',",
            "    item.failure_code = #{failureCode},",
            "    item.diagnostic_summary = #{diagnosticSummary},",
            "    item.gmt_updated = #{now}",
            "WHERE recovery.status = 'CANCELLED'",
            "  AND item.status IN ('PENDING', 'VALIDATING', 'FAILED')",
            "  AND (#{identityKey} IS NULL OR recovery.identity_key = #{identityKey})"
    })
    int skipItemsForDrainedRecoveries(
            @Param("identityKey") String identityKey,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_pull_task task",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = task.auth_recovery_id",
            "SET task.status = 'QUEUED',",
            "    task.auth_recovery_id = NULL,",
            "    task.failure_type = NULL,",
            "    task.retry_action = NULL,",
            "    task.retryable = NULL,",
            "    task.requires_manual_action = NULL,",
            "    task.diagnostic_summary = #{diagnosticSummary},",
            "    task.readiness_state = 'auth_recovery_disabled',",
            "    task.locked_by = NULL,",
            "    task.queued_at = #{now},",
            "    task.started_at = NULL,",
            "    task.finished_at = NULL,",
            "    task.gmt_updated = #{now}",
            "WHERE task.status = 'BLOCKED_AUTH'",
            "  AND task.is_deleted = b'0'",
            "  AND recovery.status IN ('CANCELLED', 'COMPLETED', 'FAILED_FINAL')",
            "  AND (#{identityKey} IS NULL OR recovery.identity_key = #{identityKey})"
    })
    int requeueTasksForDrainedRecoveries(
            @Param("identityKey") String identityKey,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_project_auth_state state",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = state.active_recovery_id",
            "SET state.status = 'RECOVERY_DISABLED',",
            "    state.active_recovery_id = NULL,",
            "    state.auth_version = state.auth_version + 1,",
            "    state.last_failure_code = #{failureCode},",
            "    state.manual_hold_reason = NULL,",
            "    state.gmt_updated = #{now}",
            "WHERE recovery.status IN ('CANCELLED', 'COMPLETED', 'FAILED_FINAL')",
            "  AND (#{identityKey} IS NULL OR recovery.identity_key = #{identityKey})"
    })
    int releaseProjectsForDrainedRecoveries(
            @Param("identityKey") String identityKey,
            @Param("failureCode") String failureCode,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET lease_owner = #{leaseOwner},",
            "    lease_token = #{leaseToken},",
            "    lease_until = #{leaseUntil},",
            "    started_at = COALESCE(started_at, #{now}),",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND next_attempt_at <= #{now}",
            "  AND (lease_until IS NULL OR lease_until <= #{now})",
            "  AND active_identity_slot IS NOT NULL"
    })
    int tryClaimRecovery(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET lease_until = #{leaseUntil},",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND lease_token = #{expectedLeaseToken}",
            "  AND lease_until > #{now}",
            "  AND active_identity_slot IS NOT NULL"
    })
    int renewLease(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );
}
