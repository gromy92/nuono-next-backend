package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productselection.Ali1688CollectionRecords;
import com.nuono.next.productselection.Ali1688RealPriceSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface Ali1688CollectionMapper {

    String TASK_SELECT = ""
            + "SELECT task.id, task.source_collection_id, task.current_task_key, task.owner_user_id, task.logical_store_id, task.task_no, "
            + "task.status, task.progress_percent, task.search_mode, task.source_image_url, task.selected_image_count, "
            + "task.scanned_count, task.candidate_count, task.recommended_count, task.failure_code, task.failure_message, "
            + "task.official_search_url, task.search_image_id, task.search_image_id_list_json, task.raw_search_snapshot_json, "
            + "DATE_FORMAT(task.started_at, '%Y-%m-%d %H:%i') AS started_at, DATE_FORMAT(task.finished_at, '%Y-%m-%d %H:%i') AS finished_at, "
            + "DATE_FORMAT(task.locked_at, '%Y-%m-%d %H:%i') AS locked_at, task.locked_by, task.attempt_count, task.created_by, task.updated_by, "
            + "source.collection_no AS source_collection_no, source.source_platform, source.source_title, source.source_title_cn, "
            + "source.source_url, source.page_url, COALESCE(store.project_name, '') AS store_name, "
            + "(SELECT site.store_code FROM logical_store_site site WHERE site.logical_store_id = task.logical_store_id AND site.is_deleted = b'0' ORDER BY site.is_reference_site DESC, site.id ASC LIMIT 1) AS store_code "
            + "FROM product_selection_ali1688_collection_task task "
            + "JOIN product_selection_source_collection source ON source.id = task.source_collection_id AND source.is_deleted = b'0' "
            + "LEFT JOIN logical_store store ON store.id = task.logical_store_id AND store.is_deleted = b'0' ";

    String CANDIDATE_SELECT = ""
            + "SELECT candidate.id, candidate.task_id, candidate.source_collection_id, candidate.owner_user_id, candidate.logical_store_id, "
            + "candidate.rank_no, candidate.selected_rank_no, candidate.level, candidate.offer_id, candidate.candidate_url, candidate.candidate_url_hash, "
            + "candidate.active_candidate_key, candidate.title, candidate.supplier_name, candidate.price_text, candidate.price_min, candidate.price_max, "
            + "candidate.moq_text, candidate.moq_value, candidate.location_text, candidate.main_image_url, candidate.image_urls_json, candidate.badges_json, "
            + "candidate.sku_snapshot_json, candidate.supplier_snapshot_json, candidate.logistics_snapshot_json, candidate.rule_score, candidate.total_score, "
            + "candidate.match_score, candidate.spec_score, candidate.price_score, candidate.moq_score, candidate.supplier_score, candidate.delivery_score, "
            + "candidate.score_status, candidate.score_version, candidate.score_detail_json, candidate.ai_assessment_status, candidate.created_by, candidate.updated_by "
            + "FROM product_selection_ali1688_candidate candidate ";

    String AI_ASSESSMENT_SELECT = ""
            + "SELECT assessment.id, assessment.task_id, assessment.candidate_id, assessment.status, assessment.feature_code, assessment.operation_code, "
            + "assessment.prompt_version, assessment.schema_version, assessment.model_name, assessment.input_hash, assessment.input_snapshot_json, assessment.output_json, "
            + "assessment.match_score, assessment.spec_score, assessment.risk_level, assessment.failure_code, assessment.failure_message, "
            + "DATE_FORMAT(assessment.started_at, '%Y-%m-%d %H:%i') AS started_at, DATE_FORMAT(assessment.finished_at, '%Y-%m-%d %H:%i') AS finished_at, "
            + "DATE_FORMAT(assessment.locked_at, '%Y-%m-%d %H:%i') AS locked_at, assessment.locked_by, assessment.attempt_count, "
            + "DATE_FORMAT(assessment.next_run_at, '%Y-%m-%d %H:%i') AS next_run_at, assessment.created_by, assessment.updated_by "
            + "FROM product_selection_ali1688_candidate_ai_assessment assessment ";

    String PLUGIN_ASSIGNMENT_SELECT = ""
            + "SELECT assignment.id, assignment.task_id, assignment.source_collection_id, assignment.owner_user_id, assignment.logical_store_id, "
            + "assignment.active_assignment_key, assignment.assignment_code_hash, assignment.status, "
            + "DATE_FORMAT(assignment.expires_at, '%Y-%m-%d %H:%i:%s') AS expires_at, "
            + "DATE_FORMAT(assignment.started_at, '%Y-%m-%d %H:%i:%s') AS started_at, "
            + "DATE_FORMAT(assignment.finished_at, '%Y-%m-%d %H:%i:%s') AS finished_at, "
            + "assignment.failure_code, assignment.failure_message, assignment.raw_assignment_snapshot_json, assignment.created_by, assignment.updated_by, "
            + "assignment.submission_idempotency_key, assignment.submitted_candidate_count, assignment.accepted_candidate_count, assignment.rejected_candidate_count, "
            + "DATE_FORMAT(assignment.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at, "
            + "task.current_task_key AS task_current_task_key, task.task_no, task.status AS task_status, task.candidate_count AS task_candidate_count, "
            + "task.recommended_count AS task_recommended_count, task.source_image_url, "
            + "source.collection_no AS source_collection_no, source.source_platform, source.source_title, source.source_title_cn, "
            + "source.source_url, source.page_url, COALESCE(store.project_name, '') AS store_name, "
            + "(SELECT site.store_code FROM logical_store_site site WHERE site.logical_store_id = assignment.logical_store_id AND site.is_deleted = b'0' ORDER BY site.is_reference_site DESC, site.id ASC LIMIT 1) AS store_code "
            + "FROM product_selection_ali1688_plugin_assignment assignment "
            + "JOIN product_selection_ali1688_collection_task task ON task.id = assignment.task_id AND task.is_deleted = b'0' "
            + "JOIN product_selection_source_collection source ON source.id = assignment.source_collection_id AND source.is_deleted = b'0' "
            + "LEFT JOIN logical_store store ON store.id = assignment.logical_store_id AND store.is_deleted = b'0' ";

    String REAL_PRICE_SNAPSHOT_SELECT = ""
            + "SELECT snapshot.id, snapshot.task_id, snapshot.candidate_id, snapshot.source_collection_id, snapshot.owner_user_id, snapshot.logical_store_id, "
            + "snapshot.status, snapshot.safety_mode, snapshot.side_effect_policy, snapshot.source, snapshot.sku_text, snapshot.quantity, "
            + "snapshot.unit_price, snapshot.freight_price, snapshot.discount_price, snapshot.total_price, snapshot.currency, "
            + "snapshot.rmb_total_price, snapshot.exchange_rate_to_rmb, snapshot.region_text, snapshot.address_context_json, "
            + "DATE_FORMAT(snapshot.captured_at, '%Y-%m-%d %H:%i:%s') AS captured_at, snapshot.failure_code, snapshot.failure_message, "
            + "snapshot.raw_snapshot_json, snapshot.created_by, snapshot.updated_by "
            + "FROM product_selection_ali1688_real_price_snapshot snapshot ";

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextTaskId() {
        return nextId("product_selection_ali1688_collection_task", 87000L);
    }

    default Long nextCandidateId() {
        return nextId("product_selection_ali1688_candidate", 88000L);
    }

    default Long nextAiAssessmentId() {
        return nextId("product_selection_ali1688_candidate_ai_assessment", 89000L);
    }

    default Long nextPluginAssignmentId() {
        return nextId("product_selection_ali1688_plugin_assignment", 90000L);
    }

    default Long nextRealPriceSnapshotId() {
        return nextId("product_selection_ali1688_real_price_snapshot", 91000L);
    }

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("1688 采集 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }

    @Insert({
            "INSERT INTO product_selection_ali1688_collection_task (",
            "id, source_collection_id, current_task_key, owner_user_id, logical_store_id, task_no, status, progress_percent, search_mode,",
            "source_image_url, selected_image_count, scanned_count, candidate_count, recommended_count, failure_code, failure_message,",
            "raw_search_snapshot_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.sourceCollectionId}, #{row.currentTaskKey}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.taskNo}, #{row.status}, #{row.progressPercent}, #{row.searchMode},",
            "#{row.sourceImageUrl}, #{row.selectedImageCount}, #{row.scannedCount}, #{row.candidateCount}, #{row.recommendedCount}, #{row.failureCode}, #{row.failureMessage},",
            "#{row.rawSearchSnapshotJson}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertTask(@Param("row") Ali1688CollectionRecords.TaskRecord row);

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET current_task_key = NULL, status = 'superseded', locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE source_collection_id = #{sourceCollectionId} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int supersedeCurrentTask(@Param("sourceCollectionId") Long sourceCollectionId, @Param("updatedBy") Long updatedBy);

    @Select({TASK_SELECT, "WHERE task.source_collection_id = #{sourceCollectionId} AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.TaskRecord selectCurrentTaskBySourceId(@Param("sourceCollectionId") Long sourceCollectionId);

    @Select({TASK_SELECT, "WHERE task.id = #{taskId} AND task.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.TaskRecord selectTaskById(@Param("taskId") Long taskId);

    @Select({
            "<script>",
            TASK_SELECT,
            "WHERE task.logical_store_id = #{logicalStoreId} AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            "<if test='status != null and status != \"\"'> AND task.status = #{status}</if>",
            "ORDER BY task.gmt_updated DESC, task.id DESC LIMIT #{limit}",
            "</script>"
    })
    List<Ali1688CollectionRecords.TaskRecord> listCurrentTasks(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("status") String status,
            @Param("limit") Integer limit
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'queued', progress_percent = GREATEST(progress_percent, 5), source_image_url = #{sourceImageUrl},",
            "selected_image_count = #{selectedImageCount}, failure_code = NULL, failure_message = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE source_collection_id = #{sourceCollectionId} AND current_task_key IS NOT NULL AND status = 'waiting_source' AND is_deleted = b'0'"
    })
    int markCurrentTaskQueued(
            @Param("sourceCollectionId") Long sourceCollectionId,
            @Param("sourceImageUrl") String sourceImageUrl,
            @Param("selectedImageCount") Integer selectedImageCount,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'failed', progress_percent = 100, failure_code = #{failureCode}, failure_message = #{failureMessage},",
            "finished_at = NOW(), locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE source_collection_id = #{sourceCollectionId} AND current_task_key IS NOT NULL",
            "AND status IN ('waiting_source', 'queued', 'running') AND is_deleted = b'0'"
    })
    int markCurrentTaskFailed(
            @Param("sourceCollectionId") Long sourceCollectionId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id FROM product_selection_ali1688_collection_task",
            "WHERE current_task_key IS NOT NULL AND is_deleted = b'0' AND status IN ('queued', 'running')",
            "AND attempt_count >= #{maxAttempts} AND locked_at IS NOT NULL AND locked_at < DATE_SUB(NOW(), INTERVAL #{lockTimeoutMinutes} MINUTE)",
            "ORDER BY locked_at ASC, id ASC LIMIT #{limit}"
    })
    List<Long> listExpiredOverRetryTaskIds(
            @Param("maxAttempts") Integer maxAttempts,
            @Param("lockTimeoutMinutes") Integer lockTimeoutMinutes,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT id FROM product_selection_ali1688_collection_task",
            "WHERE current_task_key IS NOT NULL AND is_deleted = b'0' AND attempt_count < #{maxAttempts}",
            "AND (status = 'queued' OR (status = 'running' AND locked_at < DATE_SUB(NOW(), INTERVAL #{lockTimeoutMinutes} MINUTE)))",
            "ORDER BY gmt_updated ASC, id ASC LIMIT #{limit}"
    })
    List<Long> listClaimableTaskIds(
            @Param("maxAttempts") Integer maxAttempts,
            @Param("lockTimeoutMinutes") Integer lockTimeoutMinutes,
            @Param("limit") Integer limit
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'running', progress_percent = GREATEST(progress_percent, 20), started_at = COALESCE(started_at, NOW()),",
            "locked_at = NOW(), locked_by = #{lockedBy}, attempt_count = attempt_count + 1, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND current_task_key IS NOT NULL AND is_deleted = b'0' AND attempt_count < #{maxAttempts}",
            "AND (status = 'queued' OR (status = 'running' AND locked_at < DATE_SUB(NOW(), INTERVAL #{lockTimeoutMinutes} MINUTE)))"
    })
    int claimTask(
            @Param("taskId") Long taskId,
            @Param("lockedBy") String lockedBy,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("lockTimeoutMinutes") Integer lockTimeoutMinutes
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET search_mode = #{searchMode}, official_search_url = #{officialSearchUrl}, search_image_id = #{searchImageId},",
            "search_image_id_list_json = #{searchImageIdListJson}, raw_search_snapshot_json = #{rawSearchSnapshotJson}, scanned_count = #{scannedCount}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND locked_by = #{lockedBy} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int updateSearchSnapshot(
            @Param("taskId") Long taskId,
            @Param("lockedBy") String lockedBy,
            @Param("searchMode") String searchMode,
            @Param("officialSearchUrl") String officialSearchUrl,
            @Param("searchImageId") String searchImageId,
            @Param("searchImageIdListJson") String searchImageIdListJson,
            @Param("rawSearchSnapshotJson") String rawSearchSnapshotJson,
            @Param("scannedCount") Integer scannedCount
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = #{status}, progress_percent = 100, scanned_count = #{scannedCount}, candidate_count = #{candidateCount},",
            "recommended_count = #{recommendedCount}, failure_code = #{failureCode}, failure_message = #{failureMessage}, finished_at = NOW(),",
            "locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND locked_by = #{lockedBy} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int markTaskCompleted(
            @Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("scannedCount") Integer scannedCount,
            @Param("candidateCount") Integer candidateCount,
            @Param("recommendedCount") Integer recommendedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'failed', progress_percent = 100, failure_code = #{failureCode}, failure_message = #{failureMessage},",
            "finished_at = NOW(), locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int markTaskFailed(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'failed', progress_percent = 100, failure_code = #{failureCode}, failure_message = #{failureMessage},",
            "finished_at = NOW(), locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND locked_by = #{lockedBy} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int markTaskFailedByClaimedTask(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'queued', progress_percent = 5, failure_code = #{failureCode}, failure_message = #{failureMessage},",
            "locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND locked_by = #{lockedBy} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int markTaskRetryableFailure(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'queued', progress_percent = 5, failure_code = NULL, failure_message = NULL, finished_at = NULL,",
            "locked_at = NULL, locked_by = NULL, attempt_count = 0, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND current_task_key IS NOT NULL AND status = 'failed' AND is_deleted = b'0'"
    })
    int retryFailedTask(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

    @Insert({
            "INSERT INTO product_selection_ali1688_plugin_assignment (",
            "id, task_id, source_collection_id, owner_user_id, logical_store_id, active_assignment_key, assignment_code_hash,",
            "status, expires_at, started_at, finished_at, failure_code, failure_message, raw_assignment_snapshot_json,",
            "submission_idempotency_key, submitted_candidate_count, accepted_candidate_count, rejected_candidate_count,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.taskId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.activeAssignmentKey}, #{row.assignmentCodeHash},",
            "#{row.status}, #{row.expiresAt}, NULL, NULL, #{row.failureCode}, #{row.failureMessage}, #{row.rawAssignmentSnapshotJson},",
            "#{row.submissionIdempotencyKey}, 0, 0, 0,",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertPluginAssignment(@Param("row") Ali1688CollectionRecords.PluginAssignmentRecord row);

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'expired', active_assignment_key = NULL, finished_at = COALESCE(finished_at, NOW()),",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND active_assignment_key IS NOT NULL AND is_deleted = b'0'",
            "AND status IN ('created', 'running')"
    })
    int expireCurrentPluginAssignmentsByTask(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

    @Select({PLUGIN_ASSIGNMENT_SELECT, "WHERE assignment.id = #{assignmentId} AND assignment.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.PluginAssignmentRecord selectPluginAssignmentById(@Param("assignmentId") Long assignmentId);

    @Select({PLUGIN_ASSIGNMENT_SELECT, "WHERE assignment.assignment_code_hash = #{assignmentCodeHash} AND assignment.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.PluginAssignmentRecord selectPluginAssignmentByCodeHash(@Param("assignmentCodeHash") String assignmentCodeHash);

    @Select({
            PLUGIN_ASSIGNMENT_SELECT,
            "WHERE assignment.task_id = #{taskId} AND assignment.is_deleted = b'0'",
            "ORDER BY CASE WHEN assignment.active_assignment_key IS NOT NULL THEN 0 ELSE 1 END, assignment.gmt_updated DESC, assignment.id DESC LIMIT 1"
    })
    Ali1688CollectionRecords.PluginAssignmentRecord selectLatestPluginAssignmentByTask(@Param("taskId") Long taskId);

    @Select({
            PLUGIN_ASSIGNMENT_SELECT,
            "WHERE assignment.created_by = #{operatorUserId} AND assignment.is_deleted = b'0'",
            "AND assignment.active_assignment_key = CAST(task.id AS CHAR)",
            "AND assignment.status IN ('created', 'running')",
            "AND assignment.expires_at > NOW()",
            "AND task.current_task_key IS NOT NULL",
            "ORDER BY assignment.gmt_updated DESC, assignment.id DESC LIMIT #{limit}"
    })
    List<Ali1688CollectionRecords.PluginAssignmentRecord> listCurrentPluginAssignmentsByCreatedBy(
            @Param("operatorUserId") Long operatorUserId,
            @Param("limit") Integer limit
    );

    @Select({
            PLUGIN_ASSIGNMENT_SELECT,
            "WHERE assignment.created_by = #{operatorUserId} AND assignment.is_deleted = b'0'",
            "ORDER BY assignment.gmt_updated DESC, assignment.id DESC LIMIT #{limit}"
    })
    List<Ali1688CollectionRecords.PluginAssignmentRecord> listLatestPluginAssignmentsByCreatedBy(
            @Param("operatorUserId") Long operatorUserId,
            @Param("limit") Integer limit
    );

    @Select({
            TASK_SELECT,
            "WHERE task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            "AND task.status IN ('waiting_source', 'queued', 'running', 'failed', 'partial_success')",
            "ORDER BY task.gmt_updated DESC, task.id DESC LIMIT #{limit}"
    })
    List<Ali1688CollectionRecords.TaskRecord> listCurrentPluginAssignableTasks(@Param("limit") Integer limit);

    @Select({
            TASK_SELECT,
            "WHERE task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            "AND task.status IN ('waiting_source', 'queued', 'running', 'failed', 'partial_success')",
            "AND EXISTS (",
            "  SELECT 1",
            "  FROM logical_store_site visible_site",
            "  WHERE visible_site.logical_store_id = task.logical_store_id",
            "    AND visible_site.is_deleted = b'0'",
            "    AND (",
            "      EXISTS (",
            "        SELECT 1",
            "        FROM logical_store owner_store",
            "        WHERE owner_store.id = visible_site.logical_store_id",
            "          AND owner_store.owner_user_id = #{operatorUserId}",
            "          AND owner_store.is_deleted = b'0'",
            "      )",
            "      OR EXISTS (",
            "        SELECT 1",
            "        FROM user_project owner_project",
            "        JOIN user_store owner_site",
            "          ON owner_site.user_id = owner_project.user_id",
            "         AND owner_site.project_code = owner_project.project_code",
            "         AND owner_site.is_deleted = 0",
            "        WHERE owner_project.user_id = #{operatorUserId}",
            "          AND owner_project.is_deleted = 0",
            "          AND owner_site.store_code COLLATE utf8mb4_unicode_ci = visible_site.store_code COLLATE utf8mb4_unicode_ci",
            "        UNION ALL",
            "        SELECT 1",
            "        FROM user_project_access access",
            "        JOIN user_project member_project",
            "          ON member_project.id = access.project_id",
            "         AND member_project.is_deleted = 0",
            "        JOIN user_store member_site",
            "          ON member_site.user_id = member_project.user_id",
            "         AND member_site.project_code = member_project.project_code",
            "         AND member_site.is_deleted = 0",
            "        WHERE access.user_id = #{operatorUserId}",
            "          AND access.is_deleted = 0",
            "          AND member_site.store_code COLLATE utf8mb4_unicode_ci = visible_site.store_code COLLATE utf8mb4_unicode_ci",
            "      )",
            "    )",
            ")",
            "ORDER BY task.gmt_updated DESC, task.id DESC LIMIT #{limit}"
    })
    List<Ali1688CollectionRecords.TaskRecord> listCurrentPluginAssignableTasksByOperator(
            @Param("operatorUserId") Long operatorUserId,
            @Param("limit") Integer limit
    );

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'running', started_at = COALESCE(started_at, NOW()), updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND active_assignment_key IS NOT NULL AND is_deleted = b'0'",
            "AND status IN ('created', 'running')"
    })
    int markPluginAssignmentRunning(@Param("assignmentId") Long assignmentId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'failed', active_assignment_key = NULL, failure_code = #{failureCode}, failure_message = #{failureMessage},",
            "finished_at = NOW(), updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND active_assignment_key IS NOT NULL AND is_deleted = b'0'",
            "AND status IN ('created', 'running')"
    })
    int markPluginAssignmentFailed(
            @Param("assignmentId") Long assignmentId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'expired', active_assignment_key = NULL, finished_at = COALESCE(finished_at, NOW()),",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND active_assignment_key IS NOT NULL AND is_deleted = b'0'",
            "AND status IN ('created', 'running')"
    })
    int expirePluginAssignment(@Param("assignmentId") Long assignmentId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'cancelled', active_assignment_key = NULL, finished_at = COALESCE(finished_at, NOW()),",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND active_assignment_key IS NOT NULL AND is_deleted = b'0'",
            "AND status IN ('created', 'running')"
    })
    int cancelPluginAssignment(@Param("assignmentId") Long assignmentId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'accepted', active_assignment_key = NULL, finished_at = NOW(),",
            "submission_idempotency_key = #{idempotencyKey}, submitted_candidate_count = #{acceptedCandidateCount} + #{rejectedCandidateCount},",
            "accepted_candidate_count = #{acceptedCandidateCount}, rejected_candidate_count = #{rejectedCandidateCount},",
            "raw_assignment_snapshot_json = #{rawAssignmentSnapshotJson}, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND active_assignment_key IS NOT NULL AND is_deleted = b'0'",
            "AND status IN ('created', 'running')"
    })
    int markPluginAssignmentAccepted(
            @Param("assignmentId") Long assignmentId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("acceptedCandidateCount") Integer acceptedCandidateCount,
            @Param("rejectedCandidateCount") Integer rejectedCandidateCount,
            @Param("rawAssignmentSnapshotJson") String rawAssignmentSnapshotJson,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate candidate",
            "SET candidate.is_deleted = b'1', candidate.active_candidate_key = NULL, candidate.updated_by = #{updatedBy}, candidate.gmt_updated = NOW()",
            "WHERE candidate.task_id = #{taskId} AND candidate.is_deleted = b'0'",
            "AND EXISTS (",
            "  SELECT 1 FROM product_selection_ali1688_plugin_assignment assignment",
            "  JOIN product_selection_ali1688_collection_task task ON task.id = assignment.task_id",
            "  WHERE assignment.id = #{assignmentId} AND assignment.task_id = #{taskId}",
            "  AND assignment.active_assignment_key = CAST(task.id AS CHAR)",
            "  AND assignment.is_deleted = b'0' AND assignment.status IN ('created', 'running')",
            "  AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            ")"
    })
    int softDeleteCandidatesByCurrentPluginAssignment(
            @Param("taskId") Long taskId,
            @Param("updatedBy") Long updatedBy,
            @Param("assignmentId") Long assignmentId
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET is_deleted = b'1', active_candidate_key = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND is_deleted = b'0'"
    })
    int softDeleteCandidatesByTask(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET is_deleted = b'1', active_candidate_key = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND is_deleted = b'0'",
            "AND EXISTS (",
            "  SELECT 1 FROM product_selection_ali1688_collection_task task",
            "  WHERE task.id = #{taskId} AND task.locked_by = #{lockedBy}",
            "  AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            ")"
    })
    int softDeleteCandidatesByClaimedTask(
            @Param("taskId") Long taskId,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy
    );

    @Insert({
            "INSERT INTO product_selection_ali1688_candidate (",
            "id, task_id, source_collection_id, owner_user_id, logical_store_id, rank_no, selected_rank_no, level, offer_id, candidate_url,",
            "candidate_url_hash, active_candidate_key, title, supplier_name, price_text, price_min, price_max, moq_text, moq_value, location_text,",
            "main_image_url, image_urls_json, badges_json, sku_snapshot_json, supplier_snapshot_json, logistics_snapshot_json,",
            "rule_score, total_score, match_score, spec_score, price_score, moq_score, supplier_score, delivery_score, score_status, score_version, score_detail_json, ai_assessment_status,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.taskId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.rankNo}, #{row.selectedRankNo}, #{row.level}, #{row.offerId}, #{row.candidateUrl},",
            "#{row.candidateUrlHash}, #{row.activeCandidateKey}, #{row.title}, #{row.supplierName}, #{row.priceText}, #{row.priceMin}, #{row.priceMax}, #{row.moqText}, #{row.moqValue}, #{row.locationText},",
            "#{row.mainImageUrl}, #{row.imageUrlsJson}, #{row.badgesJson}, #{row.skuSnapshotJson}, #{row.supplierSnapshotJson}, #{row.logisticsSnapshotJson},",
            "#{row.ruleScore}, #{row.totalScore}, #{row.matchScore}, #{row.specScore}, #{row.priceScore}, #{row.moqScore}, #{row.supplierScore}, #{row.deliveryScore}, #{row.scoreStatus}, #{row.scoreVersion}, #{row.scoreDetailJson}, #{row.aiAssessmentStatus},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertCandidate(@Param("row") Ali1688CollectionRecords.CandidateRecord row);

    @Insert({
            "INSERT INTO product_selection_ali1688_candidate (",
            "id, task_id, source_collection_id, owner_user_id, logical_store_id, rank_no, selected_rank_no, level, offer_id, candidate_url,",
            "candidate_url_hash, active_candidate_key, title, supplier_name, price_text, price_min, price_max, moq_text, moq_value, location_text,",
            "main_image_url, image_urls_json, badges_json, sku_snapshot_json, supplier_snapshot_json, logistics_snapshot_json,",
            "rule_score, total_score, match_score, spec_score, price_score, moq_score, supplier_score, delivery_score, score_status, score_version, score_detail_json, ai_assessment_status,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") SELECT",
            "#{row.id}, #{row.taskId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.rankNo}, #{row.selectedRankNo}, #{row.level}, #{row.offerId}, #{row.candidateUrl},",
            "#{row.candidateUrlHash}, #{row.activeCandidateKey}, #{row.title}, #{row.supplierName}, #{row.priceText}, #{row.priceMin}, #{row.priceMax}, #{row.moqText}, #{row.moqValue}, #{row.locationText},",
            "#{row.mainImageUrl}, #{row.imageUrlsJson}, #{row.badgesJson}, #{row.skuSnapshotJson}, #{row.supplierSnapshotJson}, #{row.logisticsSnapshotJson},",
            "#{row.ruleScore}, #{row.totalScore}, #{row.matchScore}, #{row.specScore}, #{row.priceScore}, #{row.moqScore}, #{row.supplierScore}, #{row.deliveryScore}, #{row.scoreStatus}, #{row.scoreVersion}, #{row.scoreDetailJson}, #{row.aiAssessmentStatus},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            "FROM product_selection_ali1688_collection_task task",
            "WHERE task.id = #{row.taskId} AND task.locked_by = #{lockedBy}",
            "AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'"
    })
    int insertCandidateForClaimedTask(
            @Param("row") Ali1688CollectionRecords.CandidateRecord row,
            @Param("lockedBy") String lockedBy
    );

    @Insert({
            "INSERT INTO product_selection_ali1688_candidate (",
            "id, task_id, source_collection_id, owner_user_id, logical_store_id, rank_no, selected_rank_no, level, offer_id, candidate_url,",
            "candidate_url_hash, active_candidate_key, title, supplier_name, price_text, price_min, price_max, moq_text, moq_value, location_text,",
            "main_image_url, image_urls_json, badges_json, sku_snapshot_json, supplier_snapshot_json, logistics_snapshot_json,",
            "rule_score, total_score, match_score, spec_score, price_score, moq_score, supplier_score, delivery_score, score_status, score_version, score_detail_json, ai_assessment_status,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") SELECT",
            "#{row.id}, #{row.taskId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.rankNo}, #{row.selectedRankNo}, #{row.level}, #{row.offerId}, #{row.candidateUrl},",
            "#{row.candidateUrlHash}, #{row.activeCandidateKey}, #{row.title}, #{row.supplierName}, #{row.priceText}, #{row.priceMin}, #{row.priceMax}, #{row.moqText}, #{row.moqValue}, #{row.locationText},",
            "#{row.mainImageUrl}, #{row.imageUrlsJson}, #{row.badgesJson}, #{row.skuSnapshotJson}, #{row.supplierSnapshotJson}, #{row.logisticsSnapshotJson},",
            "#{row.ruleScore}, #{row.totalScore}, #{row.matchScore}, #{row.specScore}, #{row.priceScore}, #{row.moqScore}, #{row.supplierScore}, #{row.deliveryScore}, #{row.scoreStatus}, #{row.scoreVersion}, #{row.scoreDetailJson}, #{row.aiAssessmentStatus},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            "FROM product_selection_ali1688_plugin_assignment assignment",
            "JOIN product_selection_ali1688_collection_task task ON task.id = assignment.task_id",
            "WHERE assignment.id = #{assignmentId} AND assignment.task_id = #{row.taskId}",
            "AND assignment.active_assignment_key = CAST(task.id AS CHAR)",
            "AND assignment.is_deleted = b'0' AND assignment.status IN ('created', 'running')",
            "AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'"
    })
    int insertCandidateForCurrentPluginAssignment(
            @Param("row") Ali1688CollectionRecords.CandidateRecord row,
            @Param("assignmentId") Long assignmentId
    );

    @Select({CANDIDATE_SELECT, "WHERE candidate.task_id = #{taskId} AND candidate.is_deleted = b'0' ORDER BY candidate.rank_no ASC, candidate.id ASC"})
    List<Ali1688CollectionRecords.CandidateRecord> listCandidatesByTask(@Param("taskId") Long taskId);

    @Select({CANDIDATE_SELECT, "WHERE candidate.id = #{candidateId} AND candidate.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.CandidateRecord selectCandidateById(@Param("candidateId") Long candidateId);

    @Select({REAL_PRICE_SNAPSHOT_SELECT, "WHERE snapshot.candidate_id = #{candidateId} AND snapshot.is_deleted = b'0' ORDER BY snapshot.captured_at DESC, snapshot.id DESC LIMIT 1"})
    Ali1688RealPriceSnapshot selectLatestRealPriceSnapshotByCandidate(@Param("candidateId") Long candidateId);

    @Insert({
            "INSERT INTO product_selection_ali1688_real_price_snapshot (",
            "id, task_id, candidate_id, source_collection_id, owner_user_id, logical_store_id, status, safety_mode, side_effect_policy, source,",
            "sku_text, quantity, unit_price, freight_price, discount_price, total_price, currency, rmb_total_price, exchange_rate_to_rmb,",
            "region_text, address_context_json, captured_at, failure_code, failure_message, raw_snapshot_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.taskId}, #{row.candidateId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.status}, #{row.safetyMode}, #{row.sideEffectPolicy}, #{row.source},",
            "#{row.skuText}, #{row.quantity}, #{row.unitPrice}, #{row.freightPrice}, #{row.discountPrice}, #{row.totalPrice}, #{row.currency}, #{row.rmbTotalPrice}, #{row.exchangeRateToRmb},",
            "#{row.regionText}, #{row.addressContextJson}, COALESCE(#{row.capturedAt}, NOW()), #{row.failureCode}, #{row.failureMessage}, #{row.rawSnapshotJson}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertRealPriceSnapshot(@Param("row") Ali1688RealPriceSnapshot row);

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET selected_rank_no = NULL, level = 'review', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND is_deleted = b'0'"
    })
    int clearSelectedRanks(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET selected_rank_no = NULL, level = 'review', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND is_deleted = b'0'",
            "AND EXISTS (",
            "  SELECT 1 FROM product_selection_ali1688_collection_task task",
            "  WHERE task.id = #{taskId} AND task.locked_by = #{lockedBy}",
            "  AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            ")"
    })
    int clearSelectedRanksForClaimedTask(
            @Param("taskId") Long taskId,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET selected_rank_no = NULL, level = 'review', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND is_deleted = b'0'",
            "AND EXISTS (",
            "  SELECT 1 FROM product_selection_ali1688_plugin_assignment assignment",
            "  JOIN product_selection_ali1688_collection_task task ON task.id = assignment.task_id",
            "  WHERE assignment.id = #{assignmentId} AND assignment.task_id = #{taskId}",
            "  AND assignment.active_assignment_key = CAST(task.id AS CHAR)",
            "  AND assignment.is_deleted = b'0' AND assignment.status IN ('created', 'running')",
            "  AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            ")"
    })
    int clearSelectedRanksForCurrentPluginAssignment(
            @Param("taskId") Long taskId,
            @Param("updatedBy") Long updatedBy,
            @Param("assignmentId") Long assignmentId
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET selected_rank_no = #{selectedRankNo}, level = 'recommended', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{candidateId} AND task_id = #{taskId} AND is_deleted = b'0'"
    })
    int updateSelectedRank(
            @Param("taskId") Long taskId,
            @Param("candidateId") Long candidateId,
            @Param("selectedRankNo") Integer selectedRankNo,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET selected_rank_no = #{selectedRankNo}, level = 'recommended', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{candidateId} AND task_id = #{taskId} AND is_deleted = b'0'",
            "AND EXISTS (",
            "  SELECT 1 FROM product_selection_ali1688_collection_task task",
            "  WHERE task.id = #{taskId} AND task.locked_by = #{lockedBy}",
            "  AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            ")"
    })
    int updateSelectedRankForClaimedTask(
            @Param("taskId") Long taskId,
            @Param("candidateId") Long candidateId,
            @Param("selectedRankNo") Integer selectedRankNo,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET selected_rank_no = #{selectedRankNo}, level = 'recommended', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{candidateId} AND task_id = #{taskId} AND is_deleted = b'0'",
            "AND EXISTS (",
            "  SELECT 1 FROM product_selection_ali1688_plugin_assignment assignment",
            "  JOIN product_selection_ali1688_collection_task task ON task.id = assignment.task_id",
            "  WHERE assignment.id = #{assignmentId} AND assignment.task_id = #{taskId}",
            "  AND assignment.active_assignment_key = CAST(task.id AS CHAR)",
            "  AND assignment.is_deleted = b'0' AND assignment.status IN ('created', 'running')",
            "  AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            ")"
    })
    int updateSelectedRankForCurrentPluginAssignment(
            @Param("taskId") Long taskId,
            @Param("candidateId") Long candidateId,
            @Param("selectedRankNo") Integer selectedRankNo,
            @Param("updatedBy") Long updatedBy,
            @Param("assignmentId") Long assignmentId
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task task",
            "SET task.status = #{status}, task.progress_percent = 100, task.scanned_count = #{scannedCount},",
            "task.candidate_count = #{candidateCount}, task.recommended_count = #{recommendedCount},",
            "task.failure_code = #{failureCode}, task.failure_message = #{failureMessage},",
            "task.raw_search_snapshot_json = #{rawSearchSnapshotJson}, task.finished_at = NOW(),",
            "task.locked_at = NULL, task.locked_by = NULL, task.updated_by = #{updatedBy}, task.gmt_updated = NOW()",
            "WHERE task.id = #{taskId} AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'",
            "AND EXISTS (",
            "  SELECT 1 FROM product_selection_ali1688_plugin_assignment assignment",
            "  WHERE assignment.id = #{assignmentId} AND assignment.task_id = task.id",
            "  AND assignment.active_assignment_key = CAST(task.id AS CHAR)",
            "  AND assignment.is_deleted = b'0' AND assignment.status IN ('created', 'running')",
            ")"
    })
    int markTaskCompletedFromPluginSubmission(
            @Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("scannedCount") Integer scannedCount,
            @Param("candidateCount") Integer candidateCount,
            @Param("recommendedCount") Integer recommendedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("rawSearchSnapshotJson") String rawSearchSnapshotJson,
            @Param("updatedBy") Long updatedBy,
            @Param("assignmentId") Long assignmentId
    );

    @Insert({
            "INSERT INTO product_selection_ali1688_candidate_ai_assessment (",
            "id, task_id, candidate_id, status, feature_code, operation_code, prompt_version, schema_version, model_name, input_hash, input_snapshot_json, output_json,",
            "match_score, spec_score, risk_level, failure_code, failure_message, started_at, finished_at, locked_at, locked_by, attempt_count, next_run_at, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.taskId}, #{row.candidateId}, #{row.status}, #{row.featureCode}, #{row.operationCode}, #{row.promptVersion}, #{row.schemaVersion}, #{row.modelName}, #{row.inputHash}, #{row.inputSnapshotJson}, #{row.outputJson},",
            "#{row.matchScore}, #{row.specScore}, #{row.riskLevel}, #{row.failureCode}, #{row.failureMessage}, NULL, NULL, NULL, NULL, 0, NULL, #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertAiAssessment(@Param("row") Ali1688CollectionRecords.AiAssessmentRecord row);

    @Insert({
            "INSERT INTO product_selection_ali1688_candidate_ai_assessment (",
            "id, task_id, candidate_id, status, feature_code, operation_code, prompt_version, schema_version, model_name, input_hash, input_snapshot_json, output_json,",
            "match_score, spec_score, risk_level, failure_code, failure_message, started_at, finished_at, locked_at, locked_by, attempt_count, next_run_at, created_by, updated_by, gmt_create, gmt_updated",
            ") SELECT",
            "#{row.id}, #{row.taskId}, #{row.candidateId}, #{row.status}, #{row.featureCode}, #{row.operationCode}, #{row.promptVersion}, #{row.schemaVersion}, #{row.modelName}, #{row.inputHash}, #{row.inputSnapshotJson}, #{row.outputJson},",
            "#{row.matchScore}, #{row.specScore}, #{row.riskLevel}, #{row.failureCode}, #{row.failureMessage}, NULL, NULL, NULL, NULL, 0, NULL, #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            "FROM product_selection_ali1688_collection_task task",
            "WHERE task.id = #{row.taskId} AND task.locked_by = #{lockedBy}",
            "AND task.current_task_key IS NOT NULL AND task.is_deleted = b'0'"
    })
    int insertAiAssessmentForClaimedTask(
            @Param("row") Ali1688CollectionRecords.AiAssessmentRecord row,
            @Param("lockedBy") String lockedBy
    );

    @Select({AI_ASSESSMENT_SELECT, "WHERE assessment.id = #{assessmentId} AND assessment.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.AiAssessmentRecord selectAiAssessmentById(@Param("assessmentId") Long assessmentId);

    @Select({
            "SELECT id FROM product_selection_ali1688_candidate_ai_assessment",
            "WHERE is_deleted = b'0' AND attempt_count < #{maxAttempts}",
            "AND (",
            "  (status = 'pending' AND (next_run_at IS NULL OR next_run_at <= NOW()))",
            "  OR (status = 'running' AND locked_at < DATE_SUB(NOW(), INTERVAL #{lockTimeoutMinutes} MINUTE))",
            ")",
            "ORDER BY COALESCE(next_run_at, gmt_updated) ASC, id ASC LIMIT #{limit}"
    })
    List<Long> listClaimableAiAssessmentIds(
            @Param("maxAttempts") Integer maxAttempts,
            @Param("lockTimeoutMinutes") Integer lockTimeoutMinutes,
            @Param("limit") Integer limit
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate_ai_assessment",
            "SET status = 'running', started_at = COALESCE(started_at, NOW()), locked_at = NOW(), locked_by = #{lockedBy},",
            "attempt_count = attempt_count + 1, gmt_updated = NOW()",
            "WHERE id = #{assessmentId} AND is_deleted = b'0' AND attempt_count < #{maxAttempts}",
            "AND (",
            "  (status = 'pending' AND (next_run_at IS NULL OR next_run_at <= NOW()))",
            "  OR (status = 'running' AND locked_at < DATE_SUB(NOW(), INTERVAL #{lockTimeoutMinutes} MINUTE))",
            ")"
    })
    int claimAiAssessment(
            @Param("assessmentId") Long assessmentId,
            @Param("lockedBy") String lockedBy,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("lockTimeoutMinutes") Integer lockTimeoutMinutes
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate_ai_assessment",
            "SET status = 'success', model_name = #{modelName}, output_json = #{outputJson}, match_score = #{matchScore}, spec_score = #{specScore},",
            "risk_level = #{riskLevel}, failure_code = NULL, failure_message = NULL, finished_at = NOW(), locked_at = NULL, locked_by = NULL,",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assessmentId} AND locked_by = #{lockedBy} AND is_deleted = b'0'"
    })
    int markAiAssessmentSuccess(
            @Param("assessmentId") Long assessmentId,
            @Param("lockedBy") String lockedBy,
            @Param("modelName") String modelName,
            @Param("outputJson") String outputJson,
            @Param("matchScore") Integer matchScore,
            @Param("specScore") Integer specScore,
            @Param("riskLevel") String riskLevel,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate_ai_assessment",
            "SET status = 'failed', failure_code = #{failureCode}, failure_message = #{failureMessage}, finished_at = NOW(), locked_at = NULL, locked_by = NULL,",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assessmentId} AND is_deleted = b'0'"
    })
    int markAiAssessmentFailed(
            @Param("assessmentId") Long assessmentId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET match_score = #{matchScore}, spec_score = #{specScore}, total_score = #{totalScore}, score_status = 'final',",
            "score_detail_json = #{scoreDetailJson}, ai_assessment_status = 'success', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{candidateId} AND is_deleted = b'0'"
    })
    int updateCandidateAiScore(
            @Param("candidateId") Long candidateId,
            @Param("matchScore") Integer matchScore,
            @Param("specScore") Integer specScore,
            @Param("totalScore") Integer totalScore,
            @Param("scoreDetailJson") String scoreDetailJson,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET ai_assessment_status = 'failed', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{candidateId} AND is_deleted = b'0'"
    })
    int markCandidateAiAssessmentFailed(@Param("candidateId") Long candidateId, @Param("updatedBy") Long updatedBy);

}
