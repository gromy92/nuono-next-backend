package com.nuono.next.infrastructure.mapper;

import com.nuono.next.mobile.MobileStoreRecord;
import com.nuono.next.mobile.MobileUserRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MobileMapper {

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  u.phone,",
            "  u.real_name,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  u.status,",
            "  u.company_name",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE u.id = #{userId}",
            "  AND u.is_deleted = 0",
            "LIMIT 1"
    })
    MobileUserRecord selectUserById(@Param("userId") Long userId);

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  u.phone,",
            "  u.real_name,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  u.status,",
            "  u.company_name",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE u.is_deleted = 0",
            "  AND (u.phone = #{phoneOrAccount} OR u.account_no = #{phoneOrAccount})",
            "ORDER BY CASE WHEN u.phone = #{phoneOrAccount} THEN 0 ELSE 1 END, u.id ASC",
            "LIMIT 1"
    })
    MobileUserRecord selectUserByPhoneOrAccountNo(@Param("phoneOrAccount") String phoneOrAccount);

    @Select({
            "SELECT",
            "  us.id,",
            "  us.user_id,",
            "  us.store_code,",
            "  us.project_name,",
            "  us.project_code,",
            "  us.site,",
            "  us.is_authorized AS authorized",
            "FROM (",
            "  SELECT",
            "    owner_site.id,",
            "    #{userId} AS user_id,",
            "    owner_site.store_code,",
            "    owner_site.project_name,",
            "    owner_site.project_code,",
            "    owner_site.site,",
            "    owner_site.is_authorized,",
            "    0 AS scope_sort",
            "  FROM user_project owner_project",
            "  JOIN user_store owner_site",
            "    ON owner_site.user_id = owner_project.user_id",
            "   AND owner_site.project_code = owner_project.project_code",
            "   AND owner_site.is_deleted = 0",
            "  WHERE owner_project.user_id = #{userId}",
            "    AND owner_project.is_deleted = 0",
            "  UNION ALL",
            "  SELECT",
            "    member_site.id,",
            "    #{userId} AS user_id,",
            "    member_site.store_code,",
            "    member_site.project_name,",
            "    member_site.project_code,",
            "    member_site.site,",
            "    member_site.is_authorized,",
            "    1 AS scope_sort",
            "  FROM user_project_access access",
            "  JOIN user_project member_project",
            "    ON member_project.id = access.project_id",
            "   AND member_project.is_deleted = 0",
            "  JOIN user_store member_site",
            "    ON member_site.user_id = member_project.user_id",
            "   AND member_site.project_code = member_project.project_code",
            "   AND member_site.is_deleted = 0",
            "  WHERE access.user_id = #{userId}",
            "    AND access.is_deleted = 0",
            ") us",
            "ORDER BY us.is_authorized DESC, us.scope_sort ASC, us.project_name ASC, us.store_code ASC"
    })
    List<MobileStoreRecord> listStoresByUserId(@Param("userId") Long userId);

    @Select({
            "SELECT us.user_id",
            "FROM user_project up",
            "JOIN user_store us",
            "  ON us.user_id = up.user_id",
            " AND us.project_code = up.project_code",
            " AND us.is_deleted = 0",
            "JOIN `user` u ON u.id = up.user_id AND u.is_deleted = 0 AND u.status = 1",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE us.store_code = #{storeCode}",
            "  AND up.is_deleted = 0",
            "ORDER BY CASE WHEN COALESCE(u.account_type, '') = 'internal' THEN 0 ELSE 1 END,",
            "  COALESCE(r.level, 99) ASC, us.is_authorized DESC, us.gmt_updated DESC, us.id DESC",
            "LIMIT 1"
    })
    Long selectOwnerUserIdByStoreCode(@Param("storeCode") String storeCode);
}
