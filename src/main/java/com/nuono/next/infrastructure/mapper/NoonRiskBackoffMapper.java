package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonpull.NoonRiskBackoffHold;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface NoonRiskBackoffMapper {

    @Insert({
            "INSERT INTO noon_risk_backoff_state (",
            "  scope_key, scope_type, owner_user_id, store_code, site_code, operation_group,",
            "  risk_type, source_domain, source_task_id, blocked_until, attempt_count, diagnostic_summary,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{scopeKey}, #{scopeType}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{operationGroup},",
            "  #{riskType}, #{sourceDomain}, #{sourceTaskId}, #{blockedUntil}, #{attemptCount}, #{diagnosticSummary},",
            "  #{createdAt}, #{updatedAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  scope_type = VALUES(scope_type),",
            "  owner_user_id = VALUES(owner_user_id),",
            "  store_code = VALUES(store_code),",
            "  site_code = VALUES(site_code),",
            "  operation_group = VALUES(operation_group),",
            "  risk_type = VALUES(risk_type),",
            "  source_domain = VALUES(source_domain),",
            "  source_task_id = VALUES(source_task_id),",
            "  blocked_until = VALUES(blocked_until),",
            "  attempt_count = VALUES(attempt_count),",
            "  diagnostic_summary = VALUES(diagnostic_summary),",
            "  is_deleted = b'0',",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    void upsert(NoonRiskBackoffHold hold);

    @Select({
            "SELECT",
            "  scope_key, scope_type, owner_user_id, store_code, site_code, operation_group,",
            "  risk_type, source_domain, source_task_id, blocked_until, attempt_count, diagnostic_summary,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_risk_backoff_state",
            "WHERE scope_key = #{scopeKey}",
            "  AND blocked_until > #{now}",
            "  AND is_deleted = b'0'",
            "ORDER BY blocked_until DESC",
            "LIMIT 1"
    })
    NoonRiskBackoffHold selectActiveHold(@Param("scopeKey") String scopeKey, @Param("now") LocalDateTime now);

    @Select({
            "SELECT",
            "  scope_key, scope_type, owner_user_id, store_code, site_code, operation_group,",
            "  risk_type, source_domain, source_task_id, blocked_until, attempt_count, diagnostic_summary,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_risk_backoff_state",
            "WHERE operation_group = 'NOON'",
            "  AND owner_user_id <=> #{ownerUserId}",
            "  AND store_code <=> #{storeCode}",
            "  AND (#{siteCode} IS NULL OR site_code = #{siteCode} OR site_code IS NULL)",
            "  AND blocked_until > #{now}",
            "  AND is_deleted = b'0'",
            "ORDER BY blocked_until DESC, gmt_updated DESC",
            "LIMIT 1"
    })
    NoonRiskBackoffHold selectActiveAccountWideHold(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT",
            "  scope_key, scope_type, owner_user_id, store_code, site_code, operation_group,",
            "  risk_type, source_domain, source_task_id, blocked_until, attempt_count, diagnostic_summary,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_risk_backoff_state",
            "WHERE scope_key = #{scopeKey}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC",
            "LIMIT 1"
    })
    NoonRiskBackoffHold selectLatestHold(@Param("scopeKey") String scopeKey);

    @Update({
            "UPDATE noon_risk_backoff_state",
            "SET attempt_count = 0,",
            "  blocked_until = #{resetAt},",
            "  diagnostic_summary = 'reset after successful provider call',",
            "  gmt_updated = #{resetAt}",
            "WHERE scope_key = #{scopeKey}",
            "  AND source_domain = #{sourceDomain}",
            "  AND attempt_count > 0",
            "  AND is_deleted = b'0'"
    })
    int resetAfterSuccess(
            @Param("scopeKey") String scopeKey,
            @Param("sourceDomain") String sourceDomain,
            @Param("resetAt") LocalDateTime resetAt
    );
}
