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
            "ORDER BY gmt_updated DESC",
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
            "  AND source_task_id = #{sourceTaskId}",
            "  AND mode = 'REAL_RUN'",
            "  AND (",
            "    status IN ('running', 'submitted', 'succeeded', 'written_verify_failed')",
            "    OR (status = 'failed' AND failure_code = 'partner_sku_already_exists')",
            "  )",
            "ORDER BY submitted_at DESC",
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
            "    status IN ('succeeded', 'written_verify_failed')",
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
            "SET status = 'submitted',",
            "    started_at = NULL,",
            "    gmt_updated = NOW()",
            "WHERE mode = 'REAL_RUN'",
            "  AND status = 'running'",
            "  AND started_at IS NOT NULL",
            "  AND started_at < #{staleBefore}"
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
