package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullLegacyCutoverRow;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** One read-only snapshot of every retired DP task/waiting-task execution surface. */
public interface DataPullLegacyCutoverMapper {
    String ACTIVE_NOON = "status IN ('QUEUED', 'RUNNING', 'BLOCKED_AUTH')";
    String NEVER_STARTED_SNAPSHOT =
            "status = 'QUEUED'"
            + " AND trigger_mode = 'SCHEDULED_DAILY'"
            + " AND pull_type = 'INTERFACE'"
            + " AND ((data_domain = 'PRODUCT'"
            + " AND target_identity LIKE 'product-list:%')"
            + " OR (data_domain = 'OFFICIAL_WAREHOUSE_INVENTORY'"
            + " AND target_identity LIKE 'official-warehouse-fbn-inventory:%'))"
            + " AND started_at IS NULL AND locked_by IS NULL"
            + " AND source_batch_id IS NULL AND auth_recovery_id IS NULL"
            + " AND checkpoint_cursor IS NULL AND next_resume_position IS NULL"
            + " AND last_safe_response_summary IS NULL"
            + " AND report_export_id IS NULL AND report_download_url IS NULL"
            + " AND report_export_status IS NULL AND report_total_rows IS NULL"
            + " AND report_last_poll_at IS NULL AND report_next_poll_at IS NULL"
            + " AND COALESCE(processed_item_count, 0) = 0"
            + " AND COALESCE(request_count, 0) = 0"
            + " AND COALESCE(report_poll_attempts, 0) = 0"
            + " AND finished_at IS NULL";

    @Select({
            "SELECT 'NOON_PULL' AS recordKind, COUNT(*) AS activeCount,",
            "  COALESCE(SUM(CASE WHEN " + NEVER_STARTED_SNAPSHOT,
            "    THEN 1 ELSE 0 END), 0) AS supersedableSnapshotCount",
            "FROM noon_pull_task WHERE " + ACTIVE_NOON,
            "  AND is_deleted = b'0'",
            "UNION ALL",
            "SELECT 'DP05_OPERATIONAL_TASK', COUNT(*), 0",
            "FROM operational_task",
            "WHERE task_type = 'PRODUCT_PUBLIC_DETAIL_SYNC'",
            "  AND status IN ('QUEUED', 'RUNNING') AND is_deleted = b'0'",
            "UNION ALL",
            "SELECT 'DP10_SYNC_TASK', COUNT(*), 0",
            "FROM procurement_ali1688_order_sync_task",
            "WHERE (status = 'running' OR (status IN ('failed', 'partial_success')",
            "  AND COALESCE(retryable, b'1') = b'1')) AND is_deleted = b'0'",
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
