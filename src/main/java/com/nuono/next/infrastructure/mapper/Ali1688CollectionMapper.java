package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productselection.Ali1688CollectionRecords;
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
            + "SELECT assignment.id, assignment.assignment_code, assignment.assignment_type, assignment.task_id, assignment.candidate_id, "
            + "assignment.source_collection_id, assignment.owner_user_id, assignment.logical_store_id, assignment.current_assignment_key, "
            + "assignment.status, assignment.idempotency_key, assignment.result_status, assignment.result_snapshot_json, "
            + "assignment.failure_code, assignment.failure_message, assignment.submitted_candidate_count, assignment.accepted_candidate_count, "
            + "assignment.rejected_candidate_count, DATE_FORMAT(assignment.created_at, '%Y-%m-%d %H:%i') AS created_at, "
            + "DATE_FORMAT(assignment.expires_at, '%Y-%m-%d %H:%i') AS expires_at, DATE_FORMAT(assignment.started_at, '%Y-%m-%d %H:%i') AS started_at, "
            + "DATE_FORMAT(assignment.finished_at, '%Y-%m-%d %H:%i') AS finished_at, assignment.created_by, assignment.updated_by, "
            + "task.task_no, task.source_image_url, source.source_title, source.source_title_cn, source.source_url, source.page_url, "
            + "COALESCE(store.project_name, '') AS store_name, "
            + "(SELECT site.store_code FROM logical_store_site site WHERE site.logical_store_id = assignment.logical_store_id AND site.is_deleted = b'0' ORDER BY site.is_reference_site DESC, site.id ASC LIMIT 1) AS store_code, "
            + "candidate.title AS candidate_title, candidate.candidate_url, candidate.offer_id "
            + "FROM product_selection_ali1688_plugin_assignment assignment "
            + "JOIN product_selection_ali1688_collection_task task ON task.id = assignment.task_id AND task.is_deleted = b'0' "
            + "JOIN product_selection_source_collection source ON source.id = assignment.source_collection_id AND source.is_deleted = b'0' "
            + "LEFT JOIN logical_store store ON store.id = assignment.logical_store_id AND store.is_deleted = b'0' "
            + "LEFT JOIN product_selection_ali1688_candidate candidate ON candidate.id = assignment.candidate_id AND candidate.is_deleted = b'0' ";

    String DETAIL_ENRICHMENT_SNAPSHOT_SELECT = ""
            + "SELECT snapshot.id, snapshot.assignment_id, snapshot.task_id, snapshot.candidate_id, snapshot.source_collection_id, "
            + "snapshot.owner_user_id, snapshot.logical_store_id, snapshot.snapshot_source, "
            + "COALESCE(snapshot.collected_at_text, DATE_FORMAT(snapshot.collected_at, '%Y-%m-%d %H:%i')) AS collected_at, "
            + "snapshot.page_url, snapshot.detail_title, snapshot.main_image_urls_json, snapshot.detail_image_urls_json, snapshot.image_urls_json, "
            + "snapshot.sku_options_json, snapshot.moq_text, snapshot.supplier_name, snapshot.location_text, snapshot.list_price_text, "
            + "snapshot.service_labels_json, snapshot.sales_labels_json, snapshot.raw_evidence_snippets_json, snapshot.raw_snapshot_json, "
            + "snapshot.unit, snapshot.variant_image_urls_json, snapshot.attributes_json, snapshot.sku_combinations_json, snapshot.sku_count, "
            + "snapshot.page_price_hint_json, snapshot.supplier_profile_json, snapshot.shipping_snapshot_json, snapshot.video_json, "
            + "snapshot.created_by, snapshot.updated_by "
            + "FROM product_selection_ali1688_detail_enrichment_snapshot snapshot ";

    String PRICE_PREVIEW_SNAPSHOT_SELECT = ""
            + "SELECT snapshot.id, snapshot.assignment_id, snapshot.task_id, snapshot.candidate_id, snapshot.source_collection_id, "
            + "snapshot.owner_user_id, snapshot.logical_store_id, snapshot.snapshot_source, snapshot.result_status, "
            + "snapshot.failure_code, snapshot.failure_message, "
            + "COALESCE(snapshot.collected_at_text, DATE_FORMAT(snapshot.collected_at, '%Y-%m-%d %H:%i')) AS collected_at, "
            + "snapshot.sku_options_json, snapshot.quantity, snapshot.unit_price_text, snapshot.shipping_text, snapshot.discount_text, "
            + "snapshot.total_price_text, snapshot.currency, snapshot.region_text, snapshot.safety_mode, snapshot.side_effect_policy, "
            + "snapshot.raw_snapshot_json, snapshot.created_by, snapshot.updated_by "
            + "FROM product_selection_ali1688_price_preview_snapshot snapshot ";

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

    default Long nextDetailEnrichmentSnapshotId() {
        return nextId("product_selection_ali1688_detail_enrichment_snapshot", 91000L);
    }

    default Long nextPricePreviewSnapshotId() {
        return nextId("product_selection_ali1688_price_preview_snapshot", 92000L);
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
            "WHERE id = #{taskId} AND locked_by = #{lockedBy} AND is_deleted = b'0'"
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
            "SET search_mode = #{searchMode}, official_search_url = #{officialSearchUrl}, search_image_id = #{searchImageId},",
            "search_image_id_list_json = #{searchImageIdListJson}, raw_search_snapshot_json = #{rawSearchSnapshotJson}, scanned_count = #{scannedCount},",
            "progress_percent = GREATEST(progress_percent, 80), updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int updateSearchSnapshotFromPlugin(
            @Param("taskId") Long taskId,
            @Param("searchMode") String searchMode,
            @Param("officialSearchUrl") String officialSearchUrl,
            @Param("searchImageId") String searchImageId,
            @Param("searchImageIdListJson") String searchImageIdListJson,
            @Param("rawSearchSnapshotJson") String rawSearchSnapshotJson,
            @Param("scannedCount") Integer scannedCount,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = #{status}, progress_percent = 100, scanned_count = #{scannedCount}, candidate_count = #{candidateCount},",
            "recommended_count = #{recommendedCount}, failure_code = #{failureCode}, failure_message = #{failureMessage}, finished_at = NOW(),",
            "locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND locked_by = #{lockedBy} AND is_deleted = b'0'"
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
            "SET status = #{status}, progress_percent = 100, scanned_count = #{scannedCount}, candidate_count = #{candidateCount},",
            "recommended_count = #{recommendedCount}, failure_code = #{failureCode}, failure_message = #{failureMessage}, finished_at = NOW(),",
            "locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND current_task_key IS NOT NULL AND is_deleted = b'0'"
    })
    int markTaskCompletedFromPlugin(
            @Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("scannedCount") Integer scannedCount,
            @Param("candidateCount") Integer candidateCount,
            @Param("recommendedCount") Integer recommendedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'failed', progress_percent = 100, failure_code = #{failureCode}, failure_message = #{failureMessage},",
            "finished_at = NOW(), locked_at = NULL, locked_by = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND is_deleted = b'0'"
    })
    int markTaskFailed(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_collection_task",
            "SET status = 'queued', progress_percent = 5, failure_code = NULL, failure_message = NULL, finished_at = NULL,",
            "locked_at = NULL, locked_by = NULL, attempt_count = 0, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND current_task_key IS NOT NULL AND status = 'failed' AND is_deleted = b'0'"
    })
    int retryFailedTask(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET is_deleted = b'1', active_candidate_key = NULL, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND is_deleted = b'0'"
    })
    int softDeleteCandidatesByTask(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

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

    @Select({CANDIDATE_SELECT, "WHERE candidate.task_id = #{taskId} AND candidate.is_deleted = b'0' ORDER BY candidate.rank_no ASC, candidate.id ASC"})
    List<Ali1688CollectionRecords.CandidateRecord> listCandidatesByTask(@Param("taskId") Long taskId);

    @Select({CANDIDATE_SELECT, "WHERE candidate.id = #{candidateId} AND candidate.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.CandidateRecord selectCandidateById(@Param("candidateId") Long candidateId);

    @Update({
            "UPDATE product_selection_ali1688_candidate",
            "SET selected_rank_no = NULL, level = 'review', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE task_id = #{taskId} AND is_deleted = b'0'"
    })
    int clearSelectedRanks(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

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

    @Insert({
            "INSERT INTO product_selection_ali1688_candidate_ai_assessment (",
            "id, task_id, candidate_id, status, feature_code, operation_code, prompt_version, schema_version, model_name, input_hash, input_snapshot_json, output_json,",
            "match_score, spec_score, risk_level, failure_code, failure_message, started_at, finished_at, locked_at, locked_by, attempt_count, next_run_at, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.taskId}, #{row.candidateId}, #{row.status}, #{row.featureCode}, #{row.operationCode}, #{row.promptVersion}, #{row.schemaVersion}, #{row.modelName}, #{row.inputHash}, #{row.inputSnapshotJson}, #{row.outputJson},",
            "#{row.matchScore}, #{row.specScore}, #{row.riskLevel}, #{row.failureCode}, #{row.failureMessage}, NULL, NULL, NULL, NULL, 0, NULL, #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertAiAssessment(@Param("row") Ali1688CollectionRecords.AiAssessmentRecord row);

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

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET current_assignment_key = NULL, status = CASE WHEN status IN ('created', 'running') THEN 'cancelled' ELSE status END,",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE current_assignment_key = #{currentAssignmentKey} AND is_deleted = b'0'"
    })
    int supersedeCurrentPluginAssignments(
            @Param("currentAssignmentKey") String currentAssignmentKey,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET current_assignment_key = NULL, status = 'expired', failure_code = COALESCE(failure_code, 'assignment_expired'),",
            "failure_message = COALESCE(failure_message, '插件任务已超过有效期。'), finished_at = COALESCE(finished_at, NOW()),",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE logical_store_id = #{logicalStoreId} AND current_assignment_key IS NOT NULL AND status IN ('created', 'running')",
            "AND expires_at IS NOT NULL AND expires_at < NOW() AND is_deleted = b'0'"
    })
    int expireCurrentPluginAssignments(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO product_selection_ali1688_plugin_assignment (",
            "id, assignment_code, assignment_type, task_id, candidate_id, source_collection_id, owner_user_id, logical_store_id,",
            "current_assignment_key, status, submitted_candidate_count, accepted_candidate_count, rejected_candidate_count,",
            "expires_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.assignmentCode}, #{row.assignmentType}, #{row.taskId}, #{row.candidateId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId},",
            "#{row.currentAssignmentKey}, #{row.status}, 0, 0, 0, DATE_ADD(NOW(), INTERVAL 2 HOUR), b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertPluginAssignment(@Param("row") Ali1688CollectionRecords.PluginAssignmentRecord row);

    @Select({PLUGIN_ASSIGNMENT_SELECT, "WHERE assignment.id = #{assignmentId} AND assignment.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.PluginAssignmentRecord selectPluginAssignmentById(@Param("assignmentId") Long assignmentId);

    @Select({PLUGIN_ASSIGNMENT_SELECT, "WHERE (CAST(assignment.id AS CHAR) = #{locator} OR assignment.assignment_code = #{locator}) AND assignment.is_deleted = b'0' LIMIT 1"})
    Ali1688CollectionRecords.PluginAssignmentRecord selectPluginAssignmentByLocator(@Param("locator") String locator);

    @Select({
            PLUGIN_ASSIGNMENT_SELECT,
            "WHERE assignment.candidate_id = #{candidateId} AND assignment.assignment_type = #{assignmentType} AND assignment.is_deleted = b'0'",
            "ORDER BY assignment.gmt_updated DESC, assignment.id DESC LIMIT 1"
    })
    Ali1688CollectionRecords.PluginAssignmentRecord selectLatestPluginAssignmentByCandidateAndType(
            @Param("candidateId") Long candidateId,
            @Param("assignmentType") String assignmentType
    );

    @Select({
            PLUGIN_ASSIGNMENT_SELECT,
            "WHERE assignment.task_id = #{taskId} AND assignment.assignment_type = #{assignmentType} AND assignment.is_deleted = b'0'",
            "ORDER BY assignment.gmt_updated DESC, assignment.id DESC LIMIT 1"
    })
    Ali1688CollectionRecords.PluginAssignmentRecord selectLatestPluginAssignmentByTaskAndType(
            @Param("taskId") Long taskId,
            @Param("assignmentType") String assignmentType
    );

    @Select({
            PLUGIN_ASSIGNMENT_SELECT,
            "WHERE assignment.logical_store_id = #{logicalStoreId} AND assignment.current_assignment_key IS NOT NULL AND assignment.is_deleted = b'0'",
            "ORDER BY assignment.gmt_updated DESC, assignment.id DESC LIMIT #{limit}"
    })
    List<Ali1688CollectionRecords.PluginAssignmentRecord> listCurrentPluginAssignments(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("limit") Integer limit
    );

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'running', started_at = COALESCE(started_at, NOW()), updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND current_assignment_key IS NOT NULL AND status IN ('created', 'running')",
            "AND (expires_at IS NULL OR expires_at >= NOW()) AND is_deleted = b'0'"
    })
    int markPluginAssignmentRunning(@Param("assignmentId") Long assignmentId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'failed', failure_code = #{failureCode}, failure_message = #{failureMessage}, finished_at = NOW(),",
            "updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND current_assignment_key IS NOT NULL AND status IN ('created', 'running')",
            "AND (expires_at IS NULL OR expires_at >= NOW()) AND is_deleted = b'0'"
    })
    int markPluginAssignmentFailed(
            @Param("assignmentId") Long assignmentId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_ali1688_plugin_assignment",
            "SET status = 'accepted', idempotency_key = #{idempotencyKey}, result_status = #{resultStatus}, result_snapshot_json = #{resultSnapshotJson},",
            "submitted_candidate_count = #{submittedCandidateCount}, accepted_candidate_count = #{acceptedCandidateCount}, rejected_candidate_count = #{rejectedCandidateCount},",
            "failure_code = NULL, failure_message = NULL, finished_at = NOW(), updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{assignmentId} AND current_assignment_key IS NOT NULL AND status IN ('created', 'running', 'accepted')",
            "AND (expires_at IS NULL OR expires_at >= NOW()) AND is_deleted = b'0'"
    })
    int markPluginAssignmentAccepted(
            @Param("assignmentId") Long assignmentId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("resultStatus") String resultStatus,
            @Param("resultSnapshotJson") String resultSnapshotJson,
            @Param("submittedCandidateCount") Integer submittedCandidateCount,
            @Param("acceptedCandidateCount") Integer acceptedCandidateCount,
            @Param("rejectedCandidateCount") Integer rejectedCandidateCount,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO product_selection_ali1688_detail_enrichment_snapshot (",
            "id, assignment_id, task_id, candidate_id, source_collection_id, owner_user_id, logical_store_id, snapshot_source,",
            "collected_at, collected_at_text, page_url, detail_title, main_image_urls_json, detail_image_urls_json, image_urls_json,",
            "sku_options_json, moq_text, supplier_name, location_text, list_price_text, service_labels_json, sales_labels_json,",
            "raw_evidence_snippets_json, raw_snapshot_json,",
            "unit, variant_image_urls_json, attributes_json, sku_combinations_json, sku_count, page_price_hint_json, supplier_profile_json, shipping_snapshot_json, video_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.assignmentId}, #{row.taskId}, #{row.candidateId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.snapshotSource},",
            "NOW(), #{row.collectedAt}, #{row.pageUrl}, #{row.detailTitle}, #{row.mainImageUrlsJson}, #{row.detailImageUrlsJson}, #{row.imageUrlsJson},",
            "#{row.skuOptionsJson}, #{row.moqText}, #{row.supplierName}, #{row.locationText}, #{row.listPriceText}, #{row.serviceLabelsJson}, #{row.salesLabelsJson},",
            "#{row.rawEvidenceSnippetsJson}, #{row.rawSnapshotJson},",
            "#{row.unit}, #{row.variantImageUrlsJson}, #{row.attributesJson}, #{row.skuCombinationsJson}, #{row.skuCount}, #{row.pagePriceHintJson}, #{row.supplierProfileJson}, #{row.shippingSnapshotJson}, #{row.videoJson},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertDetailEnrichmentSnapshot(@Param("row") Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord row);

    @Select({
            DETAIL_ENRICHMENT_SNAPSHOT_SELECT,
            "WHERE snapshot.candidate_id = #{candidateId} AND snapshot.is_deleted = b'0'",
            "ORDER BY snapshot.gmt_updated DESC, snapshot.id DESC LIMIT 1"
    })
    Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord selectLatestDetailEnrichmentSnapshotByCandidateId(@Param("candidateId") Long candidateId);

    @Select({
            PRICE_PREVIEW_SNAPSHOT_SELECT,
            "WHERE snapshot.candidate_id = #{candidateId} AND snapshot.is_deleted = b'0'",
            "ORDER BY snapshot.gmt_updated DESC, snapshot.id DESC LIMIT 1"
    })
    Ali1688CollectionRecords.PricePreviewSnapshotRecord selectLatestPricePreviewSnapshotByCandidateId(@Param("candidateId") Long candidateId);

    @Insert({
            "INSERT INTO product_selection_ali1688_price_preview_snapshot (",
            "id, assignment_id, task_id, candidate_id, source_collection_id, owner_user_id, logical_store_id, snapshot_source, result_status,",
            "failure_code, failure_message, collected_at, collected_at_text, sku_options_json, quantity, unit_price_text, shipping_text,",
            "discount_text, total_price_text, currency, region_text, safety_mode, side_effect_policy, raw_snapshot_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.assignmentId}, #{row.taskId}, #{row.candidateId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.snapshotSource}, #{row.resultStatus},",
            "#{row.failureCode}, #{row.failureMessage}, NOW(), #{row.collectedAt}, #{row.skuOptionsJson}, #{row.quantity}, #{row.unitPriceText}, #{row.shippingText},",
            "#{row.discountText}, #{row.totalPriceText}, #{row.currency}, #{row.regionText}, #{row.safetyMode}, #{row.sideEffectPolicy}, #{row.rawSnapshotJson},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertPricePreviewSnapshot(@Param("row") Ali1688CollectionRecords.PricePreviewSnapshotRecord row);

}
