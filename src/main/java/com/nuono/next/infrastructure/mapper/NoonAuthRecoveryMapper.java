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

public interface NoonAuthRecoveryMapper {

    String PROJECT_BINDING_FINGERPRINT = "SHA2(CONCAT("
            + "'cookie#', CHAR_LENGTH(COALESCE(up.noon_partner_cookie, '')), ':', "
            + "COALESCE(up.noon_partner_cookie, ''), "
            + "'|user#', COALESCE(up.noon_partner_user, ''), "
            + "'|partner#', COALESCE(up.noon_partner_id, ''), "
            + "'|bind#', COALESCE(CAST(up.bind_status AS CHAR), 'NULL'), "
            + "'|authorized#', COALESCE(CAST(up.is_authorized AS CHAR), 'NULL'), "
            + "'|deleted#', COALESCE(CAST(up.is_deleted AS CHAR), 'NULL')"
            + "), 256)";

    String RECOVERY_COLUMNS = ""
            + "id, predecessor_recovery_id AS predecessorRecoveryId, identity_key AS identityKey, "
            + "status, generation_no AS generationNo, "
            + "send_attempt_count AS sendAttemptCount, first_send_at AS firstSendAt, "
            + "second_send_at AS secondSendAt, coalesce_until AS coalesceUntil, "
            + "next_attempt_at AS nextAttemptAt, lease_owner AS leaseOwner, lease_token AS leaseToken, "
            + "lease_until AS leaseUntil, version_no AS versionNo, config_fingerprint AS configFingerprint, "
            + "last_mail_uid_hash AS lastMailUidHash, last_message_id_hash AS lastMessageIdHash, "
            + "failure_code AS failureCode, diagnostic_summary AS diagnosticSummary, "
            + "requested_at AS requestedAt, started_at AS startedAt, completed_at AS completedAt, "
            + "gmt_create AS createdAt, gmt_updated AS updatedAt";

    String ITEM_COLUMNS = ""
            + "id, recovery_id AS recoveryId, owner_user_id AS ownerUserId, project_code AS projectCode, "
            + "store_code AS storeCode, site_code AS siteCode, source_task_id AS sourceTaskId, "
            + "source_domain AS sourceDomain, expected_auth_version AS expectedAuthVersion, status, "
            + "failure_code AS failureCode, diagnostic_summary AS diagnosticSummary, "
            + "recovered_at AS recoveredAt, gmt_create AS createdAt, gmt_updated AS updatedAt";

    String PROJECT_STATE_COLUMNS = ""
            + "owner_user_id AS ownerUserId, project_code AS projectCode, identity_key AS identityKey, "
            + "status, active_recovery_id AS activeRecoveryId, auth_version AS authVersion, "
            + "binding_fingerprint AS bindingFingerprint, config_fingerprint AS configFingerprint, "
            + "last_failure_code AS lastFailureCode, last_failure_task_id AS lastFailureTaskId, "
            + "last_failure_at AS lastFailureAt, last_success_at AS lastSuccessAt, "
            + "manual_hold_reason AS manualHoldReason, gmt_create AS createdAt, gmt_updated AS updatedAt";

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

    @Insert({
            "INSERT INTO noon_auth_identity_recovery (",
            "  predecessor_recovery_id, identity_key, status, generation_no, send_attempt_count,",
            "  coalesce_until, next_attempt_at, version_no, config_fingerprint, requested_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{predecessorRecoveryId}, #{identityKey}, 'WAITING_PREDECESSOR', 0, 0,",
            "  #{coalesceUntil}, #{coalesceUntil}, 0, #{configFingerprint}, #{requestedAt}, #{requestedAt}, #{requestedAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  id = LAST_INSERT_ID(id)"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "id",
            before = false,
            resultType = Long.class
    )
    int coalesceSuccessorRecovery(NoonAuthIdentityRecoveryRecord recovery);

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

    @Select({
            "SELECT", RECOVERY_COLUMNS,
            "FROM noon_auth_identity_recovery",
            "WHERE successor_identity_slot = #{identityKey}",
            "LIMIT 1 FOR UPDATE"
    })
    NoonAuthIdentityRecoveryRecord selectWaitingSuccessorForUpdate(@Param("identityKey") String identityKey);

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
            "UPDATE noon_auth_identity_recovery successor",
            "JOIN noon_auth_identity_recovery predecessor ON predecessor.id = successor.predecessor_recovery_id",
            "LEFT JOIN noon_auth_identity_recovery active",
            "  ON active.identity_key = successor.identity_key",
            " AND active.active_identity_slot IS NOT NULL",
            "SET successor.status = 'COALESCING',",
            "    successor.coalesce_until = #{coalesceUntil},",
            "    successor.next_attempt_at = #{coalesceUntil},",
            "    successor.version_no = successor.version_no + 1,",
            "    successor.gmt_updated = #{now}",
            "WHERE successor.status = 'WAITING_PREDECESSOR'",
            "  AND predecessor.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "  AND active.id IS NULL"
    })
    int promoteReadySuccessors(
            @Param("coalesceUntil") LocalDateTime coalesceUntil,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery successor",
            "JOIN noon_auth_identity_recovery predecessor ON predecessor.id = successor.predecessor_recovery_id",
            "LEFT JOIN noon_auth_identity_recovery active",
            "  ON active.identity_key = successor.identity_key",
            " AND active.active_identity_slot IS NOT NULL",
            "SET successor.status = 'COALESCING',",
            "    successor.coalesce_until = #{coalesceUntil},",
            "    successor.next_attempt_at = #{coalesceUntil},",
            "    successor.version_no = successor.version_no + 1,",
            "    successor.gmt_updated = #{now}",
            "WHERE successor.status = 'WAITING_PREDECESSOR'",
            "  AND predecessor.id = #{predecessorRecoveryId}",
            "  AND predecessor.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "  AND active.id IS NULL"
    })
    int promoteSuccessorForPredecessor(
            @Param("predecessorRecoveryId") Long predecessorRecoveryId,
            @Param("coalesceUntil") LocalDateTime coalesceUntil,
            @Param("now") LocalDateTime now
    );

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

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET status = #{targetStatus},",
            "    next_attempt_at = COALESCE(#{nextAttemptAt}, next_attempt_at),",
            "    failure_code = #{failureCode},",
            "    diagnostic_summary = #{diagnosticSummary},",
            "    completed_at = COALESCE(#{completedAt}, completed_at),",
            "    lease_owner = CASE WHEN #{releaseLease} THEN NULL ELSE lease_owner END,",
            "    lease_token = CASE WHEN #{releaseLease} THEN NULL ELSE lease_token END,",
            "    lease_until = CASE WHEN #{releaseLease} THEN NULL ELSE lease_until END,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND lease_token = #{expectedLeaseToken}",
            "  AND lease_until > #{now}",
            "  AND active_identity_slot IS NOT NULL",
            "  AND (",
            "    #{targetStatus} NOT IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')",
            "    OR NOT EXISTS (",
            "      SELECT 1 FROM noon_auth_identity_recovery_item item",
            "      WHERE item.recovery_id = noon_auth_identity_recovery.id",
            "        AND item.status IN ('PENDING', 'VALIDATING')",
            "    )",
            "  )"
    })
    int transitionRecovery(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("targetStatus") NoonAuthRecoveryStatus targetStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("releaseLease") boolean releaseLease,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery recovery",
            "SET recovery.status = 'COMPLETED',",
            "    recovery.failure_code = #{failureCode},",
            "    recovery.diagnostic_summary = #{diagnosticSummary},",
            "    recovery.completed_at = #{completedAt},",
            "    recovery.lease_owner = NULL,",
            "    recovery.lease_token = NULL,",
            "    recovery.lease_until = NULL,",
            "    recovery.version_no = recovery.version_no + 1,",
            "    recovery.gmt_updated = #{now}",
            "WHERE recovery.id = #{recoveryId}",
            "  AND recovery.status = #{expectedStatus}",
            "  AND recovery.version_no = #{expectedVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM noon_auth_identity_recovery_item item",
            "    WHERE item.recovery_id = recovery.id",
            "      AND item.status IN ('PENDING', 'VALIDATING')",
            "  )"
    })
    int completeRecoveryIfDrained(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("completedAt") LocalDateTime completedAt,
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

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET generation_no = generation_no + 1,",
            "    first_send_at = CASE WHEN send_attempt_count = 0 THEN #{sendIntentAt} ELSE first_send_at END,",
            "    second_send_at = CASE WHEN send_attempt_count = 1 THEN #{sendIntentAt} ELSE second_send_at END,",
            "    send_attempt_count = send_attempt_count + 1,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND lease_token = #{expectedLeaseToken}",
            "  AND lease_until > #{now}",
            "  AND send_attempt_count < 2",
            "  AND active_identity_slot IS NOT NULL"
    })
    int recordSendIntent(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("sendIntentAt") LocalDateTime sendIntentAt,
            @Param("now") LocalDateTime now
    );

    @Insert({
            "INSERT INTO noon_auth_identity_send_ledger (",
            "  identity_key, recovery_id, config_fingerprint, send_budget_epoch, generation_no,",
            "  send_intent_at, gmt_create",
            ")",
            "SELECT identity_key, id, config_fingerprint, send_budget_epoch, generation_no,",
            "  #{sendIntentAt}, #{now}",
            "FROM noon_auth_identity_recovery",
            "WHERE id = #{recoveryId}",
            "  AND config_fingerprint IS NOT NULL"
    })
    int insertIdentitySendLedger(
            @Param("recoveryId") Long recoveryId,
            @Param("sendIntentAt") LocalDateTime sendIntentAt,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET last_mail_uid_hash = #{mailUidHash},",
            "    last_message_id_hash = #{messageIdHash},",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND lease_token = #{expectedLeaseToken}",
            "  AND lease_until > #{now}"
    })
    int recordMailboxCorrelation(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("mailUidHash") String mailUidHash,
            @Param("messageIdHash") String messageIdHash,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT MAX(send_intent_at)",
            "FROM noon_auth_identity_send_ledger",
            "WHERE identity_key = #{identityKey}"
    })
    LocalDateTime selectLatestIdentitySendAt(@Param("identityKey") String identityKey);

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

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET config_fingerprint = #{configFingerprint},",
            "    failure_code = NULL,",
            "    diagnostic_summary = NULL,",
            "    lease_owner = NULL,",
            "    lease_token = NULL,",
            "    lease_until = NULL,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = 'WAITING_PREDECESSOR'",
            "  AND version_no = #{expectedVersion}",
            "  AND predecessor_recovery_id IS NOT NULL"
    })
    int rebaseWaitingSuccessorForBindingEpoch(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("configFingerprint") String configFingerprint,
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
            "SELECT " + PROJECT_BINDING_FINGERPRINT,
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
            "SET up.noon_partner_cookie = #{cookie},",
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
            "  AND state.binding_fingerprint = " + PROJECT_BINDING_FINGERPRINT,
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
            @Param("cookie") String cookie,
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

    @Insert({
            "INSERT INTO noon_auth_identity_recovery_item (",
            "  recovery_id, owner_user_id, project_code, store_code, site_code, source_task_id,",
            "  source_domain, expected_auth_version, status, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{recoveryId}, #{ownerUserId}, #{projectCode}, #{storeCode}, #{siteCode}, #{sourceTaskId},",
            "  #{sourceDomain}, #{expectedAuthVersion}, 'PENDING', #{createdAt}, #{createdAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  id = LAST_INSERT_ID(id)"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "id",
            before = false,
            resultType = Long.class
    )
    int coalesceRecoveryItem(NoonAuthRecoveryItemRecord item);

    @Select({
            "SELECT", ITEM_COLUMNS,
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "ORDER BY id ASC",
            "LIMIT 1"
    })
    NoonAuthRecoveryItemRecord selectProjectRecoveryItem(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Select({
            "SELECT", ITEM_COLUMNS,
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND status = 'PENDING'",
            "ORDER BY id ASC",
            "LIMIT #{limit}"
    })
    List<NoonAuthRecoveryItemRecord> listPendingItems(
            @Param("recoveryId") Long recoveryId,
            @Param("limit") int limit
    );

    @Select({
            "SELECT", ITEM_COLUMNS,
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "ORDER BY id ASC"
    })
    List<NoonAuthRecoveryItemRecord> listRecoveryItems(@Param("recoveryId") Long recoveryId);

    @Select({
            "SELECT COUNT(1)",
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND status IN ('PENDING', 'VALIDATING')"
    })
    int countPendingItems(@Param("recoveryId") Long recoveryId);

    @Update({
            "UPDATE noon_pull_task task",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = task.auth_recovery_id",
            "SET task.status = 'FAILED',",
            "    task.failure_type = 'auth_required',",
            "    task.retry_action = 'MANUAL_ACTION',",
            "    task.retryable = b'0',",
            "    task.requires_manual_action = b'1',",
            "    task.diagnostic_summary = #{diagnosticSummary},",
            "    task.readiness_state = 'auth_recovery_failed',",
            "    task.locked_by = NULL,",
            "    task.finished_at = #{now},",
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
    int failBlockedTaskAfterRecovery(
            @Param("taskId") Long taskId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET item.status = #{targetStatus},",
            "    item.failure_code = #{failureCode},",
            "    item.diagnostic_summary = #{diagnosticSummary},",
            "    item.recovered_at = #{recoveredAt},",
            "    item.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.status = #{expectedStatus}",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL",
            "  AND (",
            "    #{targetStatus} IN ('PENDING', 'VALIDATING')",
            "    OR item.source_task_id IS NULL",
            "    OR NOT EXISTS (",
            "      SELECT 1 FROM noon_pull_task task",
            "      WHERE task.id = item.source_task_id",
            "        AND task.status = 'BLOCKED_AUTH'",
            "        AND task.auth_recovery_id = item.recovery_id",
            "        AND task.is_deleted = b'0'",
            "    )",
            "  )"
    })
    int transitionRecoveryItem(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryItemStatus expectedStatus,
            @Param("targetStatus") NoonAuthRecoveryItemStatus targetStatus,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("recoveredAt") LocalDateTime recoveredAt,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET item.status = #{targetStatus},",
            "    item.failure_code = #{failureCode},",
            "    item.diagnostic_summary = #{diagnosticSummary},",
            "    item.recovered_at = #{recoveredAt},",
            "    item.gmt_updated = #{now}",
            "WHERE item.recovery_id = #{recoveryId}",
            "  AND item.owner_user_id = #{ownerUserId}",
            "  AND BINARY item.project_code = BINARY #{projectCode}",
            "  AND item.expected_auth_version = #{expectedAuthVersion}",
            "  AND item.status IN ('PENDING', 'VALIDATING')",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL",
            "  AND (",
            "    #{targetStatus} IN ('PENDING', 'VALIDATING')",
            "    OR item.source_task_id IS NULL",
            "    OR NOT EXISTS (",
            "      SELECT 1 FROM noon_pull_task task",
            "      WHERE task.id = item.source_task_id",
            "        AND task.status = 'BLOCKED_AUTH'",
            "        AND task.auth_recovery_id = item.recovery_id",
            "        AND task.is_deleted = b'0'",
            "    )",
            "  )"
    })
    int transitionProjectItems(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("expectedAuthVersion") Long expectedAuthVersion,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("targetStatus") NoonAuthRecoveryItemStatus targetStatus,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("recoveredAt") LocalDateTime recoveredAt,
            @Param("now") LocalDateTime now
    );
}
