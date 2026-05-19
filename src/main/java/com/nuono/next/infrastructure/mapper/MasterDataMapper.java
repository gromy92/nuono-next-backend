package com.nuono.next.infrastructure.mapper;

import com.nuono.next.masterdata.MasterDataMenuView;
import com.nuono.next.masterdata.MasterDataPaymentRecordView;
import com.nuono.next.masterdata.MasterDataOrgUserRow;
import com.nuono.next.masterdata.MasterDataRoleAssignmentSeed;
import com.nuono.next.masterdata.MasterDataRoleMenuRow;
import com.nuono.next.masterdata.MasterDataRoleView;
import com.nuono.next.masterdata.MasterDataStoreSeed;
import com.nuono.next.masterdata.MasterDataUserView;
import com.nuono.next.foundation.FoundationUserStoreLink;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface MasterDataMapper {

    @Select({
            "SELECT",
            "  u.id,",
            "  u.account_no,",
            "  u.real_name,",
            "  u.phone,",
            "  u.email,",
            "  u.company_name,",
            "  u.account_type,",
            "  u.status,",
            "  u.list_limit,",
            "  u.collect_limit,",
            "  u.wh_ap_limit,",
            "  u.chatgpt_translate_limit,",
            "  u.effective_time,",
            "  u.expired_time,",
            "  u.gmt_create AS created_at,",
            "  u.gmt_updated AS updated_at,",
            "  u.created_by,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  r.level AS role_level,",
            "  COALESCE(store_stats.store_count, 0) AS store_count,",
            "  COALESCE(store_stats.authorized_store_count, 0) AS authorized_store_count,",
            "  COALESCE(store_stats.sites, '') AS sites,",
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
            "    COUNT(DISTINCT CASE WHEN visible.bind_status = 1 THEN visible.project_id END) AS bound_store_count,",
            "    GROUP_CONCAT(DISTINCT visible.site ORDER BY visible.site SEPARATOR ', ') AS sites",
            "  FROM (",
            "    SELECT up.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status, us.site",
            "    FROM user_project up",
            "    LEFT JOIN user_store us ON us.user_id = up.user_id AND us.project_code = up.project_code AND us.is_deleted = 0",
            "    WHERE up.is_deleted = 0",
            "    UNION ALL",
            "    SELECT upa.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status, us.site",
            "    FROM user_project_access upa",
            "    JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "    LEFT JOIN user_store us ON us.user_id = up.user_id AND us.project_code = up.project_code AND us.is_deleted = 0",
            "    WHERE upa.is_deleted = 0",
            "  ) visible",
            "  GROUP BY visible.scope_user_id",
            ") store_stats ON store_stats.user_id = u.id",
            "WHERE u.is_deleted = 0",
            "ORDER BY r.level ASC, u.id ASC"
    })
    List<MasterDataUserView> listUsers();

    @Select({
            "SELECT",
            "  u.id,",
            "  u.account_no,",
            "  u.real_name,",
            "  u.phone,",
            "  u.email,",
            "  u.company_name,",
            "  u.account_type,",
            "  u.status,",
            "  u.list_limit,",
            "  u.collect_limit,",
            "  u.wh_ap_limit,",
            "  u.chatgpt_translate_limit,",
            "  u.effective_time,",
            "  u.expired_time,",
            "  u.gmt_create AS created_at,",
            "  u.gmt_updated AS updated_at,",
            "  u.created_by,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  r.level AS role_level,",
            "  COALESCE(store_stats.store_count, 0) AS store_count,",
            "  COALESCE(store_stats.authorized_store_count, 0) AS authorized_store_count,",
            "  COALESCE(store_stats.sites, '') AS sites,",
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
            "    COUNT(DISTINCT CASE WHEN visible.bind_status = 1 THEN visible.project_id END) AS bound_store_count,",
            "    GROUP_CONCAT(DISTINCT visible.site ORDER BY visible.site SEPARATOR ', ') AS sites",
            "  FROM (",
            "    SELECT up.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status, us.site",
            "    FROM user_project up",
            "    LEFT JOIN user_store us ON us.user_id = up.user_id AND us.project_code = up.project_code AND us.is_deleted = 0",
            "    WHERE up.is_deleted = 0",
            "    UNION ALL",
            "    SELECT upa.user_id AS scope_user_id, up.id AS project_id, up.is_authorized, up.bind_status, us.site",
            "    FROM user_project_access upa",
            "    JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "    LEFT JOIN user_store us ON us.user_id = up.user_id AND us.project_code = up.project_code AND us.is_deleted = 0",
            "    WHERE upa.is_deleted = 0",
            "  ) visible",
            "  GROUP BY visible.scope_user_id",
            ") store_stats ON store_stats.user_id = u.id",
            "WHERE u.is_deleted = 0",
            "  AND u.id = #{userId}",
            "LIMIT 1"
    })
    MasterDataUserView selectUserView(@Param("userId") Long userId);

    @Select({
            "SELECT",
            "  r.id,",
            "  r.name,",
            "  r.code,",
            "  r.description,",
            "  r.is_system AS system_role,",
            "  r.parent_id,",
            "  r.level,",
            "  COALESCE(user_stats.assigned_user_count, 0) AS assigned_user_count,",
            "  COALESCE(menu_stats.menu_count, 0) AS menu_count",
            "FROM role r",
            "LEFT JOIN (",
            "  SELECT role_id, COUNT(*) AS assigned_user_count",
            "  FROM `user`",
            "  WHERE is_deleted = 0",
            "  GROUP BY role_id",
            ") user_stats ON user_stats.role_id = r.id",
            "LEFT JOIN (",
            "  SELECT role_id, COUNT(*) AS menu_count",
            "  FROM role_menu",
            "  WHERE is_deleted = 0",
            "  GROUP BY role_id",
            ") menu_stats ON menu_stats.role_id = r.id",
            "WHERE r.is_deleted = 0",
            "ORDER BY r.level ASC, r.id ASC"
    })
    List<MasterDataRoleView> listRoles();

    @Select({
            "SELECT role_id, menu_id",
            "FROM role_menu",
            "WHERE is_deleted = 0",
            "ORDER BY role_id ASC, menu_id ASC"
    })
    List<MasterDataRoleMenuRow> listRoleMenuRows();

    @Select({
            "SELECT",
            "  id,",
            "  name,",
            "  parent_id,",
            "  url_path",
            "FROM menu",
            "WHERE is_deleted = 0",
            "ORDER BY parent_id ASC, id ASC"
    })
    List<MasterDataMenuView> listMenus();

    @Select({
            "SELECT id, name, code, level",
            "FROM role",
            "WHERE id = #{roleId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    MasterDataRoleAssignmentSeed selectRoleSeed(@Param("roleId") Long roleId);

    @Select({
            "SELECT id, account_no AS name",
            "FROM `user`",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    MasterDataRoleAssignmentSeed selectUserSeed(@Param("userId") Long userId);

    @Select({
            "SELECT COUNT(*)",
            "FROM `user`",
            "WHERE account_no = #{accountNo}",
            "  AND is_deleted = 0",
            "  AND (#{excludeUserId} IS NULL OR id <> #{excludeUserId})"
    })
    int countUsersByAccountNo(@Param("accountNo") String accountNo, @Param("excludeUserId") Long excludeUserId);

    @Select({
            "SELECT menu_id",
            "FROM role_menu",
            "WHERE role_id = #{roleId}",
            "  AND is_deleted = 0",
            "ORDER BY menu_id ASC"
    })
    List<Long> listRoleMenuIds(@Param("roleId") Long roleId);

    @Select({
            "SELECT expired_time",
            "FROM `user`",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    LocalDateTime selectUserExpiredTime(@Param("userId") Long userId);

    @Select({
            "SELECT COALESCE(MAX(id), 0) + 1 FROM `user`"
    })
    Long nextUserId();

    @Select({
            "SELECT COALESCE(MAX(id), 0) + 1 FROM user_menu"
    })
    Long nextUserMenuId();

    @Select({
            "SELECT COALESCE(MAX(id), 0) + 1 FROM role"
    })
    Long nextRoleId();

    @Select({
            "SELECT COALESCE(MAX(id), 0) + 1 FROM role_menu"
    })
    Long nextRoleMenuId();

    @Select({
            "SELECT COALESCE(MAX(id), 0) + 1 FROM menu"
    })
    Long nextMenuId();

    @Select({
            "SELECT COALESCE(MAX(id), 30000) + 1 FROM user_project_access"
    })
    Long nextProjectAccessId();

    @Select({
            "SELECT COALESCE(MAX(id), 0) + 1 FROM user_store"
    })
    Long nextUserStoreId();

    @Select({
            "SELECT COALESCE(MAX(id), 50000) + 1 FROM merchant_payment"
    })
    Long nextMerchantPaymentId();

    @Select({
            "SELECT",
            "  id,",
            "  name,",
            "  code,",
            "  description,",
            "  is_system AS system_role,",
            "  parent_id,",
            "  level",
            "FROM role",
            "WHERE id = #{roleId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    MasterDataRoleView selectRoleView(@Param("roleId") Long roleId);

    @Select({
            "SELECT COUNT(*)",
            "FROM `user`",
            "WHERE role_id = #{roleId}",
            "  AND is_deleted = 0"
    })
    int countUsersByRoleId(@Param("roleId") Long roleId);

    @Select({
            "SELECT",
            "  id,",
            "  account_no,",
            "  effective_time,",
            "  expired_time,",
            "  role_id",
            "FROM `user`",
            "WHERE role_id = #{roleId}",
            "  AND is_deleted = 0",
            "ORDER BY id ASC"
    })
    List<MasterDataUserView> listUsersByRoleId(@Param("roleId") Long roleId);

    @Insert({
            "INSERT INTO role (",
            "  id, name, code, description, is_system, parent_id, level, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{name}, #{code}, #{description}, #{systemRole}, #{parentId}, #{level}, 0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertRole(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("code") String code,
            @Param("description") String description,
            @Param("systemRole") boolean systemRole,
            @Param("parentId") Long parentId,
            @Param("level") Integer level,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE role",
            "SET name = #{name},",
            "    description = #{description},",
            "    parent_id = #{parentId},",
            "    level = #{level},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{roleId}",
            "  AND is_deleted = 0"
    })
    int updateRole(
            @Param("roleId") Long roleId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("parentId") Long parentId,
            @Param("level") Integer level,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE role",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{roleId}",
            "  AND is_deleted = 0"
    })
    int softDeleteRole(@Param("roleId") Long roleId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE role_menu",
            "SET is_deleted = 1",
            "WHERE role_id = #{roleId}",
            "  AND is_deleted = 0"
    })
    int softDeleteRoleMenusByRoleId(@Param("roleId") Long roleId);

    @Insert({
            "INSERT INTO role_menu (id, role_id, menu_id, is_deleted)",
            "VALUES (#{id}, #{roleId}, #{menuId}, 0)"
    })
    int insertRoleMenu(
            @Param("id") Long id,
            @Param("roleId") Long roleId,
            @Param("menuId") Long menuId
    );

    @Select({
            "SELECT",
            "  id,",
            "  name,",
            "  parent_id,",
            "  url_path",
            "FROM menu",
            "WHERE id = #{menuId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    MasterDataMenuView selectMenuView(@Param("menuId") Long menuId);

    @Select({
            "<script>",
            "SELECT DISTINCT",
            "  up.id,",
            "  up.org_code,",
            "  up.org_name,",
            "  up.project_code,",
            "  up.project_name,",
            "  up.project_code AS store_code,",
            "  NULL AS site,",
            "  up.is_authorized AS authorized",
            "FROM user_project up",
            "WHERE up.is_deleted = 0",
            "  AND up.project_code IN",
            "  <foreach item='storeCode' collection='storeCodes' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "UNION",
            "SELECT DISTINCT",
            "  up.id,",
            "  up.org_code,",
            "  up.org_name,",
            "  up.project_code,",
            "  up.project_name,",
            "  us.store_code AS store_code,",
            "  us.site AS site,",
            "  COALESCE(us.is_authorized, up.is_authorized) AS authorized",
            "FROM user_project up",
            "JOIN user_store us ON us.user_id = up.user_id",
            "  AND us.project_code = up.project_code",
            "  AND us.is_deleted = 0",
            "WHERE up.is_deleted = 0",
            "  AND us.store_code IN",
            "  <foreach item='storeCode' collection='storeCodes' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "ORDER BY authorized DESC, id ASC, store_code ASC",
            "</script>"
    })
    List<MasterDataStoreSeed> listStoreSeedsByCodes(@Param("storeCodes") List<String> storeCodes);

    @Select({
            "<script>",
            "SELECT DISTINCT",
            "  up.id,",
            "  up.org_code,",
            "  up.org_name,",
            "  up.project_code,",
            "  up.project_name,",
            "  up.project_code AS store_code,",
            "  NULL AS site,",
            "  up.is_authorized AS authorized",
            "FROM user_project up",
            "JOIN (",
            "  SELECT id AS project_id",
            "  FROM user_project",
            "  WHERE user_id = #{operatorUserId}",
            "    AND is_deleted = 0",
            "  UNION",
            "  SELECT up_scope.id AS project_id",
            "  FROM user_project_access upa_scope",
            "  JOIN user_project up_scope ON up_scope.id = upa_scope.project_id AND up_scope.is_deleted = 0",
            "  WHERE upa_scope.user_id = #{operatorUserId}",
            "    AND upa_scope.is_deleted = 0",
            ") operator_scope ON operator_scope.project_id = up.id",
            "WHERE up.is_deleted = 0",
            "  AND up.project_code IN",
            "  <foreach item='storeCode' collection='storeCodes' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "UNION",
            "SELECT DISTINCT",
            "  up.id,",
            "  up.org_code,",
            "  up.org_name,",
            "  up.project_code,",
            "  up.project_name,",
            "  us.store_code AS store_code,",
            "  us.site AS site,",
            "  COALESCE(us.is_authorized, up.is_authorized) AS authorized",
            "FROM user_project up",
            "JOIN (",
            "  SELECT id AS project_id",
            "  FROM user_project",
            "  WHERE user_id = #{operatorUserId}",
            "    AND is_deleted = 0",
            "  UNION",
            "  SELECT up_scope.id AS project_id",
            "  FROM user_project_access upa_scope",
            "  JOIN user_project up_scope ON up_scope.id = upa_scope.project_id AND up_scope.is_deleted = 0",
            "  WHERE upa_scope.user_id = #{operatorUserId}",
            "    AND upa_scope.is_deleted = 0",
            ") operator_scope ON operator_scope.project_id = up.id",
            "JOIN user_store us ON us.user_id = up.user_id",
            "  AND us.project_code = up.project_code",
            "  AND us.is_deleted = 0",
            "WHERE up.is_deleted = 0",
            "  AND us.store_code IN",
            "  <foreach item='storeCode' collection='storeCodes' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "ORDER BY authorized DESC, id ASC, store_code ASC",
            "</script>"
    })
    List<MasterDataStoreSeed> listStoreSeedsByCodesForOperator(
            @Param("storeCodes") List<String> storeCodes,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT",
            "  u.id,",
            "  u.status,",
            "  u.account_no,",
            "  u.real_name,",
            "  r.name AS role_name,",
            "  r.level AS role_level,",
            "  u.created_by,",
            "  u.company_name,",
            "  u.account_type,",
            "  COALESCE(store_stats.store_summary, '') AS store_summary,",
            "  '' AS manager_ids",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "LEFT JOIN (",
            "  SELECT",
            "    visible.scope_user_id AS user_id,",
            "    GROUP_CONCAT(DISTINCT COALESCE(NULLIF(visible.project_name, ''), NULLIF(visible.project_code, ''))",
            "      ORDER BY COALESCE(NULLIF(visible.project_name, ''), NULLIF(visible.project_code, '')) SEPARATOR ', ') AS store_summary",
            "  FROM (",
            "    SELECT up.user_id AS scope_user_id, up.project_code, up.project_name",
            "    FROM user_project up",
            "    WHERE up.is_deleted = 0",
            "    UNION ALL",
            "    SELECT upa.user_id AS scope_user_id, up.project_code, up.project_name",
            "    FROM user_project_access upa",
            "    JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "    WHERE upa.is_deleted = 0",
            "  ) visible",
            "  GROUP BY visible.scope_user_id",
            ") store_stats ON store_stats.user_id = u.id",
            "WHERE u.is_deleted = 0",
            "ORDER BY COALESCE(r.level, 99) ASC, u.id ASC"
    })
    List<MasterDataOrgUserRow> listOrgUsers();

    @Insert({
            "INSERT INTO menu (",
            "  id, name, parent_id, url_path, is_deleted, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{name}, #{parentId}, #{urlPath}, 0, NOW(), NOW()",
            ")"
    })
    int insertMenu(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("parentId") Long parentId,
            @Param("urlPath") String urlPath
    );

    @Insert({
            "INSERT INTO `user` (",
            "  id, phone, email, account_no, password, role, role_id, account_type, real_name, company_name, level,",
            "  status, effective_time, expired_time, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{phone}, #{email}, #{accountNo}, #{password}, #{roleCode}, #{roleId}, #{accountType}, #{realName}, #{companyName}, #{level},",
            "  #{status}, NOW(), #{expiredTime}, 0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertUser(
            @Param("id") Long id,
            @Param("phone") String phone,
            @Param("email") String email,
            @Param("accountNo") String accountNo,
            @Param("password") String password,
            @Param("roleCode") String roleCode,
            @Param("roleId") Long roleId,
            @Param("accountType") String accountType,
            @Param("realName") String realName,
            @Param("companyName") String companyName,
            @Param("level") Integer level,
            @Param("status") Integer status,
            @Param("expiredTime") LocalDateTime expiredTime,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE menu",
            "SET name = #{name},",
            "    parent_id = #{parentId},",
            "    url_path = #{urlPath},",
            "    gmt_updated = NOW()",
            "WHERE id = #{menuId}",
            "  AND is_deleted = 0"
    })
    int updateMenu(
            @Param("menuId") Long menuId,
            @Param("name") String name,
            @Param("parentId") Long parentId,
            @Param("urlPath") String urlPath
    );

    @Update({
            "UPDATE `user`",
            "SET real_name = #{realName},",
            "    phone = #{phone},",
            "    email = #{email},",
            "    account_type = #{accountType},",
            "    company_name = #{companyName},",
            "    expired_time = #{expiredTime},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0"
    })
    int updateUserProfile(
            @Param("userId") Long userId,
            @Param("realName") String realName,
            @Param("phone") String phone,
            @Param("email") String email,
            @Param("accountType") String accountType,
            @Param("companyName") String companyName,
            @Param("expiredTime") LocalDateTime expiredTime,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE menu",
            "SET is_deleted = 1,",
            "    gmt_updated = NOW()",
            "WHERE id = #{menuId}",
            "  AND is_deleted = 0"
    })
    int softDeleteMenu(@Param("menuId") Long menuId);

    @Update({
            "UPDATE role_menu",
            "SET is_deleted = 1",
            "WHERE menu_id = #{menuId}",
            "  AND is_deleted = 0"
    })
    int softDeleteRoleMenusByMenuId(@Param("menuId") Long menuId);

    @Update({
            "UPDATE `user`",
            "SET role_id = #{roleId},",
            "    `role` = #{roleCode},",
            "    `level` = #{level},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0"
    })
    int updateUserRole(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("roleCode") String roleCode,
            @Param("level") Integer level,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE `user`",
            "SET status = #{status},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0"
    })
    int updateUserStatus(
            @Param("userId") Long userId,
            @Param("status") Integer status,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE `user`",
            "SET password = #{password},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0"
    })
    int updateUserPassword(
            @Param("userId") Long userId,
            @Param("password") String password,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE `user`",
            "SET list_limit = #{listLimit},",
            "    collect_limit = #{collectLimit},",
            "    wh_ap_limit = #{whApLimit},",
            "    chatgpt_translate_limit = #{chatgptTranslateLimit},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{userId}",
            "  AND is_deleted = 0"
    })
    int updateUserQuota(
            @Param("userId") Long userId,
            @Param("listLimit") Integer listLimit,
            @Param("collectLimit") Integer collectLimit,
            @Param("whApLimit") Integer whApLimit,
            @Param("chatgptTranslateLimit") Integer chatgptTranslateLimit,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE user_project",
            "SET list_limit = #{listLimit},",
            "    collect_limit = #{collectLimit},",
            "    wh_ap_limit = #{whApLimit},",
            "    chatgpt_translate_limit = #{chatgptTranslateLimit},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{projectId}",
            "  AND is_deleted = 0"
    })
    int updateProjectQuota(
            @Param("projectId") Long projectId,
            @Param("listLimit") Integer listLimit,
            @Param("collectLimit") Integer collectLimit,
            @Param("whApLimit") Integer whApLimit,
            @Param("chatgptTranslateLimit") Integer chatgptTranslateLimit,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE user_menu",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE user_id = #{userId}",
            "  AND is_deleted = 0"
    })
    int softDeleteUserMenus(@Param("userId") Long userId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE user_menu",
            "SET expired_time = #{expiredTime},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE user_id = #{userId}",
            "  AND is_deleted = 0"
    })
    int updateUserMenuExpiredTime(
            @Param("userId") Long userId,
            @Param("expiredTime") LocalDateTime expiredTime,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE user_menu um",
            "JOIN `user` u ON u.id = um.user_id AND u.is_deleted = 0",
            "SET um.is_deleted = 1,",
            "    um.updated_by = #{updatedBy},",
            "    um.gmt_updated = NOW()",
            "WHERE u.role_id = #{roleId}",
            "  AND um.is_deleted = 0"
    })
    int softDeleteUserMenusByRoleId(@Param("roleId") Long roleId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE user_menu",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE menu_id = #{menuId}",
            "  AND is_deleted = 0"
    })
    int softDeleteUserMenusByMenuId(@Param("menuId") Long menuId, @Param("updatedBy") Long updatedBy);

    @Delete({
            "DELETE FROM user_project_access",
            "WHERE user_id = #{userId}",
            "  AND is_deleted <> 0"
    })
    int hardDeleteInactiveUserStores(@Param("userId") Long userId);

    @Update({
            "UPDATE user_project_access",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE user_id = #{userId}",
            "  AND is_deleted = 0"
    })
    int softDeleteUserStores(@Param("userId") Long userId, @Param("updatedBy") Long updatedBy);

    @Delete({
            "DELETE upa",
            "FROM user_project_access upa",
            "JOIN (",
            "  SELECT id AS project_id",
            "  FROM user_project",
            "  WHERE user_id = #{operatorUserId}",
            "    AND is_deleted = 0",
            "  UNION",
            "  SELECT up_scope.id AS project_id",
            "  FROM user_project_access upa_scope",
            "  JOIN user_project up_scope ON up_scope.id = upa_scope.project_id AND up_scope.is_deleted = 0",
            "  WHERE upa_scope.user_id = #{operatorUserId}",
            "    AND upa_scope.is_deleted = 0",
            ") operator_scope ON operator_scope.project_id = upa.project_id",
            "WHERE upa.user_id = #{userId}",
            "  AND upa.is_deleted <> 0"
    })
    int hardDeleteInactiveUserStoresForOperator(
            @Param("userId") Long userId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE user_project_access upa",
            "JOIN (",
            "  SELECT id AS project_id",
            "  FROM user_project",
            "  WHERE user_id = #{operatorUserId}",
            "    AND is_deleted = 0",
            "  UNION",
            "  SELECT up_scope.id AS project_id",
            "  FROM user_project_access upa_scope",
            "  JOIN user_project up_scope ON up_scope.id = upa_scope.project_id AND up_scope.is_deleted = 0",
            "  WHERE upa_scope.user_id = #{operatorUserId}",
            "    AND upa_scope.is_deleted = 0",
            ") operator_scope ON operator_scope.project_id = upa.project_id",
            "SET upa.is_deleted = 1,",
            "    upa.updated_by = #{updatedBy},",
            "    upa.gmt_updated = NOW()",
            "WHERE upa.user_id = #{userId}",
            "  AND upa.is_deleted = 0"
    })
    int softDeleteUserStoresForOperator(
            @Param("userId") Long userId,
            @Param("operatorUserId") Long operatorUserId,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO user_menu (",
            "  id, user_id, menu_id, status, effective_time, expired_time, is_deleted, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{userId}, #{menuId}, 1, #{effectiveTime}, #{expiredTime}, 0, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertUserMenu(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("menuId") Long menuId,
            @Param("effectiveTime") LocalDateTime effectiveTime,
            @Param("expiredTime") LocalDateTime expiredTime,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO user_project_access (",
            "  id, user_id, project_id, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{userId}, #{projectId}, 0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertUserProjectAccess(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO user_store (",
            "  id, user_id, org_code, org_name, project_code, project_name, store_code, site, is_authorized,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{userId}, #{orgCode}, #{orgName}, #{projectCode}, #{projectName}, #{storeCode}, #{site}, #{authorized},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertUserStore(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("orgCode") String orgCode,
            @Param("orgName") String orgName,
            @Param("projectCode") String projectCode,
            @Param("projectName") String projectName,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("authorized") boolean authorized,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT project_code",
            "FROM user_project",
            "WHERE user_id = #{userId}",
            "  AND is_deleted = 0",
            "UNION",
            "SELECT up.project_code",
            "FROM user_project_access upa",
            "JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "WHERE upa.user_id = #{userId}",
            "  AND upa.is_deleted = 0",
            "ORDER BY project_code ASC"
    })
    List<String> listUserStoreCodes(@Param("userId") Long userId);

    @Select({
            "SELECT",
            "  scoped.user_id AS userId,",
            "  up.id,",
            "  up.org_code AS orgCode,",
            "  up.org_name AS orgName,",
            "  up.project_code AS projectCode,",
            "  up.project_name AS projectName,",
            "  up.project_code AS storeCode,",
            "  GROUP_CONCAT(DISTINCT us.site ORDER BY us.site SEPARATOR ', ') AS site,",
            "  up.is_authorized AS authorized,",
            "  up.bind_status AS bindStatus,",
            "  up.noon_partner_user AS noonPartnerUser,",
            "  up.noon_partner_project_user AS noonPartnerProjectUser,",
            "  up.noon_partner_id AS noonPartnerId,",
            "  up.list_limit AS listLimit,",
            "  up.collect_limit AS collectLimit,",
            "  up.wh_ap_limit AS whApLimit,",
            "  up.chatgpt_translate_limit AS chatgptTranslateLimit",
            "FROM (",
            "  SELECT up_owner.user_id, up_owner.id AS project_id",
            "  FROM user_project up_owner",
            "  WHERE up_owner.user_id = #{userId}",
            "    AND up_owner.id = #{projectId}",
            "    AND up_owner.is_deleted = 0",
            "  UNION",
            "  SELECT upa.user_id, upa.project_id",
            "  FROM user_project_access upa",
            "  JOIN user_project up_access ON up_access.id = upa.project_id AND up_access.is_deleted = 0",
            "  WHERE upa.user_id = #{userId}",
            "    AND upa.project_id = #{projectId}",
            "    AND upa.is_deleted = 0",
            ") scoped",
            "JOIN user_project up ON up.id = scoped.project_id AND up.is_deleted = 0",
            "LEFT JOIN user_store us ON us.user_id = up.user_id",
            "  AND us.project_code = up.project_code",
            "  AND us.is_deleted = 0",
            "GROUP BY scoped.user_id, up.id, up.org_code, up.org_name, up.project_code, up.project_name, up.is_authorized,",
            "  up.bind_status, up.noon_partner_user, up.noon_partner_project_user, up.noon_partner_id,",
            "  up.list_limit, up.collect_limit, up.wh_ap_limit, up.chatgpt_translate_limit",
            "LIMIT 1"
    })
    FoundationUserStoreLink selectUserStoreLink(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId
    );

    @Select({
            "SELECT DISTINCT upa.user_id",
            "FROM user_project owner_project",
            "JOIN user_project_access upa ON upa.project_id = owner_project.id AND upa.is_deleted = 0",
            "WHERE owner_project.user_id = #{ownerUserId}",
            "  AND owner_project.is_deleted = 0"
    })
    List<Long> listProjectAuthorizedUserIds(@Param("ownerUserId") Long ownerUserId);

    @Select({
            "SELECT",
            "  scoped.user_id AS userId,",
            "  up.id,",
            "  up.org_code AS orgCode,",
            "  up.org_name AS orgName,",
            "  up.project_code AS projectCode,",
            "  up.project_name AS projectName,",
            "  up.project_code AS storeCode,",
            "  GROUP_CONCAT(DISTINCT us.site ORDER BY us.site SEPARATOR ', ') AS site,",
            "  up.is_authorized AS authorized,",
            "  up.bind_status AS bindStatus,",
            "  up.noon_partner_user AS noonPartnerUser,",
            "  up.noon_partner_project_user AS noonPartnerProjectUser,",
            "  up.noon_partner_id AS noonPartnerId,",
            "  up.list_limit AS listLimit,",
            "  up.collect_limit AS collectLimit,",
            "  up.wh_ap_limit AS whApLimit,",
            "  up.chatgpt_translate_limit AS chatgptTranslateLimit",
            "FROM (",
            "  SELECT up_owner.user_id, up_owner.id AS project_id",
            "  FROM user_project up_owner",
            "  JOIN (",
            "    SELECT id AS project_id",
            "    FROM user_project",
            "    WHERE user_id = #{operatorUserId}",
            "      AND is_deleted = 0",
            "    UNION",
            "    SELECT up_access.id AS project_id",
            "    FROM user_project_access upa_scope",
            "    JOIN user_project up_access ON up_access.id = upa_scope.project_id AND up_access.is_deleted = 0",
            "    WHERE upa_scope.user_id = #{operatorUserId}",
            "      AND upa_scope.is_deleted = 0",
            "  ) operator_scope ON operator_scope.project_id = up_owner.id",
            "  WHERE up_owner.is_deleted = 0",
            "  UNION",
            "  SELECT upa.user_id, upa.project_id",
            "  FROM user_project_access upa",
            "  JOIN user_project up_access ON up_access.id = upa.project_id AND up_access.is_deleted = 0",
            "  JOIN (",
            "    SELECT id AS project_id",
            "    FROM user_project",
            "    WHERE user_id = #{operatorUserId}",
            "      AND is_deleted = 0",
            "    UNION",
            "    SELECT up_scope.id AS project_id",
            "    FROM user_project_access upa_scope",
            "    JOIN user_project up_scope ON up_scope.id = upa_scope.project_id AND up_scope.is_deleted = 0",
            "    WHERE upa_scope.user_id = #{operatorUserId}",
            "      AND upa_scope.is_deleted = 0",
            "  ) operator_scope ON operator_scope.project_id = up_access.id",
            "  WHERE upa.is_deleted = 0",
            ") scoped",
            "JOIN user_project up ON up.id = scoped.project_id AND up.is_deleted = 0",
            "LEFT JOIN user_store us ON us.user_id = up.user_id",
            "  AND us.project_code = up.project_code",
            "  AND us.is_deleted = 0",
            "GROUP BY scoped.user_id, up.id, up.org_code, up.org_name, up.project_code, up.project_name, up.is_authorized,",
            "  up.bind_status, up.noon_partner_user, up.noon_partner_project_user, up.noon_partner_id,",
            "  up.list_limit, up.collect_limit, up.wh_ap_limit, up.chatgpt_translate_limit",
            "ORDER BY scoped.user_id ASC, up.project_code ASC, up.id ASC"
    })
    List<FoundationUserStoreLink> listOperatorScopedStoreLinks(@Param("operatorUserId") Long operatorUserId);

    @Select({
            "SELECT",
            "  id,",
            "  merchant_user_id,",
            "  amount,",
            "  payment_date,",
            "  remark,",
            "  gmt_create AS created_at",
            "FROM merchant_payment",
            "WHERE merchant_user_id = #{userId}",
            "  AND is_deleted = 0",
            "ORDER BY payment_date DESC, id DESC"
    })
    List<MasterDataPaymentRecordView> listMerchantPayments(@Param("userId") Long userId);

    @Insert({
            "INSERT INTO merchant_payment (",
            "  id, merchant_user_id, amount, payment_date, remark, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{merchantUserId}, #{amount}, #{paymentDate}, #{remark}, 0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertMerchantPayment(
            @Param("id") Long id,
            @Param("merchantUserId") Long merchantUserId,
            @Param("amount") BigDecimal amount,
            @Param("paymentDate") LocalDate paymentDate,
            @Param("remark") String remark,
            @Param("updatedBy") Long updatedBy
    );
}
