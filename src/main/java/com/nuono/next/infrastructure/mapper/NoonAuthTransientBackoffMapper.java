package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthTransientBackoffState;
import com.nuono.next.noonauth.NoonAuthTransientBackoffWriteFence;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface NoonAuthTransientBackoffMapper {

    String COLUMNS = ""
            + "logical_store_id AS logicalStoreId, error_type AS errorType, "
            + "owner_user_id AS ownerUserId, project_code AS projectCode, "
            + "last_store_code AS lastStoreCode, "
            + "source_stage AS sourceStage, source_recovery_id AS sourceRecoveryId, "
            + "attempt_count AS attemptCount, "
            + "blocked_until AS blockedUntil, last_failed_at AS lastFailedAt, "
            + "last_success_at AS lastSuccessAt, diagnostic_summary AS diagnosticSummary, "
            + "gmt_create AS createdAt, gmt_updated AS updatedAt";

    @Select({
            "SELECT id",
            "FROM logical_store",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    Long resolveLogicalStoreId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Insert({
            "INSERT INTO logical_store (",
            "  id, owner_user_id, manager_user_id, project_code, project_name, status,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, NULL, #{projectCode}, #{projectCode}, 'ACTIVE',",
            "  b'0', #{ownerUserId}, #{ownerUserId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP",
            ") ON DUPLICATE KEY UPDATE id = id"
    })
    int insertLogicalStoreIfAbsent(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Select({
            "SELECT id",
            "FROM noon_auth_identity_recovery",
            "WHERE id = #{recoveryId}",
            "FOR UPDATE"
    })
    Long lockRecoveryById(@Param("recoveryId") Long recoveryId);

    @Select({
            "SELECT COUNT(1)",
            "FROM noon_auth_identity_recovery",
            "WHERE id = #{fence.recoveryId}",
            "  AND status = #{fence.expectedStatus}",
            "  AND version_no = #{fence.expectedVersion}",
            "  AND lease_token = #{fence.expectedLeaseToken}",
            "  AND lease_until > UTC_TIMESTAMP",
            "  AND active_identity_slot IS NOT NULL"
    })
    int countCurrentRecoveryFence(
            @Param("fence") NoonAuthTransientBackoffWriteFence fence
    );

    @Insert({
            "INSERT INTO noon_auth_transient_backoff_state (",
            "  logical_store_id, error_type, owner_user_id, project_code,",
            "  last_store_code, source_stage, source_recovery_id,",
            "  attempt_count, blocked_until, last_failed_at, diagnostic_summary, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{logicalStoreId}, #{errorType}, #{ownerUserId}, #{projectCode},",
            "  #{lastStoreCode}, #{sourceStage}, #{sourceRecoveryId},",
            "  1, #{blockedUntil}, #{lastFailedAt}, #{diagnosticSummary}, #{createdAt}, #{updatedAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  blocked_until = TIMESTAMPADD(MINUTE,",
            "    CASE",
            "      WHEN attempt_count <= 0 THEN 2",
            "      WHEN attempt_count = 1 THEN 4",
            "      WHEN attempt_count = 2 THEN 8",
            "      ELSE 16",
            "    END,",
            "    GREATEST(last_failed_at, VALUES(last_failed_at))",
            "  ),",
            "  attempt_count = IF(attempt_count < 2147483647, attempt_count + 1, attempt_count),",
            "  owner_user_id = VALUES(owner_user_id),",
            "  project_code = VALUES(project_code),",
            "  last_store_code = VALUES(last_store_code),",
            "  source_stage = VALUES(source_stage),",
            "  source_recovery_id = VALUES(source_recovery_id),",
            "  last_failed_at = GREATEST(last_failed_at, VALUES(last_failed_at)),",
            "  diagnostic_summary = VALUES(diagnostic_summary),",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    int incrementFailure(NoonAuthTransientBackoffState failure);

    @Select({
            "SELECT", COLUMNS,
            "FROM noon_auth_transient_backoff_state",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND error_type = #{errorType}",
            "LIMIT 1"
    })
    NoonAuthTransientBackoffState selectState(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("errorType") NoonTransientErrorType errorType
    );

    @Select({
            "SELECT", COLUMNS,
            "FROM noon_auth_transient_backoff_state",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND attempt_count > 0",
            "  AND blocked_until > #{now}",
            "ORDER BY blocked_until DESC, error_type ASC"
    })
    List<NoonAuthTransientBackoffState> listActiveHolds(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM noon_auth_transient_backoff_state",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND source_recovery_id = #{recoveryId}",
            "  AND attempt_count > 0"
    })
    int countFailuresForRecovery(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("recoveryId") Long recoveryId
    );

    @Update({
            "UPDATE noon_auth_transient_backoff_state",
            "SET attempt_count = 0,",
            "    blocked_until = #{resetAt},",
            "    last_success_at = #{resetAt},",
            "    diagnostic_summary = 'reset after successful project auth validation',",
            "    gmt_updated = #{resetAt}",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND source_recovery_id = #{recoveryId}",
            "  AND attempt_count > 0"
    })
    int resetForRecovery(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("recoveryId") Long recoveryId,
            @Param("resetAt") LocalDateTime resetAt
    );
}
