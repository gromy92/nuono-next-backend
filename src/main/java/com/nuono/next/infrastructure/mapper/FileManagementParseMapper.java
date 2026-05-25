package com.nuono.next.infrastructure.mapper;

import com.nuono.next.filemanagement.parse.FileParseTargetPlanRow;
import com.nuono.next.filemanagement.parse.FileParseActiveVersionRow;
import com.nuono.next.filemanagement.parse.FileParseAiChunkView;
import com.nuono.next.filemanagement.parse.FileParseFileAssetRow;
import com.nuono.next.filemanagement.parse.FileParseItemStandardRow;
import com.nuono.next.filemanagement.parse.FileParseItemReviewRow;
import com.nuono.next.filemanagement.parse.FileParsePublishAuditRow;
import com.nuono.next.filemanagement.parse.FileParseResultItemRow;
import com.nuono.next.filemanagement.parse.FileParseSourceRowDraft;
import com.nuono.next.filemanagement.parse.FileParseSourceRowView;
import com.nuono.next.filemanagement.parse.FileParseStandardVersionRow;
import com.nuono.next.filemanagement.parse.FileParseTaskInputRow;
import com.nuono.next.filemanagement.parse.FileParseTaskListItemView;
import com.nuono.next.filemanagement.parse.FileParseTaskRow;
import com.nuono.next.filemanagement.parse.FileParseUserContext;
import com.nuono.next.filemanagement.parse.FileParseValidationIssueView;
import com.nuono.next.filemanagement.parse.FileParseVersionItemRow;
import com.nuono.next.filemanagement.parse.FileParseVersionSummaryRow;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface FileManagementParseMapper {

    @Insert({
            "INSERT INTO file_mgmt_parse_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = {
                    "SELECT LAST_INSERT_ID()"
            },
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    int allocateFileParseId(IdSequenceCommand command);

    default Long nextFileParseId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateFileParseId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("文件管理解析 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextFileAssetId() {
        return nextFileParseId("file_mgmt_parse_file_asset", 10000L);
    }

    default Long nextTaskId() {
        return nextFileParseId("file_mgmt_parse_task", 20000L);
    }

    default Long nextTaskInputId() {
        return nextFileParseId("file_mgmt_parse_task_input", 30000L);
    }

    default Long nextSourceRowId() {
        return nextFileParseId("file_mgmt_parse_source_row", 35000L);
    }

    default Long nextAiChunkId() {
        return nextFileParseId("file_mgmt_parse_ai_chunk", 36000L);
    }

    default Long nextResultId() {
        return nextFileParseId("file_mgmt_parse_result", 40000L);
    }

    default Long nextResultItemId() {
        return nextFileParseId("file_mgmt_parse_result_item", 50000L);
    }

    default Long nextResultItemSourceId() {
        return nextFileParseId("file_mgmt_parse_result_item_source", 55000L);
    }

    default Long nextValidationIssueId() {
        return nextFileParseId("file_mgmt_parse_validation_issue", 56000L);
    }

    default Long nextReviewId() {
        return nextFileParseId("file_mgmt_parse_item_review", 60000L);
    }

    default Long nextVersionId() {
        return nextFileParseId("file_mgmt_parse_version", 70000L);
    }

    default Long nextVersionItemId() {
        return nextFileParseId("file_mgmt_parse_version_item", 80000L);
    }

    default Long nextActiveVersionId() {
        return nextFileParseId("file_mgmt_parse_active_version", 72000L);
    }

    default Long nextAuditLogId() {
        return nextFileParseId("file_mgmt_parse_audit_log", 90000L);
    }

    default Long nextLogisticsChannelActivationId() {
        return nextFileParseId("file_mgmt_parse_logistics_channel_activation", 95000L);
    }

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  COALESCE(NULLIF(TRIM(u.real_name), ''), u.account_no) AS real_name,",
            "  u.role_id,",
            "  r.code AS role_code,",
            "  r.name AS role_name,",
            "  COALESCE(r.level, u.level) AS role_level,",
            "  u.status",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE u.id = #{userId}",
            "  AND u.is_deleted = 0",
            "LIMIT 1"
    })
    FileParseUserContext selectUserContext(@Param("userId") Long userId);

    @Select({
            "SELECT COUNT(1)",
            "FROM user_menu um",
            "WHERE um.user_id = #{userId}",
            "  AND um.menu_id = #{menuId}",
            "  AND um.is_deleted = 0",
            "  AND (um.status IS NULL OR um.status = 1)",
            "  AND (um.effective_time IS NULL OR um.effective_time <= NOW())",
            "  AND (um.expired_time IS NULL OR um.expired_time >= NOW())"
    })
    int countActiveUserMenu(
            @Param("userId") Long userId,
            @Param("menuId") Long menuId
    );

    @Select({
            "SELECT",
            "  p.id,",
            "  p.plan_code AS code,",
            "  p.plan_label AS label,",
            "  COALESCE(NULLIF(p.document_type, ''), s.document_type) AS document_type,",
            "  COALESCE(NULLIF(p.document_name, ''), s.document_name) AS document_name,",
            "  p.standard_version_id,",
            "  sv.standard_version,",
            "  av.version_id AS current_version_id,",
            "  v.version_no AS current_version,",
            "  p.description",
            "FROM file_mgmt_parse_target_plan p",
            "JOIN file_mgmt_parse_standard s",
            "  ON s.id = p.standard_id",
            " AND s.is_deleted = 0",
            "JOIN file_mgmt_parse_standard_version sv",
            "  ON sv.id = p.standard_version_id",
            " AND sv.status = 'active'",
            " AND sv.is_deleted = 0",
            "LEFT JOIN file_mgmt_parse_active_version av",
            "  ON av.target_plan_id = p.id",
            " AND av.data_scope_type = 'global'",
            " AND av.data_scope_key = 'global:*'",
            " AND av.is_deleted = 0",
            "LEFT JOIN file_mgmt_parse_version v",
            "  ON v.id = av.version_id",
            " AND v.is_deleted = 0",
            "WHERE p.status = 'active'",
            "  AND p.is_deleted = 0",
            "  AND (",
            "    #{includeAll} = TRUE",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM file_mgmt_parse_target_plan_scope scope",
            "      WHERE scope.target_plan_id = p.id",
            "        AND scope.is_deleted = 0",
            "        AND scope.status = 'active'",
            "        AND (",
            "          scope.scope_type = 'all'",
            "          OR (scope.scope_type = 'role_level' AND scope.scope_value = CAST(#{roleLevel} AS CHAR))",
            "        )",
            "    )",
            "  )",
            "ORDER BY p.sort_no ASC, p.id ASC"
    })
    List<FileParseTargetPlanRow> selectVisibleTargetPlans(
            @Param("roleLevel") Integer roleLevel,
            @Param("includeAll") boolean includeAll
    );

    @Select({
            "SELECT",
            "  p.id,",
            "  p.plan_code AS code,",
            "  p.plan_label AS label,",
            "  COALESCE(NULLIF(p.document_type, ''), s.document_type) AS document_type,",
            "  COALESCE(NULLIF(p.document_name, ''), s.document_name) AS document_name,",
            "  p.standard_version_id,",
            "  sv.standard_version,",
            "  av.version_id AS current_version_id,",
            "  v.version_no AS current_version,",
            "  p.description",
            "FROM file_mgmt_parse_target_plan p",
            "JOIN file_mgmt_parse_standard s",
            "  ON s.id = p.standard_id",
            " AND s.is_deleted = 0",
            "JOIN file_mgmt_parse_standard_version sv",
            "  ON sv.id = p.standard_version_id",
            " AND sv.status = 'active'",
            " AND sv.is_deleted = 0",
            "LEFT JOIN file_mgmt_parse_active_version av",
            "  ON av.target_plan_id = p.id",
            " AND av.data_scope_type = 'global'",
            " AND av.data_scope_key = 'global:*'",
            " AND av.is_deleted = 0",
            "LEFT JOIN file_mgmt_parse_version v",
            "  ON v.id = av.version_id",
            " AND v.is_deleted = 0",
            "WHERE p.id = #{targetPlanId}",
            "  AND p.status = 'active'",
            "  AND p.is_deleted = 0",
            "  AND (",
            "    #{includeAll} = TRUE",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM file_mgmt_parse_target_plan_scope scope",
            "      WHERE scope.target_plan_id = p.id",
            "        AND scope.is_deleted = 0",
            "        AND scope.status = 'active'",
            "        AND (",
            "          scope.scope_type = 'all'",
            "          OR (scope.scope_type = 'role_level' AND scope.scope_value = CAST(#{roleLevel} AS CHAR))",
            "        )",
            "    )",
            "  )",
            "LIMIT 1"
    })
    FileParseTargetPlanRow selectVisibleTargetPlan(
            @Param("targetPlanId") Long targetPlanId,
            @Param("roleLevel") Integer roleLevel,
            @Param("includeAll") boolean includeAll
    );

    @Select({
            "SELECT COUNT(DISTINCT COALESCE(t.document_group_id, t.id))",
            "FROM file_mgmt_parse_task t",
            "JOIN file_mgmt_parse_target_plan p",
            "  ON p.id = t.target_plan_id",
            " AND p.status = 'active'",
            " AND p.is_deleted = b'0'",
            "WHERE t.is_deleted = b'0'",
            "  AND (#{keyword} IS NULL OR t.document_title LIKE CONCAT('%', #{keyword}, '%'))",
            "  AND (#{targetPlanId} IS NULL OR t.target_plan_id = #{targetPlanId})",
            "  AND (#{status} IS NULL OR t.status = #{status})",
            "  AND t.id = (",
            "    SELECT latest.id",
            "    FROM file_mgmt_parse_task latest",
            "    WHERE latest.is_deleted = b'0'",
            "      AND COALESCE(latest.document_group_id, latest.id) = COALESCE(t.document_group_id, t.id)",
            "    ORDER BY latest.gmt_updated DESC, latest.id DESC",
            "    LIMIT 1",
            "  )",
            "  AND (",
            "    #{includeAll} = TRUE",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM file_mgmt_parse_target_plan_scope scope",
            "      WHERE scope.target_plan_id = p.id",
            "        AND scope.is_deleted = 0",
            "        AND scope.status = 'active'",
            "        AND (",
            "          scope.scope_type = 'all'",
            "          OR (scope.scope_type = 'role_level' AND scope.scope_value = CAST(#{roleLevel} AS CHAR))",
            "        )",
            "    )",
            "  )"
    })
    int countTasks(
            @Param("keyword") String keyword,
            @Param("targetPlanId") Long targetPlanId,
            @Param("status") String status,
            @Param("roleLevel") Integer roleLevel,
            @Param("includeAll") boolean includeAll
    );

    @Select({
            "SELECT",
            "  t.id,",
            "  t.task_no,",
            "  t.document_title,",
            "  t.target_plan_id,",
            "  p.plan_code AS target_plan_code,",
            "  p.plan_label AS target_plan_label,",
            "  COALESCE(NULLIF(p.document_type, ''), s.document_type) AS document_type,",
            "  COALESCE(NULLIF(p.document_name, ''), s.document_name) AS document_name,",
            "  sv.standard_version,",
            "  av.version_no AS current_version,",
            "  t.status,",
            "  t.data_scope_type,",
            "  t.data_scope_key,",
            "  COALESCE(t.document_group_id, t.id) AS document_group_id,",
            "  t.parent_task_id,",
            "  COALESCE(NULLIF(t.iteration_no, 0), 1) AS iteration_no,",
            "  t.current_result_id AS result_id,",
            "  t.failure_code,",
            "  t.failure_message,",
            "  t.next_run_at,",
            "  COALESCE(SUM(CASE WHEN ri.change_type <> 'delete_suspected' THEN 1 ELSE 0 END), 0) AS total_count,",
            "  COALESCE(SUM(CASE WHEN ri.change_type = 'delete_suspected' THEN 1 ELSE 0 END), 0) AS delete_suspected_count,",
            "  COALESCE(SUM(CASE WHEN ri.review_status = 'pending' AND ri.change_type <> 'delete_suspected' THEN 1 ELSE 0 END), 0) AS pending_count,",
            "  COALESCE(SUM(CASE WHEN ri.review_status = 'needs_fix' THEN 1 ELSE 0 END), 0) AS needs_fix_count,",
            "  COALESCE(SUM(CASE WHEN ri.review_status = 'hard_error' THEN 1 ELSE 0 END), 0) AS hard_error_count,",
            "  COALESCE(SUM(CASE WHEN ri.change_type = 'conflict' THEN 1 ELSE 0 END), 0) AS conflict_count,",
            "  COALESCE(SUM(CASE WHEN ri.review_status = 'confirmed' THEN 1 ELSE 0 END), 0) AS confirmed_count,",
            "  COALESCE(SUM(CASE WHEN ri.review_status = 'rejected' THEN 1 ELSE 0 END), 0) AS rejected_count,",
            "  COALESCE(SUM(CASE WHEN ri.review_status = 'keep_old' THEN 1 ELSE 0 END), 0) AS keep_old_count,",
            "  t.gmt_create AS created_at,",
            "  t.gmt_updated AS updated_at",
            "FROM file_mgmt_parse_task t",
            "JOIN file_mgmt_parse_target_plan p",
            "  ON p.id = t.target_plan_id",
            " AND p.status = 'active'",
            " AND p.is_deleted = b'0'",
            "JOIN file_mgmt_parse_standard s",
            "  ON s.id = p.standard_id",
            " AND s.is_deleted = b'0'",
            "JOIN file_mgmt_parse_standard_version sv",
            "  ON sv.id = t.standard_version_id",
            " AND sv.is_deleted = b'0'",
            "LEFT JOIN file_mgmt_parse_active_version av",
            "  ON av.target_plan_id = p.id",
            " AND av.data_scope_type = 'global'",
            " AND av.data_scope_key = 'global:*'",
            " AND av.is_deleted = b'0'",
            "LEFT JOIN file_mgmt_parse_result_item ri",
            "  ON ri.result_id = t.current_result_id",
            " AND ri.is_deleted = b'0'",
            "WHERE t.is_deleted = b'0'",
            "  AND (#{keyword} IS NULL OR t.document_title LIKE CONCAT('%', #{keyword}, '%'))",
            "  AND (#{targetPlanId} IS NULL OR t.target_plan_id = #{targetPlanId})",
            "  AND (#{status} IS NULL OR t.status = #{status})",
            "  AND t.id = (",
            "    SELECT latest.id",
            "    FROM file_mgmt_parse_task latest",
            "    WHERE latest.is_deleted = b'0'",
            "      AND COALESCE(latest.document_group_id, latest.id) = COALESCE(t.document_group_id, t.id)",
            "    ORDER BY latest.gmt_updated DESC, latest.id DESC",
            "    LIMIT 1",
            "  )",
            "  AND (",
            "    #{includeAll} = TRUE",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM file_mgmt_parse_target_plan_scope scope",
            "      WHERE scope.target_plan_id = p.id",
            "        AND scope.is_deleted = 0",
            "        AND scope.status = 'active'",
            "        AND (",
            "          scope.scope_type = 'all'",
            "          OR (scope.scope_type = 'role_level' AND scope.scope_value = CAST(#{roleLevel} AS CHAR))",
            "        )",
            "    )",
            "  )",
            "GROUP BY",
            "  t.id, t.task_no, t.document_title, t.target_plan_id, p.plan_code, p.plan_label,",
            "  COALESCE(NULLIF(p.document_type, ''), s.document_type),",
            "  COALESCE(NULLIF(p.document_name, ''), s.document_name),",
            "  sv.standard_version, av.version_no, t.status, t.data_scope_type, t.data_scope_key,",
            "  COALESCE(t.document_group_id, t.id), t.parent_task_id, COALESCE(NULLIF(t.iteration_no, 0), 1),",
            "  t.current_result_id, t.failure_code, t.failure_message, t.next_run_at, t.gmt_create, t.gmt_updated",
            "ORDER BY t.gmt_updated DESC, t.id DESC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseTaskListItemView> selectTasks(
            @Param("keyword") String keyword,
            @Param("targetPlanId") Long targetPlanId,
            @Param("status") String status,
            @Param("roleLevel") Integer roleLevel,
            @Param("includeAll") boolean includeAll,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT",
            "  id, standard_id, standard_version, result_schema_json, validation_rule_json,",
            "  display_config_json, diff_rule_json",
            "FROM file_mgmt_parse_standard_version",
            "WHERE id = #{standardVersionId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    FileParseStandardVersionRow selectStandardVersion(@Param("standardVersionId") Long standardVersionId);

    @Select({
            "SELECT",
            "  id, standard_version_id, item_type, item_label, natural_key_json,",
            "  field_schema_json, display_config_json, validation_rule_json, diff_rule_json, sort_no",
            "FROM file_mgmt_parse_item_standard",
            "WHERE standard_version_id = #{standardVersionId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'",
            "ORDER BY sort_no ASC, id ASC"
    })
    List<FileParseItemStandardRow> selectItemStandards(@Param("standardVersionId") Long standardVersionId);

    @Select({
            "SELECT",
            "  id, version_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  version_payload_json, source_result_item_id, sort_no",
            "FROM file_mgmt_parse_version_item",
            "WHERE version_id = #{versionId}",
            "  AND is_deleted = b'0'",
            "ORDER BY sort_no ASC, id ASC"
    })
    List<FileParseVersionItemRow> selectVersionItems(@Param("versionId") Long versionId);

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_result_item",
            "WHERE result_id = #{resultId}",
            "  AND is_deleted = b'0'",
            "  AND review_status <> 'rejected'",
            "  AND (change_type <> 'delete_suspected' OR review_status = 'keep_old')"
    })
    int countOverviewResultItems(@Param("resultId") Long resultId);

    @Select({
            "SELECT",
            "  id, result_id, task_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  change_type, review_status, current_review_id, confidence, validation_status,",
            "  normalized_payload_json, old_payload_json, changed_field_keys_json,",
            "  effective_payload_json, effective_validation_status, effective_payload_hash, evidence_json, validation_error_json, sort_no",
            "FROM file_mgmt_parse_result_item",
            "WHERE result_id = #{resultId}",
            "  AND is_deleted = b'0'",
            "  AND review_status <> 'rejected'",
            "  AND (change_type <> 'delete_suspected' OR review_status = 'keep_old')",
            "ORDER BY sort_no ASC, id ASC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseResultItemRow> selectOverviewResultItems(
            @Param("resultId") Long resultId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_version",
            "WHERE target_plan_id = #{targetPlanId}",
            "  AND data_scope_type = 'global'",
            "  AND data_scope_key = 'global:*'",
            "  AND is_deleted = b'0'"
    })
    int countVersionsByTargetPlan(@Param("targetPlanId") Long targetPlanId);

    @Select({
            "SELECT",
            "  id, version_no, target_plan_id, source_task_id, source_result_id, standard_version_id,",
            "  base_version_id, data_scope_type, data_scope_key, version_status, published_at,",
            "  published_by, summary_json",
            "FROM file_mgmt_parse_version",
            "WHERE target_plan_id = #{targetPlanId}",
            "  AND data_scope_type = 'global'",
            "  AND data_scope_key = 'global:*'",
            "  AND is_deleted = b'0'",
            "ORDER BY published_at DESC, id DESC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseVersionSummaryRow> selectVersionsByTargetPlan(
            @Param("targetPlanId") Long targetPlanId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT",
            "  id, version_no, target_plan_id, source_task_id, source_result_id, standard_version_id,",
            "  base_version_id, data_scope_type, data_scope_key, version_status, published_at,",
            "  published_by, summary_json",
            "FROM file_mgmt_parse_version",
            "WHERE id = #{versionId}",
            "  AND data_scope_type = 'global'",
            "  AND data_scope_key = 'global:*'",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    FileParseVersionSummaryRow selectVersion(@Param("versionId") Long versionId);

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_version_item",
            "WHERE version_id = #{versionId}",
            "  AND is_deleted = b'0'"
    })
    int countVersionSnapshotItems(@Param("versionId") Long versionId);

    @Select({
            "SELECT",
            "  id, version_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  version_payload_json, source_result_item_id, sort_no",
            "FROM file_mgmt_parse_version_item",
            "WHERE version_id = #{versionId}",
            "  AND is_deleted = b'0'",
            "ORDER BY sort_no ASC, id ASC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseVersionItemRow> selectVersionSnapshotItems(
            @Param("versionId") Long versionId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT",
            "  id, target_plan_id, data_scope_type, data_scope_key, version_id, version_no",
            "FROM file_mgmt_parse_active_version",
            "WHERE target_plan_id = #{targetPlanId}",
            "  AND data_scope_type = #{dataScopeType}",
            "  AND data_scope_key = #{dataScopeKey}",
            "  AND is_deleted = b'0'",
            "LIMIT 1",
            "FOR UPDATE"
    })
    FileParseActiveVersionRow selectActiveVersionForUpdate(
            @Param("targetPlanId") Long targetPlanId,
            @Param("dataScopeType") String dataScopeType,
            @Param("dataScopeKey") String dataScopeKey
    );

    @Select({
            "SELECT",
            "  version_id, payload_hash",
            "FROM file_mgmt_parse_audit_log",
            "WHERE task_id = #{taskId}",
            "  AND operation_type = 'publish_version'",
            "  AND request_id = #{idempotencyKey}",
            "  AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    FileParsePublishAuditRow selectPublishAuditByIdempotency(
            @Param("taskId") Long taskId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Select({
            "SELECT",
            "  id, result_id, task_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  change_type, review_status, current_review_id, confidence, validation_status,",
            "  normalized_payload_json, old_payload_json, changed_field_keys_json,",
            "  effective_payload_json, effective_validation_status, effective_payload_hash, evidence_json, validation_error_json, sort_no",
            "FROM file_mgmt_parse_result_item",
            "WHERE result_id = #{resultId}",
            "  AND is_deleted = b'0'",
            "ORDER BY sort_no ASC, id ASC"
    })
    List<FileParseResultItemRow> selectResultItemsForPublish(@Param("resultId") Long resultId);

    @Insert({
            "INSERT INTO file_mgmt_parse_version (",
            "  id, version_no, target_plan_id, source_task_id, source_result_id, standard_version_id,",
            "  base_version_id, data_scope_type, data_scope_key, version_status, published_at, published_by,",
            "  summary_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{versionNo}, #{targetPlanId}, #{sourceTaskId}, #{sourceResultId}, #{standardVersionId},",
            "  #{baseVersionId}, #{dataScopeType}, #{dataScopeKey}, 'active', #{publishedAt}, #{operatorUserId},",
            "  #{summaryJson}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertVersion(
            @Param("id") Long id,
            @Param("versionNo") String versionNo,
            @Param("targetPlanId") Long targetPlanId,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("sourceResultId") Long sourceResultId,
            @Param("standardVersionId") Long standardVersionId,
            @Param("baseVersionId") Long baseVersionId,
            @Param("dataScopeType") String dataScopeType,
            @Param("dataScopeKey") String dataScopeKey,
            @Param("publishedAt") java.time.LocalDateTime publishedAt,
            @Param("summaryJson") String summaryJson,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_version_item (",
            "  id, version_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  version_payload_json, source_result_item_id, data_scope_type, data_scope_key,",
            "  sort_no, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{versionId}, #{targetPlanId}, #{itemType}, #{naturalKey}, #{naturalKeyHash},",
            "  #{versionPayloadJson}, #{sourceResultItemId}, #{dataScopeType}, #{dataScopeKey},",
            "  #{sortNo}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertVersionItem(
            @Param("id") Long id,
            @Param("versionId") Long versionId,
            @Param("targetPlanId") Long targetPlanId,
            @Param("itemType") String itemType,
            @Param("naturalKey") String naturalKey,
            @Param("naturalKeyHash") String naturalKeyHash,
            @Param("versionPayloadJson") String versionPayloadJson,
            @Param("sourceResultItemId") Long sourceResultItemId,
            @Param("dataScopeType") String dataScopeType,
            @Param("dataScopeKey") String dataScopeKey,
            @Param("sortNo") Integer sortNo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_version",
            "SET version_status = 'history',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE target_plan_id = #{targetPlanId}",
            "  AND data_scope_type = #{dataScopeType}",
            "  AND data_scope_key = #{dataScopeKey}",
            "  AND id <> #{activeVersionId}",
            "  AND is_deleted = b'0'"
    })
    int markVersionsHistory(
            @Param("targetPlanId") Long targetPlanId,
            @Param("dataScopeType") String dataScopeType,
            @Param("dataScopeKey") String dataScopeKey,
            @Param("activeVersionId") Long activeVersionId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_active_version",
            "SET version_id = #{versionId},",
            "    version_no = #{versionNo},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE target_plan_id = #{targetPlanId}",
            "  AND data_scope_type = #{dataScopeType}",
            "  AND data_scope_key = #{dataScopeKey}",
            "  AND is_deleted = b'0'"
    })
    int updateActiveVersion(
            @Param("targetPlanId") Long targetPlanId,
            @Param("dataScopeType") String dataScopeType,
            @Param("dataScopeKey") String dataScopeKey,
            @Param("versionId") Long versionId,
            @Param("versionNo") String versionNo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_active_version (",
            "  id, target_plan_id, data_scope_type, data_scope_key, version_id, version_no,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{targetPlanId}, #{dataScopeType}, #{dataScopeKey}, #{versionId}, #{versionNo},",
            "  b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  version_id = VALUES(version_id),",
            "  version_no = VALUES(version_no),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertActiveVersion(
            @Param("id") Long id,
            @Param("targetPlanId") Long targetPlanId,
            @Param("dataScopeType") String dataScopeType,
            @Param("dataScopeKey") String dataScopeKey,
            @Param("versionId") Long versionId,
            @Param("versionNo") String versionNo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'published',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND current_result_id = #{resultId}",
            "  AND status = 'ready_to_publish'",
            "  AND is_deleted = b'0'"
    })
    int markTaskPublished(
            @Param("taskId") Long taskId,
            @Param("resultId") Long resultId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_audit_log (",
            "  id, task_id, target_plan_id, version_id, operation_type, operation_summary,",
            "  request_id, payload_hash, operator_user_id, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, #{targetPlanId}, #{versionId}, 'publish_version', #{operationSummary},",
            "  #{idempotencyKey}, #{payloadHash}, #{operatorUserId}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertPublishAudit(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("targetPlanId") Long targetPlanId,
            @Param("versionId") Long versionId,
            @Param("operationSummary") String operationSummary,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("payloadHash") String payloadHash,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_audit_log (",
            "  id, task_id, target_plan_id, version_id, operation_type, operation_summary,",
            "  request_id, payload_hash, operator_user_id, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, #{targetPlanId}, #{versionId}, #{operationType}, #{operationSummary},",
            "  #{requestId}, #{payloadHash}, #{operatorUserId}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertAuditLog(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("targetPlanId") Long targetPlanId,
            @Param("versionId") Long versionId,
            @Param("operationType") String operationType,
            @Param("operationSummary") String operationSummary,
            @Param("requestId") String requestId,
            @Param("payloadHash") String payloadHash,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT channel_key",
            "FROM file_mgmt_parse_logistics_channel_activation",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND target_plan_id = #{targetPlanId}",
            "  AND version_id = #{versionId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<String> selectActiveLogisticsChannelKeys(
            @Param("ownerUserId") Long ownerUserId,
            @Param("targetPlanId") Long targetPlanId,
            @Param("versionId") Long versionId
    );

    @Update({
            "UPDATE file_mgmt_parse_logistics_channel_activation",
            "SET is_deleted = b'1',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND target_plan_id = #{targetPlanId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteLogisticsChannelActivations(
            @Param("ownerUserId") Long ownerUserId,
            @Param("targetPlanId") Long targetPlanId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_logistics_channel_activation (",
            "  id, target_plan_id, version_id, version_item_id, owner_user_id, channel_key,",
            "  natural_key, natural_key_hash, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{targetPlanId}, #{versionId}, #{versionItemId}, #{ownerUserId}, #{channelKey},",
            "  #{naturalKey}, #{naturalKeyHash}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  version_item_id = VALUES(version_item_id),",
            "  channel_key = VALUES(channel_key),",
            "  natural_key = VALUES(natural_key),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int insertLogisticsChannelActivation(
            @Param("id") Long id,
            @Param("targetPlanId") Long targetPlanId,
            @Param("versionId") Long versionId,
            @Param("versionItemId") Long versionItemId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("channelKey") String channelKey,
            @Param("naturalKey") String naturalKey,
            @Param("naturalKeyHash") String naturalKeyHash,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_file_asset (",
            "  id, upload_id, target_plan_id, standard_version_id, original_file_name, content_type,",
            "  file_extension, file_size_bytes, sha256_hash, storage_bucket, storage_key,",
            "  bound_task_id, uploaded_by, expires_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{uploadId}, #{targetPlanId}, #{standardVersionId}, #{originalFileName}, #{contentType},",
            "  #{fileExtension}, #{fileSizeBytes}, #{sha256Hash}, #{storageBucket}, #{storageKey},",
            "  NULL, #{uploadedBy}, #{expiresAt}, b'0', #{uploadedBy}, #{uploadedBy}, NOW(), NOW()",
            ")"
    })
    int insertFileAsset(FileParseFileAssetRow row);

    @Select({
            "SELECT",
            "  id, upload_id, target_plan_id, standard_version_id, original_file_name, content_type,",
            "  file_extension, file_size_bytes, sha256_hash, storage_bucket, storage_key,",
            "  bound_task_id, uploaded_by, expires_at",
            "FROM file_mgmt_parse_file_asset",
            "WHERE id = #{fileId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    FileParseFileAssetRow selectFileAsset(@Param("fileId") Long fileId);

    @Update({
            "UPDATE file_mgmt_parse_file_asset",
            "SET bound_task_id = #{taskId},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{fileAssetId}",
            "  AND is_deleted = b'0'",
            "  AND (bound_task_id IS NULL OR bound_task_id = #{taskId})"
    })
    int bindFileAssetToTask(
            @Param("fileAssetId") Long fileAssetId,
            @Param("taskId") Long taskId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_task (",
            "  id, task_no, document_title, target_plan_id, standard_version_id, data_scope_type, data_scope_key,",
            "  status, base_version_id, document_group_id, parent_task_id, iteration_no,",
            "  remark, idempotency_key, request_hash, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskNo}, #{documentTitle}, #{targetPlanId}, #{standardVersionId}, 'global', 'global:*',",
            "  'reading', #{baseVersionId}, #{documentGroupId}, #{parentTaskId}, #{iterationNo},",
            "  #{remark}, #{idempotencyKey}, #{requestHash}, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertTask(
            @Param("id") Long id,
            @Param("taskNo") String taskNo,
            @Param("documentTitle") String documentTitle,
            @Param("targetPlanId") Long targetPlanId,
            @Param("standardVersionId") Long standardVersionId,
            @Param("baseVersionId") Long baseVersionId,
            @Param("documentGroupId") Long documentGroupId,
            @Param("parentTaskId") Long parentTaskId,
            @Param("iterationNo") Integer iterationNo,
            @Param("remark") String remark,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_task_input (",
            "  id, task_id, input_type, input_role, file_asset_id, text_content, display_name, sort_no,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, #{inputType}, #{inputRole}, #{fileAssetId}, #{textContent}, #{displayName}, #{sortNo},",
            "  b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertTaskInput(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("inputType") String inputType,
            @Param("inputRole") String inputRole,
            @Param("fileAssetId") Long fileAssetId,
            @Param("textContent") String textContent,
            @Param("displayName") String displayName,
            @Param("sortNo") Integer sortNo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT",
            "  id, task_no, document_title, target_plan_id, standard_version_id,",
            "  data_scope_type, data_scope_key, status, base_version_id,",
            "  COALESCE(document_group_id, id) AS document_group_id, parent_task_id, COALESCE(NULLIF(iteration_no, 0), 1) AS iteration_no,",
            "  current_result_id,",
            "  failure_code, failure_message, parse_attempt_count, next_run_at, created_by",
            "FROM file_mgmt_parse_task",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    FileParseTaskRow selectTask(@Param("taskId") Long taskId);

    @Select({
            "SELECT",
            "  id, task_no, document_title, target_plan_id, standard_version_id,",
            "  data_scope_type, data_scope_key, status, base_version_id,",
            "  COALESCE(document_group_id, id) AS document_group_id, parent_task_id, COALESCE(NULLIF(iteration_no, 0), 1) AS iteration_no,",
            "  current_result_id,",
            "  failure_code, failure_message, parse_attempt_count, next_run_at, created_by",
            "FROM file_mgmt_parse_task",
            "WHERE status = 'parsing'",
            "  AND is_deleted = b'0'",
            "  AND (locked_at IS NULL OR TIMESTAMPDIFF(SECOND, locked_at, NOW()) >= #{staleTimeoutSeconds})",
            "ORDER BY COALESCE(locked_at, started_at, gmt_updated) ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<FileParseTaskRow> selectStaleParsingTasks(
            @Param("staleTimeoutSeconds") int staleTimeoutSeconds,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "  id, task_no, document_title, target_plan_id, standard_version_id,",
            "  data_scope_type, data_scope_key, status, base_version_id,",
            "  COALESCE(document_group_id, id) AS document_group_id, parent_task_id, COALESCE(NULLIF(iteration_no, 0), 1) AS iteration_no,",
            "  current_result_id,",
            "  failure_code, failure_message, parse_attempt_count, next_run_at, created_by",
            "FROM file_mgmt_parse_task",
            "WHERE status = 'failed'",
            "  AND is_deleted = b'0'",
            "  AND next_run_at IS NOT NULL",
            "  AND next_run_at <= NOW()",
            "  AND (",
            "    failure_code IN (",
            "      'PARSE_STALE_RETRYING',",
            "      'PARSE_AUTO_RETRY_DISPATCH_FAILED',",
            "      'OPENAI_REQUEST_TIMEOUT',",
            "      'OPENAI_HTTP_429',",
            "      'OPENAI_HTTP_500',",
            "      'OPENAI_HTTP_502',",
            "      'OPENAI_HTTP_503',",
            "      'OPENAI_HTTP_504'",
            "    )",
            "  )",
            "  AND NOT (",
            "    failure_code = 'OPENAI_HTTP_429'",
            "    AND (",
            "      failure_message LIKE '%usage_limit_reached%'",
            "      OR LOWER(failure_message) LIKE '%usage limit has been reached%'",
            "    )",
            "  )",
            "ORDER BY next_run_at ASC, gmt_updated ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<FileParseTaskRow> selectRetryableFailedParseTasks(@Param("limit") int limit);

    @Select({
            "SELECT COALESCE(MAX(iteration_no), 1)",
            "FROM file_mgmt_parse_task",
            "WHERE COALESCE(document_group_id, id) = #{documentGroupId}",
            "  AND is_deleted = b'0'"
    })
    int selectMaxIterationNo(@Param("documentGroupId") Long documentGroupId);

    @Select({
            "SELECT",
            "  ti.id, ti.task_id, ti.input_type, ti.input_role, ti.file_asset_id,",
            "  ti.text_content, ti.display_name, ti.sort_no,",
            "  fa.original_file_name, fa.content_type, fa.file_extension, fa.storage_key, fa.sha256_hash",
            "FROM file_mgmt_parse_task_input ti",
            "LEFT JOIN file_mgmt_parse_file_asset fa",
            "  ON fa.id = ti.file_asset_id",
            " AND fa.is_deleted = b'0'",
            "WHERE ti.task_id = #{taskId}",
            "  AND ti.is_deleted = b'0'",
            "ORDER BY ti.sort_no ASC, ti.id ASC"
    })
    List<FileParseTaskInputRow> selectTaskInputs(@Param("taskId") Long taskId);

    @Update({
            "DELETE FROM file_mgmt_parse_source_row",
            "WHERE task_id = #{taskId}"
    })
    int softDeleteSourceRowsByTask(
            @Param("taskId") Long taskId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_source_row (",
            "  id, task_id, task_input_id, file_asset_id, source_type, source_locator, page_no, sheet_name,",
            "  table_no, row_no, column_range, raw_text, raw_cells_json, source_hash, extractor_type, extractor_version,",
            "  sort_no, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, #{row.taskInputId}, #{row.fileAssetId}, #{row.sourceType}, #{row.sourceLocator}, #{row.pageNo}, #{row.sheetName},",
            "  #{row.tableNo}, #{row.rowNo}, #{row.columnRange}, #{row.rawText}, #{row.rawCellsJson}, #{row.sourceHash}, #{row.extractorType}, #{row.extractorVersion},",
            "  #{row.sortNo}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertSourceRow(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("row") FileParseSourceRowDraft row,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_source_row",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND (#{inputId} IS NULL OR task_input_id = #{inputId})",
            "  AND (#{sourceType} IS NULL OR source_type = #{sourceType})",
            "  AND (#{keyword} IS NULL OR raw_text LIKE CONCAT('%', #{keyword}, '%'))"
    })
    int countSourceRows(
            @Param("taskId") Long taskId,
            @Param("inputId") Long inputId,
            @Param("sourceType") String sourceType,
            @Param("keyword") String keyword
    );

    @Select({
            "SELECT",
            "  id, task_id, task_input_id AS input_id, file_asset_id, source_type, source_locator, page_no, sheet_name,",
            "  table_no, row_no, column_range, raw_text, raw_cells_json, source_hash, sort_no",
            "FROM file_mgmt_parse_source_row",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND (#{inputId} IS NULL OR task_input_id = #{inputId})",
            "  AND (#{sourceType} IS NULL OR source_type = #{sourceType})",
            "  AND (#{keyword} IS NULL OR raw_text LIKE CONCAT('%', #{keyword}, '%'))",
            "ORDER BY sort_no ASC, id ASC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseSourceRowView> selectSourceRows(
            @Param("taskId") Long taskId,
            @Param("inputId") Long inputId,
            @Param("sourceType") String sourceType,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT id",
            "FROM file_mgmt_parse_source_row",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'",
            "ORDER BY sort_no ASC, id ASC"
    })
    List<Long> selectSourceRowIds(@Param("taskId") Long taskId);

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_source_row",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int countSourceRowsByTask(@Param("taskId") Long taskId);

    @Update({
            "UPDATE file_mgmt_parse_ai_chunk",
            "SET status = 'failed',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE task_id = #{taskId}",
            "  AND status IN ('pending', 'running', 'retrying')"
    })
    int markOpenAiChunksFailedByTask(
            @Param("taskId") Long taskId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "DELETE FROM file_mgmt_parse_ai_chunk",
            "WHERE task_id = #{taskId}"
    })
    int deleteAiChunksByTask(@Param("taskId") Long taskId);

    @Insert({
            "INSERT INTO file_mgmt_parse_ai_chunk (",
            "  id, task_id, result_id, chunk_no, chunk_type, source_row_ids_json, source_row_count,",
            "  prompt_hash, input_hash, model_provider, model_name, status, started_at,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, NULL, #{chunkNo}, #{chunkType}, #{sourceRowIdsJson}, #{sourceRowCount},",
            "  #{promptHash}, #{inputHash}, #{modelProvider}, #{modelName}, 'running', NOW(),",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertAiChunk(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("chunkNo") Integer chunkNo,
            @Param("chunkType") String chunkType,
            @Param("sourceRowIdsJson") String sourceRowIdsJson,
            @Param("sourceRowCount") Integer sourceRowCount,
            @Param("promptHash") String promptHash,
            @Param("inputHash") String inputHash,
            @Param("modelProvider") String modelProvider,
            @Param("modelName") String modelName,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_ai_chunk",
            "SET result_id = #{resultId},",
            "    status = 'succeeded',",
            "    output_item_count = #{outputItemCount},",
            "    response_hash = #{responseHash},",
            "    raw_response_json = #{rawResponseJson},",
            "    finished_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{chunkId}",
            "  AND task_id = #{taskId}"
    })
    int markAiChunkSucceeded(
            @Param("chunkId") Long chunkId,
            @Param("taskId") Long taskId,
            @Param("resultId") Long resultId,
            @Param("outputItemCount") Integer outputItemCount,
            @Param("responseHash") String responseHash,
            @Param("rawResponseJson") String rawResponseJson,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_ai_chunk",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    finished_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{chunkId}",
            "  AND task_id = #{taskId}"
    })
    int markAiChunkFailed(
            @Param("chunkId") Long chunkId,
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_ai_chunk",
            "WHERE task_id = #{taskId}",
            "  AND (#{status} IS NULL OR status = #{status})"
    })
    int countAiChunks(
            @Param("taskId") Long taskId,
            @Param("status") String status
    );

    @Select({
            "SELECT",
            "  id, task_id, result_id, chunk_no, chunk_type, source_row_count, prompt_hash, input_hash,",
            "  model_provider, model_name, status, output_item_count, response_hash, failure_code, failure_message,",
            "  started_at, finished_at",
            "FROM file_mgmt_parse_ai_chunk",
            "WHERE task_id = #{taskId}",
            "  AND (#{status} IS NULL OR status = #{status})",
            "ORDER BY chunk_no ASC, id ASC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseAiChunkView> selectAiChunks(
            @Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT COALESCE(SUM(output_item_count), 0)",
            "FROM file_mgmt_parse_ai_chunk",
            "WHERE task_id = #{taskId}",
            "  AND status = 'succeeded'"
    })
    int sumAiChunkOutputItems(@Param("taskId") Long taskId);

    @Select({
            "SELECT COALESCE(SUM(source_row_count), 0)",
            "FROM file_mgmt_parse_ai_chunk",
            "WHERE task_id = #{taskId}",
            "  AND status = 'succeeded'"
    })
    int sumSucceededAiChunkSourceRows(@Param("taskId") Long taskId);

    @Select({
            "SELECT COALESCE(SUM(source_row_count), 0)",
            "FROM file_mgmt_parse_ai_chunk",
            "WHERE task_id = #{taskId}"
    })
    int sumAiChunkSourceRows(@Param("taskId") Long taskId);

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'parsing',",
            "    parse_attempt_count = parse_attempt_count + 1,",
            "    locked_by = #{lockOwner},",
            "    locked_at = NOW(),",
            "    started_at = COALESCE(started_at, NOW()),",
            "    finished_at = NULL,",
            "    next_run_at = NULL,",
            "    failure_code = NULL,",
            "    failure_message = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND status IN ('reading', 'failed')",
            "  AND (locked_by IS NULL OR locked_at < DATE_SUB(NOW(), INTERVAL 15 MINUTE))"
    })
    int markTaskParsing(
            @Param("taskId") Long taskId,
            @Param("lockOwner") String lockOwner,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET locked_by = NULL,",
            "    locked_at = NULL,",
            "    next_run_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND locked_by = #{lockOwner}"
    })
    int releaseTaskParsingLock(
            @Param("taskId") Long taskId,
            @Param("lockOwner") String lockOwner,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET locked_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND status = 'parsing'",
            "  AND locked_by = #{lockOwner}"
    })
    int touchTaskParsingLock(
            @Param("taskId") Long taskId,
            @Param("lockOwner") String lockOwner,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    finished_at = NOW(),",
            "    next_run_at = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND locked_by = #{lockOwner}"
    })
    int markTaskFailed(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("lockOwner") String lockOwner,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    finished_at = NOW(),",
            "    next_run_at = DATE_ADD(NOW(), INTERVAL #{retryDelaySeconds} SECOND),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND locked_by = #{lockOwner}"
    })
    int markTaskFailedRetryable(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("lockOwner") String lockOwner,
            @Param("retryDelaySeconds") int retryDelaySeconds,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    finished_at = NOW(),",
            "    next_run_at = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status = 'failed'",
            "  AND is_deleted = b'0'"
    })
    int markRetryableParseTaskFinalFailed(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    finished_at = NOW(),",
            "    next_run_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status = 'parsing'",
            "  AND is_deleted = b'0'",
            "  AND (locked_at IS NULL OR TIMESTAMPDIFF(SECOND, locked_at, NOW()) >= #{staleTimeoutSeconds})"
    })
    int resetStaleParsingTaskForRetry(
            @Param("taskId") Long taskId,
            @Param("staleTimeoutSeconds") int staleTimeoutSeconds,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    finished_at = NOW(),",
            "    next_run_at = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status = 'parsing'",
            "  AND is_deleted = b'0'",
            "  AND (locked_at IS NULL OR TIMESTAMPDIFF(SECOND, locked_at, NOW()) >= #{staleTimeoutSeconds})"
    })
    int markStaleParsingTaskFinalFailed(
            @Param("taskId") Long taskId,
            @Param("staleTimeoutSeconds") int staleTimeoutSeconds,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    finished_at = NOW(),",
            "    next_run_at = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status = 'failed'",
            "  AND is_deleted = b'0'"
    })
    int markAutoRetryDispatchFailed(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_result (",
            "  id, result_no, task_id, target_plan_id, standard_version_id, base_version_id,",
            "  data_scope_type, data_scope_key, parser_type, parser_model, status,",
            "  summary_json, raw_result_json, validation_summary_json, created_by, gmt_create",
            ") VALUES (",
            "  #{id}, #{resultNo}, #{taskId}, #{targetPlanId}, #{standardVersionId}, #{baseVersionId},",
            "  #{dataScopeType}, #{dataScopeKey}, #{parserType}, #{parserModel}, 'created',",
            "  #{summaryJson}, #{rawResultJson}, #{validationSummaryJson}, #{operatorUserId}, NOW()",
            ")"
    })
    int insertResult(
            @Param("id") Long id,
            @Param("resultNo") String resultNo,
            @Param("taskId") Long taskId,
            @Param("targetPlanId") Long targetPlanId,
            @Param("standardVersionId") Long standardVersionId,
            @Param("baseVersionId") Long baseVersionId,
            @Param("dataScopeType") String dataScopeType,
            @Param("dataScopeKey") String dataScopeKey,
            @Param("parserType") String parserType,
            @Param("parserModel") String parserModel,
            @Param("summaryJson") String summaryJson,
            @Param("rawResultJson") String rawResultJson,
            @Param("validationSummaryJson") String validationSummaryJson,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_result_item (",
            "  id, result_id, task_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  change_type, review_status, confidence, validation_status, normalized_payload_json,",
            "  old_payload_json, changed_field_keys_json, effective_payload_json, effective_validation_status, effective_payload_hash,",
            "  evidence_json, validation_error_json, sort_no, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{resultId}, #{taskId}, #{targetPlanId}, #{itemType}, #{naturalKey}, #{naturalKeyHash},",
            "  #{changeType}, #{reviewStatus}, #{confidence}, #{validationStatus}, #{normalizedPayloadJson},",
            "  #{oldPayloadJson}, #{changedFieldKeysJson}, #{effectivePayloadJson}, #{effectiveValidationStatus}, #{effectivePayloadHash},",
            "  #{evidenceJson}, #{validationErrorJson}, #{sortNo}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertResultItem(
            @Param("id") Long id,
            @Param("resultId") Long resultId,
            @Param("taskId") Long taskId,
            @Param("targetPlanId") Long targetPlanId,
            @Param("itemType") String itemType,
            @Param("naturalKey") String naturalKey,
            @Param("naturalKeyHash") String naturalKeyHash,
            @Param("changeType") String changeType,
            @Param("reviewStatus") String reviewStatus,
            @Param("confidence") String confidence,
            @Param("validationStatus") String validationStatus,
            @Param("normalizedPayloadJson") String normalizedPayloadJson,
            @Param("oldPayloadJson") String oldPayloadJson,
            @Param("changedFieldKeysJson") String changedFieldKeysJson,
            @Param("effectivePayloadJson") String effectivePayloadJson,
            @Param("effectiveValidationStatus") String effectiveValidationStatus,
            @Param("effectivePayloadHash") String effectivePayloadHash,
            @Param("evidenceJson") String evidenceJson,
            @Param("validationErrorJson") String validationErrorJson,
            @Param("sortNo") Integer sortNo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_result_item_source (",
            "  id, task_id, result_id, result_item_id, source_row_id, ai_chunk_id, source_role, confidence, evidence_text,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, #{resultId}, #{resultItemId}, #{sourceRowId}, #{aiChunkId}, #{sourceRole}, #{confidence}, #{evidenceText},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertResultItemSource(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("resultId") Long resultId,
            @Param("resultItemId") Long resultItemId,
            @Param("sourceRowId") Long sourceRowId,
            @Param("aiChunkId") Long aiChunkId,
            @Param("sourceRole") String sourceRole,
            @Param("confidence") String confidence,
            @Param("evidenceText") String evidenceText,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_validation_issue",
            "SET is_deleted = b'1',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteValidationIssuesByTask(
            @Param("taskId") Long taskId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_validation_issue (",
            "  id, task_id, result_id, result_item_id, source_row_id, ai_chunk_id, issue_type, severity,",
            "  field_key, message, details_json, resolved_status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, #{resultId}, #{resultItemId}, #{sourceRowId}, #{aiChunkId}, #{issueType}, #{severity},",
            "  #{fieldKey}, #{message}, #{detailsJson}, 'open', b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertValidationIssue(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("resultId") Long resultId,
            @Param("resultItemId") Long resultItemId,
            @Param("sourceRowId") Long sourceRowId,
            @Param("aiChunkId") Long aiChunkId,
            @Param("issueType") String issueType,
            @Param("severity") String severity,
            @Param("fieldKey") String fieldKey,
            @Param("message") String message,
            @Param("detailsJson") String detailsJson,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_validation_issue",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND (#{severity} IS NULL OR severity = #{severity})",
            "  AND (#{issueType} IS NULL OR issue_type = #{issueType})",
            "  AND (#{resolvedStatus} IS NULL OR resolved_status = #{resolvedStatus})"
    })
    int countValidationIssues(
            @Param("taskId") Long taskId,
            @Param("severity") String severity,
            @Param("issueType") String issueType,
            @Param("resolvedStatus") String resolvedStatus
    );

    @Select({
            "SELECT",
            "  id, task_id, result_id, result_item_id, source_row_id, ai_chunk_id, issue_type, severity,",
            "  field_key, message, details_json, resolved_status",
            "FROM file_mgmt_parse_validation_issue",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND (#{severity} IS NULL OR severity = #{severity})",
            "  AND (#{issueType} IS NULL OR issue_type = #{issueType})",
            "  AND (#{resolvedStatus} IS NULL OR resolved_status = #{resolvedStatus})",
            "ORDER BY CASE severity WHEN 'hard_error' THEN 1 WHEN 'warning' THEN 2 ELSE 3 END, id ASC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseValidationIssueView> selectValidationIssues(
            @Param("taskId") Long taskId,
            @Param("severity") String severity,
            @Param("issueType") String issueType,
            @Param("resolvedStatus") String resolvedStatus,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_validation_issue",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND severity = 'hard_error'",
            "  AND resolved_status = 'open'"
    })
    int countOpenHardValidationIssues(@Param("taskId") Long taskId);

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_result_item",
            "WHERE result_id = #{resultId}",
            "  AND is_deleted = b'0'",
            "  AND (#{reviewStatus} IS NULL OR review_status = #{reviewStatus})",
            "  AND (#{changeType} IS NULL OR change_type = #{changeType})"
    })
    int countResultItems(
            @Param("resultId") Long resultId,
            @Param("reviewStatus") String reviewStatus,
            @Param("changeType") String changeType
    );

    @Select({
            "SELECT",
            "  id, result_id, task_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  change_type, review_status, current_review_id, confidence, validation_status,",
            "  normalized_payload_json, old_payload_json, changed_field_keys_json,",
            "  effective_payload_json, effective_validation_status, effective_payload_hash, evidence_json, validation_error_json, sort_no",
            "FROM file_mgmt_parse_result_item",
            "WHERE result_id = #{resultId}",
            "  AND is_deleted = b'0'",
            "  AND (#{reviewStatus} IS NULL OR review_status = #{reviewStatus})",
            "  AND (#{changeType} IS NULL OR change_type = #{changeType})",
            "ORDER BY sort_no ASC, id ASC",
            "LIMIT #{limit} OFFSET #{offset}"
    })
    List<FileParseResultItemRow> selectResultItems(
            @Param("resultId") Long resultId,
            @Param("reviewStatus") String reviewStatus,
            @Param("changeType") String changeType,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "SELECT",
            "  id, result_id, task_id, target_plan_id, item_type, natural_key, natural_key_hash,",
            "  change_type, review_status, current_review_id, confidence, validation_status,",
            "  normalized_payload_json, old_payload_json, changed_field_keys_json,",
            "  effective_payload_json, effective_validation_status, effective_payload_hash, evidence_json, validation_error_json, sort_no",
            "FROM file_mgmt_parse_result_item",
            "WHERE task_id = #{taskId}",
            "  AND id = #{itemId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    FileParseResultItemRow selectResultItem(
            @Param("taskId") Long taskId,
            @Param("itemId") Long itemId
    );

    @Select({
            "SELECT",
            "  id, result_item_id, result_id, task_id, review_action, review_status,",
            "  override_payload_json, effective_payload_json, validation_status, validation_message,",
            "  review_note, expected_result_id, idempotency_key, request_hash",
            "FROM file_mgmt_parse_item_review",
            "WHERE task_id = #{taskId}",
            "  AND result_item_id = #{resultItemId}",
            "  AND idempotency_key = #{idempotencyKey}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    FileParseItemReviewRow selectReviewByIdempotency(
            @Param("taskId") Long taskId,
            @Param("resultItemId") Long resultItemId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Update({
            "UPDATE file_mgmt_parse_item_review",
            "SET is_current = b'0',",
            "    current_marker = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE result_item_id = #{resultItemId}",
            "  AND is_deleted = b'0'",
            "  AND is_current = b'1'"
    })
    int clearCurrentReview(
            @Param("resultItemId") Long resultItemId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_item_review (",
            "  id, result_item_id, result_id, task_id, review_action, review_status,",
            "  override_payload_json, effective_payload_json, validation_status, validation_message,",
            "  review_note, expected_result_id, idempotency_key, request_hash,",
            "  is_current, current_marker, reviewed_by, reviewed_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{resultItemId}, #{resultId}, #{taskId}, #{reviewAction}, #{reviewStatus},",
            "  #{overridePayloadJson}, #{effectivePayloadJson}, #{validationStatus}, #{validationMessage},",
            "  #{reviewNote}, #{expectedResultId}, #{idempotencyKey}, #{requestHash},",
            "  b'1', 'current', #{operatorUserId}, NOW(), b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertItemReview(
            @Param("id") Long id,
            @Param("resultItemId") Long resultItemId,
            @Param("resultId") Long resultId,
            @Param("taskId") Long taskId,
            @Param("reviewAction") String reviewAction,
            @Param("reviewStatus") String reviewStatus,
            @Param("overridePayloadJson") String overridePayloadJson,
            @Param("effectivePayloadJson") String effectivePayloadJson,
            @Param("validationStatus") String validationStatus,
            @Param("validationMessage") String validationMessage,
            @Param("reviewNote") String reviewNote,
            @Param("expectedResultId") Long expectedResultId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_result_item",
            "SET current_review_id = #{reviewId},",
            "    review_status = #{reviewStatus},",
            "    effective_payload_json = #{effectivePayloadJson},",
            "    effective_validation_status = #{effectiveValidationStatus},",
            "    effective_payload_hash = #{effectivePayloadHash},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{resultItemId}",
            "  AND is_deleted = b'0'"
    })
    int updateResultItemReviewCache(
            @Param("resultItemId") Long resultItemId,
            @Param("reviewId") Long reviewId,
            @Param("reviewStatus") String reviewStatus,
            @Param("effectivePayloadJson") String effectivePayloadJson,
            @Param("effectiveValidationStatus") String effectiveValidationStatus,
            @Param("effectivePayloadHash") String effectivePayloadHash,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM file_mgmt_parse_result_item",
            "WHERE result_id = #{resultId}",
            "  AND is_deleted = b'0'",
            "  AND (",
            "    review_status IN ('pending', 'needs_fix', 'hard_error')",
            "    OR COALESCE(effective_validation_status, validation_status) = 'hard_error'",
            "    OR (change_type = 'conflict' AND review_status NOT IN ('confirmed', 'rejected', 'keep_old'))",
            "  )"
    })
    int countBlockingResultItems(@Param("resultId") Long resultId);

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = #{status},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND status IN ('review_required', 'ready_to_publish')"
    })
    int updateTaskStatusAfterReview(
            @Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE file_mgmt_parse_task t",
            "SET t.status = CASE",
            "      WHEN EXISTS (",
            "        SELECT 1",
            "        FROM file_mgmt_parse_result_item ri",
            "        WHERE ri.result_id = t.current_result_id",
            "          AND ri.is_deleted = b'0'",
            "          AND (",
            "            ri.review_status IN ('pending', 'needs_fix', 'hard_error')",
            "            OR COALESCE(ri.effective_validation_status, ri.validation_status) = 'hard_error'",
            "            OR (ri.change_type = 'conflict' AND ri.review_status NOT IN ('confirmed', 'rejected', 'keep_old'))",
            "          )",
            "      )",
            "      OR EXISTS (",
            "        SELECT 1",
            "        FROM file_mgmt_parse_validation_issue vi",
            "        WHERE vi.task_id = t.id",
            "          AND vi.is_deleted = b'0'",
            "          AND vi.severity = 'hard_error'",
            "          AND vi.resolved_status = 'open'",
            "      ) THEN 'review_required'",
            "      ELSE 'ready_to_publish'",
            "    END,",
            "    t.updated_by = #{operatorUserId},",
            "    t.gmt_updated = NOW()",
            "WHERE t.id = #{taskId}",
            "  AND t.is_deleted = b'0'",
            "  AND t.status IN ('review_required', 'ready_to_publish')"
    })
    int updateTaskStatusAfterReviewFromItems(
            @Param("taskId") Long taskId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO file_mgmt_parse_current_result (task_id, result_id, gmt_create, gmt_updated)",
            "VALUES (#{taskId}, #{resultId}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  result_id = VALUES(result_id),",
            "  gmt_updated = NOW()"
    })
    int upsertCurrentResult(
            @Param("taskId") Long taskId,
            @Param("resultId") Long resultId
    );

    @Update({
            "UPDATE file_mgmt_parse_task",
            "SET status = 'review_required',",
            "    current_result_id = #{resultId},",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    finished_at = NOW(),",
            "    next_run_at = NULL,",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "  AND locked_by = #{lockOwner}"
    })
    int markTaskReviewRequired(
            @Param("taskId") Long taskId,
            @Param("resultId") Long resultId,
            @Param("lockOwner") String lockOwner,
            @Param("operatorUserId") Long operatorUserId
    );
}
