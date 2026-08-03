package com.nuono.next.infrastructure.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface NoonAuthRateLimitRecoveryMapper {

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET status = 'WAITING_COOLDOWN',",
            "    next_attempt_at = #{nextAttemptAt},",
            "    lease_owner = NULL,",
            "    lease_token = NULL,",
            "    lease_until = NULL,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND version_no = #{expectedVersion}",
            "  AND identity_key = #{identityKey}",
            "  AND status = 'MANUAL_HOLD'",
            "  AND failure_code = 'SEND_RATE_LIMITED'",
            "  AND config_fingerprint <=> #{expectedConfigFingerprint}",
            "  AND send_attempt_count = 1",
            "  AND second_send_at IS NULL",
            "  AND COALESCE(second_send_at, first_send_at) <= #{cooldownCutoff}",
            "  AND active_identity_slot IS NOT NULL",
            "  AND (lease_until IS NULL OR lease_until <= #{now})",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM noon_project_auth_state state",
            "    WHERE state.active_recovery_id = noon_auth_identity_recovery.id",
            "      AND state.status = 'MANUAL_HOLD'",
            "      AND COALESCE(state.last_failure_code, '') <> 'SEND_RATE_LIMITED'",
            "  )"
    })
    int releaseEligibleRateLimitedManualHold(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("identityKey") String identityKey,
            @Param("expectedConfigFingerprint") String expectedConfigFingerprint,
            @Param("cooldownCutoff") LocalDateTime cooldownCutoff,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_project_auth_state",
            "SET status = 'REAUTH_REQUIRED',",
            "    manual_hold_reason = NULL,",
            "    gmt_updated = #{now}",
            "WHERE active_recovery_id = #{recoveryId}",
            "  AND status = 'MANUAL_HOLD'",
            "  AND last_failure_code = 'SEND_RATE_LIMITED'"
    })
    int releaseRateLimitedProjectHolds(
            @Param("recoveryId") Long recoveryId,
            @Param("now") LocalDateTime now
    );
}
