package com.nuono.next.infrastructure.mapper;

import com.nuono.next.foundation.FoundationStats;
import com.nuono.next.foundation.FoundationUserSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FoundationOverviewMapper {

    @Select({
            "SELECT",
            "  (SELECT COUNT(*) FROM role WHERE is_deleted = 0) AS role_count,",
            "  (SELECT COUNT(*) FROM `user` WHERE is_deleted = 0) AS user_count,",
            "  (SELECT COUNT(*) FROM `user` WHERE is_deleted = 0 AND status = 1) AS active_user_count,",
            "  (SELECT COUNT(*) FROM user_store WHERE is_deleted = 0) AS store_link_count,",
            "  (SELECT COUNT(*) FROM user_store WHERE is_deleted = 0 AND is_authorized = 1) AS authorized_store_count,",
            "  (SELECT COUNT(*)",
            "     FROM `user`",
            "    WHERE is_deleted = 0",
            "      AND noon_partner_user IS NOT NULL",
            "      AND LENGTH(TRIM(noon_partner_user)) > 0) AS noon_binding_user_count"
    })
    FoundationStats selectStats();

    @Select({
            "<script>",
            "SELECT",
            "  u.id,",
            "  u.account_no,",
            "  u.real_name,",
            "  u.phone,",
            "  u.account_type,",
            "  u.company_name,",
            "  u.status,",
            "  r.name AS role_name,",
            "  COALESCE(store_stats.store_count, 0) AS store_count,",
            "  COALESCE(store_stats.authorized_store_count, 0) AS authorized_store_count,",
            "  COALESCE(store_stats.sites, '') AS sites,",
            "  CASE",
            "    WHEN u.noon_partner_project_user IS NOT NULL AND LENGTH(TRIM(u.noon_partner_project_user)) > 0 THEN 'PROJECT_BOUND'",
            "    WHEN u.noon_partner_user IS NOT NULL AND LENGTH(TRIM(u.noon_partner_user)) > 0 THEN 'ACCOUNT_ONLY'",
            "    ELSE 'UNBOUND'",
            "  END AS binding_status",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "LEFT JOIN (",
            "  SELECT",
            "    user_id,",
            "    COUNT(*) AS store_count,",
            "    SUM(CASE WHEN is_authorized = 1 THEN 1 ELSE 0 END) AS authorized_store_count,",
            "    GROUP_CONCAT(DISTINCT site ORDER BY site SEPARATOR ', ') AS sites",
            "  FROM user_store",
            "  WHERE is_deleted = 0",
            "  GROUP BY user_id",
            ") store_stats ON store_stats.user_id = u.id",
            "WHERE u.is_deleted = 0",
            "ORDER BY u.gmt_updated DESC, u.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<FoundationUserSnapshot> listSampleUsers(@Param("limit") int limit);
}
