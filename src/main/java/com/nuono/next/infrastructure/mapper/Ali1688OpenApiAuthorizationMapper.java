package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/** Writes refreshed 1688 OpenAPI credentials without coupling the HTTP adapter to the order mapper. */
@Mapper
public interface Ali1688OpenApiAuthorizationMapper {

    @Update({
            "UPDATE procurement_ali1688_order_authorization",
            "SET provider_account_id = #{providerAccountId},",
            "    account_label = #{accountLabel},",
            "    status = #{status},",
            "    scope_summary = #{scopeSummary},",
            "    access_token_cipher = #{accessTokenCipher},",
            "    refresh_token_cipher = #{refreshTokenCipher},",
            "    expires_at = #{expiresAt},",
            "    revoked_at = NULL,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateAuthorizationTokens(Ali1688HistoricalOrderAuthorizationRow row);
}
