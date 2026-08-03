package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlisting.ProductListingTaskRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductListingMapper extends
        ProductListingDraftTaskMapper,
        ProductListingTaskLeaseMapper,
        ProductListingAttemptGuardMapper {
    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND draft_id = #{draftId}",
            "  AND mode = 'DRY_RUN'",
            "ORDER BY submitted_at DESC, id DESC",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectLatestDryRunTaskByDraftId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("draftId") Long draftId
    );
    @Update({
            "UPDATE product_listing_task dry_run",
            "SET dry_run.status = 'superseded',",
            "    dry_run.failure_category = 'workflow',",
            "    dry_run.failure_code = 'review_reopened',",
            "    dry_run.failure_message = '用户返回修改，原上架检查已失效。',",
            "    dry_run.completed_at = COALESCE(dry_run.completed_at, NOW()),",
            "    dry_run.gmt_updated = NOW()",
            "WHERE dry_run.id = #{taskId}",
            "  AND dry_run.owner_user_id = #{ownerUserId}",
            "  AND dry_run.mode = 'DRY_RUN'",
            "  AND dry_run.status IN ('validated', 'validation_failed')"
    })
    int markValidatedDryRunSuperseded(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId
    );
    @Update({
            "UPDATE product_listing_task",
            "SET noon_result_json = #{newNoonResultJson},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'written_verify_failed'",
            "  AND failure_code IN ('noon_create_outcome_unknown', 'real_run_interrupted')",
            "  AND (",
            "    (noon_result_json IS NULL AND #{expectedNoonResultJson} IS NULL)",
            "    OR noon_result_json = #{expectedNoonResultJson}",
            "  )"
    })
    int persistRecoveredCreateReference(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedNoonResultJson") String expectedNoonResultJson,
            @Param("newNoonResultJson") String newNoonResultJson
    );

    @Update({
            "UPDATE product_listing_task",
            "SET noon_result_json = #{newNoonResultJson},",
            "    failure_category = 'authentication',",
            "    failure_code = 'noon_auth_required',",
            "    failure_message = '核对 Noon 创建结果时授权已失效；任务已等待统一授权恢复，恢复后自动继续只读核对，禁止重复创建。',",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'written_verify_failed'",
            "  AND failure_code IN ('noon_create_outcome_unknown', 'real_run_interrupted')",
            "  AND (",
            "    (noon_result_json IS NULL AND #{expectedNoonResultJson} IS NULL)",
            "    OR noon_result_json = #{expectedNoonResultJson}",
            "  )"
    })
    int markCreateOutcomeLookupAuthenticationRequired(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedNoonResultJson") String expectedNoonResultJson,
            @Param("newNoonResultJson") String newNoonResultJson
    );

    @Insert({
            "INSERT IGNORE INTO product_listing_real_run_attempt_claim (",
            "  owner_user_id, source_task_id, attempt_task_id, claimed_at, gmt_updated",
            ") VALUES (",
            "  #{ownerUserId}, #{sourceTaskId}, #{attemptTaskId}, NOW(), NOW()",
            ")"
    })
    int claimRealRunAttempt(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("attemptTaskId") Long attemptTaskId
    );

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND source_task_id = #{sourceTaskId}",
            "  AND mode = 'REAL_RUN'",
            "ORDER BY CASE",
            "  WHEN COALESCE(failure_code, '') IN ('real_run_already_active', 'real_run_already_attempted') THEN 1",
            "  ELSE 0",
            "END, submitted_at DESC, id DESC",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectRealWriteAttemptTaskBySourceTaskId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceTaskId") Long sourceTaskId
    );

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND mode = 'REAL_RUN'",
            "  AND (",
            "    status IN ('submitted', 'running', 'succeeded', 'written_verify_failed')",
            "    OR (status = 'failed' AND failure_code IN (",
            "      'partner_sku_already_exists', 'noon_auth_required'",
            "    ))",
            "  )",
            "  AND UPPER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(input_snapshot_json, '$.psku')))) = UPPER(TRIM(#{partnerSku}))",
            "  AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM product_publish_task delete_task",
            "      WHERE delete_task.owner_user_id = product_listing_task.owner_user_id",
            "        AND UPPER(delete_task.store_code) = UPPER(product_listing_task.store_code)",
            "        AND UPPER(TRIM(delete_task.partner_sku)) = UPPER(TRIM(#{partnerSku}))",
            "        AND delete_task.task_type = 'product-delete'",
            "        AND delete_task.status = 'synced'",
            "        AND delete_task.is_deleted = b'0'",
            "        AND delete_task.finished_at >= product_listing_task.completed_at",
            "  )",
            "ORDER BY completed_at DESC, id DESC",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectListedPartnerSkuTask(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND mode = 'REAL_RUN'",
            "  AND (",
            "    status IN ('submitted', 'running', 'succeeded', 'written_verify_failed')",
            "    OR (status = 'failed' AND failure_code = 'noon_auth_required')",
            "  )",
            "  AND UPPER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(input_snapshot_json, '$.barcode')))) = UPPER(TRIM(#{barcode}))",
            "  AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM product_publish_task delete_task",
            "      WHERE delete_task.owner_user_id = product_listing_task.owner_user_id",
            "        AND UPPER(delete_task.store_code) = UPPER(product_listing_task.store_code)",
            "        AND UPPER(TRIM(delete_task.partner_sku)) = UPPER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product_listing_task.input_snapshot_json, '$.psku'))))",
            "        AND delete_task.task_type = 'product-delete'",
            "        AND delete_task.status = 'synced'",
            "        AND delete_task.is_deleted = b'0'",
            "        AND delete_task.finished_at >= product_listing_task.completed_at",
            "  )",
            "ORDER BY submitted_at DESC, id DESC",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectReservedBarcodeTask(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("barcode") String barcode
    );

    @Select("SELECT GET_LOCK(SHA2(CONCAT('product-listing:', #{lockKey}), 256), #{timeoutSeconds})")
    Integer acquireIdentityLock(
            @Param("lockKey") String lockKey,
            @Param("timeoutSeconds") int timeoutSeconds
    );

    @Select("SELECT RELEASE_LOCK(SHA2(CONCAT('product-listing:', #{lockKey}), 256))")
    Integer releaseIdentityLock(@Param("lockKey") String lockKey);

    @Select({
            "SELECT pm.id",
            "FROM logical_store_site lss",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = lss.logical_store_id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "LEFT JOIN product_master_draft pmd",
            "  ON pmd.product_master_id = pm.id",
            " AND pmd.is_deleted = b'0'",
            "LEFT JOIN product_master_snapshot pms",
            "  ON pms.product_master_id = pm.id",
            " AND pms.snapshot_type = 'baseline'",
            " AND pms.is_deleted = b'0'",
            " AND pms.id = (",
            "      SELECT MAX(pms_latest.id)",
            "      FROM product_master_snapshot pms_latest",
            "      WHERE pms_latest.product_master_id = pm.id",
            "        AND pms_latest.snapshot_type = 'baseline'",
            "        AND pms_latest.is_deleted = b'0'",
            " )",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND lss.is_deleted = b'0'",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND UPPER(TRIM(COALESCE(NULLIF(pm.partner_sku, ''), pv.partner_sku))) = UPPER(TRIM(#{partnerSku}))",
            "  AND (",
            "    #{excludeListingDraftId} IS NULL",
            "    OR (",
            "      (",
            "        pmd.id IS NULL",
            "        OR JSON_EXTRACT(pmd.draft_json, '$.identity.listingDraftId') IS NULL",
            "        OR CAST(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.identity.listingDraftId')) AS UNSIGNED) <> #{excludeListingDraftId}",
            "      )",
            "      AND (",
            "        pms.id IS NULL",
            "        OR JSON_EXTRACT(pms.snapshot_json, '$.identity.listingDraftId') IS NULL",
            "        OR CAST(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.identity.listingDraftId')) AS UNSIGNED) <> #{excludeListingDraftId}",
            "      )",
            "    )",
            "  )",
            "ORDER BY pm.id ASC",
            "LIMIT 1"
    })
    Long selectLocalProductIdByPartnerSku(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("partnerSku") String partnerSku,
            @Param("excludeListingDraftId") Long excludeListingDraftId
    );

    @Select({
            "SELECT pm.id",
            "FROM logical_store_site lss",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = lss.logical_store_id",
            " AND pm.is_deleted = b'0'",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "JOIN product_barcode pb",
            "  ON pb.variant_id = pv.id",
            " AND pb.is_deleted = b'0'",
            "LEFT JOIN product_master_draft pmd",
            "  ON pmd.product_master_id = pm.id",
            " AND pmd.is_deleted = b'0'",
            "LEFT JOIN product_master_snapshot pms",
            "  ON pms.product_master_id = pm.id",
            " AND pms.snapshot_type = 'baseline'",
            " AND pms.is_deleted = b'0'",
            " AND pms.id = (",
            "      SELECT MAX(pms_latest.id)",
            "      FROM product_master_snapshot pms_latest",
            "      WHERE pms_latest.product_master_id = pm.id",
            "        AND pms_latest.snapshot_type = 'baseline'",
            "        AND pms_latest.is_deleted = b'0'",
            " )",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND lss.is_deleted = b'0'",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND UPPER(TRIM(pb.barcode)) = UPPER(TRIM(#{barcode}))",
            "  AND (",
            "    #{excludeListingDraftId} IS NULL",
            "    OR (",
            "      (",
            "        pmd.id IS NULL",
            "        OR JSON_EXTRACT(pmd.draft_json, '$.identity.listingDraftId') IS NULL",
            "        OR CAST(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.identity.listingDraftId')) AS UNSIGNED) <> #{excludeListingDraftId}",
            "      )",
            "      AND (",
            "        pms.id IS NULL",
            "        OR JSON_EXTRACT(pms.snapshot_json, '$.identity.listingDraftId') IS NULL",
            "        OR CAST(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.identity.listingDraftId')) AS UNSIGNED) <> #{excludeListingDraftId}",
            "      )",
            "    )",
            "  )",
            "ORDER BY pm.id ASC",
            "LIMIT 1"
    })
    Long selectLocalProductIdByBarcode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("barcode") String barcode,
            @Param("excludeListingDraftId") Long excludeListingDraftId
    );
    @Select({
            "SELECT",
            "  t.id, t.draft_id, t.owner_user_id, t.store_code, t.task_no, t.mode, t.status,",
            "  t.source_task_id, t.input_snapshot_json, t.validation_json, t.confirmation_json,",
            "  t.noon_result_json, t.failure_category, t.failure_code, t.failure_message,",
            "  t.submitted_by, t.submitted_at, t.started_at, t.completed_at, t.gmt_create, t.gmt_updated",
            "FROM product_listing_task t",
            "JOIN product_listing_draft d",
            "  ON d.id = t.draft_id",
            " AND d.owner_user_id = t.owner_user_id",
            "WHERE d.owner_user_id = #{ownerUserId}",
            "  AND d.store_code = #{storeCode}",
            "  AND d.source_type = #{sourceType}",
            "  AND d.source_ref_id = #{sourceRefId}",
            "  AND t.mode = 'REAL_RUN'",
            "  AND t.status <> 'rejected'",
            "ORDER BY t.submitted_at DESC, t.id DESC",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectLatestRealRunTaskByDraftSource(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("sourceType") String sourceType,
            @Param("sourceRefId") Long sourceRefId
    );

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE mode = 'REAL_RUN'",
            "  AND status = 'submitted'",
            "ORDER BY submitted_at ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<ProductListingTaskRecord> selectRunnableRealRunTasks(@Param("limit") int limit);

    @Update({
            "UPDATE product_listing_task",
            "SET status = #{task.status},",
            "    noon_result_json = #{task.noonResultJson},",
            "    failure_category = #{task.failureCategory},",
            "    failure_code = #{task.failureCode},",
            "    failure_message = #{task.failureMessage},",
            "    completed_at = #{task.completedAt},",
            "    gmt_updated = NOW()",
            "WHERE id = #{task.id}",
            "  AND owner_user_id = #{task.ownerUserId}"
    })
    int updateTaskResult(@Param("task") ProductListingTaskRecord task);
}
