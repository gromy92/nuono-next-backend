package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullLegacyCutoverRow;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** One read-only snapshot of every retired DP task/waiting-task execution surface. */
public interface DataPullLegacyCutoverMapper {
    String ACTIVE_NOON = "status IN ('QUEUED', 'RUNNING', 'BLOCKED_AUTH')";
    String SUPERSEDABLE_NOON =
            ACTIVE_NOON
            + " AND trigger_mode = 'SCHEDULED_DAILY'"
            + " AND pull_type IN ('INTERFACE', 'REPORT')"
            + " AND data_domain IN ('PRODUCT', 'SALES', 'ORDER', 'FINANCE_TRANSACTION',"
            + " 'NOON_ADVERTISING', 'OFFICIAL_WAREHOUSE_ASN',"
            + " 'OFFICIAL_WAREHOUSE_INVENTORY', 'OFFICIAL_WAREHOUSE_FBN_RECEIVED')"
            + " AND ((status = 'BLOCKED_AUTH' AND retry_action = 'WAIT_FOR_AUTH')"
            + " OR (status = 'QUEUED' AND started_at IS NULL)"
            + " OR (status = 'RUNNING' AND started_at IS NOT NULL"
            + " AND COALESCE(retry_action, '') <> 'WAIT_FOR_AUTH'))"
            + " AND checkpoint_cursor IS NULL AND next_resume_position IS NULL"
            + " AND last_safe_response_summary IS NULL"
            + " AND COALESCE(processed_item_count, 0) = 0"
            + " AND COALESCE(request_count, 0) = 0"
            + " AND finished_at IS NULL";
    String SUPERSEDABLE_DP10 =
            "status = 'running'"
            + " AND COALESCE(processed_count, 0) = 0"
            + " AND COALESCE(imported_count, 0) = 0"
            + " AND COALESCE(failed_count, 0) = 0"
            + " AND COALESCE(progress_percent, 0) = 0"
            + " AND failure_code IS NULL AND failure_message IS NULL"
            + " AND COALESCE(requires_manual_action, b'0') = b'0'"
            + " AND finished_at IS NULL";

    @Select({
            "SELECT 'NOON_PULL' AS recordKind, COUNT(*) AS activeCount,",
            "  COALESCE(SUM(CASE WHEN " + SUPERSEDABLE_NOON,
            "    THEN 1 ELSE 0 END), 0) AS supersedableSnapshotCount",
            "FROM noon_pull_task WHERE " + ACTIVE_NOON,
            "  AND is_deleted = b'0'",
            "UNION ALL",
            "SELECT 'DP05_OPERATIONAL_TASK', COUNT(*), 0",
            "FROM operational_task",
            "WHERE task_type = 'PRODUCT_PUBLIC_DETAIL_SYNC'",
            "  AND status IN ('QUEUED', 'RUNNING') AND is_deleted = b'0'",
            "UNION ALL",
            "SELECT 'DP10_SYNC_TASK', COUNT(*),",
            "  COALESCE(SUM(CASE WHEN " + SUPERSEDABLE_DP10,
            "    THEN 1 ELSE 0 END), 0)",
            "FROM procurement_ali1688_order_sync_task",
            "WHERE status = 'running' AND is_deleted = b'0'",
            "UNION ALL",
            "SELECT 'SALES_SYNC_TASK', COUNT(*), 0",
            "FROM sales_sync_task",
            "WHERE status IN ('queued', 'running', 'waiting_authorization')",
            "UNION ALL",
            "SELECT 'LEGACY_AUTH_WAIT', COUNT(*), 0",
            "FROM noon_auth_identity_recovery_item",
            "WHERE status IN ('PENDING', 'VALIDATING')",
            "  AND source_task_id IS NOT NULL",
            "  AND (source_domain IS NULL OR UPPER(source_domain) IN (",
            "    'NOON_PULL', 'PRODUCT', 'SALES', 'SALES_SYNC', 'ORDER',",
            "    'FINANCE_TRANSACTION', 'NOON_ADVERTISING',",
            "    'OFFICIAL_WAREHOUSE_ASN', 'OFFICIAL_WAREHOUSE_INVENTORY',",
            "    'OFFICIAL_WAREHOUSE_FBN_RECEIVED'",
            "  ))"
    })
    List<DataPullLegacyCutoverRow> selectActiveCohort();
}
