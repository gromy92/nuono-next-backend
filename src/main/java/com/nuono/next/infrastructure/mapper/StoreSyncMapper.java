package com.nuono.next.infrastructure.mapper;

import com.nuono.next.store.StoreSyncManagerRow;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncOwnerOption;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface StoreSyncMapper {

    @Select({
            "SELECT",
            "  u.id,",
            "  u.account_no,",
            "  u.real_name,",
            "  r.name AS role_name,",
            "  u.company_name,",
            "  COALESCE(store_stats.store_count, 0) AS store_count,",
            "  COALESCE(store_stats.authorized_store_count, 0) AS authorized_store_count,",
            "  CASE",
            "    WHEN COALESCE(store_stats.bound_store_count, 0) > 0 THEN 'PROJECT_BOUND'",
            "    WHEN COALESCE(store_stats.store_count, 0) > 0 THEN 'ACCOUNT_ONLY'",
            "    ELSE 'UNBOUND'",
            "  END AS binding_status",
            "FROM `user` u",
            "JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "LEFT JOIN (",
            "  SELECT",
            "    user_id,",
            "    COUNT(*) AS store_count,",
            "    SUM(CASE WHEN is_authorized = 1 THEN 1 ELSE 0 END) AS authorized_store_count,",
            "    SUM(CASE WHEN bind_status = 1 THEN 1 ELSE 0 END) AS bound_store_count",
            "  FROM user_project",
            "  WHERE is_deleted = 0",
            "  GROUP BY user_id",
            ") store_stats ON store_stats.user_id = u.id",
            "WHERE u.is_deleted = 0",
            "  AND u.status = 1",
            "  AND r.level = 1",
            "ORDER BY COALESCE(store_stats.store_count, 0) DESC, u.id ASC"
    })
    List<StoreSyncOwnerOption> listOwnerOptions();

    @Select({
            "SELECT",
            "  u.id,",
            "  u.account_no,",
            "  u.real_name,",
            "  r.name AS role_name,",
            "  u.company_name,",
            "  project_credential.noon_partner_user,",
            "  project_credential.noon_partner_project_user,",
            "  project_credential.noon_partner_pwd,",
            "  project_credential.noon_partner_cookie,",
            "  project_credential.cookie_generate_time,",
            "  project_credential.noon_partner_id",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "LEFT JOIN (",
            "  SELECT",
            "    user_id,",
            "    MAX(NULLIF(noon_partner_user, '')) AS noon_partner_user,",
            "    MAX(NULLIF(noon_partner_project_user, '')) AS noon_partner_project_user,",
            "    MAX(NULLIF(noon_partner_pwd, '')) AS noon_partner_pwd,",
            "    MAX(NULLIF(noon_partner_cookie, '')) AS noon_partner_cookie,",
            "    MAX(cookie_generate_time) AS cookie_generate_time,",
            "    MAX(NULLIF(noon_partner_id, '')) AS noon_partner_id",
            "  FROM user_project",
            "  WHERE is_deleted = 0",
            "  GROUP BY user_id",
            ") project_credential ON project_credential.user_id = u.id",
            "WHERE u.id = #{ownerUserId}",
            "  AND u.is_deleted = 0"
    })
    StoreSyncOwnerContext selectOwnerContext(@Param("ownerUserId") Long ownerUserId);

    @Select({
            "SELECT",
            "  id,",
            "  project_name,",
            "  project_code AS store_code,",
            "  NULL AS site,",
            "  is_authorized AS owner_authorized,",
            "  project_code,",
            "  noon_partner_user,",
            "  noon_partner_project_user,",
            "  noon_partner_pwd,",
            "  noon_partner_cookie,",
            "  cookie_generate_time,",
            "  noon_partner_id,",
            "  bind_status",
            "FROM user_project",
            "WHERE user_id = #{ownerUserId}",
            "  AND is_deleted = 0",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<StoreSyncStoreRecord> listOwnerProjects(@Param("ownerUserId") Long ownerUserId);

    @Select({
            "SELECT",
            "  up.id,",
            "  up.project_name,",
            "  up.project_code AS store_code,",
            "  NULL AS site,",
            "  up.is_authorized AS owner_authorized,",
            "  up.project_code,",
            "  up.noon_partner_user,",
            "  up.noon_partner_project_user,",
            "  up.noon_partner_pwd,",
            "  up.noon_partner_cookie,",
            "  up.cookie_generate_time,",
            "  up.noon_partner_id,",
            "  up.bind_status",
            "FROM user_project up",
            "WHERE up.user_id = #{ownerUserId}",
            "  AND up.is_deleted = 0",
            "  AND (",
            "    up.project_code = #{storeCode}",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM user_store us",
            "      WHERE us.user_id = up.user_id",
            "        AND us.project_code = up.project_code",
            "        AND us.store_code = #{storeCode}",
            "        AND us.is_deleted = 0",
            "    )",
            "  )",
            "LIMIT 1"
    })
    StoreSyncStoreRecord selectOwnerProject(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT",
            "  us.id,",
            "  us.project_name,",
            "  us.store_code,",
            "  us.site,",
            "  COALESCE(up.is_authorized, us.is_authorized) AS owner_authorized,",
            "  us.project_code,",
            "  up.noon_partner_user,",
            "  up.noon_partner_project_user,",
            "  up.noon_partner_pwd,",
            "  up.noon_partner_cookie,",
            "  up.cookie_generate_time,",
            "  up.noon_partner_id,",
            "  up.bind_status",
            "FROM user_store us",
            "LEFT JOIN user_project up ON up.user_id = us.user_id",
            "  AND up.project_code = us.project_code",
            "  AND up.is_deleted = 0",
            "WHERE us.user_id = #{ownerUserId}",
            "  AND us.is_deleted = 0",
            "ORDER BY us.project_code ASC, us.store_code ASC, us.site ASC, us.id ASC"
    })
    List<StoreSyncStoreRecord> listOwnerStores(@Param("ownerUserId") Long ownerUserId);

    @Select({
            "SELECT",
            "    us.id,",
            "    us.project_name,",
            "    us.store_code,",
            "    us.site,",
            "    COALESCE(up.is_authorized, us.is_authorized) AS owner_authorized,",
            "    us.project_code,",
            "    up.noon_partner_user,",
            "    up.noon_partner_project_user,",
            "    up.noon_partner_pwd,",
            "    up.noon_partner_cookie,",
            "    up.cookie_generate_time,",
            "    up.noon_partner_id,",
            "    up.bind_status",
            "  FROM user_store us",
            "  LEFT JOIN user_project up ON up.user_id = us.user_id",
            "    AND BINARY up.project_code = BINARY us.project_code",
            "    AND up.is_deleted = 0",
            "  WHERE us.user_id = #{ownerUserId}",
            "    AND (",
            "      BINARY us.store_code = BINARY #{storeCode}",
            "      OR BINARY us.project_code = BINARY #{storeCode}",
            "    )",
            "    AND us.is_deleted = 0",
            "ORDER BY us.store_code ASC, us.site ASC, us.id ASC",
            "LIMIT 1"
    })
    StoreSyncStoreRecord selectOwnerStore(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT up.user_id",
            "FROM user_store us",
            "JOIN user_project up ON BINARY up.project_code = BINARY us.project_code",
            "  AND up.is_deleted = 0",
            "WHERE us.user_id = #{userId}",
            "  AND us.is_deleted = 0",
            "  AND COALESCE(us.is_authorized, b'0') = b'1'",
            "  AND COALESCE(up.is_authorized, b'0') = b'1'",
            "  AND (",
            "    BINARY us.store_code = BINARY #{storeCode}",
            "    OR BINARY us.project_code = BINARY #{storeCode}",
            "  )",
            "ORDER BY CASE WHEN up.user_id = #{userId} THEN 0 ELSE 1 END, up.user_id ASC",
            "LIMIT 1"
    })
    Long selectAccessibleOwnerUserIdForStore(
            @Param("userId") Long userId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT",
            "  lss.id,",
            "  ls.project_name,",
            "  lss.store_code,",
            "  lss.site,",
            "  COALESCE(up.is_authorized, b'1') AS owner_authorized,",
            "  ls.project_code,",
            "  up.noon_partner_user,",
            "  up.noon_partner_project_user,",
            "  up.noon_partner_pwd,",
            "  up.noon_partner_cookie,",
            "  up.cookie_generate_time,",
            "  up.noon_partner_id,",
            "  up.bind_status",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = 0",
            "LEFT JOIN user_project up ON up.user_id = ls.owner_user_id",
            "  AND BINARY up.project_code = BINARY ls.project_code",
            "  AND up.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (",
            "    BINARY lss.store_code = BINARY #{storeCode}",
            "    OR BINARY ls.project_code = BINARY #{storeCode}",
            "  )",
            "ORDER BY",
            "  CASE",
            "    WHEN BINARY lss.store_code = BINARY #{storeCode} THEN 0",
            "    WHEN lss.is_reference_site = b'1' THEN 1",
            "    ELSE 2",
            "  END ASC,",
            "  lss.store_code ASC, lss.site ASC, lss.id ASC",
            "LIMIT 1"
    })
    StoreSyncStoreRecord selectOwnerProjectionStore(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "<script>",
            "SELECT",
            "  us.id,",
            "  us.project_name,",
            "  us.store_code,",
            "  us.site,",
            "  up.is_authorized AS owner_authorized,",
            "  us.project_code,",
            "  up.noon_partner_user,",
            "  up.noon_partner_project_user,",
            "  up.noon_partner_pwd,",
            "  up.noon_partner_cookie,",
            "  up.cookie_generate_time,",
            "  up.noon_partner_id,",
            "  up.bind_status",
            "FROM user_project up",
            "JOIN user_store us ON us.user_id = up.user_id",
            "  AND us.project_code = up.project_code",
            "  AND us.is_deleted = 0",
            "WHERE up.user_id = #{ownerUserId}",
            "  AND up.is_deleted = 0",
            "  AND up.project_code IN",
            "  <foreach item='projectCode' collection='projectCodes' open='(' separator=',' close=')'>",
            "    #{projectCode}",
            "  </foreach>",
            "ORDER BY us.project_code ASC, us.store_code ASC, us.site ASC, us.id ASC",
            "</script>"
    })
    List<StoreSyncStoreRecord> listOwnerProjectSites(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCodes") List<String> projectCodes
    );

    @Select({
            "<script>",
            "SELECT",
            "  lss.id,",
            "  ls.project_name,",
            "  lss.store_code,",
            "  lss.site,",
            "  COALESCE(up.is_authorized, b'1') AS owner_authorized,",
            "  ls.project_code,",
            "  up.noon_partner_user,",
            "  up.noon_partner_project_user,",
            "  up.noon_partner_pwd,",
            "  up.noon_partner_cookie,",
            "  up.cookie_generate_time,",
            "  up.noon_partner_id,",
            "  up.bind_status",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = 0",
            "LEFT JOIN user_project up ON up.user_id = ls.owner_user_id",
            "  AND BINARY up.project_code = BINARY ls.project_code",
            "  AND up.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (",
            "    <foreach item='projectCode' collection='projectCodes' separator=' OR '>",
            "      BINARY ls.project_code = BINARY #{projectCode}",
            "    </foreach>",
            "  )",
            "ORDER BY ls.project_code ASC, lss.is_reference_site DESC, lss.store_code ASC, lss.site ASC, lss.id ASC",
            "</script>"
    })
    List<StoreSyncStoreRecord> listOwnerProjectionProjectSites(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCodes") List<String> projectCodes
    );

    @Select({
            "<script>",
            "SELECT",
            "  up.project_code AS store_code,",
            "  u.id,",
            "  COALESCE(NULLIF(TRIM(u.real_name), ''), u.account_no) AS name,",
            "  r.name AS role",
            "FROM user_project_access upa",
            "JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "JOIN `user` u ON u.id = upa.user_id AND u.is_deleted = 0 AND u.status = 1",
            "JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE upa.is_deleted = 0",
            "  AND up.user_id = #{ownerUserId}",
            "  AND upa.user_id &lt;&gt; #{ownerUserId}",
            "  AND COALESCE(u.account_type, 'internal') = 'internal'",
            "  AND r.level &gt;= 2",
            "  AND up.project_code IN",
            "  <foreach item='projectCode' collection='projectCodes' open='(' separator=',' close=')'>",
            "    #{projectCode}",
            "  </foreach>",
            "ORDER BY up.project_code ASC, r.level ASC, CASE WHEN u.account_type = 'internal' THEN 0 ELSE 1 END ASC, u.id ASC",
            "</script>"
    })
    List<StoreSyncManagerRow> listManagersByProjectCodes(
            @Param("projectCodes") List<String> projectCodes,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "<script>",
            "SELECT",
            "  us.store_code,",
            "  u.id,",
            "  COALESCE(NULLIF(TRIM(u.real_name), ''), u.account_no) AS name,",
            "  r.name AS role",
            "FROM user_project_access upa",
            "JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0",
            "JOIN user_store us ON us.user_id = up.user_id AND us.project_code = up.project_code AND us.is_deleted = 0",
            "JOIN `user` u ON u.id = upa.user_id AND u.is_deleted = 0 AND u.status = 1",
            "JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE upa.is_deleted = 0",
            "  AND up.user_id = #{ownerUserId}",
            "  AND upa.user_id &lt;&gt; #{ownerUserId}",
            "  AND COALESCE(u.account_type, 'internal') = 'internal'",
            "  AND r.level &gt;= 2",
            "  AND us.store_code IN",
            "  <foreach item='storeCode' collection='storeCodes' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "ORDER BY us.store_code ASC, r.level ASC, CASE WHEN u.account_type = 'internal' THEN 0 ELSE 1 END ASC, u.id ASC",
            "</script>"
    })
    List<StoreSyncManagerRow> listManagersByStoreCodes(
            @Param("storeCodes") List<String> storeCodes,
            @Param("ownerUserId") Long ownerUserId
    );

    @Update({
            "UPDATE user_project",
            "SET noon_partner_user = #{noonUser},",
            "    noon_partner_project_user = #{noonProjectUser},",
            "    noon_partner_pwd = #{noonPassword},",
            "    noon_partner_id = #{noonPartnerId},",
            "    bind_status = 1,",
            "    is_authorized = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{projectId}",
            "  AND user_id = #{ownerUserId}",
            "  AND is_deleted = 0"
    })
    int updateProjectBinding(
            @Param("projectId") Long projectId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("noonUser") String noonUser,
            @Param("noonProjectUser") String noonProjectUser,
            @Param("noonPassword") String noonPassword,
            @Param("noonPartnerId") String noonPartnerId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE user_project",
            "SET noon_partner_cookie = #{cookie},",
            "    cookie_generate_time = NOW(),",
            "    bind_status = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{projectId}",
            "  AND user_id = #{ownerUserId}",
            "  AND is_deleted = 0"
    })
    int updateProjectConnectionSuccess(
            @Param("projectId") Long projectId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("cookie") String cookie,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE user_project",
            "SET bind_status = 0,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{projectId}",
            "  AND user_id = #{ownerUserId}",
            "  AND is_deleted = 0"
    })
    int updateProjectConnectionFailure(
            @Param("projectId") Long projectId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE user_project",
            "SET noon_partner_cookie = #{cookie},",
            "    cookie_generate_time = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE user_id = #{ownerUserId}",
            "  AND project_code = #{projectCode}",
            "  AND is_deleted = 0",
            "  AND bind_status = 1"
    })
    int updateProjectSessionCookie(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("cookie") String cookie,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE user_project",
            "SET is_authorized = #{authorized},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{projectId}",
            "  AND user_id = #{ownerUserId}",
            "  AND is_deleted = 0"
    })
    int updateProjectAuthorization(
            @Param("projectId") Long projectId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorized") boolean authorized,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT COALESCE(MAX(id), 30000) + 1 FROM user_store"
    })
    Long nextStoreId();

    @Insert({
            "INSERT INTO user_project (",
            "  user_id, org_code, org_name, project_code, project_name,",
            "  noon_partner_user, noon_partner_project_user, noon_partner_pwd, noon_partner_id,",
            "  bind_status, is_authorized, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{ownerUserId}, #{orgCode}, #{orgName}, #{projectCode}, #{projectName},",
            "  #{noonUser}, #{noonProjectUser}, #{noonPassword}, #{noonPartnerId},",
            "  #{bound}, #{authorized}, 0, #{ownerUserId}, #{ownerUserId}, NOW(), NOW()",
            ")"
    })
    int insertOwnerProject(
            @Param("ownerUserId") Long ownerUserId,
            @Param("orgCode") String orgCode,
            @Param("orgName") String orgName,
            @Param("projectCode") String projectCode,
            @Param("projectName") String projectName,
            @Param("noonUser") String noonUser,
            @Param("noonProjectUser") String noonProjectUser,
            @Param("noonPassword") String noonPassword,
            @Param("noonPartnerId") String noonPartnerId,
            @Param("bound") boolean bound,
            @Param("authorized") boolean authorized
    );

    @Insert({
            "INSERT INTO user_store (",
            "  id, user_id, org_code, org_name, project_code, project_name, store_code, site,",
            "  is_authorized, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{orgCode}, #{orgName}, #{projectCode}, #{projectName}, #{storeCode}, #{site},",
            "  #{authorized}, 0, #{ownerUserId}, #{ownerUserId}, NOW(), NOW()",
            ")"
    })
    int insertOwnerSiteStore(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("orgCode") String orgCode,
            @Param("orgName") String orgName,
            @Param("projectCode") String projectCode,
            @Param("projectName") String projectName,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("authorized") boolean authorized
    );

    @Insert({
            "INSERT INTO user_store (",
            "  id, user_id, org_code, org_name, project_code, project_name, store_code, site,",
            "  is_authorized, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{orgCode}, #{orgName}, #{projectCode}, #{projectName}, #{storeCode}, #{site},",
            "  #{authorized}, 0, #{ownerUserId}, #{ownerUserId}, NOW(), NOW()",
            ")"
    })
    int insertOwnerStore(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("orgCode") String orgCode,
            @Param("orgName") String orgName,
            @Param("projectCode") String projectCode,
            @Param("projectName") String projectName,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("authorized") boolean authorized
    );

    @Update({
            "UPDATE user_project",
            "SET noon_partner_user = #{noonUser},",
            "    noon_partner_project_user = #{noonProjectUser},",
            "    noon_partner_pwd = #{noonPassword},",
            "    noon_partner_id = #{noonPartnerId},",
            "    bind_status = 1,",
            "    is_authorized = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE user_id = #{ownerUserId}",
            "  AND is_deleted = 0"
    })
    int updateOwnerBinding(
            @Param("ownerUserId") Long ownerUserId,
            @Param("noonUser") String noonUser,
            @Param("noonProjectUser") String noonProjectUser,
            @Param("noonPassword") String noonPassword,
            @Param("noonPartnerId") String noonPartnerId,
            @Param("updatedBy") Long updatedBy
    );
}
