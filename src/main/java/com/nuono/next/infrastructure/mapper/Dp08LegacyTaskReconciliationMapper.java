package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.Dp08LegacyTaskReconciliationRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Read-only cutover snapshot of active legacy DP08 tasks and search runs. */
@Mapper
public interface Dp08LegacyTaskReconciliationMapper {

    @Select({
            "SELECT 'TASK' AS recordKind, t.id AS recordId,",
            "  CAST(NULL AS SIGNED) AS taskId, t.task_type AS taskType,",
            "  t.owner_user_id AS ownerUserId, t.store_code AS storeCode,",
            "  t.site_code AS siteCode, t.natural_key AS naturalKey,",
            "  t.status, t.payload_json AS payloadJson,",
            "  CAST(NULL AS SIGNED) AS watchProductId,",
            "  CAST(NULL AS CHAR(32)) AS triggerMode",
            "FROM operational_task t",
            "WHERE t.task_type IN (",
            "  'OPERATIONS_COMPETITOR_REFRESH',",
            "  'OPERATIONS_COMPETITOR_MONITORING',",
            "  'OPERATIONS_COMPETITOR_MONITORING_CYCLE'",
            ")",
            "  AND t.status IN ('QUEUED', 'RUNNING')",
            "  AND t.is_deleted = b'0'",
            "UNION ALL",
            "SELECT 'RUN' AS recordKind, r.id AS recordId, r.task_id AS taskId,",
            "  CAST(NULL AS CHAR(64)) AS taskType,",
            "  CAST(NULL AS SIGNED) AS ownerUserId,",
            "  CAST(NULL AS CHAR(100)) AS storeCode,",
            "  CAST(NULL AS CHAR(32)) AS siteCode,",
            "  CAST(NULL AS CHAR(255)) AS naturalKey,",
            "  r.status, CAST(NULL AS CHAR) AS payloadJson,",
            "  r.watch_product_id AS watchProductId, r.trigger_mode AS triggerMode",
            "FROM operations_competitor_search_run r",
            "WHERE r.status IN ('QUEUED', 'RUNNING')",
            "  AND r.is_deleted = b'0'",
            "ORDER BY recordKind, recordId"
    })
    List<Dp08LegacyTaskReconciliationRow> listActiveRows();
}
