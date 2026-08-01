package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryProjectCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface NoonAuthRecoveryProjectBatchMapper {

    @Select({
            "SELECT",
            "  up.user_id AS ownerUserId,",
            "  up.project_code AS projectCode,",
            "  us.store_code AS storeCode,",
            "  us.site AS siteCode",
            "FROM user_project up",
            "LEFT JOIN user_store us ON us.user_id = up.user_id",
            "  AND BINARY us.project_code = BINARY up.project_code",
            "  AND us.is_deleted = 0",
            "LEFT JOIN logical_store ls ON ls.owner_user_id = up.user_id",
            "  AND BINARY ls.project_code = BINARY up.project_code",
            "  AND ls.is_deleted = b'0'",
            "LEFT JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "  AND BINARY lss.store_code = BINARY us.store_code",
            "  AND BINARY lss.site = BINARY us.site",
            "  AND lss.is_deleted = b'0'",
            "WHERE up.is_deleted = 0",
            "  AND up.bind_status = 1",
            "  AND up.is_authorized = 1",
            "ORDER BY up.user_id ASC, BINARY up.project_code ASC,",
            "  CASE WHEN lss.is_reference_site = b'1' THEN 0 ELSE 1 END ASC,",
            "  BINARY us.store_code ASC, BINARY us.site ASC, us.id ASC"
    })
    List<NoonAuthRecoveryProjectCandidate> listEligibleIdentityProjects();
}
