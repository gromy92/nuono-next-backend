package com.nuono.next.permission.access;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BusinessAccessMapper {

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  u.real_name,",
            "  COALESCE(u.account_type, 'internal') AS account_type,",
            "  u.created_by,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  r.code AS role_code,",
            "  u.level AS user_level,",
            "  r.level AS role_level,",
            "  u.status",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE u.id = #{userId}",
            "  AND u.is_deleted = 0",
            "LIMIT 1"
    })
    BusinessUserAccessRow selectUserAccess(@Param("userId") Long userId);

    @Select({
            "SELECT DISTINCT m.url_path",
            "FROM user_menu um",
            "JOIN menu m ON m.id = um.menu_id AND m.is_deleted = 0",
            "WHERE um.user_id = #{userId}",
            "  AND um.is_deleted = 0",
            "  AND (um.status IS NULL OR um.status = 1)",
            "  AND (um.effective_time IS NULL OR um.effective_time <= NOW())",
            "  AND (um.expired_time IS NULL OR um.expired_time >= NOW())",
            "  AND m.url_path IS NOT NULL",
            "  AND m.url_path <> ''"
    })
    List<String> selectGrantedMenuPaths(@Param("userId") Long userId);

    @Select({
            "SELECT DISTINCT scope.owner_user_id, scope.store_code",
            "FROM (",
            "  SELECT up.user_id AS owner_user_id, CONVERT(up.project_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code",
            "  FROM user_project up",
            "  WHERE up.user_id = #{userId}",
            "    AND up.is_deleted = 0",
            "  UNION ALL",
            "  SELECT up.user_id AS owner_user_id, CONVERT(up.project_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code",
            "  FROM user_project_access upa",
            "  JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "  WHERE upa.user_id = #{userId}",
            "    AND upa.is_deleted = 0",
            "  UNION ALL",
            "  SELECT up.user_id AS owner_user_id, CONVERT(us.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code",
            "  FROM user_project up",
            "  JOIN user_store us ON us.user_id = up.user_id",
            "    AND us.project_code = up.project_code",
            "    AND us.is_deleted = 0",
            "  WHERE up.user_id = #{userId}",
            "    AND up.is_deleted = 0",
            "  UNION ALL",
            "  SELECT up.user_id AS owner_user_id, CONVERT(us.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code",
            "  FROM user_project_access upa",
            "  JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "  JOIN user_store us ON us.user_id = up.user_id",
            "    AND us.project_code = up.project_code",
            "    AND us.is_deleted = 0",
            "  WHERE upa.user_id = #{userId}",
            "    AND upa.is_deleted = 0",
            "  UNION ALL",
            "  SELECT ls.owner_user_id AS owner_user_id, CONVERT(lss.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code",
            "  FROM logical_store ls",
            "  JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "    AND lss.is_deleted = 0",
            "  WHERE ls.owner_user_id = #{userId}",
            "    AND ls.is_deleted = 0",
            "  UNION ALL",
            "  SELECT up.user_id AS owner_user_id, CONVERT(lss.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code",
            "  FROM user_project_access upa",
            "  JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "  JOIN logical_store ls ON ls.owner_user_id = up.user_id",
            "    AND BINARY ls.project_code = BINARY up.project_code",
            "    AND ls.is_deleted = 0",
            "  JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "    AND lss.is_deleted = 0",
            "  WHERE upa.user_id = #{userId}",
            "    AND upa.is_deleted = 0",
            ") scope",
            "WHERE scope.store_code IS NOT NULL",
            "  AND scope.store_code <> ''",
            "ORDER BY scope.owner_user_id ASC, scope.store_code ASC"
    })
    List<BusinessStoreScopeRow> selectStoreScope(@Param("userId") Long userId);
}
