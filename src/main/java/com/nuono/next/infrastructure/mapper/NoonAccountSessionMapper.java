package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noon.NoonAccountSessionProjectTarget;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Persistence for the one configured Noon account; it never stores an OTP or identity grant. */
public interface NoonAccountSessionMapper {
    @Select({
            "SELECT up.user_id AS ownerUserId, up.project_code AS projectCode,",
            "       up.noon_partner_cookie AS sessionCookie,",
            "       MIN(us.store_code) AS storeCode",
            "FROM user_project up",
            "JOIN user_store us ON us.user_id = up.user_id",
            "  AND BINARY us.project_code = BINARY up.project_code",
            "  AND us.is_deleted = 0 AND COALESCE(us.is_authorized, 0) = 1",
            "WHERE up.is_deleted = 0",
            "  AND up.bind_status = 1",
            "  AND COALESCE(up.is_authorized, 0) = 1",
            "  AND NULLIF(TRIM(up.project_code), '') IS NOT NULL",
            "GROUP BY up.user_id, up.project_code",
            "ORDER BY up.user_id ASC, up.project_code ASC"
    })
    List<NoonAccountSessionProjectTarget> listBoundProjects();

    @Update({
            "UPDATE user_project",
            "SET noon_partner_cookie = #{cookie},",
            "    noon_partner_user_code = #{userCode},",
            "    cookie_generate_time = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "  AND is_deleted = 0",
            "  AND bind_status = 1",
            "  AND COALESCE(is_authorized, 0) = 1"
    })
    int persistProjectSession(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("cookie") String cookie,
            @Param("userCode") String userCode,
            @Param("updatedBy") Long updatedBy
    );
}
