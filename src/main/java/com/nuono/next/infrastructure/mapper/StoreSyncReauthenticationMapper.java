package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface StoreSyncReauthenticationMapper {

    @Update({
            "UPDATE user_project",
            "SET noon_partner_user_code = COALESCE(NULLIF(#{noonUserCode}, ''), noon_partner_user_code),",
            "    noon_partner_cookie = #{cookie},",
            "    cookie_generate_time = NOW(),",
            "    bind_status = 1,",
            "    is_authorized = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{projectId}",
            "  AND user_id = #{ownerUserId}",
            "  AND is_deleted = 0"
    })
    int updateProjectReauthenticationSuccess(
            @Param("projectId") Long projectId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("noonUserCode") String noonUserCode,
            @Param("cookie") String cookie,
            @Param("updatedBy") Long updatedBy
    );
}
