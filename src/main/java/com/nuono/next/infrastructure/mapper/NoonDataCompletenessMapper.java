package com.nuono.next.infrastructure.mapper;

import com.nuono.next.nooncompleteness.NoonDataCompletenessAuditScope;
import com.nuono.next.nooncompleteness.NoonDataCompletenessAuditScopeSource;
import com.nuono.next.nooncompleteness.NoonDataCompletenessQuery;
import com.nuono.next.nooncompleteness.NoonDataCompletenessRecord;
import com.nuono.next.nooncompleteness.NoonDataCategory;
import com.nuono.next.nooncompleteness.NoonDataGapQuery;
import com.nuono.next.nooncompleteness.NoonDataGapWindowRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonDataCompletenessMapper extends NoonDataCompletenessAuditScopeSource {

    @Select({
            "SELECT DISTINCT",
            "  ls.owner_user_id AS ownerUserId,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            "WHERE ls.is_deleted = 0",
            "  AND ls.owner_user_id IS NOT NULL",
            "  AND lss.store_code IS NOT NULL",
            "  AND lss.store_code <> ''",
            "  AND lss.site IS NOT NULL",
            "  AND lss.site <> ''",
            "ORDER BY ls.owner_user_id ASC, lss.store_code ASC, lss.site ASC"
    })
    @Override
    List<NoonDataCompletenessAuditScope> listAuditScopes();

    @Insert({
            "INSERT INTO noon_pull_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, #{initialValue}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    void nextId(IdSequenceCommand command);

    @Insert({
            "INSERT INTO noon_data_completeness (",
            "  id, owner_user_id, store_code, site_code, data_category, latest_status, history_status,",
            "  latest_data_date, history_covered_from, history_covered_to, patrol_enabled, next_patrol_at,",
            "  active_gap_count, last_task_id, last_source_batch_id, last_failure_type, diagnostic_summary,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{category}, #{latestStatus}, #{historyStatus},",
            "  #{latestDataDate}, #{historyCoveredFrom}, #{historyCoveredTo}, #{patrolEnabled}, #{nextPatrolAt},",
            "  #{activeGapCount}, #{lastTaskId}, #{lastSourceBatchId}, #{lastFailureType}, #{diagnosticSummary},",
            "  #{createdAt}, #{updatedAt}",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  latest_status = VALUES(latest_status),",
            "  history_status = VALUES(history_status),",
            "  latest_data_date = VALUES(latest_data_date),",
            "  history_covered_from = VALUES(history_covered_from),",
            "  history_covered_to = VALUES(history_covered_to),",
            "  patrol_enabled = VALUES(patrol_enabled),",
            "  next_patrol_at = VALUES(next_patrol_at),",
            "  active_gap_count = VALUES(active_gap_count),",
            "  last_task_id = VALUES(last_task_id),",
            "  last_source_batch_id = VALUES(last_source_batch_id),",
            "  last_failure_type = VALUES(last_failure_type),",
            "  diagnostic_summary = VALUES(diagnostic_summary),",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    void insertCompleteness(NoonDataCompletenessRecord record);

    @Select({
            "<script>",
            "SELECT",
            "  ndc.id, ndc.owner_user_id, ls.project_name AS storeName, ndc.store_code, ndc.site_code,",
            "  ndc.data_category AS category, ndc.latest_status, ndc.history_status,",
            "  ndc.latest_data_date, ndc.history_covered_from, ndc.history_covered_to,",
            "  ndc.patrol_enabled, ndc.next_patrol_at, ndc.active_gap_count, ndc.last_task_id,",
            "  ndc.last_source_batch_id, ndc.last_failure_type, ndc.diagnostic_summary,",
            "  ndc.gmt_create AS created_at, ndc.gmt_updated AS updated_at",
            "FROM noon_data_completeness ndc",
            "LEFT JOIN logical_store_site lss",
            "  ON lss.store_code = ndc.store_code COLLATE utf8mb4_unicode_ci",
            " AND lss.site = ndc.site_code COLLATE utf8mb4_unicode_ci",
            " AND lss.is_deleted = b'0'",
            "LEFT JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.owner_user_id = ndc.owner_user_id",
            " AND ls.is_deleted = b'0'",
            "WHERE ndc.is_deleted = b'0'",
            "<if test='ownerUserId != null'> AND ndc.owner_user_id = #{ownerUserId}</if>",
            "<if test='storeCode != null'> AND ndc.store_code = #{storeCode}</if>",
            "<if test='siteCode != null'> AND ndc.site_code = #{siteCode}</if>",
            "<if test='category != null'> AND ndc.data_category = #{category}</if>",
            "<if test='latestStatus != null'> AND ndc.latest_status = #{latestStatus}</if>",
            "<if test='historyStatus != null'> AND ndc.history_status = #{historyStatus}</if>",
            "ORDER BY ndc.owner_user_id ASC, ndc.store_code ASC, ndc.site_code ASC, ndc.data_category ASC",
            "</script>"
    })
    List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query);

    @Insert({
            "INSERT INTO noon_data_gap_window (",
            "  id, completeness_id, owner_user_id, store_code, site_code, data_category, window_type,",
            "  date_from, date_to, status, attempts, next_retry_at, linked_pull_plan_id, linked_pull_task_id,",
            "  linked_source_batch_id, row_or_item_count, failure_type, retryable, requires_manual_action,",
            "  diagnostic_summary, completed_empty_evidence_summary, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{completenessId}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{category}, #{windowType},",
            "  #{dateFrom}, #{dateTo}, #{status}, #{attempts}, #{nextRetryAt}, #{linkedPullPlanId}, #{linkedPullTaskId},",
            "  #{linkedSourceBatchId}, #{rowOrItemCount}, #{failureType}, #{retryable}, #{requiresManualAction},",
            "  #{diagnosticSummary}, #{completedEmptyEvidenceSummary}, #{createdAt}, #{updatedAt}",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  status = VALUES(status),",
            "  attempts = VALUES(attempts),",
            "  next_retry_at = VALUES(next_retry_at),",
            "  linked_pull_plan_id = VALUES(linked_pull_plan_id),",
            "  linked_pull_task_id = VALUES(linked_pull_task_id),",
            "  linked_source_batch_id = VALUES(linked_source_batch_id),",
            "  row_or_item_count = VALUES(row_or_item_count),",
            "  failure_type = VALUES(failure_type),",
            "  retryable = VALUES(retryable),",
            "  requires_manual_action = VALUES(requires_manual_action),",
            "  diagnostic_summary = VALUES(diagnostic_summary),",
            "  completed_empty_evidence_summary = VALUES(completed_empty_evidence_summary),",
            "  is_deleted = b'0',",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    void insertGapWindow(NoonDataGapWindowRecord record);

    @Select({
            "<script>",
            "SELECT",
            "  id, completeness_id, owner_user_id, store_code, site_code, data_category AS category,",
            "  window_type, date_from, date_to, status, attempts, next_retry_at,",
            "  linked_pull_plan_id, linked_pull_task_id, linked_source_batch_id, row_or_item_count,",
            "  failure_type, retryable, requires_manual_action, diagnostic_summary,",
            "  completed_empty_evidence_summary, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_data_gap_window",
            "WHERE is_deleted = b'0'",
            "<if test='ownerUserId != null'> AND owner_user_id = #{ownerUserId}</if>",
            "<if test='storeCode != null'> AND store_code = #{storeCode}</if>",
            "<if test='siteCode != null'> AND site_code = #{siteCode}</if>",
            "<if test='category != null'> AND data_category = #{category}</if>",
            "<if test='status != null'> AND status = #{status}</if>",
            "<if test='failureType != null'> AND failure_type = #{failureType}</if>",
            "<if test='retryable != null'> AND retryable = #{retryable}</if>",
            "ORDER BY owner_user_id ASC, store_code ASC, site_code ASC, data_category ASC, id ASC",
            "</script>"
    })
    List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query);

    @Update({
            "UPDATE noon_data_gap_window",
            "SET is_deleted = b'1',",
            "    gmt_updated = #{updatedAt}",
            "WHERE is_deleted = b'0'",
            "  AND completeness_id = #{completenessId}",
            "  AND data_category = #{category}",
            "  AND window_type = 'HISTORY_BACKFILL'"
    })
    void deleteHistoryBackfillGaps(
            @Param("completenessId") Long completenessId,
            @Param("category") NoonDataCategory category,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
