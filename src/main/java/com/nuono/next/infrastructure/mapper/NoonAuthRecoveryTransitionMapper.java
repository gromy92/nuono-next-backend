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

public interface NoonAuthRecoveryTransitionMapper extends NoonAuthRecoveryMapperColumns {
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
}
