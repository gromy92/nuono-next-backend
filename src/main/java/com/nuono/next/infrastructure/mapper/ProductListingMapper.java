package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlisting.ProductListingDraftRecord;
import com.nuono.next.productlisting.ProductListingTaskRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface ProductListingMapper {

    @Insert({
            "INSERT INTO product_listing_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
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
    int allocateProductListingId(IdSequenceCommand command);

    default Long nextProductListingId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateProductListingId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Product listing ID allocation failed: " + sequenceName);
        }
        return id;
    }

    default Long nextProductListingDraftId() {
        return nextProductListingId("product_listing_draft", 10000L);
    }

    default Long nextProductListingTaskId() {
        return nextProductListingId("product_listing_task", 10000L);
    }

    @Insert({
            "INSERT INTO product_listing_draft (",
            "  id, owner_user_id, store_code, draft_no, source_type, source_ref_id,",
            "  optional_purchase_order_id, status, draft_json, validation_json,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{draft.id}, #{draft.ownerUserId}, #{draft.storeCode}, #{draft.draftNo},",
            "  #{draft.sourceType}, #{draft.sourceRefId}, #{draft.optionalPurchaseOrderId},",
            "  #{draft.status}, #{draft.draftJson}, #{draft.validationJson},",
            "  #{draft.createdBy}, #{draft.updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertDraft(@Param("draft") ProductListingDraftRecord draft);

    @Update({
            "UPDATE product_listing_draft",
            "SET store_code = #{draft.storeCode},",
            "    source_type = #{draft.sourceType},",
            "    source_ref_id = #{draft.sourceRefId},",
            "    optional_purchase_order_id = #{draft.optionalPurchaseOrderId},",
            "    status = #{draft.status},",
            "    draft_json = #{draft.draftJson},",
            "    validation_json = #{draft.validationJson},",
            "    updated_by = #{draft.updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{draft.id}",
            "  AND owner_user_id = #{draft.ownerUserId}"
    })
    int updateDraft(@Param("draft") ProductListingDraftRecord draft);

    @Select({
            "SELECT",
            "  id, owner_user_id, store_code, draft_no, source_type, source_ref_id,",
            "  optional_purchase_order_id, status, draft_json, validation_json,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            "FROM product_listing_draft",
            "WHERE id = #{draftId}",
            "  AND owner_user_id = #{ownerUserId}",
            "LIMIT 1"
    })
    ProductListingDraftRecord selectDraftById(
            @Param("draftId") Long draftId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT",
            "  id, owner_user_id, store_code, draft_no, source_type, source_ref_id,",
            "  optional_purchase_order_id, status, draft_json, validation_json,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            "FROM product_listing_draft",
            "WHERE id = #{draftId}",
            "  AND owner_user_id = #{ownerUserId}",
            "LIMIT 1",
            "FOR UPDATE"
    })
    ProductListingDraftRecord selectDraftByIdForUpdate(
            @Param("draftId") Long draftId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT",
            "  id, owner_user_id, store_code, draft_no, source_type, source_ref_id,",
            "  optional_purchase_order_id, status, draft_json, validation_json,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            "FROM product_listing_draft",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND status IN ('draft', 'validation_failed', 'ready_for_dry_run')",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT #{limit}"
    })
    List<ProductListingDraftRecord> selectRecentDrafts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("limit") int limit
    );

    @Select({
            "SELECT id",
            "FROM product_listing_draft",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND source_type = #{sourceType}",
            "  AND source_ref_id = #{sourceRefId}",
            "  AND status IN ('draft', 'validation_failed', 'ready_for_dry_run')",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Long findActiveDraftId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("sourceType") String sourceType,
            @Param("sourceRefId") Long sourceRefId
    );

    @Insert({
            "INSERT INTO product_listing_task (",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{task.id}, #{task.draftId}, #{task.ownerUserId}, #{task.storeCode},",
            "  #{task.taskNo}, #{task.mode}, #{task.status},",
            "  #{task.sourceTaskId}, #{task.inputSnapshotJson}, #{task.validationJson},",
            "  #{task.confirmationJson}, #{task.noonResultJson}, #{task.failureCategory},",
            "  #{task.failureCode}, #{task.failureMessage}, #{task.submittedBy},",
            "  #{task.submittedAt}, #{task.startedAt}, #{task.completedAt}, NOW(), NOW()",
            ")"
    })
    int insertTask(@Param("task") ProductListingTaskRecord task);

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectTaskById(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "LIMIT 1",
            "FOR UPDATE"
    })
    ProductListingTaskRecord selectTaskByIdForUpdate(
            @Param("taskId") Long taskId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE id = #{taskId}",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectTaskByIdForWorker(@Param("taskId") Long taskId);

    @Select({
            "SELECT",
            "  id, draft_id, owner_user_id, store_code, task_no, mode, status,",
            "  source_task_id, input_snapshot_json, validation_json, confirmation_json,",
            "  noon_result_json, failure_category, failure_code, failure_message,",
            "  submitted_by, submitted_at, started_at, completed_at, gmt_create, gmt_updated",
            "FROM product_listing_task",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "ORDER BY submitted_at DESC",
            "LIMIT #{limit}"
    })
    List<ProductListingTaskRecord> selectRecentTasks(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("limit") int limit
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
            "  AND draft_id = #{draftId}",
            "ORDER BY submitted_at DESC",
            "LIMIT #{limit}"
    })
    List<ProductListingTaskRecord> selectRecentTasksByDraftId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("draftId") Long draftId,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "  real_run.id, real_run.draft_id, real_run.owner_user_id, real_run.store_code,",
            "  real_run.task_no, real_run.mode, real_run.status, real_run.source_task_id,",
            "  real_run.input_snapshot_json, real_run.validation_json, real_run.confirmation_json,",
            "  real_run.noon_result_json, real_run.failure_category, real_run.failure_code,",
            "  real_run.failure_message, real_run.submitted_by, real_run.submitted_at,",
            "  real_run.started_at, real_run.completed_at, real_run.gmt_create, real_run.gmt_updated",
            "FROM product_listing_task real_run",
            "LEFT JOIN product_listing_task source_dry_run",
            "  ON source_dry_run.id = real_run.source_task_id",
            " AND source_dry_run.owner_user_id = real_run.owner_user_id",
            " AND source_dry_run.draft_id = real_run.draft_id",
            " AND source_dry_run.mode = 'DRY_RUN'",
            "WHERE real_run.owner_user_id = #{ownerUserId}",
            "  AND real_run.draft_id = #{draftId}",
            "  AND real_run.mode = 'REAL_RUN'",
            "  AND NOT (",
            "    COALESCE(source_dry_run.status, '') = 'superseded'",
            "    AND real_run.status IN ('failed', 'rejected')",
            "    AND (",
            "      real_run.noon_result_json IS NULL",
            "      OR TRIM(real_run.noon_result_json) = ''",
            "      OR CASE",
            "        WHEN JSON_VALID(real_run.noon_result_json) THEN",
            "          JSON_TYPE(JSON_EXTRACT(real_run.noon_result_json, '$')) = 'OBJECT'",
            "          AND (",
            "            JSON_EXTRACT(real_run.noon_result_json, '$.success') IS NULL",
            "            OR JSON_TYPE(JSON_EXTRACT(real_run.noon_result_json, '$.success')) = 'BOOLEAN'",
            "          )",
            "          AND (",
            "            JSON_EXTRACT(real_run.noon_result_json, '$.steps') IS NULL",
            "            OR JSON_TYPE(JSON_EXTRACT(real_run.noon_result_json, '$.steps')) = 'ARRAY'",
            "          )",
            "        ELSE FALSE",
            "      END",
            "    )",
            "    AND COALESCE(CASE",
            "          WHEN JSON_VALID(real_run.noon_result_json)",
            "          THEN JSON_UNQUOTE(JSON_EXTRACT(",
            "               real_run.noon_result_json, '$.success'))",
            "          ELSE 'false'",
            "        END, 'false') <> 'true'",
            "    AND NOT (",
            "      LOWER(COALESCE(real_run.noon_result_json, '')) LIKE '%skuparent=%'",
            "      AND LOWER(COALESCE(real_run.noon_result_json, '')) LIKE '%pskucode=%'",
            "    )",
            "    AND COALESCE(real_run.failure_code, '')",
            "        NOT IN ('noon_create_outcome_unknown', 'real_run_interrupted')",
            "    AND (",
            "      real_run.status = 'rejected'",
            "      OR (",
            "        real_run.status = 'failed'",
            "        AND COALESCE(real_run.failure_code, '') NOT IN (",
            "          'noon_write_exception', 'noon_write_outcome_unknown'",
            "        )",
            "        AND (",
            "          LOWER(COALESCE(real_run.failure_category, '')) IN ('validation', 'guard')",
            "          OR LOWER(COALESCE(real_run.failure_code, '')) IN (",
            "            'noon_auth_required',",
            "            'noon_pre_create_failed',",
            "            'noon_create_rejected',",
            "            'noon_create_not_found_confirmed',",
            "            'noon_warehouse_stock_not_supported',",
            "            'partner_sku_already_exists',",
            "            'barcode_already_exists'",
            "          )",
            "        )",
            "      )",
            "    )",
            "  )",
            "ORDER BY CASE",
            "  WHEN real_run.status IN ('submitted', 'running', 'written_verify_failed') THEN 0",
            "  WHEN real_run.status = 'succeeded' THEN 1",
            "  ELSE 2",
            "END, real_run.submitted_at DESC, real_run.id DESC",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectCurrentRealRunTaskByDraftId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("draftId") Long draftId
    );

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
            "    failure_message = '核对 Noon 创建结果时授权已失效；请重新授权后继续只读核对，禁止重复创建。',",
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
            "    OR (status = 'failed' AND failure_code = 'partner_sku_already_exists')",
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
            "  AND status IN ('submitted', 'running', 'succeeded', 'written_verify_failed')",
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
            "SET status = 'written_verify_failed',",
            "    failure_category = 'recovery',",
            "    failure_code = 'real_run_interrupted',",
            "    failure_message = '真实上架任务执行中断，系统不会自动重放 Noon 写入；请人工核对 Noon 后继续。',",
            "    completed_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at IS NOT NULL",
            "  AND gmt_updated < #{staleBefore}"
    })
    int recoverStaleRunningRealRunTasks(@Param("staleBefore") LocalDateTime staleBefore);

    @Update({
            "UPDATE product_listing_task",
            "SET status = 'running',",
            "    started_at = #{startedAt},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'submitted'"
    })
    int markTaskRunning(
            @Param("taskId") Long taskId,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update({
            "UPDATE product_listing_task",
            "SET gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at = #{startedAt}"
    })
    int heartbeatRunningRealRunTask(
            @Param("taskId") Long taskId,
            @Param("startedAt") LocalDateTime startedAt
    );

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
            "  AND owner_user_id = #{task.ownerUserId}",
            "  AND mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at = #{task.startedAt}"
    })
    int updateRunningTaskResult(@Param("task") ProductListingTaskRecord task);

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
