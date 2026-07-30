package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryProjectCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface NoonAuthRecoveryProjectBatchMapper {

    @Select({
            "SELECT",
            "  up.user_id AS ownerUserId,",
            "  up.project_code AS projectCode,",
            "  up.project_code AS storeCode",
            "FROM user_project up",
            "WHERE up.is_deleted = 0",
            "  AND up.bind_status = 1",
            "  AND up.is_authorized = 1",
            "ORDER BY up.user_id ASC, BINARY up.project_code ASC"
    })
    List<NoonAuthRecoveryProjectCandidate> listEligibleIdentityProjects();
}
