package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductPublishRetryMapper {
    String SAFE_REQUEST_JSON =
            "(CASE WHEN JSON_VALID(target.request_json) THEN target.request_json ELSE NULL END)";
    String SAFE_CHANGED_DOMAINS_JSON =
            "(CASE WHEN JSON_VALID(target.changed_domains_json) "
                    + "THEN target.changed_domains_json ELSE JSON_ARRAY() END)";
    String SAFE_DRAFT_JSON =
            "(CASE WHEN JSON_VALID(target.draft_json) THEN target.draft_json ELSE NULL END)";
    String SAFE_BASELINE_JSON =
            "(CASE WHEN JSON_VALID(target.baseline_json) THEN target.baseline_json ELSE NULL END)";
    String DELETE_TASK_PREDICATE = "("
            + "LOWER(TRIM(COALESCE(target.task_type, ''))) = 'product-delete'"
            + " OR LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT("
            + SAFE_REQUEST_JSON
            + ", '$.action')), ''))) = 'product-delete'"
            + " OR LOWER(TRIM(COALESCE(target.idempotency_key, ''))) LIKE 'delete:%'"
            + " OR COALESCE(JSON_CONTAINS(LOWER("
            + SAFE_CHANGED_DOMAINS_JSON
            + "), JSON_QUOTE('delete'), '$'), 0) = 1"
            + " OR LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT("
            + SAFE_CHANGED_DOMAINS_JSON
            + ", '$.domain')), ''))) = 'delete'"
            + " OR LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT("
            + SAFE_CHANGED_DOMAINS_JSON
            + ", '$.action')), ''))) = 'delete'"
            + " OR LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT("
            + SAFE_DRAFT_JSON
            + ", '$.mode')), ''))) = 'product-delete-task'"
            + " OR LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT("
            + SAFE_BASELINE_JSON
            + ", '$.mode')), ''))) = 'product-delete-task'"
            + ")";
    String SAFE_DELETE_RETRY_PREDICATE = "("
            + DELETE_TASK_PREDICATE
            + " AND JSON_VALID(target.result_json)"
            + " AND JSON_TYPE(target.result_json) = 'OBJECT'"
            + " AND ("
            + "(LOWER(TRIM(COALESCE(target.error_code, ''))) != 'product_delete_result_unknown' AND ("
            + "(JSON_TYPE(JSON_EXTRACT(target.result_json, '$.writeMayHaveOccurred')) = 'BOOLEAN'"
            + " AND LOWER(JSON_UNQUOTE(JSON_EXTRACT(target.result_json, '$.writeMayHaveOccurred'))) = 'false')"
            + " OR LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT("
            + "target.result_json, '$.stage')), ''))) IN ("
            + "'retry_scheduled', 'pre_delete_unavailable', 'pre_delete_captured'))"
            + ") OR LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT("
            + "target.result_json, '$.stage')), ''))) IN ("
            + "'unmap_submitted', 'delete_submitted', 'current_psku_delete_submitted')"
            + ")"
            + ")";
    String SAFE_MANUAL_RETRY_PREDICATE = "("
            + SAFE_DELETE_RETRY_PREDICATE
            + " OR (NOT " + DELETE_TASK_PREDICATE + " AND ("
            + "LOWER(TRIM(COALESCE(target.error_code, ''))) NOT IN ("
            + "'product_write_outcome_unknown',"
            + "'group_partial_write_unknown',"
            + "'noon_effect_not_confirmed')"
            + " AND ("
            + "NULLIF(TRIM(target.result_json), '') IS NULL"
            + " OR (JSON_VALID(target.result_json)"
            + " AND JSON_TYPE(target.result_json) = 'OBJECT'"
            + " AND (JSON_EXTRACT(target.result_json, '$.writeMayHaveOccurred') IS NULL"
            + " OR (JSON_TYPE(JSON_EXTRACT(target.result_json, '$.writeMayHaveOccurred')) = 'BOOLEAN'"
            + " AND LOWER(JSON_UNQUOTE(JSON_EXTRACT("
            + "target.result_json, '$.writeMayHaveOccurred'))) = 'false')))"
            + ")"
            + ")"
            + ")"
            + ")";

    @Update({
            "<script>",
            "UPDATE product_publish_task t",
            "JOIN (",
            "  SELECT MAX(candidate.id) AS id",
            "  FROM product_publish_task candidate",
            "  WHERE candidate.is_deleted = 0",
            "    AND candidate.status = 'failed'",
            "    AND candidate.error_code IN ('noon_write_failed', 'publish_task_failed', 'noon_request_failed')",
            "    AND candidate.locked_at IS NULL",
            "    AND COALESCE(candidate.retry_count, 0) &lt; COALESCE(candidate.max_retry_count, 3)",
            "    AND COALESCE(candidate.finished_at, candidate.gmt_updated, candidate.gmt_create) &gt;= DATE_SUB(NOW(), INTERVAL #{lookbackHours} HOUR)",
            "    AND (",
            "      LOWER(COALESCE(candidate.error_message, '')) REGEXP 'http([/][0-9]+([.][0-9]+)?)?[[:space:]]+(408|500|502|503|504)([^0-9]|$)'",
            "      OR (",
            "        LOWER(COALESCE(candidate.error_message, ''))",
            "          NOT REGEXP 'http([/][0-9]+([.][0-9]+)?)?[[:space:]]+[0-9]{3}([^0-9]|$)'",
            "        AND (",
            "          LOWER(COALESCE(candidate.error_message, '')) REGEXP '(^|[^a-z])eof(exception)?([^a-z]|$)'",
            "          OR LOWER(COALESCE(candidate.error_message, '')) LIKE '%http/1.1 header parser received no bytes%'",
            "          OR LOWER(COALESCE(candidate.error_message, '')) REGEXP '(connection|connect)[[:space:]_-]+(timeout|timed[[:space:]_-]+out)'",
            "        )",
            "      )",
            "    )",
            "    AND LOWER(COALESCE(candidate.error_message, '')) NOT REGEXP",
            "      'http([/][0-9]+([.][0-9]+)?)?[[:space:]]+(1[0-9]{2}|2[0-9]{2}|3[0-9]{2}|400|40[1-7]|409|4[1-9][0-9]|501|50[5-9]|5[1-9][0-9]|[6-9][0-9]{2})([^0-9]|$)'",
            "    AND LOWER(COALESCE(candidate.error_message, '')) NOT REGEXP 'auth[_ -]?required|authentication|authorization|credential|password'",
            "    AND LOWER(COALESCE(candidate.error_message, '')) NOT REGEXP 'project[_ -]?(scope|access)|current[[:space:]]+project|scope[_ -]?mismatch'",
            "    AND LOWER(COALESCE(candidate.error_message, '')) NOT REGEXP 'access[[:space:]]+denied|permission|forbidden|unauthorized'",
            "    AND LOWER(COALESCE(candidate.error_message, '')) NOT REGEXP 'validation|invalid[[:space:]_-]+(parameter|request|payload|value|partner.?sku|barcode)|bad[[:space:]_-]+request|unsupported|duplicate|conflict|not[[:space:]_-]+found'",
            "    AND candidate.id = (",
            "      SELECT MAX(latest.id)",
            "      FROM product_publish_task latest",
            "      WHERE latest.product_master_id = candidate.product_master_id",
            "        AND latest.is_deleted = 0",
            "    )",
            "    AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM product_publish_task active",
            "      WHERE active.product_master_id = candidate.product_master_id",
            "        AND active.is_deleted = 0",
            "        AND active.status IN (",
            "          'queued', 'running', 'submitted', 'verifying', 'pending_effective', 'write_unknown', 'verify_timeout', 'write_retry_scheduled',",
            "          'product_delete_queued', 'product_delete_running', 'product_delete_submitted', 'product_delete_verifying',",
            "          'product_delete_pending_effective', 'product_delete_verify_timeout', 'product_delete_write_retry_scheduled'",
            "        )",
            "    )",
            "  GROUP BY candidate.product_master_id",
            ") recoverable ON recoverable.id = t.id",
            "SET t.status = CASE",
            "      WHEN t.task_type = 'product-delete' THEN 'product_delete_write_retry_scheduled'",
            "      ELSE 'write_retry_scheduled'",
            "    END,",
            "    t.error_code = CASE",
            "      WHEN t.error_code IN ('publish_task_failed', 'noon_request_failed') THEN 'noon_request_failed'",
            "      ELSE t.error_code",
            "    END,",
            "    t.error_message = 'Noon 发布接口暂时不可用，系统将后台自动核对并重试。',",
            "    t.next_run_at = NOW(),",
            "    t.finished_at = NULL,",
            "    t.retry_count = COALESCE(t.retry_count, 0) + 1,",
            "    t.active_lock_key = CONCAT('product:', t.product_master_id),",
            "    t.locked_by = NULL,",
            "    t.locked_at = NULL,",
            "    t.version_no = t.version_no + 1,",
            "    t.updated_by = #{updatedBy},",
            "    t.gmt_updated = NOW()",
            "</script>"
    })
    int recoverRetryableFailedNoonWriteProductPublishTasks(
            @Param("lookbackHours") int lookbackHours,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_publish_task target",
            "SET target.task_type = 'product-delete',",
            "    target.status = 'pending_manual_check',",
            "    target.error_code = 'product_delete_retry_exhausted',",
            "    target.error_message = '商品删除已超过自动重试上限，系统已在再次访问 Noon 前停止，请人工核对后决定是否重试。',",
            "    target.next_run_at = NULL,",
            "    target.finished_at = NOW(),",
            "    target.active_lock_key = NULL,",
            "    target.locked_by = NULL,",
            "    target.locked_at = NULL,",
            "    target.version_no = target.version_no + 1,",
            "    target.updated_by = #{updatedBy},",
            "    target.gmt_updated = NOW()",
            "WHERE " + DELETE_TASK_PREDICATE,
            "  AND target.status IN ('write_retry_scheduled', 'product_delete_write_retry_scheduled')",
            "  AND COALESCE(target.retry_count, 0) >= LEAST(",
            "      GREATEST(COALESCE(target.max_retry_count, 3), 0),",
            "      #{automaticRetryLimit}",
            "  )",
            "  AND target.locked_at IS NULL",
            "  AND target.is_deleted = 0"
    })
    int stopExhaustedProductDeleteRetries(
            @Param("automaticRetryLimit") int automaticRetryLimit,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_publish_task target",
            "LEFT JOIN product_publish_task newer",
            "  ON newer.product_master_id = target.product_master_id",
            " AND newer.id > target.id",
            " AND newer.is_deleted = 0",
            "SET target.task_type = CASE",
            "        WHEN " + DELETE_TASK_PREDICATE + " THEN 'product-delete'",
            "        ELSE target.task_type",
            "    END,",
            "    target.status = CASE",
            "        WHEN " + DELETE_TASK_PREDICATE + " THEN 'product_delete_queued'",
            "        ELSE 'queued'",
            "    END,",
            "    target.retry_count = CASE",
            "        WHEN target.error_code = 'noon_auth_recovery_pending' THEN target.retry_count",
            "        ELSE target.retry_count + 1",
            "    END,",
            "    target.next_run_at = NOW(),",
            "    target.error_code = NULL,",
            "    target.error_message = NULL,",
            "    target.result_json = CASE",
            "        WHEN " + DELETE_TASK_PREDICATE + " THEN target.result_json",
            "        ELSE NULL",
            "    END,",
            "    target.finished_at = NULL,",
            "    target.active_lock_key = CONCAT('product:', target.product_master_id),",
            "    target.updated_by = #{updatedBy},",
            "    target.gmt_updated = NOW()",
            "WHERE target.id = #{id}",
            "  AND target.status IN ('failed', 'pending_manual_check')",
            "  AND (target.status = 'pending_manual_check'",
            "       OR target.retry_count < target.max_retry_count)",
            "  AND " + SAFE_MANUAL_RETRY_PREDICATE,
            "  AND target.is_deleted = 0",
            "  AND newer.id IS NULL"
    })
    int retryProductPublishTask(@Param("id") Long id, @Param("updatedBy") Long updatedBy);
}
