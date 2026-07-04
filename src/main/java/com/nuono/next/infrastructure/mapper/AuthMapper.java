package com.nuono.next.infrastructure.mapper;

import com.nuono.next.auth.AuthGrantedMenu;
import com.nuono.next.auth.AuthLoginAccount;
import com.nuono.next.auth.AuthSampleAccount;
import com.nuono.next.auth.AuthUserStore;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AuthMapper {

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  u.password AS stored_password,",
            "  u.real_name,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  u.company_name,",
            "  u.status,",
            "  COALESCE(r.level, u.level) AS level,",
            "  u.effective_time,",
            "  u.expired_time,",
            "  COALESCE(store_stats.store_count, 0) AS store_count,",
            "  COALESCE(store_stats.authorized_store_count, 0) AS authorized_store_count,",
            "  CASE",
            "    WHEN COALESCE(store_stats.bound_store_count, 0) > 0 THEN 'PROJECT_BOUND'",
            "    WHEN COALESCE(store_stats.store_count, 0) > 0 THEN 'ACCOUNT_ONLY'",
            "    ELSE 'UNBOUND'",
            "  END AS binding_status",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "LEFT JOIN (",
            "  SELECT",
            "    visible.scope_user_id AS user_id,",
            "    COUNT(DISTINCT visible.project_id) AS store_count,",
            "    COUNT(DISTINCT CASE WHEN visible.is_authorized = 1 THEN visible.project_id END) AS authorized_store_count,",
            "    COUNT(DISTINCT CASE WHEN visible.bind_status = 1 THEN visible.project_id END) AS bound_store_count",
            "  FROM (",
            "    SELECT up.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status",
            "    FROM user_project up",
            "    WHERE up.is_deleted = 0",
            "    UNION ALL",
            "    SELECT upa.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status",
            "    FROM user_project_access upa",
            "    JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "    WHERE upa.is_deleted = 0",
            "  ) visible",
            "  GROUP BY visible.scope_user_id",
            ") store_stats ON store_stats.user_id = u.id",
            "WHERE u.is_deleted = 0",
            "  AND u.account_no = #{accountNo}",
            "LIMIT 1"
    })
    AuthLoginAccount selectLoginAccount(@Param("accountNo") String accountNo);

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  u.password AS stored_password,",
            "  u.real_name,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  u.company_name,",
            "  u.status,",
            "  COALESCE(r.level, u.level) AS level,",
            "  u.effective_time,",
            "  u.expired_time,",
            "  COALESCE(store_stats.store_count, 0) AS store_count,",
            "  COALESCE(store_stats.authorized_store_count, 0) AS authorized_store_count,",
            "  CASE",
            "    WHEN COALESCE(store_stats.bound_store_count, 0) > 0 THEN 'PROJECT_BOUND'",
            "    WHEN COALESCE(store_stats.store_count, 0) > 0 THEN 'ACCOUNT_ONLY'",
            "    ELSE 'UNBOUND'",
            "  END AS binding_status",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "LEFT JOIN (",
            "  SELECT",
            "    visible.scope_user_id AS user_id,",
            "    COUNT(DISTINCT visible.project_id) AS store_count,",
            "    COUNT(DISTINCT CASE WHEN visible.is_authorized = 1 THEN visible.project_id END) AS authorized_store_count,",
            "    COUNT(DISTINCT CASE WHEN visible.bind_status = 1 THEN visible.project_id END) AS bound_store_count",
            "  FROM (",
            "    SELECT up.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status",
            "    FROM user_project up",
            "    WHERE up.is_deleted = 0",
            "    UNION ALL",
            "    SELECT upa.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status",
            "    FROM user_project_access upa",
            "    JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "    WHERE upa.is_deleted = 0",
            "  ) visible",
            "  GROUP BY visible.scope_user_id",
            ") store_stats ON store_stats.user_id = u.id",
            "WHERE u.is_deleted = 0",
            "  AND LOWER(u.email) = #{email}",
            "LIMIT 1"
    })
    AuthLoginAccount selectLoginAccountByEmail(@Param("email") String email);

    @Select({
            "SELECT",
            "  us.id,",
            "  us.org_code,",
            "  us.org_name,",
            "  us.project_code,",
            "  us.project_name,",
            "  us.store_code,",
            "  us.site,",
            "  visible.is_authorized AS authorized",
            "FROM (",
            "  SELECT up.id AS project_id, up.user_id AS owner_user_id, up.project_code, up.is_authorized",
            "  FROM user_project up",
            "  WHERE up.user_id = #{userId}",
            "    AND up.is_deleted = 0",
            "  UNION",
            "  SELECT up.id AS project_id, up.user_id AS owner_user_id, up.project_code, up.is_authorized",
            "  FROM user_project_access upa",
            "  JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "  WHERE upa.user_id = #{userId}",
            "    AND upa.is_deleted = 0",
            ") visible",
            "JOIN user_store us ON us.user_id = visible.owner_user_id",
            "  AND us.project_code = visible.project_code",
            "  AND us.is_deleted = 0",
            "ORDER BY visible.is_authorized DESC, us.project_code ASC, us.store_code ASC, us.site ASC, us.id ASC"
    })
    List<AuthUserStore> selectUserStores(@Param("userId") Long userId);

    @Select({
            "SELECT",
            "  m.id AS menu_id,",
            "  m.name AS menu_name,",
            "  m.url_path",
            "FROM user_menu um",
            "JOIN menu m ON m.id = um.menu_id AND m.is_deleted = 0",
            "WHERE um.is_deleted = 0",
            "  AND um.user_id = #{userId}",
            "  AND (um.status IS NULL OR um.status = 1)",
            "  AND (um.effective_time IS NULL OR um.effective_time <= NOW())",
            "  AND (um.expired_time IS NULL OR um.expired_time >= NOW())",
            "ORDER BY m.parent_id ASC, m.id ASC"
    })
    List<AuthGrantedMenu> selectGrantedMenus(@Param("userId") Long userId);

    @Update({
            "UPDATE `user`",
            "SET password = #{newPassword},",
            "    updated_by = #{userId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0"
    })
    int updateCurrentUserPassword(
            @Param("userId") Long userId,
            @Param("newPassword") String newPassword
    );

    @Select({
            "SELECT",
            "  u.account_no,",
            "  u.password,",
            "  u.real_name,",
            "  r.name AS role_name",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE u.is_deleted = 0",
            "  AND u.status = 1",
            "  AND NOT (CHAR_LENGTH(COALESCE(u.password, '')) = 32 AND LOWER(u.password) REGEXP '^[0-9a-f]{32}$')",
            "ORDER BY u.id ASC",
            "LIMIT 6"
    })
    List<AuthSampleAccount> listSampleAccounts();
}
