package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10TaskFenceRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** DP-10-only scope and transaction guards; target discovery deliberately has no business LIMIT. */
@Mapper
public interface Ali1688Dp10RuntimeMapper {

    @Select({
            "SELECT id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at,",
            "  created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE provider_code = 'ALI1688_OPEN_API'",
            "  AND status = 'authorized'",
            "  AND revoked_at IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY owner_user_id ASC, id ASC"
    })
    List<Ali1688HistoricalOrderAuthorizationRow> listEffectiveOpenApiAuthorizations();

    @Select({
            "<script>",
            "SELECT id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at,",
            "  created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE provider_code = 'ALI1688_OPEN_API' AND status = 'authorized'",
            "  AND revoked_at IS NULL AND is_deleted = b'0'",
            "<if test='afterOwnerUserId != null'>",
            "  AND (owner_user_id > #{afterOwnerUserId}",
            "       OR (owner_user_id = #{afterOwnerUserId} AND id > #{afterAuthorizationId}))",
            "</if>",
            "ORDER BY owner_user_id ASC, id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<Ali1688HistoricalOrderAuthorizationRow> listEffectiveOpenApiAuthorizationsAfter(
            @Param("afterOwnerUserId") Long afterOwnerUserId,
            @Param("afterAuthorizationId") Long afterAuthorizationId,
            @Param("limit") int limit
    );

    @Select({
            "SELECT id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at,",
            "  created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND provider_code = 'ALI1688_OPEN_API'",
            "  AND provider_account_id = #{providerAccountId}",
            "  AND status = 'authorized'",
            "  AND revoked_at IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderAuthorizationRow selectEffectiveOpenApiAuthorization(
            @Param("ownerUserId") Long ownerUserId,
            @Param("providerAccountId") String providerAccountId
    );

    @Select({
            "SELECT id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at,",
            "  created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND provider_code = 'ALI1688_OPEN_API'",
            "  AND provider_account_id = #{providerAccountId}",
            "  AND status = 'authorized'",
            "  AND revoked_at IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1 FOR UPDATE"
    })
    Ali1688HistoricalOrderAuthorizationRow lockEffectiveOpenApiAuthorization(
            @Param("ownerUserId") Long ownerUserId,
            @Param("providerAccountId") String providerAccountId
    );

    @Select({
            "SELECT id, operation_code AS operationCode, owner_user_id AS ownerUserId,",
            "  account_key AS accountKey, scope_key AS scopeKey, state,",
            "  lease_owner AS leaseOwner, lease_until AS leaseUntil,",
            "  fence_epoch AS fenceEpoch, version_no AS version",
            "FROM dp_pull_task",
            "WHERE id = #{taskId}",
            "  AND operation_code = 'DP10'",
            "  AND state = 'RUNNING'",
            "  AND lease_until > UTC_TIMESTAMP(6)",
            "LIMIT 1 FOR UPDATE"
    })
    Ali1688Dp10TaskFenceRow lockTask(@Param("taskId") Long taskId);
}
