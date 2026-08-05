package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Exact active store-site guard used by the DP-04 existing-scope projection path. */
public interface Dp04ProductScopeMapper {

    @Select({
            "SELECT lss.id",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            " AND lss.is_mounted = b'1'",
            " AND lss.site_enabled = b'1'",
            "JOIN user_project up",
            "  ON up.user_id = ls.owner_user_id",
            " AND BINARY up.project_code = BINARY ls.project_code",
            " AND up.is_deleted = 0",
            " AND up.bind_status = 1",
            "JOIN user_store us",
            "  ON us.user_id = ls.owner_user_id",
            " AND BINARY us.project_code = BINARY ls.project_code",
            " AND BINARY us.store_code = BINARY lss.store_code",
            " AND BINARY us.site = BINARY lss.site",
            " AND us.is_deleted = 0",
            "WHERE ls.id = #{logicalStoreId}",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND BINARY ls.project_code = BINARY #{projectCode}",
            "  AND BINARY lss.store_code = BINARY #{storeCode}",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  AND ls.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            "LIMIT 1"
    })
    Long selectActiveBoundLogicalStoreSiteId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );
}
