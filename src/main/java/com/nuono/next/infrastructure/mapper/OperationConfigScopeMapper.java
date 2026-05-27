package com.nuono.next.infrastructure.mapper;

import com.nuono.next.operationsconfig.OperationConfigBossOption;
import com.nuono.next.operationsconfig.OperationConfigStoreScope;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OperationConfigScopeMapper {

    @ConstructorArgs({
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "displayName", javaType = String.class),
            @Arg(column = "accountNo", javaType = String.class)
    })
    @Select({
            "SELECT",
            "  u.id AS ownerUserId,",
            "  COALESCE(NULLIF(u.real_name, ''), u.account_no) AS displayName,",
            "  u.account_no AS accountNo",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE u.is_deleted = 0",
            "  AND u.status = 1",
            "  AND COALESCE(u.account_type, 'internal') = 'internal'",
            "  AND (u.level = 1 OR r.level = 1 OR r.name = '老板')",
            "ORDER BY displayName ASC, u.id ASC"
    })
    List<OperationConfigBossOption> selectBossOptions();

    @ConstructorArgs({
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "logicalStoreId", javaType = Long.class),
            @Arg(column = "projectCode", javaType = String.class),
            @Arg(column = "projectName", javaType = String.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class)
    })
    @Select({
            "<script>",
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  ls.project_code AS projectCode,",
            "  ls.project_name AS projectName,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = 0",
            "WHERE ls.is_deleted = 0",
            "  AND ls.owner_user_id IN",
            "  <foreach collection='bossUserIds' item='bossUserId' open='(' separator=',' close=')'>",
            "    #{bossUserId}",
            "  </foreach>",
            "ORDER BY ls.owner_user_id ASC, ls.project_code ASC, lss.store_code ASC, lss.site ASC",
            "</script>"
    })
    List<OperationConfigStoreScope> selectStoreSitesByBossUserIds(
            @Param("bossUserIds") Collection<Long> bossUserIds
    );

    @ConstructorArgs({
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "logicalStoreId", javaType = Long.class),
            @Arg(column = "projectCode", javaType = String.class),
            @Arg(column = "projectName", javaType = String.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class)
    })
    @Select({
            "<script>",
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  ls.project_code AS projectCode,",
            "  ls.project_name AS projectName,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id",
            "  AND ls.is_deleted = 0",
            "WHERE lss.is_deleted = 0",
            "  AND lss.store_code IN",
            "  <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "ORDER BY ls.owner_user_id ASC, ls.project_code ASC, lss.store_code ASC, lss.site ASC",
            "</script>"
    })
    List<OperationConfigStoreScope> selectStoreSitesByStoreCodes(
            @Param("storeCodes") Collection<String> storeCodes
    );
}
