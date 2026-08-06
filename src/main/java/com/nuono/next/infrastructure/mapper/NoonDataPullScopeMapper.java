package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.NoonDataPullScopeRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Scope discovery for every active Noon binding, including temporarily unauthorized projects. */
public interface NoonDataPullScopeMapper {

    @Select({
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  lss.id AS logicalStoreSiteId,",
            "  up.id AS userProjectId,",
            "  us.id AS userStoreId,",
            "  ls.project_code AS projectCode,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode",
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
            "WHERE ls.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            "ORDER BY ls.owner_user_id ASC, ls.id ASC, lss.id ASC, up.id ASC, us.id ASC"
    })
    List<NoonDataPullScopeRow> listActiveBoundScopes();

    @Select({
            "<script>",
            "SELECT",
            "  ls.owner_user_id AS ownerUserId, ls.id AS logicalStoreId,",
            "  lss.id AS logicalStoreSiteId, up.id AS userProjectId, us.id AS userStoreId,",
            "  ls.project_code AS projectCode, lss.store_code AS storeCode, lss.site AS siteCode",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0' AND lss.is_mounted = b'1' AND lss.site_enabled = b'1'",
            "JOIN user_project up ON up.user_id = ls.owner_user_id",
            " AND BINARY up.project_code = BINARY ls.project_code",
            " AND up.is_deleted = 0 AND up.bind_status = 1",
            "JOIN user_store us ON us.user_id = ls.owner_user_id",
            " AND BINARY us.project_code = BINARY ls.project_code",
            " AND BINARY us.store_code = BINARY lss.store_code",
            " AND BINARY us.site = BINARY lss.site AND us.is_deleted = 0",
            "WHERE ls.is_deleted = b'0' AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            "<if test='afterOwnerUserId != null'>",
            " AND (ls.owner_user_id > #{afterOwnerUserId}",
            "   OR (ls.owner_user_id = #{afterOwnerUserId} AND ls.id > #{afterLogicalStoreId})",
            "   OR (ls.owner_user_id = #{afterOwnerUserId} AND ls.id = #{afterLogicalStoreId}",
            "       AND lss.id > #{afterLogicalStoreSiteId})",
            "   OR (ls.owner_user_id = #{afterOwnerUserId} AND ls.id = #{afterLogicalStoreId}",
            "       AND lss.id = #{afterLogicalStoreSiteId} AND up.id > #{afterUserProjectId})",
            "   OR (ls.owner_user_id = #{afterOwnerUserId} AND ls.id = #{afterLogicalStoreId}",
            "       AND lss.id = #{afterLogicalStoreSiteId} AND up.id = #{afterUserProjectId}",
            "       AND us.id > #{afterUserStoreId}))",
            "</if>",
            "ORDER BY ls.owner_user_id, ls.id, lss.id, up.id, us.id",
            "LIMIT #{limit}",
            "</script>"
    })
    List<NoonDataPullScopeRow> listActiveBoundScopesAfter(
            @Param("afterOwnerUserId") Long afterOwnerUserId,
            @Param("afterLogicalStoreId") Long afterLogicalStoreId,
            @Param("afterLogicalStoreSiteId") Long afterLogicalStoreSiteId,
            @Param("afterUserProjectId") Long afterUserProjectId,
            @Param("afterUserStoreId") Long afterUserStoreId,
            @Param("limit") int limit
    );
}
