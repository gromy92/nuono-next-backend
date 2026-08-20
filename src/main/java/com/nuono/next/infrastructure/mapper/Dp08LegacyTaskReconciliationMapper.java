package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Read-only cutover snapshot of active legacy DP08 tasks and search runs. */
@Mapper
public interface Dp08LegacyTaskReconciliationMapper {

    @Select({
            "SELECT",
            "  (SELECT COUNT(*) FROM operational_task t",
            "   WHERE t.task_type IN (",
            "     'OPERATIONS_COMPETITOR_REFRESH',",
            "     'OPERATIONS_COMPETITOR_MONITORING',",
            "     'OPERATIONS_COMPETITOR_MONITORING_CYCLE'",
            "   )",
            "     AND t.status IN ('QUEUED', 'RUNNING')",
            "     AND t.is_deleted = b'0')",
            "  +",
            "  (SELECT COUNT(*) FROM operations_competitor_search_run r",
            "   WHERE r.status IN ('QUEUED', 'RUNNING')",
            "     AND r.is_deleted = b'0')"
    })
    int countActiveRows();
}
