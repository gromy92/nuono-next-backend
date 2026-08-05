package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.EmergencyClaimHold;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis statements for expiring technical holds on new DP claims. */
public interface DataPullEmergencyClaimHoldMapper {

    @Insert({
            "INSERT INTO dp_pull_emergency_claim_hold (",
            "  hold_key, hold_scope, operation_code, scope_key, blocked_until,",
            "  sanitized_reason, version_no, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{holdKey}, #{holdScope}, #{operationCode}, #{scopeKey}, #{blockedUntil},",
            "  #{sanitizedReason}, 0, #{updatedAt}, #{updatedAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  sanitized_reason = CASE",
            "    WHEN VALUES(blocked_until) >= blocked_until THEN VALUES(sanitized_reason)",
            "    ELSE sanitized_reason",
            "  END,",
            "  hold_scope = VALUES(hold_scope),",
            "  operation_code = VALUES(operation_code),",
            "  scope_key = VALUES(scope_key),",
            "  blocked_until = GREATEST(blocked_until, VALUES(blocked_until)),",
            "  version_no = version_no + 1,",
            "  gmt_updated = GREATEST(gmt_updated, VALUES(gmt_updated))"
    })
    int upsert(EmergencyClaimHold hold);

    @Select({
            "SELECT hold_key AS holdKey, hold_scope AS holdScope,",
            "       operation_code AS operationCode, scope_key AS scopeKey,",
            "       blocked_until AS blockedUntil, sanitized_reason AS sanitizedReason,",
            "       version_no AS version, gmt_updated AS updatedAt",
            "FROM dp_pull_emergency_claim_hold hold",
            "WHERE hold.blocked_until > #{now}",
            "ORDER BY hold.hold_key ASC"
    })
    List<EmergencyClaimHold> selectActive(@Param("now") LocalDateTime nowUtc);
}
