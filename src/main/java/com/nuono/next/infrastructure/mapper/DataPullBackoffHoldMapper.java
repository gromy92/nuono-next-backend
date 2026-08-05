package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullBackoffHoldRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis statements for durable provider backoff holds. */
public interface DataPullBackoffHoldMapper {

    @Insert({
            "INSERT INTO dp_pull_backoff_hold (",
            "  hold_key, share_level, provider_channel, account_key, operation_code, scope_key,",
            "  egress_key, blocked_until, sanitized_code, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{holdKey}, #{shareLevel}, #{providerChannel}, #{accountKey}, #{operationCode},",
            "  #{scopeKey}, #{egressKey}, #{blockedUntil}, #{sanitizedCode}, #{observedAt}, #{observedAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  sanitized_code = CASE",
            "    WHEN VALUES(blocked_until) >= blocked_until THEN VALUES(sanitized_code)",
            "    ELSE sanitized_code",
            "  END,",
            "  blocked_until = GREATEST(blocked_until, VALUES(blocked_until)),",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    int upsert(DataPullBackoffHoldRow row);

    @Select({
            "SELECT blocked_until",
            "FROM dp_pull_backoff_hold",
            "WHERE hold_key = #{holdKey}",
            "LIMIT 1"
    })
    LocalDateTime selectBlockedUntil(@Param("holdKey") String holdKey);
}
