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
            "  AND status IN ('running', 'submitted', 'succeeded', 'failed', 'written_verify_failed')",
            "ORDER BY submitted_at DESC",
            "LIMIT 1"
    })
    ProductListingTaskRecord selectRealWriteAttemptTaskBySourceTaskId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceTaskId") Long sourceTaskId
    );

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
