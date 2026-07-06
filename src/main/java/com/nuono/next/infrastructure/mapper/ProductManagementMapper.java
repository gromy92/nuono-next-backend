package com.nuono.next.infrastructure.mapper;

import com.nuono.next.nooncompleteness.NoonProductCompletenessAudit;
import com.nuono.next.product.ProductActionLogRecord;
import com.nuono.next.product.ProductClassificationOptionRecord;
import com.nuono.next.product.ProductGroupCandidateContextRecord;
import com.nuono.next.product.ProductMasterDraftRecord;
import com.nuono.next.product.ProductKeyContentHistoryRecord;
import com.nuono.next.product.ProductMasterSnapshotRecord;
import com.nuono.next.product.ProductListProjectionRecord;
import com.nuono.next.product.ProductListSnapshotMediaRecord;
import com.nuono.next.product.ProductMasterIdentityRecord;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.ProductVariantLogisticsProfileCommand;
import com.nuono.next.product.ProductVariantLogisticsProfileView;
import com.nuono.next.product.ProductVariantSpecCommand;
import com.nuono.next.product.ProductVariantSpecRecord;
import com.nuono.next.product.ProductVariantSpecSourceCommand;
import com.nuono.next.product.ProductVariantSpecSourceRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface ProductManagementMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
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
    int allocateProductManagementId(IdSequenceCommand command);

    @Select({
            "SELECT",
            "  COUNT(DISTINCT pm.id) AS productMasterCount,",
            "  COUNT(DISTINCT pso.id) AS siteOfferCount,",
            "  COUNT(DISTINCT CASE WHEN pms.id IS NOT NULL THEN pm.id END) AS detailBaselineCount",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.site = #{siteCode}",
            " AND lss.is_deleted = 0",
            "LEFT JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.site_id = lss.id",
            " AND pso.is_deleted = 0",
            "LEFT JOIN product_master_snapshot pms",
            "  ON pms.product_master_id = pm.id",
            " AND pms.snapshot_type = 'baseline'",
            " AND pms.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0"
    })
    NoonProductCompletenessAudit auditNoonProductCompleteness(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    default Long nextProductManagementId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateProductManagementId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("商品管理 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextLogicalStoreId() {
        return nextProductManagementId("logical_store", 50000L);
    }

    @Insert({
            "INSERT INTO logical_store (",
            "  id, owner_user_id, manager_user_id, project_code, project_name, status,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, NULL, #{projectCode}, #{projectName}, #{status},",
            "  0, #{ownerUserId}, #{ownerUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  project_name = VALUES(project_name),",
            "  status = VALUES(status),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertLogicalStore(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("projectName") String projectName,
            @Param("status") String status
    );

    @Select({
            "SELECT id",
            "FROM logical_store",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND project_code = #{projectCode}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectLogicalStoreId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    default Long nextLogicalStoreSiteId() {
        return nextProductManagementId("logical_store_site", 51000L);
    }

    @Insert({
            "INSERT INTO logical_store_site (",
            "  id, logical_store_id, store_code, site, is_reference_site, is_mounted, site_status,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{logicalStoreId}, #{storeCode}, #{site}, #{referenceSite}, #{mounted}, #{siteStatus},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  store_code = VALUES(store_code),",
            "  logical_store_id = VALUES(logical_store_id),",
            "  site = VALUES(site),",
            "  is_reference_site = VALUES(is_reference_site),",
            "  is_mounted = VALUES(is_mounted),",
            "  site_status = VALUES(site_status),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertLogicalStoreSite(
            @Param("id") Long id,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("referenceSite") boolean referenceSite,
            @Param("mounted") boolean mounted,
            @Param("siteStatus") String siteStatus,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM logical_store_site",
            "WHERE store_code = #{storeCode}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectLogicalStoreSiteId(@Param("storeCode") String storeCode);

    @Select({
            "SELECT id",
            "FROM logical_store_site",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND BINARY store_code = BINARY #{storeCode}",
            "LIMIT 1"
    })
    Long selectLogicalStoreSiteIdInLogicalStore(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT logical_store_id",
            "FROM logical_store_site",
            "WHERE BINARY store_code = BINARY #{storeCode}",
            "LIMIT 1"
    })
    Long selectLogicalStoreIdBySiteStoreCode(@Param("storeCode") String storeCode);

    @Select({
            "SELECT ls.id",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND BINARY lss.store_code = BINARY #{storeCode}",
            " AND lss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    Long selectLogicalStoreIdByOwnerStoreCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Update({
            "<script>",
            "UPDATE logical_store_site",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND is_deleted = 0",
            "  AND store_code NOT IN",
            "  <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "</script>"
    })
    int markStaleLogicalStoreSitesDeleted(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("storeCodes") List<String> storeCodes,
            @Param("updatedBy") Long updatedBy
    );

    default Long nextProductMasterId() {
        return nextProductManagementId("product_master", 52000L);
    }

    default Long nextProductImageAssetId() {
        return nextProductManagementId("product_image_asset", 62000L);
    }

    default Long nextProductIssueId() {
        return nextProductManagementId("product_issue", 63000L);
    }

    default Long nextProductPublishTaskId() {
        return nextProductManagementId("product_publish_task", 64000L);
    }

    default Long nextNoonBrandDictionaryId() {
        return nextProductManagementId("noon_brand_dictionary", 66000L);
    }

    default Long nextNoonProductFulltypeDictionaryId() {
        return nextProductManagementId("noon_product_fulltype_dictionary", 68000L);
    }

    @Insert({
            "INSERT INTO product_publish_task (",
            "  id, owner_user_id, product_master_id, baseline_snapshot_id, draft_snapshot_id,",
            "  store_code, project_code, sku_parent, partner_sku, psku_code, current_site_code,",
            "  task_type, status, active_lock_key, idempotency_key, draft_hash, changed_domains_json,",
            "  baseline_json, draft_json, request_json, result_json, error_code, error_message,",
            "  retry_count, verify_attempt_count, max_retry_count, version_no, next_run_at,",
            "  locked_by, locked_at, submitted_at, verify_started_at, verify_finished_at, finished_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{productMasterId}, #{baselineSnapshotId}, #{draftSnapshotId},",
            "  #{storeCode}, #{projectCode}, #{skuParent}, #{partnerSku}, #{pskuCode}, #{currentSiteCode},",
            "  #{taskType}, #{status}, #{activeLockKey}, #{idempotencyKey}, #{draftHash}, #{changedDomainsJson},",
            "  #{baselineJson}, #{draftJson}, #{requestJson}, #{resultJson}, #{errorCode}, #{errorMessage},",
            "  COALESCE(#{retryCount}, 0), COALESCE(#{verifyAttemptCount}, 0), COALESCE(#{maxRetryCount}, 3), COALESCE(#{versionNo}, 1), #{nextRunAt},",
            "  #{lockedBy}, #{lockedAt}, #{submittedAt}, #{verifyStartedAt}, #{verifyFinishedAt}, #{finishedAt},",
            "  0, #{ownerUserId}, #{ownerUserId}, NOW(), NOW()",
            ")"
    })
    int insertProductPublishTask(ProductPublishTaskRecord record);

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE id = #{id}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    @Results(id = "ProductPublishTaskRecordMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "owner_user_id", property = "ownerUserId"),
            @Result(column = "product_master_id", property = "productMasterId"),
            @Result(column = "baseline_snapshot_id", property = "baselineSnapshotId"),
            @Result(column = "draft_snapshot_id", property = "draftSnapshotId"),
            @Result(column = "store_code", property = "storeCode"),
            @Result(column = "project_code", property = "projectCode"),
            @Result(column = "sku_parent", property = "skuParent"),
            @Result(column = "partner_sku", property = "partnerSku"),
            @Result(column = "psku_code", property = "pskuCode"),
            @Result(column = "current_site_code", property = "currentSiteCode"),
            @Result(column = "task_type", property = "taskType"),
            @Result(column = "status", property = "status"),
            @Result(column = "active_lock_key", property = "activeLockKey"),
            @Result(column = "idempotency_key", property = "idempotencyKey"),
            @Result(column = "draft_hash", property = "draftHash"),
            @Result(column = "changed_domains_json", property = "changedDomainsJson"),
            @Result(column = "baseline_json", property = "baselineJson"),
            @Result(column = "draft_json", property = "draftJson"),
            @Result(column = "request_json", property = "requestJson"),
            @Result(column = "result_json", property = "resultJson"),
            @Result(column = "error_code", property = "errorCode"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "retry_count", property = "retryCount"),
            @Result(column = "verify_attempt_count", property = "verifyAttemptCount"),
            @Result(column = "max_retry_count", property = "maxRetryCount"),
            @Result(column = "version_no", property = "versionNo"),
            @Result(column = "next_run_at", property = "nextRunAt"),
            @Result(column = "locked_by", property = "lockedBy"),
            @Result(column = "locked_at", property = "lockedAt"),
            @Result(column = "submitted_at", property = "submittedAt"),
            @Result(column = "verify_started_at", property = "verifyStartedAt"),
            @Result(column = "verify_finished_at", property = "verifyFinishedAt"),
            @Result(column = "finished_at", property = "finishedAt")
    })
    ProductPublishTaskRecord selectProductPublishTaskById(@Param("id") Long id);

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE idempotency_key = #{idempotencyKey}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    ProductPublishTaskRecord selectProductPublishTaskByIdempotency(@Param("idempotencyKey") String idempotencyKey);

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0",
            "  AND id = (",
            "    SELECT MAX(latest.id)",
            "    FROM product_publish_task latest",
            "    WHERE latest.product_master_id = #{productMasterId}",
            "      AND latest.is_deleted = 0",
            "  )",
            "  AND status IN (",
            "    'queued', 'running', 'submitted', 'verifying',",
            "    'pending_effective', 'write_unknown', 'verify_timeout',",
            "    'write_retry_scheduled',",
            "    'product_delete_queued', 'product_delete_running',",
            "    'product_delete_submitted', 'product_delete_verifying',",
            "    'product_delete_pending_effective',",
            "    'product_delete_verify_timeout', 'product_delete_write_retry_scheduled'",
            "  )",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    ProductPublishTaskRecord selectActiveProductPublishTask(@Param("productMasterId") Long productMasterId);

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0",
            "ORDER BY COALESCE(finished_at, verify_finished_at, submitted_at, locked_at, gmt_updated, gmt_create) DESC, id DESC",
            "LIMIT 20"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectRecentProductPublishTasks(@Param("productMasterId") Long productMasterId);

    @Select({
            "<script>",
            "SELECT ppt.*",
            "FROM product_variant pv",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.logical_store_id = pv.logical_store_id",
            " AND pm.is_deleted = 0",
            "JOIN product_publish_task ppt",
            "  ON ppt.product_master_id = pm.id",
            " AND ppt.is_deleted = 0",
            "WHERE pv.logical_store_id = #{logicalStoreId}",
            "  AND pv.is_deleted = 0",
            "  AND pv.partner_sku IN",
            "  <foreach collection='partnerSkus' item='partnerSku' open='(' separator=',' close=')'>",
            "    #{partnerSku}",
            "  </foreach>",
            "  AND ppt.id = (",
            "    SELECT latest.id",
            "    FROM product_publish_task latest",
            "    WHERE latest.product_master_id = pm.id",
            "      AND latest.is_deleted = 0",
            "    ORDER BY COALESCE(latest.finished_at, latest.verify_finished_at, latest.submitted_at,",
            "             latest.locked_at, latest.gmt_updated, latest.gmt_create) DESC, latest.id DESC",
            "    LIMIT 1",
            "  )",
            "</script>"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectLatestProductPublishTasksByStorePartnerSkus(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSkus") List<String> partnerSkus
    );

    @Select({
            "SELECT ppt.*",
            "FROM product_publish_task ppt",
            "JOIN product_master source_pm",
            "  ON source_pm.id = ppt.product_master_id",
            "JOIN logical_store_site requested_site",
            "  ON requested_site.logical_store_id = source_pm.logical_store_id",
            " AND requested_site.store_code = #{storeCode}",
            " AND requested_site.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = source_pm.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = 0",
            "LEFT JOIN product_master active_pm",
            "  ON active_pm.logical_store_id = source_pm.logical_store_id",
            " AND active_pm.partner_sku = ppt.partner_sku",
            " AND active_pm.is_deleted = 0",
            "WHERE ppt.owner_user_id = #{ownerUserId}",
            "  AND ppt.is_deleted = 0",
            "  AND ppt.task_type = 'product-delete'",
            "  AND JSON_VALID(ppt.request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(ppt.request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND active_pm.id IS NULL",
            "  AND ppt.id = (",
            "    SELECT MAX(latest.id)",
            "    FROM product_publish_task latest",
            "    WHERE latest.product_master_id = ppt.product_master_id",
            "      AND latest.is_deleted = 0",
            "      AND latest.task_type = 'product-delete'",
            "      AND JSON_VALID(latest.request_json)",
            "      AND JSON_UNQUOTE(JSON_EXTRACT(latest.request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  )",
            "ORDER BY COALESCE(ppt.finished_at, ppt.verify_finished_at, ppt.submitted_at,",
            "         ppt.locked_at, ppt.gmt_updated, ppt.gmt_create) DESC, ppt.id DESC",
            "LIMIT #{limit}"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectVisibleProductRebuildTasksByStore(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT ppt.*",
            "FROM product_publish_task ppt",
            "JOIN product_master source_pm",
            "  ON source_pm.id = ppt.product_master_id",
            "JOIN logical_store_site requested_site",
            "  ON requested_site.logical_store_id = source_pm.logical_store_id",
            " AND requested_site.store_code = #{storeCode}",
            " AND requested_site.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = source_pm.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = 0",
            "LEFT JOIN product_master active_pm",
            "  ON active_pm.logical_store_id = source_pm.logical_store_id",
            " AND active_pm.partner_sku = ppt.partner_sku",
            " AND active_pm.is_deleted = 0",
            "WHERE ppt.owner_user_id = #{ownerUserId}",
            "  AND ppt.is_deleted = 0",
            "  AND ppt.task_type = 'product-delete'",
            "  AND JSON_VALID(ppt.request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(ppt.request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND active_pm.id IS NULL",
            "  <choose>",
            "    <when test='partnerSku != null and partnerSku != \"\"'>",
            "      AND (ppt.partner_sku = #{partnerSku} OR source_pm.partner_sku = #{partnerSku})",
            "    </when>",
            "    <when test='skuParent != null and skuParent != \"\"'>",
            "      AND (",
            "        ppt.sku_parent = #{skuParent}",
            "        OR source_pm.sku_parent = #{skuParent}",
            "        OR source_pm.current_z_code = #{skuParent}",
            "      )",
            "    </when>",
            "    <otherwise>",
            "      AND 1 = 0",
            "    </otherwise>",
            "  </choose>",
            "ORDER BY COALESCE(ppt.finished_at, ppt.verify_finished_at, ppt.submitted_at,",
            "         ppt.locked_at, ppt.gmt_updated, ppt.gmt_create) DESC, ppt.id DESC",
            "LIMIT 1",
            "</script>"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    ProductPublishTaskRecord selectLatestVisibleProductRebuildTaskByStoreIdentity(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("partnerSku") String partnerSku,
            @Param("skuParent") String skuParent
    );

    @Select({
            "<script>",
            "SELECT *",
            "FROM product_publish_task",
            "WHERE is_deleted = 0",
            "  AND status IN (",
            "    'queued', 'submitted', 'verifying', 'pending_effective', 'write_unknown', 'verify_timeout', 'write_retry_scheduled',",
            "    'product_delete_queued', 'product_delete_running', 'product_delete_submitted', 'product_delete_verifying',",
            "    'product_delete_pending_effective', 'product_delete_verify_timeout', 'product_delete_write_retry_scheduled'",
            "  )",
            "  AND locked_at IS NULL",
            "  AND (next_run_at IS NULL OR next_run_at &lt;= NOW())",
            "  AND id = (",
            "    SELECT MAX(latest.id)",
            "    FROM product_publish_task latest",
            "    WHERE latest.product_master_id = product_publish_task.product_master_id",
            "      AND latest.is_deleted = 0",
            "  )",
            "ORDER BY COALESCE(next_run_at, gmt_create), id",
            "LIMIT #{limit}",
            "</script>"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectRunnableProductPublishTasks(@Param("limit") int limit);

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE is_deleted = 0",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND (",
            "    result_json IS NULL",
            "    OR NOT JSON_VALID(result_json)",
            "    OR JSON_EXTRACT(result_json, '$.rebuild.status') IS NULL",
            "  )",
            "ORDER BY COALESCE(finished_at, gmt_updated, gmt_create), id",
            "LIMIT #{limit}"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectProductRebuildDeleteTasksReadyForListing(@Param("limit") int limit);

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE is_deleted = 0",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND JSON_VALID(result_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.status')) IN (",
            "    'listing_submitted', 'listing_running', 'listing_already_submitted'",
            "  )",
            "ORDER BY COALESCE(gmt_updated, gmt_create), id",
            "LIMIT #{limit}"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectProductRebuildDeleteTasksPendingListingReconciliation(@Param("limit") int limit);

    @Update({
            "UPDATE product_publish_task",
            "SET result_json = #{resultJson},",
            "    updated_by = #{ownerUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND (",
            "    result_json IS NULL",
            "    OR NOT JSON_VALID(result_json)",
            "    OR JSON_EXTRACT(result_json, '$.rebuild.status') IS NULL",
            "  )",
            "  AND is_deleted = 0"
    })
    int claimProductRebuildDeleteTaskForListing(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("resultJson") String resultJson
    );

    @Update({
            "UPDATE product_publish_task",
            "SET result_json = #{resultJson},",
            "    updated_by = #{ownerUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND is_deleted = 0"
    })
    int updateProductRebuildDeleteTaskResult(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("resultJson") String resultJson
    );

    @Update({
            "UPDATE product_publish_task t",
            "JOIN (",
            "  SELECT product_master_id, MAX(id) AS latest_id",
            "  FROM product_publish_task",
            "  WHERE is_deleted = 0",
            "  GROUP BY product_master_id",
            ") latest ON latest.latest_id = t.id",
            "SET t.status = CASE",
            "      WHEN t.task_type = 'product-delete' THEN 'product_delete_verify_timeout'",
            "      ELSE 'write_unknown'",
            "    END,",
            "    t.error_code = 'task_recovered_from_stale_running',",
            "    t.error_message = '发布任务执行中断，系统将只回读校验 Noon 当前结果。',",
            "    t.next_run_at = NOW(),",
            "    t.locked_by = NULL,",
            "    t.locked_at = NULL,",
            "    t.version_no = t.version_no + 1,",
            "    t.updated_by = #{updatedBy},",
            "    t.gmt_updated = NOW()",
            "WHERE t.status IN ('running', 'submitted', 'verifying', 'product_delete_running', 'product_delete_submitted', 'product_delete_verifying')",
            "  AND t.locked_at IS NOT NULL",
            "  AND t.locked_at < DATE_SUB(NOW(), INTERVAL #{staleMinutes} MINUTE)",
            "  AND t.is_deleted = 0"
    })
    int recoverStaleRunningProductPublishTasks(
            @Param("staleMinutes") int staleMinutes,
            @Param("updatedBy") Long updatedBy
    );

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
            "      candidate.error_message REGEXP 'HTTP[[:space:]]+(408|429|500|502|503|504)'",
            "      OR (",
            "        LOWER(candidate.error_message) LIKE '%http 403%'",
            "        AND LOWER(candidate.error_message) LIKE '%access denied%'",
            "        AND LOWER(candidate.error_message) LIKE '%you don''t have permission to access%'",
            "      )",
            "    )",
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
            "UPDATE product_publish_task",
            "SET status = CASE",
            "      WHEN task_type = 'product-delete' THEN 'product_delete_running'",
            "      ELSE 'running'",
            "    END,",
            "    locked_by = #{lockedBy},",
            "    locked_at = NOW(),",
            "    version_no = version_no + 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersionNo}",
            "  AND locked_at IS NULL",
            "  AND is_deleted = 0"
    })
    int tryStartProductPublishTask(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersionNo") Integer expectedVersionNo,
            @Param("lockedBy") String lockedBy,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_publish_task",
            "SET status = #{status},",
            "    result_json = #{resultJson},",
            "    error_code = #{errorCode},",
            "    error_message = #{errorMessage},",
            "    next_run_at = #{nextRunAt},",
            "    submitted_at = COALESCE(#{submittedAt}, submitted_at),",
            "    verify_started_at = COALESCE(#{verifyStartedAt}, verify_started_at),",
            "    verify_finished_at = COALESCE(#{verifyFinishedAt}, verify_finished_at),",
            "    finished_at = #{finishedAt},",
            "    retry_count = CASE",
            "      WHEN #{status} IN ('write_retry_scheduled', 'product_delete_write_retry_scheduled') THEN COALESCE(retry_count, 0) + 1",
            "      ELSE retry_count",
            "    END,",
            "    verify_attempt_count = COALESCE(#{verifyAttemptCount}, verify_attempt_count),",
            "    active_lock_key = CASE",
            "      WHEN #{status} IN ('synced', 'failed', 'cancelled', 'pending_manual_check') THEN NULL",
            "      ELSE active_lock_key",
            "    END,",
            "    locked_by = CASE WHEN #{releaseLock} THEN NULL ELSE locked_by END,",
            "    locked_at = CASE WHEN #{releaseLock} THEN NULL ELSE locked_at END,",
            "    version_no = version_no + 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status = #{expectedStatus}",
            "  AND locked_by = #{expectedLockedBy}",
            "  AND version_no = #{expectedVersionNo}",
            "  AND is_deleted = 0"
    })
    int updateProductPublishTaskStatus(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedLockedBy") String expectedLockedBy,
            @Param("expectedVersionNo") Integer expectedVersionNo,
            @Param("status") String status,
            @Param("resultJson") String resultJson,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("nextRunAt") LocalDateTime nextRunAt,
            @Param("submittedAt") LocalDateTime submittedAt,
            @Param("verifyStartedAt") LocalDateTime verifyStartedAt,
            @Param("verifyFinishedAt") LocalDateTime verifyFinishedAt,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("verifyAttemptCount") Integer verifyAttemptCount,
            @Param("releaseLock") boolean releaseLock,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_publish_task",
            "SET draft_json = #{draftJson},",
            "    baseline_json = #{baselineJson},",
            "    request_json = #{requestJson},",
            "    changed_domains_json = #{changedDomainsJson},",
            "    draft_hash = #{draftHash},",
            "    result_json = NULL,",
            "    error_code = #{errorCode},",
            "    error_message = #{errorMessage},",
            "    retry_count = 0,",
            "    next_run_at = NOW(),",
            "    finished_at = NULL,",
            "    locked_by = NULL,",
            "    locked_at = NULL,",
            "    version_no = version_no + 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status = 'write_retry_scheduled'",
            "  AND locked_at IS NULL",
            "  AND version_no = #{expectedVersionNo}",
            "  AND is_deleted = 0"
    })
    int refreshRetryScheduledProductPublishTaskDraft(
            @Param("id") Long id,
            @Param("expectedVersionNo") Integer expectedVersionNo,
            @Param("draftJson") String draftJson,
            @Param("baselineJson") String baselineJson,
            @Param("requestJson") String requestJson,
            @Param("changedDomainsJson") String changedDomainsJson,
            @Param("draftHash") String draftHash,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_publish_task",
            "SET status = CASE",
            "        WHEN task_type = 'product-delete' THEN 'product_delete_queued'",
            "        ELSE 'queued'",
            "    END,",
            "    retry_count = retry_count + 1,",
            "    next_run_at = NOW(),",
            "    error_code = NULL,",
            "    error_message = NULL,",
            "    result_json = NULL,",
            "    active_lock_key = CONCAT('product:', product_master_id),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status IN ('failed', 'pending_manual_check')",
            "  AND retry_count < max_retry_count",
            "  AND is_deleted = 0"
    })
    int retryProductPublishTask(@Param("id") Long id, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE product_publish_task",
            "SET status = 'cancelled',",
            "    finished_at = NOW(),",
            "    active_lock_key = NULL,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status IN ('queued', 'product_delete_queued')",
            "  AND is_deleted = 0"
    })
    int cancelQueuedProductPublishTask(@Param("id") Long id, @Param("updatedBy") Long updatedBy);

    @Insert({
            "INSERT INTO product_master (",
            "  id, logical_store_id, partner_sku, current_z_code, sku_parent, product_source_type, brand_cache, title_cache, title_cn_cache, product_fulltype_cache, cover_image_url,",
            "  sku_group, group_name_cache, group_ref, group_member_count, issue_count, issue_summary_json,",
            "  variant_count_cache, sync_status, last_synced_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{logicalStoreId}, #{partnerSku}, #{currentZCode}, #{skuParent}, #{productSourceType}, #{brandCache}, #{titleCache}, #{titleCnCache}, #{productFulltypeCache}, #{coverImageUrl},",
            "  #{skuGroup}, #{groupNameCache}, #{groupRef}, #{groupMemberCount}, #{issueCount}, #{issueSummaryJson},",
            "  #{variantCountCache}, #{syncStatus}, #{lastSyncedAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  partner_sku = COALESCE(NULLIF(VALUES(partner_sku), ''), partner_sku),",
            "  current_z_code = COALESCE(NULLIF(VALUES(current_z_code), ''), current_z_code),",
            "  sku_parent = VALUES(sku_parent),",
            "  product_source_type = VALUES(product_source_type),",
            "  brand_cache = VALUES(brand_cache),",
            "  title_cache = VALUES(title_cache),",
            "  title_cn_cache = COALESCE(NULLIF(VALUES(title_cn_cache), ''), title_cn_cache),",
            "  product_fulltype_cache = VALUES(product_fulltype_cache),",
            "  cover_image_url = VALUES(cover_image_url),",
            "  sku_group = VALUES(sku_group),",
            "  group_name_cache = VALUES(group_name_cache),",
            "  group_ref = VALUES(group_ref),",
            "  group_member_count = VALUES(group_member_count),",
            "  issue_count = VALUES(issue_count),",
            "  issue_summary_json = VALUES(issue_summary_json),",
            "  variant_count_cache = VALUES(variant_count_cache),",
            "  sync_status = VALUES(sync_status),",
            "  last_synced_at = VALUES(last_synced_at),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductMaster(
            @Param("id") Long id,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("currentZCode") String currentZCode,
            @Param("skuParent") String skuParent,
            @Param("productSourceType") String productSourceType,
            @Param("brandCache") String brandCache,
            @Param("titleCache") String titleCache,
            @Param("titleCnCache") String titleCnCache,
            @Param("productFulltypeCache") String productFulltypeCache,
            @Param("coverImageUrl") String coverImageUrl,
            @Param("skuGroup") String skuGroup,
            @Param("groupNameCache") String groupNameCache,
            @Param("groupRef") String groupRef,
            @Param("groupMemberCount") Integer groupMemberCount,
            @Param("issueCount") Integer issueCount,
            @Param("issueSummaryJson") String issueSummaryJson,
            @Param("variantCountCache") Integer variantCountCache,
            @Param("syncStatus") String syncStatus,
            @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO noon_brand_dictionary (",
            "  id, owner_user_id, project_code, store_code, brand_key, brand_name, label_en, label_ar,",
            "  source, status, usage_count, last_seen_at, fetched_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{projectCode}, #{storeCode}, #{brandKey}, #{brandName}, #{labelEn}, #{labelAr},",
            "  #{source}, 'active', COALESCE(#{usageCount}, 1), #{lastSeenAt}, NOW(), 0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  brand_name = VALUES(brand_name),",
            "  label_en = VALUES(label_en),",
            "  label_ar = COALESCE(VALUES(label_ar), label_ar),",
            "  source = VALUES(source),",
            "  status = 'active',",
            "  usage_count = GREATEST(usage_count, VALUES(usage_count)),",
            "  last_seen_at = GREATEST(COALESCE(last_seen_at, '1970-01-01'), COALESCE(VALUES(last_seen_at), '1970-01-01')),",
            "  fetched_at = VALUES(fetched_at),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertNoonBrandDictionary(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("brandKey") String brandKey,
            @Param("brandName") String brandName,
            @Param("labelEn") String labelEn,
            @Param("labelAr") String labelAr,
            @Param("source") String source,
            @Param("usageCount") Integer usageCount,
            @Param("lastSeenAt") LocalDateTime lastSeenAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM noon_brand_dictionary",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND project_code = #{projectCode}",
            "  AND store_code = #{storeCode}",
            "  AND brand_key = #{brandKey}",
            "LIMIT 1"
    })
    Long selectNoonBrandDictionaryId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("brandKey") String brandKey
    );

    @Insert({
            "INSERT INTO noon_product_fulltype_dictionary (",
            "  id, owner_user_id, project_code, store_code, product_fulltype, family, product_type, product_subtype,",
            "  label_en, label_ar, source, status, usage_count, last_seen_at, fetched_at, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{projectCode}, #{storeCode}, #{productFulltype}, #{family}, #{productType}, #{productSubtype},",
            "  #{labelEn}, #{labelAr}, #{source}, 'active', COALESCE(#{usageCount}, 1), #{lastSeenAt}, NOW(), 0,",
            "  #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  family = VALUES(family),",
            "  product_type = VALUES(product_type),",
            "  product_subtype = VALUES(product_subtype),",
            "  label_en = VALUES(label_en),",
            "  label_ar = COALESCE(VALUES(label_ar), label_ar),",
            "  source = VALUES(source),",
            "  status = 'active',",
            "  usage_count = GREATEST(usage_count, VALUES(usage_count)),",
            "  last_seen_at = GREATEST(COALESCE(last_seen_at, '1970-01-01'), COALESCE(VALUES(last_seen_at), '1970-01-01')),",
            "  fetched_at = VALUES(fetched_at),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertNoonProductFulltypeDictionary(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype,
            @Param("family") String family,
            @Param("productType") String productType,
            @Param("productSubtype") String productSubtype,
            @Param("labelEn") String labelEn,
            @Param("labelAr") String labelAr,
            @Param("source") String source,
            @Param("usageCount") Integer usageCount,
            @Param("lastSeenAt") LocalDateTime lastSeenAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM noon_product_fulltype_dictionary",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND project_code = #{projectCode}",
            "  AND store_code = #{storeCode}",
            "  AND product_fulltype = #{productFulltype}",
            "LIMIT 1"
    })
    Long selectNoonProductFulltypeDictionaryId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("storeCode") String storeCode,
            @Param("productFulltype") String productFulltype
    );

    default Long selectProductMasterId(Long logicalStoreId, String skuParent) {
        if (logicalStoreId == null || skuParent == null || skuParent.trim().isEmpty()) {
            return null;
        }
        String normalizedSkuParent = skuParent.trim();
        Long currentZMatch = selectProductMasterIdByCurrentZCode(logicalStoreId, normalizedSkuParent);
        if (currentZMatch != null) {
            return currentZMatch;
        }
        return selectProductMasterIdByLegacySkuParent(logicalStoreId, normalizedSkuParent);
    }

    @Select({
            "SELECT id",
            "FROM product_master",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND current_z_code = #{currentZCode}",
            "  AND is_deleted = 0",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Long selectProductMasterIdByCurrentZCode(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("currentZCode") String currentZCode
    );

    @Select({
            "SELECT id",
            "FROM product_master",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND sku_parent = #{skuParent}",
            "  AND is_deleted = 0",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Long selectProductMasterIdByLegacySkuParent(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT id",
            "FROM product_master",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND partner_sku = #{partnerSku}",
            "  AND is_deleted = 0",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Long selectProductMasterIdByStorePartnerSku(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku
    );

    default Long selectProductMasterIdByStoreCode(Long ownerUserId, String storeCode, String skuParent) {
        if (ownerUserId == null || storeCode == null || storeCode.trim().isEmpty()
                || skuParent == null || skuParent.trim().isEmpty()) {
            return null;
        }
        String normalizedStoreCode = storeCode.trim();
        String normalizedSkuParent = skuParent.trim();
        Long currentZMatch = selectProductMasterIdByStoreCodeCurrentZCode(
                ownerUserId,
                normalizedStoreCode,
                normalizedSkuParent
        );
        if (currentZMatch != null) {
            return currentZMatch;
        }
        return selectProductMasterIdByStoreCodeLegacySkuParent(
                ownerUserId,
                normalizedStoreCode,
                normalizedSkuParent
        );
    }

    @Select({
            "SELECT pm.id",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.current_z_code = #{currentZCode}",
            " AND pm.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "ORDER BY pm.gmt_updated DESC, pm.id DESC",
            "LIMIT 1"
    })
    Long selectProductMasterIdByStoreCodeCurrentZCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("currentZCode") String currentZCode
    );

    @Select({
            "SELECT pm.id",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "ORDER BY pm.gmt_updated DESC, pm.id DESC",
            "LIMIT 1"
    })
    Long selectProductMasterIdByStoreCodeLegacySkuParent(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT",
            "  pm.id AS productMasterId,",
            "  ls.id AS logicalStoreId,",
            "  anchor.store_code AS storeCode,",
            "  anchor.site AS siteCode,",
            "  pm.sku_parent AS skuParent,",
            "  pm.product_source_type AS productSourceType,",
            "  pv.partner_sku AS partnerSku,",
            "  COALESCE(",
            "    MAX(CASE WHEN pso.site_id = anchor.id THEN pso.psku_code END),",
            "    MAX(pso.psku_code)",
            "  ) AS pskuCode",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.logical_store_id = ls.id",
            " AND pv.partner_sku = #{partnerSku}",
            " AND pv.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "GROUP BY pm.id, ls.id, anchor.store_code, anchor.site, pm.sku_parent, pm.product_source_type, pv.partner_sku",
            "LIMIT 1"
    })
    ProductMasterIdentityRecord selectProductMasterIdentityByStorePartnerSku(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT",
            "  pm.id AS productMasterId,",
            "  ls.id AS logicalStoreId,",
            "  anchor.store_code AS storeCode,",
            "  anchor.site AS siteCode,",
            "  pm.sku_parent AS skuParent,",
            "  pm.product_source_type AS productSourceType,",
            "  MAX(pv.partner_sku) AS partnerSku,",
            "  COALESCE(",
            "    MAX(CASE WHEN pso.site_id = anchor.id THEN pso.psku_code END),",
            "    MAX(pso.psku_code)",
            "  ) AS pskuCode",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.logical_store_id = ls.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "GROUP BY pm.id, ls.id, anchor.store_code, anchor.site, pm.sku_parent, pm.product_source_type",
            "LIMIT 1"
    })
    ProductMasterIdentityRecord selectProductMasterIdentityByStoreSkuParent(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT pm.sku_parent",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 1",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0"
    })
    List<String> selectDeletedProductSkuParentsByStoreCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Update({
            "UPDATE product_master",
            "SET sync_status = #{syncStatus},",
            "    last_synced_at = #{lastSyncedAt},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND sku_parent = #{skuParent}",
            "  AND is_deleted = 0"
    })
    int updateProductMasterStatus(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("skuParent") String skuParent,
            @Param("syncStatus") String syncStatus,
            @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_master",
            "SET sync_status = #{syncStatus},",
            "    last_synced_at = COALESCE(#{lastSyncedAt}, last_synced_at),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int updateProductMasterStatusById(
            @Param("productMasterId") Long productMasterId,
            @Param("syncStatus") String syncStatus,
            @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_master",
            "SET issue_count = #{issueCount},",
            "    issue_summary_json = #{issueSummaryJson},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int updateProductMasterIssueSummary(
            @Param("productMasterId") Long productMasterId,
            @Param("issueCount") Integer issueCount,
            @Param("issueSummaryJson") String issueSummaryJson,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_group_member",
            "SET member_status = 'deleted',",
            "    is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int markProductGroupMembersDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_group pg",
            "SET member_count = (",
            "      SELECT COUNT(1)",
            "      FROM product_group_member pgm",
            "      WHERE pgm.product_group_id = pg.id",
            "        AND pgm.member_status = 'active'",
            "        AND pgm.is_deleted = 0",
            "    ),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE EXISTS (",
            "  SELECT 1",
            "  FROM product_group_member pgm2",
            "  WHERE pgm2.product_group_id = pg.id",
            "    AND pgm2.product_master_id = #{productMasterId}",
            ")"
    })
    int refreshProductGroupMemberCountsForProductMaster(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            "SET pso.is_deleted = 1,",
            "    pso.updated_by = #{updatedBy},",
            "    pso.gmt_updated = NOW()",
            "WHERE pv.product_master_id = #{productMasterId}",
            "  AND pso.is_deleted = 0"
    })
    int markProductSiteOffersDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            " AND pv.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = COALESCE(pso.logical_store_id, pv.logical_store_id)",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = 0",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "SET pso.operation_stage_code = #{operationStageCode},",
            "    pso.operation_stage_updated_at = NOW(),",
            "    pso.operation_stage_updated_by = #{operatorUserId},",
            "    pso.updated_by = #{operatorUserId},",
            "    pso.gmt_updated = NOW()",
            "WHERE pso.is_deleted = 0",
            "  AND (pso.partner_sku = #{partnerSku} OR pv.partner_sku = #{partnerSku})"
    })
    int updateProductSiteOfferOperationStage(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("partnerSku") String partnerSku,
            @Param("operationStageCode") String operationStageCode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE product_barcode pb",
            "JOIN product_variant pv",
            "  ON pv.id = pb.variant_id",
            "SET pb.is_deleted = 1,",
            "    pb.updated_by = #{updatedBy},",
            "    pb.gmt_updated = NOW()",
            "WHERE pv.product_master_id = #{productMasterId}",
            "  AND pb.is_deleted = 0"
    })
    int markProductBarcodesDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_variant",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int markProductVariantsDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_master_snapshot",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int markProductMasterSnapshotsDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_asset",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int markProductImageAssetsDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_issue",
            "SET issue_status = 'resolved',",
            "    resolved_at = COALESCE(resolved_at, NOW()),",
            "    is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int markProductIssuesDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_master",
            "SET is_deleted = 1,",
            "    sync_status = 'deleted',",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int markProductMasterDeletedById(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS skuParent,",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS currentZCode,",
            "  pm.product_source_type AS productSourceType,",
            "  pm.partner_sku AS partnerSku,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.psku_code END),",
            "    MAX(pso.psku_code)",
            "  ) AS pskuCode,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.offer_code END),",
            "    MAX(pso.offer_code)",
            "  ) AS offerCode,",
            "  pm.title_cache AS title,",
            "  pm.title_cn_cache AS titleCn,",
            "  pm.brand_cache AS brand,",
            "  pm.cover_image_url AS imageUrl,",
            "  (",
            "    SELECT COALESCE(",
            "      MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END),",
            "      MAX(pb.barcode)",
            "    )",
            "    FROM product_variant bpv",
            "    JOIN product_barcode pb",
            "      ON pb.variant_id = bpv.id",
            "     AND pb.is_deleted = 0",
            "    WHERE bpv.product_master_id = pm.id",
            "      AND bpv.is_deleted = 0",
            "  ) AS barcode,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN COALESCE(pso.final_price, pso.sale_price, pso.price) END),",
            "    MAX(COALESCE(pso.final_price, pso.sale_price, pso.price))",
            "  ) AS CHAR) AS referencePrice,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.price END),",
            "    MAX(pso.price)",
            "  ) AS CHAR) AS originalPrice,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sale_price END),",
            "    MAX(pso.sale_price)",
            "  ) AS CHAR) AS salePrice,",
            "  pm.product_fulltype_cache AS productFulltype,",
            "  pm.variant_count_cache AS variantCount,",
            "  COUNT(DISTINCT lss.id) AS siteOfferCount,",
            "  GROUP_CONCAT(DISTINCT lss.site ORDER BY lss.site SEPARATOR ',') AS siteLabelsCsv,",
            "  GROUP_CONCAT(DISTINCT COALESCE(pso.live_status, pso.status_code) ",
            "    ORDER BY COALESCE(pso.live_status, pso.status_code) SEPARATOR ',') AS liveStatusesCsv,",
            "  COALESCE(SUM(COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0)), 0) AS totalFbnStock,",
            "  COALESCE(SUM(pso.supermall_stock), 0) AS totalSupermallStock,",
            "  COALESCE(SUM(pso.fbp_stock), 0) AS totalFbpStock,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.views_count END),",
            "    MAX(pso.views_count)",
            "  ) AS viewsCount,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.units_sold END),",
            "    MAX(pso.units_sold)",
            "  ) AS unitsSold,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sales_amount END),",
            "    MAX(pso.sales_amount)",
            "  ) AS CHAR) AS salesAmount,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sales_currency END),",
            "    MAX(pso.sales_currency)",
            "  ) AS salesCurrency,",
            "  MAX(pg_for_member.sku_group) AS skuGroup,",
            "  MAX(pg_for_member.group_ref) AS groupRef,",
            "  MAX(pg_for_member.group_ref_canonical) AS groupRefCanonical,",
            "  pm.issue_count AS issueCount,",
            "  (",
            "    SELECT GROUP_CONCAT(DISTINCT COALESCE(pi.title, pi.message, pi.issue_code) ORDER BY pi.last_seen_at DESC SEPARATOR ',')",
            "    FROM product_issue pi",
            "    WHERE pi.product_master_id = pm.id",
            "      AND pi.issue_status = 'open'",
            "      AND pi.is_deleted = 0",
            "  ) AS issueTagsCsv,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN CASE WHEN pso.is_active = b'1' THEN 1 ELSE 0 END END),",
            "    MAX(CASE WHEN pso.is_active = b'1' THEN 1 ELSE 0 END)",
            "  ) AS SIGNED) AS currentSiteActiveFlag,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.live_status END),",
            "    MAX(pso.live_status)",
            "  ) AS currentSiteLiveStatus,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.status_code END),",
            "    MAX(pso.status_code)",
            "  ) AS currentSiteStatusCode,",
            "  DATE_FORMAT(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.listing_started_at END),",
            "    MAX(pso.listing_started_at)",
            "  ), '%Y-%m-%d %H:%i:%s') AS listingStartedAt,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.listing_started_source END),",
            "    MAX(pso.listing_started_source)",
            "  ) AS listingStartedSource,",
            "  MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_code END) AS operationStageCode,",
            "  DATE_FORMAT(MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_updated_at END), '%Y-%m-%d %H:%i:%s') AS operationStageUpdatedAt,",
            "  MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_updated_by END) AS operationStageUpdatedBy,",
            "  CASE WHEN EXISTS (",
            "    SELECT 1",
            "    FROM product_master_draft pmd",
            "    JOIN product_master_snapshot pms",
            "      ON pms.id = pmd.baseline_snapshot_id",
            "     AND pms.is_deleted = 0",
            "    WHERE pmd.product_master_id = pm.id",
            "      AND pmd.is_deleted = 0",
            "      AND COALESCE(pmd.draft_json, '') <> COALESCE(pms.snapshot_json, '')",
            "  ) THEN 'draft' ELSE pm.sync_status END AS syncStatus,",
            "  DATE_FORMAT(pm.last_synced_at, '%Y-%m-%d %H:%i:%s') AS lastSyncedAt,",
            "  (",
            "    SELECT DATE_FORMAT(pmd.saved_at, '%Y-%m-%d %H:%i:%s')",
            "    FROM product_master_draft pmd",
            "    JOIN product_master_snapshot pms",
            "      ON pms.id = pmd.baseline_snapshot_id",
            "     AND pms.is_deleted = 0",
            "    WHERE pmd.product_master_id = pm.id",
            "      AND pmd.is_deleted = 0",
            "      AND COALESCE(pmd.draft_json, '') <> COALESCE(pms.snapshot_json, '')",
            "    ORDER BY pmd.saved_at DESC, pmd.id DESC",
            "    LIMIT 1",
            "  ) AS lastDraftSavedAt,",
            "  CASE WHEN EXISTS (",
            "    SELECT 1",
            "    FROM product_master_snapshot pms",
            "    WHERE pms.product_master_id = pm.id",
            "      AND pms.snapshot_type = 'baseline'",
            "      AND pms.is_deleted = 0",
            "  ) THEN 'ready' ELSE 'missing' END AS detailBaselineStatus,",
            "  (",
            "    SELECT DATE_FORMAT(MAX(pms.fetched_at), '%Y-%m-%d %H:%i:%s')",
            "    FROM product_master_snapshot pms",
            "    WHERE pms.product_master_id = pm.id",
            "      AND pms.snapshot_type = 'baseline'",
            "      AND pms.is_deleted = 0",
            "  ) AS detailBaselineSyncedAt,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_variant spec_pv",
            "    WHERE spec_pv.product_master_id = pm.id",
            "      AND spec_pv.is_deleted = 0",
            "  ) AS productVariantSpecTotalCount,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_variant spec_pv",
            "    JOIN product_variant_spec pvs",
            "      ON pvs.variant_id = spec_pv.id",
            "     AND pvs.is_deleted = 0",
            "    WHERE spec_pv.product_master_id = pm.id",
            "      AND spec_pv.is_deleted = 0",
            "  ) AS productVariantSpecMaintainedCount,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_variant spec_pv",
            "    JOIN product_variant_spec pvs",
            "      ON pvs.variant_id = spec_pv.id",
            "     AND pvs.is_deleted = 0",
            "    WHERE spec_pv.product_master_id = pm.id",
            "      AND spec_pv.is_deleted = 0",
            "      AND pvs.product_length_cm IS NOT NULL",
            "      AND pvs.product_width_cm IS NOT NULL",
            "      AND pvs.product_height_cm IS NOT NULL",
            "      AND pvs.product_weight_g IS NOT NULL",
            "      AND COALESCE(pvs.battery_magnetic_type, 'unknown') <> 'unknown'",
            "      AND COALESCE(pvs.liquid_powder_type, 'unknown') <> 'unknown'",
            "  ) AS productVariantSpecReadyCount",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.is_deleted = 0",
            "LEFT JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "LEFT JOIN product_group_member pgm_for_member",
            "  ON pgm_for_member.product_master_id = pm.id",
            " AND pgm_for_member.member_status = 'active'",
            " AND pgm_for_member.is_deleted = 0",
            "LEFT JOIN product_group pg_for_member",
            "  ON pg_for_member.id = pgm_for_member.product_group_id",
            " AND pg_for_member.logical_store_id = ls.id",
            " AND pg_for_member.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "GROUP BY",
            "  pm.id, pm.sku_parent, pm.current_z_code, pm.partner_sku, pm.title_cache, pm.title_cn_cache, pm.brand_cache, pm.cover_image_url,",
            "  pm.product_source_type, pm.product_fulltype_cache, pm.variant_count_cache, pm.group_ref, pm.issue_count,",
            "  pm.sync_status, pm.last_synced_at",
            "ORDER BY pm.gmt_updated DESC, pm.id DESC"
    })
    List<ProductListProjectionRecord> selectProductListProjection(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT owner_user_id",
            "FROM logical_store",
            "WHERE id = #{logicalStoreId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectLogicalStoreOwnerUserId(@Param("logicalStoreId") Long logicalStoreId);

    default ProductListProjectionRecord selectProductListProjectionByStorePartnerSku(
            Long logicalStoreId,
            String storeCode,
            String partnerSku
    ) {
        if (logicalStoreId == null || storeCode == null || storeCode.trim().isEmpty()
                || partnerSku == null || partnerSku.trim().isEmpty()) {
            return null;
        }
        Long productMasterId = selectProductMasterIdByStorePartnerSku(logicalStoreId, partnerSku.trim());
        if (productMasterId == null) {
            return null;
        }
        Long ownerUserId = selectLogicalStoreOwnerUserId(logicalStoreId);
        if (ownerUserId == null) {
            return null;
        }
        return selectProductListProjectionByProductMasterId(ownerUserId, storeCode.trim(), productMasterId);
    }

    @Select({
            "SELECT",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS skuParent,",
            "  pia.url AS imageUrl",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_image_asset pia",
            "  ON pia.product_master_id = pm.id",
            " AND pia.source_type = 'noon'",
            " AND pia.asset_status = 'synced'",
            " AND pia.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "ORDER BY pm.id, pia.id"
    })
    List<ProductListSnapshotMediaRecord> selectLatestProductListSnapshotMedia(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS skuParent,",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS currentZCode,",
            "  pm.product_source_type AS productSourceType,",
            "  pm.partner_sku AS partnerSku,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.psku_code END),",
            "    MAX(pso.psku_code)",
            "  ) AS pskuCode,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.offer_code END),",
            "    MAX(pso.offer_code)",
            "  ) AS offerCode,",
            "  pm.title_cache AS title,",
            "  pm.title_cn_cache AS titleCn,",
            "  pm.brand_cache AS brand,",
            "  pm.cover_image_url AS imageUrl,",
            "  (",
            "    SELECT COALESCE(",
            "      MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END),",
            "      MAX(pb.barcode)",
            "    )",
            "    FROM product_variant bpv",
            "    JOIN product_barcode pb",
            "      ON pb.variant_id = bpv.id",
            "     AND pb.is_deleted = 0",
            "    WHERE bpv.product_master_id = pm.id",
            "      AND bpv.is_deleted = 0",
            "  ) AS barcode,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN COALESCE(pso.final_price, pso.sale_price, pso.price) END),",
            "    MAX(COALESCE(pso.final_price, pso.sale_price, pso.price))",
            "  ) AS CHAR) AS referencePrice,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.price END),",
            "    MAX(pso.price)",
            "  ) AS CHAR) AS originalPrice,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sale_price END),",
            "    MAX(pso.sale_price)",
            "  ) AS CHAR) AS salePrice,",
            "  pm.product_fulltype_cache AS productFulltype,",
            "  pm.variant_count_cache AS variantCount,",
            "  COUNT(DISTINCT lss.id) AS siteOfferCount,",
            "  GROUP_CONCAT(DISTINCT lss.site ORDER BY lss.site SEPARATOR ',') AS siteLabelsCsv,",
            "  GROUP_CONCAT(DISTINCT COALESCE(pso.live_status, pso.status_code) ",
            "    ORDER BY COALESCE(pso.live_status, pso.status_code) SEPARATOR ',') AS liveStatusesCsv,",
            "  COALESCE(SUM(COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0)), 0) AS totalFbnStock,",
            "  COALESCE(SUM(pso.supermall_stock), 0) AS totalSupermallStock,",
            "  COALESCE(SUM(pso.fbp_stock), 0) AS totalFbpStock,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.views_count END),",
            "    MAX(pso.views_count)",
            "  ) AS viewsCount,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.units_sold END),",
            "    MAX(pso.units_sold)",
            "  ) AS unitsSold,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sales_amount END),",
            "    MAX(pso.sales_amount)",
            "  ) AS CHAR) AS salesAmount,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sales_currency END),",
            "    MAX(pso.sales_currency)",
            "  ) AS salesCurrency,",
            "  MAX(pg_for_member.sku_group) AS skuGroup,",
            "  MAX(pg_for_member.group_ref) AS groupRef,",
            "  MAX(pg_for_member.group_ref_canonical) AS groupRefCanonical,",
            "  pm.issue_count AS issueCount,",
            "  (",
            "    SELECT GROUP_CONCAT(DISTINCT COALESCE(pi.title, pi.message, pi.issue_code) ORDER BY pi.last_seen_at DESC SEPARATOR ',')",
            "    FROM product_issue pi",
            "    WHERE pi.product_master_id = pm.id",
            "      AND pi.issue_status = 'open'",
            "      AND pi.is_deleted = 0",
            "  ) AS issueTagsCsv,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN CASE WHEN pso.is_active = b'1' THEN 1 ELSE 0 END END),",
            "    MAX(CASE WHEN pso.is_active = b'1' THEN 1 ELSE 0 END)",
            "  ) AS SIGNED) AS currentSiteActiveFlag,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.live_status END),",
            "    MAX(pso.live_status)",
            "  ) AS currentSiteLiveStatus,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.status_code END),",
            "    MAX(pso.status_code)",
            "  ) AS currentSiteStatusCode,",
            "  DATE_FORMAT(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.listing_started_at END),",
            "    MAX(pso.listing_started_at)",
            "  ), '%Y-%m-%d %H:%i:%s') AS listingStartedAt,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.listing_started_source END),",
            "    MAX(pso.listing_started_source)",
            "  ) AS listingStartedSource,",
            "  MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_code END) AS operationStageCode,",
            "  DATE_FORMAT(MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_updated_at END), '%Y-%m-%d %H:%i:%s') AS operationStageUpdatedAt,",
            "  MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_updated_by END) AS operationStageUpdatedBy,",
            "  CASE WHEN EXISTS (",
            "    SELECT 1",
            "    FROM product_master_draft pmd",
            "    JOIN product_master_snapshot pms",
            "      ON pms.id = pmd.baseline_snapshot_id",
            "     AND pms.is_deleted = 0",
            "    WHERE pmd.product_master_id = pm.id",
            "      AND pmd.is_deleted = 0",
            "      AND COALESCE(pmd.draft_json, '') <> COALESCE(pms.snapshot_json, '')",
            "  ) THEN 'draft' ELSE pm.sync_status END AS syncStatus,",
            "  DATE_FORMAT(pm.last_synced_at, '%Y-%m-%d %H:%i:%s') AS lastSyncedAt,",
            "  (",
            "    SELECT DATE_FORMAT(pmd.saved_at, '%Y-%m-%d %H:%i:%s')",
            "    FROM product_master_draft pmd",
            "    JOIN product_master_snapshot pms",
            "      ON pms.id = pmd.baseline_snapshot_id",
            "     AND pms.is_deleted = 0",
            "    WHERE pmd.product_master_id = pm.id",
            "      AND pmd.is_deleted = 0",
            "      AND COALESCE(pmd.draft_json, '') <> COALESCE(pms.snapshot_json, '')",
            "    ORDER BY pmd.saved_at DESC, pmd.id DESC",
            "    LIMIT 1",
            "  ) AS lastDraftSavedAt,",
            "  CASE WHEN EXISTS (",
            "    SELECT 1",
            "    FROM product_master_snapshot pms",
            "    WHERE pms.product_master_id = pm.id",
            "      AND pms.snapshot_type = 'baseline'",
            "      AND pms.is_deleted = 0",
            "  ) THEN 'ready' ELSE 'missing' END AS detailBaselineStatus,",
            "  (",
            "    SELECT DATE_FORMAT(MAX(pms.fetched_at), '%Y-%m-%d %H:%i:%s')",
            "    FROM product_master_snapshot pms",
            "    WHERE pms.product_master_id = pm.id",
            "      AND pms.snapshot_type = 'baseline'",
            "      AND pms.is_deleted = 0",
            "  ) AS detailBaselineSyncedAt,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_variant spec_pv",
            "    WHERE spec_pv.product_master_id = pm.id",
            "      AND spec_pv.is_deleted = 0",
            "  ) AS productVariantSpecTotalCount,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_variant spec_pv",
            "    JOIN product_variant_spec pvs",
            "      ON pvs.variant_id = spec_pv.id",
            "     AND pvs.is_deleted = 0",
            "    WHERE spec_pv.product_master_id = pm.id",
            "      AND spec_pv.is_deleted = 0",
            "  ) AS productVariantSpecMaintainedCount,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_variant spec_pv",
            "    JOIN product_variant_spec pvs",
            "      ON pvs.variant_id = spec_pv.id",
            "     AND pvs.is_deleted = 0",
            "    WHERE spec_pv.product_master_id = pm.id",
            "      AND spec_pv.is_deleted = 0",
            "      AND pvs.product_length_cm IS NOT NULL",
            "      AND pvs.product_width_cm IS NOT NULL",
            "      AND pvs.product_height_cm IS NOT NULL",
            "      AND pvs.product_weight_g IS NOT NULL",
            "      AND COALESCE(pvs.battery_magnetic_type, 'unknown') <> 'unknown'",
            "      AND COALESCE(pvs.liquid_powder_type, 'unknown') <> 'unknown'",
            "  ) AS productVariantSpecReadyCount",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.is_deleted = 0",
            "LEFT JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "LEFT JOIN product_group_member pgm_for_member",
            "  ON pgm_for_member.product_master_id = pm.id",
            " AND pgm_for_member.member_status = 'active'",
            " AND pgm_for_member.is_deleted = 0",
            "LEFT JOIN product_group pg_for_member",
            "  ON pg_for_member.id = pgm_for_member.product_group_id",
            " AND pg_for_member.logical_store_id = ls.id",
            " AND pg_for_member.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND pm.id = #{productMasterId}",
            "GROUP BY",
            "  pm.id, pm.sku_parent, pm.current_z_code, pm.partner_sku, pm.title_cache, pm.title_cn_cache, pm.brand_cache, pm.cover_image_url,",
            "  pm.product_source_type, pm.product_fulltype_cache, pm.variant_count_cache, pm.group_ref, pm.issue_count,",
            "  pm.sync_status, pm.last_synced_at",
            "LIMIT 1"
    })
    ProductListProjectionRecord selectProductListProjectionByProductMasterId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("productMasterId") Long productMasterId
    );

    default ProductListProjectionRecord selectProductListProjectionBySkuParent(
            Long ownerUserId,
            String storeCode,
            String skuParent
    ) {
        Long productMasterId = selectProductMasterIdByStoreCode(ownerUserId, storeCode, skuParent);
        return productMasterId == null
                ? null
                : selectProductListProjectionByProductMasterId(ownerUserId, storeCode.trim(), productMasterId);
    }

    @Select({
            "<script>",
            "SELECT",
            "  MIN(brand_name) AS value,",
            "  COALESCE(MIN(label_en), MIN(brand_name)) AS label,",
            "  SUM(usage_count) AS usageCount",
            "FROM noon_brand_dictionary",
            "WHERE is_deleted = 0",
            "  AND status = 'active'",
            "  <if test='query != null and query != \"\"'>",
            "    AND (brand_name LIKE CONCAT('%', #{query}, '%') OR label_en LIKE CONCAT('%', #{query}, '%'))",
            "  </if>",
            "GROUP BY brand_key",
            "ORDER BY usageCount DESC, value",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductClassificationOptionRecord> selectBrandDictionaryOptions(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("query") String query,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT",
            "  product_fulltype AS value,",
            "  COALESCE(label_en, product_fulltype) AS label,",
            "  MAX(family) AS family,",
            "  MAX(product_type) AS productType,",
            "  MAX(product_subtype) AS productSubtype,",
            "  SUM(usage_count) AS usageCount",
            "FROM noon_product_fulltype_dictionary",
            "WHERE is_deleted = 0",
            "  AND status = 'active'",
            "  <if test='query != null and query != \"\"'>",
            "    AND (product_fulltype LIKE CONCAT('%', #{query}, '%') OR label_en LIKE CONCAT('%', #{query}, '%'))",
            "  </if>",
            "GROUP BY product_fulltype, COALESCE(label_en, product_fulltype)",
            "ORDER BY usageCount DESC, product_fulltype",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductClassificationOptionRecord> selectFulltypeDictionaryOptions(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("query") String query,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT",
            "  pm.brand_cache AS value,",
            "  pm.brand_cache AS label,",
            "  COUNT(*) AS usageCount",
            "FROM logical_store ls",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "WHERE ls.is_deleted = 0",
            "  AND pm.brand_cache IS NOT NULL",
            "  AND pm.brand_cache != ''",
            "  <if test='query != null and query != \"\"'>",
            "    AND pm.brand_cache LIKE CONCAT('%', #{query}, '%')",
            "  </if>",
            "GROUP BY pm.brand_cache",
            "ORDER BY usageCount DESC, pm.brand_cache",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductClassificationOptionRecord> selectBrandProjectionClassificationOptions(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("query") String query,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT",
            "  pm.product_fulltype_cache AS value,",
            "  pm.product_fulltype_cache AS label,",
            "  SUBSTRING_INDEX(pm.product_fulltype_cache, '-', 1) AS family,",
            "  CASE",
            "    WHEN pm.product_fulltype_cache LIKE '%-%-%' THEN",
            "      SUBSTRING_INDEX(SUBSTRING_INDEX(pm.product_fulltype_cache, '-', 2), '-', -1)",
            "    WHEN pm.product_fulltype_cache LIKE '%-%' THEN",
            "      SUBSTRING_INDEX(pm.product_fulltype_cache, '-', -1)",
            "    ELSE NULL",
            "  END AS productType,",
            "  CASE",
            "    WHEN pm.product_fulltype_cache LIKE '%-%-%' THEN",
            "      SUBSTRING_INDEX(pm.product_fulltype_cache, '-', -1)",
            "    ELSE NULL",
            "  END AS productSubtype,",
            "  COUNT(*) AS usageCount",
            "FROM logical_store ls",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "WHERE ls.is_deleted = 0",
            "  AND pm.product_fulltype_cache IS NOT NULL",
            "  AND pm.product_fulltype_cache != ''",
            "  <if test='query != null and query != \"\"'>",
            "    AND pm.product_fulltype_cache LIKE CONCAT('%', #{query}, '%')",
            "  </if>",
            "GROUP BY pm.product_fulltype_cache",
            "ORDER BY usageCount DESC, pm.product_fulltype_cache",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductClassificationOptionRecord> selectFulltypeProjectionClassificationOptions(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("query") String query,
            @Param("limit") int limit
    );

    default ProductGroupCandidateContextRecord selectProductGroupCandidateContext(
            Long ownerUserId,
            String storeCode,
            String skuParent
    ) {
        Long productMasterId = selectProductMasterIdByStoreCode(ownerUserId, storeCode, skuParent);
        return productMasterId == null
                ? null
                : selectProductGroupCandidateContextByProductMasterId(ownerUserId, storeCode.trim(), productMasterId);
    }

    @Select({
            "SELECT",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS skuParent,",
            "  pm.brand_cache AS brand,",
            "  pm.product_fulltype_cache AS productFulltype,",
            "  pm.sku_group AS skuGroup",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.id = #{productMasterId}",
            " AND pm.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    ProductGroupCandidateContextRecord selectProductGroupCandidateContextByProductMasterId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("productMasterId") Long productMasterId
    );

    @Select({
            "<script>",
            "SELECT",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS skuParent,",
            "  COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) AS currentZCode,",
            "  pm.product_source_type AS productSourceType,",
            "  pm.partner_sku AS partnerSku,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.psku_code END),",
            "    MAX(pso.psku_code)",
            "  ) AS pskuCode,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.offer_code END),",
            "    MAX(pso.offer_code)",
            "  ) AS offerCode,",
            "  pm.title_cache AS title,",
            "  pm.title_cn_cache AS titleCn,",
            "  pm.brand_cache AS brand,",
            "  pm.cover_image_url AS imageUrl,",
            "  (",
            "    SELECT COALESCE(",
            "      MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END),",
            "      MAX(pb.barcode)",
            "    )",
            "    FROM product_variant bpv",
            "    JOIN product_barcode pb",
            "      ON pb.variant_id = bpv.id",
            "     AND pb.is_deleted = 0",
            "    WHERE bpv.product_master_id = pm.id",
            "      AND bpv.is_deleted = 0",
            "  ) AS barcode,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN COALESCE(pso.final_price, pso.sale_price, pso.price) END),",
            "    MAX(COALESCE(pso.final_price, pso.sale_price, pso.price))",
            "  ) AS CHAR) AS referencePrice,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.price END),",
            "    MAX(pso.price)",
            "  ) AS CHAR) AS originalPrice,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sale_price END),",
            "    MAX(pso.sale_price)",
            "  ) AS CHAR) AS salePrice,",
            "  pm.product_fulltype_cache AS productFulltype,",
            "  pm.variant_count_cache AS variantCount,",
            "  COUNT(DISTINCT lss.id) AS siteOfferCount,",
            "  GROUP_CONCAT(DISTINCT lss.site ORDER BY lss.site SEPARATOR ',') AS siteLabelsCsv,",
            "  GROUP_CONCAT(DISTINCT COALESCE(pso.live_status, pso.status_code)",
            "    ORDER BY COALESCE(pso.live_status, pso.status_code) SEPARATOR ',') AS liveStatusesCsv,",
            "  COALESCE(SUM(COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0)), 0) AS totalFbnStock,",
            "  COALESCE(SUM(pso.supermall_stock), 0) AS totalSupermallStock,",
            "  COALESCE(SUM(pso.fbp_stock), 0) AS totalFbpStock,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.views_count END),",
            "    MAX(pso.views_count)",
            "  ) AS viewsCount,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.units_sold END),",
            "    MAX(pso.units_sold)",
            "  ) AS unitsSold,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sales_amount END),",
            "    MAX(pso.sales_amount)",
            "  ) AS CHAR) AS salesAmount,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.sales_currency END),",
            "    MAX(pso.sales_currency)",
            "  ) AS salesCurrency,",
            "  MAX(pg_for_member.sku_group) AS skuGroup,",
            "  MAX(pg_for_member.group_ref) AS groupRef,",
            "  MAX(pg_for_member.group_ref_canonical) AS groupRefCanonical,",
            "  pm.issue_count AS issueCount,",
            "  (",
            "    SELECT GROUP_CONCAT(DISTINCT COALESCE(pi.title, pi.message, pi.issue_code) ORDER BY pi.last_seen_at DESC SEPARATOR ',')",
            "    FROM product_issue pi",
            "    WHERE pi.product_master_id = pm.id",
            "      AND pi.issue_status = 'open'",
            "      AND pi.is_deleted = 0",
            "  ) AS issueTagsCsv,",
            "  CAST(COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN CASE WHEN pso.is_active = b'1' THEN 1 ELSE 0 END END),",
            "    MAX(CASE WHEN pso.is_active = b'1' THEN 1 ELSE 0 END)",
            "  ) AS SIGNED) AS currentSiteActiveFlag,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.live_status END),",
            "    MAX(pso.live_status)",
            "  ) AS currentSiteLiveStatus,",
            "  COALESCE(",
            "    MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.status_code END),",
            "    MAX(pso.status_code)",
            "  ) AS currentSiteStatusCode,",
            "  pm.sync_status AS syncStatus,",
            "  DATE_FORMAT(pm.last_synced_at, '%Y-%m-%d %H:%i:%s') AS lastSyncedAt",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.is_deleted = 0",
            "LEFT JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "LEFT JOIN product_group_member pgm_for_member",
            "  ON pgm_for_member.product_master_id = pm.id",
            " AND pgm_for_member.member_status = 'active'",
            " AND pgm_for_member.is_deleted = 0",
            "LEFT JOIN product_group pg_for_member",
            "  ON pg_for_member.id = pgm_for_member.product_group_id",
            " AND pg_for_member.logical_store_id = ls.id",
            " AND pg_for_member.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) != #{skuParent}",
            "  <if test='brand != null and brand != \"\"'>",
            "    AND LOWER(pm.brand_cache) = LOWER(#{brand})",
            "  </if>",
            "  <if test='productFulltype != null and productFulltype != \"\"'>",
            "    AND (",
            "      pm.product_fulltype_cache = #{productFulltype}",
            "      OR SUBSTRING_INDEX(pm.product_fulltype_cache, '-', 1) = SUBSTRING_INDEX(#{productFulltype}, '-', 1)",
            "    )",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      COALESCE(NULLIF(pm.current_z_code, ''), pm.sku_parent) LIKE CONCAT('%', #{keyword}, '%')",
            "      OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%')",
            "      OR pm.title_cn_cache LIKE CONCAT('%', #{keyword}, '%')",
            "      OR pm.brand_cache LIKE CONCAT('%', #{keyword}, '%')",
            "      OR pv.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "  </if>",
            "  <if test='skuGroup != null and skuGroup != \"\"'>",
            "    AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM product_group pg",
            "      JOIN product_group_member pgm",
            "        ON pgm.product_group_id = pg.id",
            "       AND pgm.sku_parent = pm.sku_parent",
            "       AND pgm.member_status = 'active'",
            "       AND pgm.is_deleted = 0",
            "      WHERE pg.logical_store_id = ls.id",
            "        AND pg.sku_group = #{skuGroup}",
            "        AND pg.is_deleted = 0",
            "    )",
            "  </if>",
            "GROUP BY",
            "  pm.id, pm.sku_parent, pm.current_z_code, pm.partner_sku, pm.title_cache, pm.title_cn_cache, pm.brand_cache, pm.cover_image_url,",
            "  pm.product_source_type, pm.product_fulltype_cache, pm.variant_count_cache, pm.group_ref, pm.issue_count,",
            "  pm.sync_status, pm.last_synced_at",
            "ORDER BY",
            "  CASE",
            "    WHEN LOWER(pm.brand_cache) = LOWER(#{brand}) AND pm.product_fulltype_cache = #{productFulltype} THEN 0",
            "    WHEN LOWER(pm.brand_cache) = LOWER(#{brand}) THEN 1",
            "    ELSE 2",
            "  END,",
            "  pm.gmt_updated DESC, pm.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductListProjectionRecord> selectProductGroupCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent,
            @Param("brand") String brand,
            @Param("productFulltype") String productFulltype,
            @Param("skuGroup") String skuGroup,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit
    );

    default Long nextProductVariantId() {
        return nextProductManagementId("product_variant", 53000L);
    }

    @Insert({
            "INSERT INTO product_variant (",
            "  id, logical_store_id, product_master_id, child_sku, partner_sku, size_en, size_ar, variant_ix,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{logicalStoreId}, #{productMasterId}, #{childSku}, #{partnerSku}, #{sizeEn}, #{sizeAr}, #{variantIx},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  logical_store_id = VALUES(logical_store_id),",
            "  product_master_id = VALUES(product_master_id),",
            "  child_sku = VALUES(child_sku),",
            "  partner_sku = VALUES(partner_sku),",
            "  size_en = VALUES(size_en),",
            "  size_ar = VALUES(size_ar),",
            "  variant_ix = VALUES(variant_ix),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductVariant(
            @Param("id") Long id,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("productMasterId") Long productMasterId,
            @Param("childSku") String childSku,
            @Param("partnerSku") String partnerSku,
            @Param("sizeEn") String sizeEn,
            @Param("sizeAr") String sizeAr,
            @Param("variantIx") Integer variantIx,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM product_variant",
            "WHERE product_master_id = #{productMasterId}",
            "  AND partner_sku = #{partnerSku}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductVariantIdByPartnerSku(
            @Param("productMasterId") Long productMasterId,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT id",
            "FROM product_variant",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND partner_sku = #{partnerSku}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductVariantIdByStorePartnerSku(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku
    );

    default Long nextProductVariantSpecId() {
        return nextProductManagementId("product_variant_spec", 99000L);
    }

    default Long nextProductVariantSpecSourceId() {
        return nextProductManagementId("product_variant_spec_source", 120000L);
    }

    default Long nextProductVariantLogisticsProfileId() {
        return nextProductManagementId("product_variant_logistics_profile", 130000L);
    }

    @Select({
            "SELECT",
            "  pvs.id AS spec_id,",
            "  pvs.effective_source_id,",
            "  pvs.effective_source_type,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.id AS variant_id,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar,",
            "  COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS product_length_cm,",
            "  COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS product_width_cm,",
            "  COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS product_height_cm,",
            "  COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS product_weight_g,",
            "  COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS carton_length_cm,",
            "  COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS carton_width_cm,",
            "  COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS carton_height_cm,",
            "  COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS carton_weight_kg,",
            "  COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS carton_quantity,",
            "  COALESCE(pvss.carton_source_type, 'none') AS carton_source_type,",
            "  COALESCE(pvss.battery_magnetic_type, pvs.battery_magnetic_type) AS battery_magnetic_type,",
            "  COALESCE(pvss.liquid_powder_type, pvs.liquid_powder_type) AS liquid_powder_type,",
            "  COALESCE(pvss.source_type, pvs.source_type) AS source_type,",
            "  COALESCE(pvss.confirmed_at, pvs.confirmed_at) AS confirmed_at,",
            "  COALESCE(pvss.confirmed_by, pvs.confirmed_by) AS confirmed_by",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = pv.id",
            " AND pvs.is_deleted = 0",
            "LEFT JOIN product_variant_spec_source pvss",
            "  ON pvss.id = pvs.effective_source_id",
            " AND pvss.variant_id = pv.id",
            " AND pvss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "ORDER BY COALESCE(pv.variant_ix, 999999), pv.partner_sku"
    })
    List<ProductVariantSpecRecord> selectProductVariantSpecs(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT",
            "  pvs.id AS spec_id,",
            "  pvs.effective_source_id,",
            "  pvs.effective_source_type,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.id AS variant_id,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar,",
            "  COALESCE(pvss.product_length_cm, pvs.product_length_cm) AS product_length_cm,",
            "  COALESCE(pvss.product_width_cm, pvs.product_width_cm) AS product_width_cm,",
            "  COALESCE(pvss.product_height_cm, pvs.product_height_cm) AS product_height_cm,",
            "  COALESCE(pvss.product_weight_g, pvs.product_weight_g) AS product_weight_g,",
            "  COALESCE(pvss.carton_length_cm, pvs.carton_length_cm) AS carton_length_cm,",
            "  COALESCE(pvss.carton_width_cm, pvs.carton_width_cm) AS carton_width_cm,",
            "  COALESCE(pvss.carton_height_cm, pvs.carton_height_cm) AS carton_height_cm,",
            "  COALESCE(pvss.carton_weight_kg, pvs.carton_weight_kg) AS carton_weight_kg,",
            "  COALESCE(pvss.carton_quantity, pvs.carton_quantity) AS carton_quantity,",
            "  COALESCE(pvss.carton_source_type, 'none') AS carton_source_type,",
            "  COALESCE(pvss.battery_magnetic_type, pvs.battery_magnetic_type) AS battery_magnetic_type,",
            "  COALESCE(pvss.liquid_powder_type, pvs.liquid_powder_type) AS liquid_powder_type,",
            "  COALESCE(pvss.source_type, pvs.source_type) AS source_type,",
            "  COALESCE(pvss.confirmed_at, pvs.confirmed_at) AS confirmed_at,",
            "  COALESCE(pvss.confirmed_by, pvs.confirmed_by) AS confirmed_by",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = pv.id",
            " AND pvs.is_deleted = 0",
            "LEFT JOIN product_variant_spec_source pvss",
            "  ON pvss.id = pvs.effective_source_id",
            " AND pvss.variant_id = pv.id",
            " AND pvss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (#{keyword} IS NULL OR #{keyword} = ''",
            "       OR pm.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.child_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cn_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "ORDER BY pm.sku_parent, COALESCE(pv.variant_ix, 999999), pv.partner_sku"
    })
    List<ProductVariantSpecRecord> selectProductVariantSpecOverview(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword
    );

    @Select({
            "SELECT",
            "  pvss.id AS source_id,",
            "  pvss.variant_id,",
            "  pvss.source_type,",
            "  pvss.product_length_cm,",
            "  pvss.product_width_cm,",
            "  pvss.product_height_cm,",
            "  pvss.product_weight_g,",
            "  pvss.carton_length_cm,",
            "  pvss.carton_width_cm,",
            "  pvss.carton_height_cm,",
            "  pvss.carton_weight_kg,",
            "  pvss.carton_quantity,",
            "  pvss.carton_source_type,",
            "  pvss.battery_magnetic_type,",
            "  pvss.liquid_powder_type,",
            "  pvss.source_recorded_at,",
            "  pvss.confirmed_at,",
            "  pvss.confirmed_by,",
            "  pvss.updated_by,",
            "  pvss.gmt_updated",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "JOIN product_variant_spec_source pvss",
            "  ON pvss.variant_id = pv.id",
            " AND pvss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (#{keyword} IS NULL OR #{keyword} = ''",
            "       OR pm.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.child_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cn_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "ORDER BY pv.id, FIELD(pvss.source_type, 'ali1688', 'warehouse', 'noon_official'), pvss.source_type"
    })
    List<ProductVariantSpecSourceRecord> selectProductVariantSpecSourcesForOverview(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword
    );

    @Select({
            "SELECT",
            "  pv.id AS variant_id,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.partner_sku = #{partnerSku}",
            " AND pv.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (#{childSku} IS NULL OR #{childSku} = '' OR pv.child_sku = #{childSku})",
            "LIMIT 1"
    })
    ProductVariantSpecRecord selectProductVariantForSpec(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent,
            @Param("partnerSku") String partnerSku,
            @Param("childSku") String childSku
    );

    @Select({
            "SELECT",
            "  pv.id AS variant_id,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar,",
            "  pvs.id AS spec_id,",
            "  pvs.effective_source_id,",
            "  pvs.effective_source_type",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.id = #{variantId}",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = pv.id",
            " AND pvs.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    ProductVariantSpecRecord selectProductVariantForSpecByVariantId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId
    );

    @Select({
            "SELECT",
            "  pvlp.id AS profile_id,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.id AS variant_id,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar,",
            "  COALESCE(pvlp.profile_status, 'needs_review') AS profile_status,",
            "  COALESCE(pvlp.battery_electric_type, 'unknown') AS battery_electric_type,",
            "  COALESCE(pvlp.battery_type, 'unknown') AS battery_type,",
            "  COALESCE(pvlp.magnetic_type, 'unknown') AS magnetic_type,",
            "  COALESCE(pvlp.liquid_type, 'unknown') AS liquid_type,",
            "  COALESCE(pvlp.powder_type, 'unknown') AS powder_type,",
            "  COALESCE(pvlp.liquid_powder_type, 'unknown') AS liquid_powder_type,",
            "  COALESCE(pvlp.electric_type, 'unknown') AS electric_type,",
            "  COALESCE(pvlp.plug_type, 'unknown') AS plug_type,",
            "  COALESCE(pvlp.voltage_compatible_type, 'unknown') AS voltage_compatible_type,",
            "  COALESCE(pvlp.made_in_china_label_status, 'unknown') AS made_in_china_label_status,",
            "  COALESCE(pvlp.msds_status, 'unknown') AS msds_status,",
            "  COALESCE(pvlp.sea_transport_report_status, 'unknown') AS sea_transport_report_status,",
            "  COALESCE(pvlp.brand_risk_type, 'unknown') AS brand_risk_type,",
            "  COALESCE(pvlp.food_contact_type, 'unknown') AS food_contact_type,",
            "  COALESCE(pvlp.medical_type, 'unknown') AS medical_type,",
            "  COALESCE(pvlp.cosmetic_type, 'unknown') AS cosmetic_type,",
            "  COALESCE(pvlp.wireless_camera_gps_type, 'unknown') AS wireless_camera_gps_type,",
            "  COALESCE(pvlp.laser_type, 'unknown') AS laser_type,",
            "  COALESCE(pvlp.blade_weapon_type, 'unknown') AS blade_weapon_type,",
            "  COALESCE(pvlp.cultural_restriction_type, 'unknown') AS cultural_restriction_type,",
            "  COALESCE(pvlp.wooden_material_type, 'unknown') AS wooden_material_type,",
            "  pvlp.sensitive_tags_json,",
            "  pvlp.prohibited_tags_json,",
            "  CASE WHEN pvlp.manual_confirm_required IS NULL THEN 1",
            "       WHEN pvlp.manual_confirm_required = b'1' THEN 1 ELSE 0 END AS manual_confirm_required,",
            "  DATE_FORMAT(pvlp.confirmed_at, '%Y-%m-%d %H:%i:%s') AS confirmed_at,",
            "  pvlp.confirmed_by,",
            "  pvlp.notes,",
            "  DATE_FORMAT(pvlp.gmt_updated, '%Y-%m-%d %H:%i:%s') AS gmt_updated",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_variant_logistics_profile pvlp",
            "  ON pvlp.variant_id = pv.id",
            " AND pvlp.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "ORDER BY COALESCE(pv.variant_ix, 999999), pv.partner_sku"
    })
    List<ProductVariantLogisticsProfileView> selectProductVariantLogisticsProfiles(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT",
            "  pvlp.id AS profile_id,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.id AS variant_id,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar,",
            "  COALESCE(pvlp.profile_status, 'needs_review') AS profile_status,",
            "  COALESCE(pvlp.battery_electric_type, 'unknown') AS battery_electric_type,",
            "  COALESCE(pvlp.battery_type, 'unknown') AS battery_type,",
            "  COALESCE(pvlp.magnetic_type, 'unknown') AS magnetic_type,",
            "  COALESCE(pvlp.liquid_type, 'unknown') AS liquid_type,",
            "  COALESCE(pvlp.powder_type, 'unknown') AS powder_type,",
            "  COALESCE(pvlp.liquid_powder_type, 'unknown') AS liquid_powder_type,",
            "  COALESCE(pvlp.electric_type, 'unknown') AS electric_type,",
            "  COALESCE(pvlp.plug_type, 'unknown') AS plug_type,",
            "  COALESCE(pvlp.voltage_compatible_type, 'unknown') AS voltage_compatible_type,",
            "  COALESCE(pvlp.made_in_china_label_status, 'unknown') AS made_in_china_label_status,",
            "  COALESCE(pvlp.msds_status, 'unknown') AS msds_status,",
            "  COALESCE(pvlp.sea_transport_report_status, 'unknown') AS sea_transport_report_status,",
            "  COALESCE(pvlp.brand_risk_type, 'unknown') AS brand_risk_type,",
            "  COALESCE(pvlp.food_contact_type, 'unknown') AS food_contact_type,",
            "  COALESCE(pvlp.medical_type, 'unknown') AS medical_type,",
            "  COALESCE(pvlp.cosmetic_type, 'unknown') AS cosmetic_type,",
            "  COALESCE(pvlp.wireless_camera_gps_type, 'unknown') AS wireless_camera_gps_type,",
            "  COALESCE(pvlp.laser_type, 'unknown') AS laser_type,",
            "  COALESCE(pvlp.blade_weapon_type, 'unknown') AS blade_weapon_type,",
            "  COALESCE(pvlp.cultural_restriction_type, 'unknown') AS cultural_restriction_type,",
            "  COALESCE(pvlp.wooden_material_type, 'unknown') AS wooden_material_type,",
            "  pvlp.sensitive_tags_json,",
            "  pvlp.prohibited_tags_json,",
            "  CASE WHEN pvlp.manual_confirm_required IS NULL THEN 1",
            "       WHEN pvlp.manual_confirm_required = b'1' THEN 1 ELSE 0 END AS manual_confirm_required,",
            "  DATE_FORMAT(pvlp.confirmed_at, '%Y-%m-%d %H:%i:%s') AS confirmed_at,",
            "  pvlp.confirmed_by,",
            "  pvlp.notes,",
            "  DATE_FORMAT(pvlp.gmt_updated, '%Y-%m-%d %H:%i:%s') AS gmt_updated",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.id = #{variantId}",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_variant_logistics_profile pvlp",
            "  ON pvlp.variant_id = pv.id",
            " AND pvlp.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    ProductVariantLogisticsProfileView selectProductVariantLogisticsProfile(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId
    );

    @Select({
            "SELECT",
            "  pvlp.id AS profile_id,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.id AS variant_id,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar,",
            "  COALESCE(pvlp.profile_status, 'needs_review') AS profile_status,",
            "  COALESCE(pvlp.battery_electric_type, 'unknown') AS battery_electric_type,",
            "  COALESCE(pvlp.battery_type, 'unknown') AS battery_type,",
            "  COALESCE(pvlp.magnetic_type, 'unknown') AS magnetic_type,",
            "  COALESCE(pvlp.liquid_type, 'unknown') AS liquid_type,",
            "  COALESCE(pvlp.powder_type, 'unknown') AS powder_type,",
            "  COALESCE(pvlp.liquid_powder_type, 'unknown') AS liquid_powder_type,",
            "  COALESCE(pvlp.electric_type, 'unknown') AS electric_type,",
            "  COALESCE(pvlp.plug_type, 'unknown') AS plug_type,",
            "  COALESCE(pvlp.voltage_compatible_type, 'unknown') AS voltage_compatible_type,",
            "  COALESCE(pvlp.made_in_china_label_status, 'unknown') AS made_in_china_label_status,",
            "  COALESCE(pvlp.msds_status, 'unknown') AS msds_status,",
            "  COALESCE(pvlp.sea_transport_report_status, 'unknown') AS sea_transport_report_status,",
            "  COALESCE(pvlp.brand_risk_type, 'unknown') AS brand_risk_type,",
            "  COALESCE(pvlp.food_contact_type, 'unknown') AS food_contact_type,",
            "  COALESCE(pvlp.medical_type, 'unknown') AS medical_type,",
            "  COALESCE(pvlp.cosmetic_type, 'unknown') AS cosmetic_type,",
            "  COALESCE(pvlp.wireless_camera_gps_type, 'unknown') AS wireless_camera_gps_type,",
            "  COALESCE(pvlp.laser_type, 'unknown') AS laser_type,",
            "  COALESCE(pvlp.blade_weapon_type, 'unknown') AS blade_weapon_type,",
            "  COALESCE(pvlp.cultural_restriction_type, 'unknown') AS cultural_restriction_type,",
            "  COALESCE(pvlp.wooden_material_type, 'unknown') AS wooden_material_type,",
            "  pvlp.sensitive_tags_json,",
            "  pvlp.prohibited_tags_json,",
            "  CASE WHEN pvlp.manual_confirm_required IS NULL THEN 1",
            "       WHEN pvlp.manual_confirm_required = b'1' THEN 1 ELSE 0 END AS manual_confirm_required,",
            "  DATE_FORMAT(pvlp.confirmed_at, '%Y-%m-%d %H:%i:%s') AS confirmed_at,",
            "  pvlp.confirmed_by,",
            "  pvlp.notes,",
            "  DATE_FORMAT(pvlp.gmt_updated, '%Y-%m-%d %H:%i:%s') AS gmt_updated",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "LEFT JOIN product_variant_logistics_profile pvlp",
            "  ON pvlp.variant_id = pv.id",
            " AND pvlp.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (#{keyword} IS NULL OR #{keyword} = ''",
            "       OR pm.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.child_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cn_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "ORDER BY pm.sku_parent, COALESCE(pv.variant_ix, 999999), pv.partner_sku"
    })
    List<ProductVariantLogisticsProfileView> selectProductVariantLogisticsProfilesForOverview(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword
    );

    @Insert({
            "INSERT INTO product_variant_logistics_profile (",
            "  id, variant_id, effective_source_id, effective_source_type, profile_status,",
            "  battery_electric_type, battery_type, magnetic_type, liquid_type, powder_type,",
            "  liquid_powder_type, electric_type,",
            "  plug_type, voltage_compatible_type, made_in_china_label_status,",
            "  msds_status, sea_transport_report_status,",
            "  brand_risk_type, food_contact_type, medical_type, cosmetic_type,",
            "  wireless_camera_gps_type, laser_type, blade_weapon_type, cultural_restriction_type,",
            "  wooden_material_type, sensitive_tags_json, prohibited_tags_json,",
            "  manual_confirm_required, confirmed_at, confirmed_by, notes,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{variantId}, NULL, 'manual', #{profileStatus},",
            "  #{batteryElectricType}, #{batteryType}, #{magneticType}, #{liquidType}, #{powderType},",
            "  #{liquidPowderType}, #{electricType},",
            "  #{plugType}, #{voltageCompatibleType}, #{madeInChinaLabelStatus},",
            "  #{msdsStatus}, #{seaTransportReportStatus},",
            "  #{brandRiskType}, #{foodContactType}, #{medicalType}, #{cosmeticType},",
            "  #{wirelessCameraGpsType}, #{laserType}, #{bladeWeaponType}, #{culturalRestrictionType},",
            "  #{woodenMaterialType}, #{sensitiveTagsJson}, #{prohibitedTagsJson},",
            "  #{manualConfirmRequired}, CASE WHEN #{profileStatus} = 'confirmed' THEN NOW() ELSE NULL END,",
            "  CASE WHEN #{profileStatus} = 'confirmed' THEN #{operatorUserId} ELSE NULL END, #{notes},",
            "  0, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  effective_source_id = NULL,",
            "  effective_source_type = 'manual',",
            "  profile_status = VALUES(profile_status),",
            "  battery_electric_type = VALUES(battery_electric_type),",
            "  battery_type = VALUES(battery_type),",
            "  magnetic_type = VALUES(magnetic_type),",
            "  liquid_type = VALUES(liquid_type),",
            "  powder_type = VALUES(powder_type),",
            "  liquid_powder_type = VALUES(liquid_powder_type),",
            "  electric_type = VALUES(electric_type),",
            "  plug_type = VALUES(plug_type),",
            "  voltage_compatible_type = VALUES(voltage_compatible_type),",
            "  made_in_china_label_status = VALUES(made_in_china_label_status),",
            "  msds_status = VALUES(msds_status),",
            "  sea_transport_report_status = VALUES(sea_transport_report_status),",
            "  brand_risk_type = VALUES(brand_risk_type),",
            "  food_contact_type = VALUES(food_contact_type),",
            "  medical_type = VALUES(medical_type),",
            "  cosmetic_type = VALUES(cosmetic_type),",
            "  wireless_camera_gps_type = VALUES(wireless_camera_gps_type),",
            "  laser_type = VALUES(laser_type),",
            "  blade_weapon_type = VALUES(blade_weapon_type),",
            "  cultural_restriction_type = VALUES(cultural_restriction_type),",
            "  wooden_material_type = VALUES(wooden_material_type),",
            "  sensitive_tags_json = VALUES(sensitive_tags_json),",
            "  prohibited_tags_json = VALUES(prohibited_tags_json),",
            "  manual_confirm_required = VALUES(manual_confirm_required),",
            "  confirmed_at = VALUES(confirmed_at),",
            "  confirmed_by = VALUES(confirmed_by),",
            "  notes = VALUES(notes),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductVariantLogisticsProfile(ProductVariantLogisticsProfileCommand command);

    @Select({
            "SELECT",
            "  pvss.id AS source_id,",
            "  pvss.variant_id,",
            "  pvss.source_type,",
            "  pvss.product_length_cm,",
            "  pvss.product_width_cm,",
            "  pvss.product_height_cm,",
            "  pvss.product_weight_g,",
            "  pvss.carton_length_cm,",
            "  pvss.carton_width_cm,",
            "  pvss.carton_height_cm,",
            "  pvss.carton_weight_kg,",
            "  pvss.carton_quantity,",
            "  pvss.carton_source_type,",
            "  pvss.battery_magnetic_type,",
            "  pvss.liquid_powder_type,",
            "  pvss.source_recorded_at,",
            "  pvss.confirmed_at,",
            "  pvss.confirmed_by,",
            "  pvss.updated_by,",
            "  pvss.gmt_updated",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.id = #{variantId}",
            " AND pv.is_deleted = 0",
            "JOIN product_variant_spec_source pvss",
            "  ON pvss.variant_id = pv.id",
            " AND pvss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "ORDER BY FIELD(pvss.source_type, 'ali1688', 'warehouse', 'noon_official'), pvss.source_type"
    })
    List<ProductVariantSpecSourceRecord> selectProductVariantSpecSources(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId
    );

    @Select({
            "SELECT",
            "  pvss.id AS source_id,",
            "  pvss.variant_id,",
            "  pvss.source_type,",
            "  pvss.product_length_cm,",
            "  pvss.product_width_cm,",
            "  pvss.product_height_cm,",
            "  pvss.product_weight_g,",
            "  pvss.carton_length_cm,",
            "  pvss.carton_width_cm,",
            "  pvss.carton_height_cm,",
            "  pvss.carton_weight_kg,",
            "  pvss.carton_quantity,",
            "  pvss.carton_source_type,",
            "  pvss.battery_magnetic_type,",
            "  pvss.liquid_powder_type,",
            "  pvss.source_recorded_at,",
            "  pvss.confirmed_at,",
            "  pvss.confirmed_by,",
            "  pvss.updated_by,",
            "  pvss.gmt_updated",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.id = #{variantId}",
            " AND pv.is_deleted = 0",
            "JOIN product_variant_spec_source pvss",
            "  ON pvss.variant_id = pv.id",
            " AND pvss.id = #{sourceId}",
            " AND pvss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    ProductVariantSpecSourceRecord selectProductVariantSpecSourceForScope(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId,
            @Param("sourceId") Long sourceId
    );

    @Insert({
            "INSERT INTO product_variant_spec_source (",
            "  id, variant_id, source_type,",
            "  product_length_cm, product_width_cm, product_height_cm, product_weight_g,",
            "  carton_length_cm, carton_width_cm, carton_height_cm, carton_weight_kg, carton_quantity,",
            "  carton_source_type, battery_magnetic_type, liquid_powder_type, source_recorded_at,",
            "  confirmed_at, confirmed_by, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{variantId}, #{sourceType},",
            "  #{productLengthCm}, #{productWidthCm}, #{productHeightCm}, #{productWeightG},",
            "  #{cartonLengthCm}, #{cartonWidthCm}, #{cartonHeightCm}, #{cartonWeightKg}, #{cartonQuantity},",
            "  #{cartonSourceType}, #{batteryMagneticType}, #{liquidPowderType}, COALESCE(#{sourceRecordedAt}, NOW()),",
            "  NOW(), #{operatorUserId}, 0, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  product_length_cm = VALUES(product_length_cm),",
            "  product_width_cm = VALUES(product_width_cm),",
            "  product_height_cm = VALUES(product_height_cm),",
            "  product_weight_g = VALUES(product_weight_g),",
            "  carton_length_cm = VALUES(carton_length_cm),",
            "  carton_width_cm = VALUES(carton_width_cm),",
            "  carton_height_cm = VALUES(carton_height_cm),",
            "  carton_weight_kg = VALUES(carton_weight_kg),",
            "  carton_quantity = VALUES(carton_quantity),",
            "  carton_source_type = VALUES(carton_source_type),",
            "  battery_magnetic_type = VALUES(battery_magnetic_type),",
            "  liquid_powder_type = VALUES(liquid_powder_type),",
            "  source_recorded_at = VALUES(source_recorded_at),",
            "  confirmed_at = VALUES(confirmed_at),",
            "  confirmed_by = VALUES(confirmed_by),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductVariantSpecSource(ProductVariantSpecSourceCommand command);

    @Insert({
            "INSERT INTO product_variant_spec (",
            "  id, variant_id, effective_source_id, effective_source_type, confirmed_at, confirmed_by,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{variantId}, #{sourceId}, #{sourceType}, NOW(), #{operatorUserId},",
            "  0, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  effective_source_id = VALUES(effective_source_id),",
            "  effective_source_type = VALUES(effective_source_type),",
            "  confirmed_at = VALUES(confirmed_at),",
            "  confirmed_by = VALUES(confirmed_by),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductVariantSpecEffectiveSource(
            @Param("id") Long id,
            @Param("variantId") Long variantId,
            @Param("sourceId") Long sourceId,
            @Param("sourceType") String sourceType,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO product_variant_spec (",
            "  id, variant_id, product_length_cm, product_width_cm, product_height_cm, product_weight_g,",
            "  carton_length_cm, carton_width_cm, carton_height_cm, carton_weight_kg, carton_quantity,",
            "  battery_magnetic_type, liquid_powder_type, source_type, confirmed_at, confirmed_by,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{variantId}, #{productLengthCm}, #{productWidthCm}, #{productHeightCm}, #{productWeightG},",
            "  #{cartonLengthCm}, #{cartonWidthCm}, #{cartonHeightCm}, #{cartonWeightKg}, #{cartonQuantity},",
            "  #{batteryMagneticType}, #{liquidPowderType}, 'manual', NOW(), #{operatorUserId},",
            "  0, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  product_length_cm = VALUES(product_length_cm),",
            "  product_width_cm = VALUES(product_width_cm),",
            "  product_height_cm = VALUES(product_height_cm),",
            "  product_weight_g = VALUES(product_weight_g),",
            "  carton_length_cm = VALUES(carton_length_cm),",
            "  carton_width_cm = VALUES(carton_width_cm),",
            "  carton_height_cm = VALUES(carton_height_cm),",
            "  carton_weight_kg = VALUES(carton_weight_kg),",
            "  carton_quantity = VALUES(carton_quantity),",
            "  battery_magnetic_type = VALUES(battery_magnetic_type),",
            "  liquid_powder_type = VALUES(liquid_powder_type),",
            "  source_type = VALUES(source_type),",
            "  confirmed_at = VALUES(confirmed_at),",
            "  confirmed_by = VALUES(confirmed_by),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductVariantSpec(ProductVariantSpecCommand command);

    default Long nextProductBarcodeId() {
        return nextProductManagementId("product_barcode", 54000L);
    }

    @Insert({
            "INSERT INTO product_barcode (",
            "  id, variant_id, barcode, barcode_type, is_primary,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{variantId}, #{barcode}, #{barcodeType}, #{primary},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  variant_id = VALUES(variant_id),",
            "  barcode_type = VALUES(barcode_type),",
            "  is_primary = VALUES(is_primary),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductBarcode(
            @Param("id") Long id,
            @Param("variantId") Long variantId,
            @Param("barcode") String barcode,
            @Param("barcodeType") String barcodeType,
            @Param("primary") boolean primary,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM product_barcode",
            "WHERE barcode = #{barcode}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductBarcodeIdByBarcode(@Param("barcode") String barcode);

    @Insert({
            "INSERT INTO product_image_asset (",
            "  id, product_master_id, source_type, url, storage_key, original_filename, content_type,",
            "  size_bytes, width_px, height_px, sha256, asset_status,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productMasterId}, #{sourceType}, #{url}, #{storageKey}, #{originalFilename}, #{contentType},",
            "  #{sizeBytes}, #{widthPx}, #{heightPx}, #{sha256}, #{assetStatus},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  product_master_id = VALUES(product_master_id),",
            "  source_type = VALUES(source_type),",
            "  url = VALUES(url),",
            "  storage_key = VALUES(storage_key),",
            "  original_filename = VALUES(original_filename),",
            "  content_type = VALUES(content_type),",
            "  size_bytes = VALUES(size_bytes),",
            "  width_px = VALUES(width_px),",
            "  height_px = VALUES(height_px),",
            "  sha256 = VALUES(sha256),",
            "  asset_status = VALUES(asset_status),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductImageAsset(
            @Param("id") Long id,
            @Param("productMasterId") Long productMasterId,
            @Param("sourceType") String sourceType,
            @Param("url") String url,
            @Param("storageKey") String storageKey,
            @Param("originalFilename") String originalFilename,
            @Param("contentType") String contentType,
            @Param("sizeBytes") Long sizeBytes,
            @Param("widthPx") Integer widthPx,
            @Param("heightPx") Integer heightPx,
            @Param("sha256") String sha256,
            @Param("assetStatus") String assetStatus,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM product_image_asset",
            "WHERE product_master_id = #{productMasterId}",
            "  AND source_type = #{sourceType}",
            "  AND url = #{url}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductImageAssetId(
            @Param("productMasterId") Long productMasterId,
            @Param("sourceType") String sourceType,
            @Param("url") String url
    );

    @Update({
            "<script>",
            "UPDATE product_image_asset",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND source_type = #{sourceType}",
            "  AND is_deleted = 0",
            "  <if test='urls != null and urls.size() > 0'>",
            "    AND url NOT IN",
            "    <foreach collection='urls' item='url' open='(' separator=',' close=')'>",
            "      #{url}",
            "    </foreach>",
            "  </if>",
            "</script>"
    })
    int markStaleProductImageAssetsDeleted(
            @Param("productMasterId") Long productMasterId,
            @Param("sourceType") String sourceType,
            @Param("urls") List<String> urls,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO product_issue (",
            "  id, product_master_id, site_id, variant_id, issue_scope_key, issue_source, issue_code, issue_hash,",
            "  severity, title, message, raw_json, issue_status, first_seen_at, last_seen_at, resolved_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productMasterId}, #{siteId}, #{variantId}, #{issueScopeKey}, #{issueSource}, #{issueCode}, #{issueHash},",
            "  #{severity}, #{title}, #{message}, #{rawJson}, #{issueStatus}, #{seenAt}, #{seenAt}, NULL,",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  site_id = VALUES(site_id),",
            "  variant_id = VALUES(variant_id),",
            "  issue_source = VALUES(issue_source),",
            "  issue_code = VALUES(issue_code),",
            "  severity = VALUES(severity),",
            "  title = VALUES(title),",
            "  message = VALUES(message),",
            "  raw_json = VALUES(raw_json),",
            "  issue_status = VALUES(issue_status),",
            "  last_seen_at = VALUES(last_seen_at),",
            "  resolved_at = NULL,",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductIssue(
            @Param("id") Long id,
            @Param("productMasterId") Long productMasterId,
            @Param("siteId") Long siteId,
            @Param("variantId") Long variantId,
            @Param("issueScopeKey") String issueScopeKey,
            @Param("issueSource") String issueSource,
            @Param("issueCode") String issueCode,
            @Param("issueHash") String issueHash,
            @Param("severity") String severity,
            @Param("title") String title,
            @Param("message") String message,
            @Param("rawJson") String rawJson,
            @Param("issueStatus") String issueStatus,
            @Param("seenAt") LocalDateTime seenAt,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "<script>",
            "UPDATE product_issue",
            "SET issue_status = 'resolved',",
            "    resolved_at = #{resolvedAt},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND issue_status = 'open'",
            "  AND is_deleted = 0",
            "  <if test='issueHashes != null and issueHashes.size() > 0'>",
            "    AND issue_hash NOT IN",
            "    <foreach collection='issueHashes' item='issueHash' open='(' separator=',' close=')'>",
            "      #{issueHash}",
            "    </foreach>",
            "  </if>",
            "</script>"
    })
    int markProductIssuesResolvedExcept(
            @Param("productMasterId") Long productMasterId,
            @Param("issueHashes") List<String> issueHashes,
            @Param("resolvedAt") LocalDateTime resolvedAt,
            @Param("updatedBy") Long updatedBy
    );

    default Long nextProductSiteOfferId() {
        return nextProductManagementId("product_site_offer", 55000L);
    }

    @Insert({
            "INSERT INTO product_site_offer (",
            "  id, product_master_id, logical_store_id, partner_sku, variant_id, site_id, site_code, psku_code, offer_code, currency, price, sale_price, sale_start, sale_end,",
            "  price_min, price_max, final_price, final_price_source, active_promotion_code, active_promotion_name,",
            "  active_promotion_url, promotion_payload_json, price_synced_at,",
            "  pricing_method, pricing_rule, price_engine_min, price_engine_max,",
            "  id_warranty, offer_note, delivery_method, is_winning_buybox, is_active, live_status, status_code,",
            "  listing_started_at, listing_started_source,",
            "  fbn_stock, supermall_stock, fbp_stock, views_count, units_sold, sales_amount, sales_currency, last_synced_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productMasterId}, #{logicalStoreId}, #{partnerSku}, #{variantId}, #{siteId}, #{siteCode}, #{pskuCode}, #{offerCode}, #{currency}, #{price}, #{salePrice}, #{saleStart}, #{saleEnd},",
            "  #{priceMin}, #{priceMax}, #{finalPrice}, #{finalPriceSource}, #{activePromotionCode}, #{activePromotionName},",
            "  #{activePromotionUrl}, #{promotionPayloadJson}, #{priceSyncedAt},",
            "  #{pricingMethod}, #{pricingRule}, #{priceEngineMin}, #{priceEngineMax},",
            "  #{idWarranty}, #{offerNote}, #{deliveryMethod}, #{isWinningBuybox}, #{isActive}, #{liveStatus}, #{statusCode},",
            "  #{listingStartedAt}, #{listingStartedSource},",
            "  #{fbnStock}, #{supermallStock}, #{fbpStock}, #{viewsCount}, #{unitsSold}, #{salesAmount}, #{salesCurrency}, #{lastSyncedAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  product_master_id = COALESCE(VALUES(product_master_id), product_master_id),",
            "  logical_store_id = COALESCE(VALUES(logical_store_id), logical_store_id),",
            "  partner_sku = COALESCE(NULLIF(VALUES(partner_sku), ''), partner_sku),",
            "  variant_id = VALUES(variant_id),",
            "  site_id = VALUES(site_id),",
            "  site_code = COALESCE(NULLIF(VALUES(site_code), ''), site_code),",
            "  psku_code = VALUES(psku_code),",
            "  offer_code = VALUES(offer_code),",
            "  currency = VALUES(currency),",
            "  price = VALUES(price),",
            "  sale_price = VALUES(sale_price),",
            "  sale_start = VALUES(sale_start),",
            "  sale_end = VALUES(sale_end),",
            "  price_min = VALUES(price_min),",
            "  price_max = VALUES(price_max),",
            "  final_price = VALUES(final_price),",
            "  final_price_source = VALUES(final_price_source),",
            "  active_promotion_code = VALUES(active_promotion_code),",
            "  active_promotion_name = VALUES(active_promotion_name),",
            "  active_promotion_url = VALUES(active_promotion_url),",
            "  promotion_payload_json = VALUES(promotion_payload_json),",
            "  price_synced_at = VALUES(price_synced_at),",
            "  pricing_method = VALUES(pricing_method),",
            "  pricing_rule = VALUES(pricing_rule),",
            "  price_engine_min = VALUES(price_engine_min),",
            "  price_engine_max = VALUES(price_engine_max),",
            "  id_warranty = VALUES(id_warranty),",
            "  offer_note = VALUES(offer_note),",
            "  delivery_method = VALUES(delivery_method),",
            "  is_winning_buybox = VALUES(is_winning_buybox),",
            "  is_active = VALUES(is_active),",
            "  live_status = VALUES(live_status),",
            "  status_code = VALUES(status_code),",
            "  listing_started_at = COALESCE(VALUES(listing_started_at), listing_started_at),",
            "  listing_started_source = COALESCE(VALUES(listing_started_source), listing_started_source),",
            "  fbn_stock = VALUES(fbn_stock),",
            "  supermall_stock = VALUES(supermall_stock),",
            "  fbp_stock = VALUES(fbp_stock),",
            "  views_count = VALUES(views_count),",
            "  units_sold = VALUES(units_sold),",
            "  sales_amount = VALUES(sales_amount),",
            "  sales_currency = VALUES(sales_currency),",
            "  last_synced_at = VALUES(last_synced_at),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductSiteOffer(
            @Param("id") Long id,
            @Param("productMasterId") Long productMasterId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("variantId") Long variantId,
            @Param("siteId") Long siteId,
            @Param("siteCode") String siteCode,
            @Param("pskuCode") String pskuCode,
            @Param("offerCode") String offerCode,
            @Param("currency") String currency,
            @Param("price") BigDecimal price,
            @Param("salePrice") BigDecimal salePrice,
            @Param("saleStart") LocalDateTime saleStart,
            @Param("saleEnd") LocalDateTime saleEnd,
            @Param("priceMin") BigDecimal priceMin,
            @Param("priceMax") BigDecimal priceMax,
            @Param("finalPrice") BigDecimal finalPrice,
            @Param("finalPriceSource") String finalPriceSource,
            @Param("activePromotionCode") String activePromotionCode,
            @Param("activePromotionName") String activePromotionName,
            @Param("activePromotionUrl") String activePromotionUrl,
            @Param("promotionPayloadJson") String promotionPayloadJson,
            @Param("priceSyncedAt") LocalDateTime priceSyncedAt,
            @Param("pricingMethod") String pricingMethod,
            @Param("pricingRule") String pricingRule,
            @Param("priceEngineMin") BigDecimal priceEngineMin,
            @Param("priceEngineMax") BigDecimal priceEngineMax,
            @Param("idWarranty") Integer idWarranty,
            @Param("offerNote") String offerNote,
            @Param("deliveryMethod") String deliveryMethod,
            @Param("isWinningBuybox") Boolean isWinningBuybox,
            @Param("isActive") Boolean isActive,
            @Param("liveStatus") String liveStatus,
            @Param("statusCode") String statusCode,
            @Param("listingStartedAt") LocalDateTime listingStartedAt,
            @Param("listingStartedSource") String listingStartedSource,
            @Param("fbnStock") Integer fbnStock,
            @Param("supermallStock") Integer supermallStock,
            @Param("fbpStock") Integer fbpStock,
            @Param("viewsCount") Long viewsCount,
            @Param("unitsSold") Long unitsSold,
            @Param("salesAmount") BigDecimal salesAmount,
            @Param("salesCurrency") String salesCurrency,
            @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN (",
            "  SELECT source.logical_store_id, source.partner_sku_key,",
            "         MIN(source.first_flow_at) AS first_flow_at,",
            "         MAX(source.last_flow_at) AS last_flow_at",
            "  FROM (",
            "    SELECT stock_offer.logical_store_id,",
            "           CONVERT(UPPER(TRIM(stock_offer.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,",
            "           COALESCE(stock_offer.last_synced_at, stock_offer.gmt_updated, stock_offer.gmt_create) AS first_flow_at,",
            "           COALESCE(stock_offer.last_synced_at, stock_offer.gmt_updated, NOW()) AS last_flow_at",
            "    FROM product_site_offer stock_offer",
            "    WHERE stock_offer.is_deleted = b'0'",
            "      AND stock_offer.logical_store_id = #{logicalStoreId}",
            "      AND stock_offer.partner_sku IS NOT NULL",
            "      AND TRIM(stock_offer.partner_sku) <> ''",
            "      AND CONVERT(UPPER(TRIM(stock_offer.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "          = CONVERT(UPPER(TRIM(#{partnerSku})) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      AND COALESCE(stock_offer.fbn_stock, 0) + COALESCE(stock_offer.supermall_stock, 0) + COALESCE(stock_offer.fbp_stock, 0) > 0",
            "  ) source",
            "  GROUP BY source.logical_store_id, source.partner_sku_key",
            ") stock",
            "  ON stock.logical_store_id = pso.logical_store_id",
            " AND (",
            "     stock.partner_sku_key = CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "     OR (",
            "         CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'",
            "         AND stock.partner_sku_key = CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "     )",
            " )",
            "SET pso.logistics_has_history = b'1',",
            "    pso.logistics_first_flow_at = CASE",
            "        WHEN pso.logistics_first_flow_at IS NULL THEN COALESCE(stock.first_flow_at, NOW())",
            "        WHEN stock.first_flow_at IS NULL THEN pso.logistics_first_flow_at",
            "        WHEN pso.logistics_first_flow_at > stock.first_flow_at THEN stock.first_flow_at",
            "        ELSE pso.logistics_first_flow_at",
            "    END,",
            "    pso.logistics_last_flow_at = CASE",
            "        WHEN pso.logistics_last_flow_at IS NULL THEN COALESCE(stock.last_flow_at, NOW())",
            "        WHEN stock.last_flow_at IS NULL THEN pso.logistics_last_flow_at",
            "        WHEN pso.logistics_last_flow_at < stock.last_flow_at THEN stock.last_flow_at",
            "        ELSE pso.logistics_last_flow_at",
            "    END,",
            "    pso.logistics_history_source = 'PRODUCT_SITE_OFFER_STOCK',",
            "    pso.updated_by = #{updatedBy},",
            "    pso.gmt_updated = NOW()",
            "WHERE pso.is_deleted = b'0'",
            "  AND pso.logical_store_id = #{logicalStoreId}",
            "  AND pso.partner_sku IS NOT NULL",
            "  AND TRIM(pso.partner_sku) <> ''",
            "  AND (",
            "      CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "          = CONVERT(UPPER(TRIM(#{partnerSku})) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      OR (",
            "          CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'",
            "          AND CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "              = CONVERT(UPPER(TRIM(#{partnerSku})) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      )",
            "  )"
    })
    int markProductSiteOfferLogisticsHistoryByStock(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            " AND pv.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.is_deleted = 0",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = 0",
            " AND pm.logical_store_id = ls.id",
            "SET pso.listing_started_at = CASE",
            "      WHEN NOT EXISTS (",
            "        SELECT 1",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "        LIMIT 1",
            "      ) THEN NULL",
            "      WHEN (",
            "        SELECT MIN(dsf.fact_date)",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "          AND (",
            "            NULLIF(dsf.partner_sku, '') = NULLIF(pv.partner_sku, '')",
            "            OR NULLIF(dsf.sku, '') = NULLIF(pv.partner_sku, '')",
            "          )",
            "          AND (COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0)",
            "      ) IS NOT NULL THEN CAST((",
            "        SELECT MIN(dsf.fact_date)",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "          AND (",
            "            NULLIF(dsf.partner_sku, '') = NULLIF(pv.partner_sku, '')",
            "            OR NULLIF(dsf.sku, '') = NULLIF(pv.partner_sku, '')",
            "          )",
            "          AND (COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0)",
            "      ) AS DATETIME)",
            "      WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0 THEN #{fallbackNow}",
            "      WHEN (",
            "        SELECT MIN(dsf.fact_date)",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "          AND (",
            "            NULLIF(dsf.partner_sku, '') = NULLIF(pv.partner_sku, '')",
            "            OR NULLIF(dsf.sku, '') = NULLIF(pv.partner_sku, '')",
            "          )",
            "          AND COALESCE(dsf.net_units, 0) > 0",
            "      ) IS NOT NULL THEN CAST((",
            "        SELECT MIN(dsf.fact_date)",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "          AND (",
            "            NULLIF(dsf.partner_sku, '') = NULLIF(pv.partner_sku, '')",
            "            OR NULLIF(dsf.sku, '') = NULLIF(pv.partner_sku, '')",
            "          )",
            "          AND COALESCE(dsf.net_units, 0) > 0",
            "      ) AS DATETIME)",
            "      ELSE NULL",
            "    END,",
            "    pso.listing_started_source = CASE",
            "      WHEN NOT EXISTS (",
            "        SELECT 1",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "        LIMIT 1",
            "      ) THEN 'data_missing'",
            "      WHEN (",
            "        SELECT MIN(dsf.fact_date)",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "          AND (",
            "            NULLIF(dsf.partner_sku, '') = NULLIF(pv.partner_sku, '')",
            "            OR NULLIF(dsf.sku, '') = NULLIF(pv.partner_sku, '')",
            "          )",
            "          AND (COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0)",
            "      ) IS NOT NULL THEN 'pv'",
            "      WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0 THEN 'inventory'",
            "      WHEN (",
            "        SELECT MIN(dsf.fact_date)",
            "        FROM daily_sales_fact dsf",
            "        WHERE dsf.owner_user_id = ls.owner_user_id",
            "          AND dsf.store_code = lss.store_code",
            "          AND dsf.site_code = lss.site",
            "          AND (",
            "            NULLIF(dsf.partner_sku, '') = NULLIF(pv.partner_sku, '')",
            "            OR NULLIF(dsf.sku, '') = NULLIF(pv.partner_sku, '')",
            "          )",
            "          AND COALESCE(dsf.net_units, 0) > 0",
            "      ) IS NOT NULL THEN 'sales'",
            "      ELSE 'not_listed'",
            "    END,",
            "    pso.updated_by = #{updatedBy},",
            "    pso.gmt_updated = NOW()",
            "WHERE pso.id = #{productSiteOfferId}",
            "  AND pso.is_deleted = 0",
            "  AND pso.listing_started_at IS NULL",
            "  AND (pso.listing_started_source IS NULL OR pso.listing_started_source IN ('data_missing', 'not_listed'))"
    })
    int backfillProductSiteOfferListingStartedAtById(
            @Param("productSiteOfferId") Long productSiteOfferId,
            @Param("fallbackNow") LocalDateTime fallbackNow,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            " AND pv.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.is_deleted = 0",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = 0",
            " AND pm.logical_store_id = ls.id",
            "LEFT JOIN (",
            "  SELECT dsf.owner_user_id, dsf.store_code, dsf.site_code, COUNT(1) AS site_fact_row_count",
            "  FROM daily_sales_fact dsf",
            "  WHERE dsf.owner_user_id = #{ownerUserId}",
            "    AND dsf.store_code = #{storeCode}",
            "    AND dsf.site_code = #{siteCode}",
            "  GROUP BY dsf.owner_user_id, dsf.store_code, dsf.site_code",
            ") site_fact_signal",
            "  ON site_fact_signal.owner_user_id = ls.owner_user_id",
            " AND site_fact_signal.store_code = lss.store_code",
            " AND site_fact_signal.site_code = lss.site",
            "LEFT JOIN (",
            "  SELECT dsf.owner_user_id, dsf.store_code, dsf.site_code, MIN(dsf.fact_date) AS first_pv_date",
            "  FROM daily_sales_fact dsf",
            "  WHERE dsf.owner_user_id = #{ownerUserId}",
            "    AND dsf.store_code = #{storeCode}",
            "    AND dsf.site_code = #{siteCode}",
            "    AND (",
            "      NULLIF(dsf.partner_sku, '') IN (NULLIF(#{partnerSku}, ''), NULLIF(#{sku}, ''))",
            "      OR NULLIF(dsf.sku, '') IN (NULLIF(#{partnerSku}, ''), NULLIF(#{sku}, ''))",
            "    )",
            "    AND (COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0)",
            "  GROUP BY dsf.owner_user_id, dsf.store_code, dsf.site_code",
            ") pv_signal",
            "  ON pv_signal.owner_user_id = ls.owner_user_id",
            " AND pv_signal.store_code = lss.store_code",
            " AND pv_signal.site_code = lss.site",
            "LEFT JOIN (",
            "  SELECT dsf.owner_user_id, dsf.store_code, dsf.site_code, MIN(dsf.fact_date) AS first_sales_date",
            "  FROM daily_sales_fact dsf",
            "  WHERE dsf.owner_user_id = #{ownerUserId}",
            "    AND dsf.store_code = #{storeCode}",
            "    AND dsf.site_code = #{siteCode}",
            "    AND (",
            "      NULLIF(dsf.partner_sku, '') IN (NULLIF(#{partnerSku}, ''), NULLIF(#{sku}, ''))",
            "      OR NULLIF(dsf.sku, '') IN (NULLIF(#{partnerSku}, ''), NULLIF(#{sku}, ''))",
            "    )",
            "    AND COALESCE(dsf.net_units, 0) > 0",
            "  GROUP BY dsf.owner_user_id, dsf.store_code, dsf.site_code",
            ") sales_signal",
            "  ON sales_signal.owner_user_id = ls.owner_user_id",
            " AND sales_signal.store_code = lss.store_code",
            " AND sales_signal.site_code = lss.site",
            "SET pso.listing_started_at = CASE",
            "      WHEN COALESCE(site_fact_signal.site_fact_row_count, 0) = 0 THEN NULL",
            "      WHEN pv_signal.first_pv_date IS NOT NULL THEN CAST(pv_signal.first_pv_date AS DATETIME)",
            "      WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0 THEN",
            "        #{fallbackNow}",
            "      WHEN sales_signal.first_sales_date IS NOT NULL THEN CAST(sales_signal.first_sales_date AS DATETIME)",
            "      ELSE NULL",
            "    END,",
            "    pso.listing_started_source = CASE",
            "      WHEN COALESCE(site_fact_signal.site_fact_row_count, 0) = 0 THEN 'data_missing'",
            "      WHEN pv_signal.first_pv_date IS NOT NULL THEN 'pv'",
            "      WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0 THEN 'inventory'",
            "      WHEN sales_signal.first_sales_date IS NOT NULL THEN 'sales'",
            "      ELSE 'not_listed'",
            "    END,",
            "    pso.updated_by = #{updatedBy},",
            "    pso.gmt_updated = NOW()",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND lss.store_code = #{storeCode}",
            "  AND lss.site = #{siteCode}",
            "  AND pso.is_deleted = 0",
            "  AND pso.listing_started_at IS NULL",
            "  AND (pso.listing_started_source IS NULL OR pso.listing_started_source IN ('data_missing', 'not_listed'))",
            "  AND (",
            "    NULLIF(#{partnerSku}, '') = NULLIF(pv.partner_sku, '')",
            "    OR NULLIF(#{sku}, '') = NULLIF(pv.partner_sku, '')",
            "  )"
    })
    int refreshProductSiteOfferListingStartedAtBySalesFact(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("sku") String sku,
            @Param("fallbackNow") LocalDateTime fallbackNow,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = 0",
            "SET pso.listing_started_at = NULL,",
            "    pso.listing_started_source = 'not_listed',",
            "    pso.updated_by = #{updatedBy},",
            "    pso.gmt_updated = NOW()",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND lss.store_code = #{storeCode}",
            "  AND lss.site = #{siteCode}",
            "  AND pso.is_deleted = 0",
            "  AND pso.listing_started_at IS NULL",
            "  AND (pso.listing_started_source IS NULL OR pso.listing_started_source IN ('data_missing', 'not_listed'))"
    })
    int markSiteProductOffersNotListedForEmptySalesReport(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            " AND pv.is_deleted = 0",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "SET pso.price = COALESCE(pso.price, #{price}),",
            "    pso.sale_price = COALESCE(pso.sale_price, #{salePrice}),",
            "    pso.sale_start = COALESCE(pso.sale_start, #{saleStart}),",
            "    pso.sale_end = COALESCE(pso.sale_end, #{saleEnd}),",
            "    pso.price_min = COALESCE(pso.price_min, #{priceMin}),",
            "    pso.price_max = COALESCE(pso.price_max, #{priceMax}),",
            "    pso.id_warranty = COALESCE(pso.id_warranty, #{idWarranty}),",
            "    pso.updated_by = #{updatedBy},",
            "    pso.gmt_updated = NOW()",
            "WHERE pv.product_master_id = #{productMasterId}",
            "  AND lss.store_code = #{storeCode}",
            "  AND pso.is_deleted = 0",
            "  AND (",
            "    (pso.price IS NULL AND #{price} IS NOT NULL)",
            "    OR (pso.sale_price IS NULL AND #{salePrice} IS NOT NULL)",
            "    OR (pso.sale_start IS NULL AND #{saleStart} IS NOT NULL)",
            "    OR (pso.sale_end IS NULL AND #{saleEnd} IS NOT NULL)",
            "    OR (pso.price_min IS NULL AND #{priceMin} IS NOT NULL)",
            "    OR (pso.price_max IS NULL AND #{priceMax} IS NOT NULL)",
            "    OR (pso.id_warranty IS NULL AND #{idWarranty} IS NOT NULL)",
            "  )"
    })
    int patchMissingProductSiteOfferEditableFields(
            @Param("productMasterId") Long productMasterId,
            @Param("storeCode") String storeCode,
            @Param("price") BigDecimal price,
            @Param("salePrice") BigDecimal salePrice,
            @Param("saleStart") LocalDateTime saleStart,
            @Param("saleEnd") LocalDateTime saleEnd,
            @Param("priceMin") BigDecimal priceMin,
            @Param("priceMax") BigDecimal priceMax,
            @Param("idWarranty") Integer idWarranty,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM product_site_offer",
            "WHERE variant_id = #{variantId}",
            "  AND site_id = #{siteId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductSiteOfferId(
            @Param("variantId") Long variantId,
            @Param("siteId") Long siteId
    );

    @Select({
            "SELECT id",
            "FROM product_site_offer",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND partner_sku = #{partnerSku}",
            "  AND site_code = #{siteCode}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductSiteOfferIdByStorePartnerSkuSite(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("siteCode") String siteCode
    );

    @Select({
            "SELECT",
            "  lss.store_code AS storeCode,",
            "  lss.site AS site,",
            "  pv.partner_sku AS partnerSku,",
            "  pso.psku_code AS pskuCode,",
            "  pso.offer_code AS offerCode,",
            "  pso.currency AS currency,",
            "  pso.price AS price,",
            "  pso.sale_price AS salePrice,",
            "  DATE_FORMAT(pso.sale_start, '%Y-%m-%d %H:%i:%s') AS saleStart,",
            "  DATE_FORMAT(pso.sale_end, '%Y-%m-%d %H:%i:%s') AS saleEnd,",
            "  pso.price_min AS priceMin,",
            "  pso.price_max AS priceMax,",
            "  pso.final_price AS finalPrice,",
            "  pso.final_price_source AS finalPriceSource,",
            "  pso.active_promotion_code AS activePromotionCode,",
            "  pso.active_promotion_name AS activePromotionName,",
            "  pso.active_promotion_url AS activePromotionUrl,",
            "  pso.pricing_method AS pricingMethod,",
            "  pso.id_warranty AS idWarranty,",
            "  pso.offer_note AS offerNote,",
            "  pso.delivery_method AS deliveryMethod,",
            "  CASE",
            "    WHEN pso.is_winning_buybox = b'1' THEN 1",
            "    WHEN pso.is_winning_buybox = b'0' THEN 0",
            "    ELSE NULL",
            "  END AS winningBuyboxFlag,",
            "  CASE",
            "    WHEN pso.is_active = b'1' THEN 1",
            "    WHEN pso.is_active = b'0' THEN 0",
            "    ELSE NULL",
            "  END AS activeFlag,",
            "  pso.live_status AS liveStatus,",
            "  pso.status_code AS statusCode,",
            "  DATE_FORMAT(pso.listing_started_at, '%Y-%m-%d %H:%i:%s') AS listingStartedAt,",
            "  pso.listing_started_source AS listingStartedSource,",
            "  pso.fbn_stock AS fbnStock,",
            "  pso.supermall_stock AS supermallStock,",
            "  pso.fbp_stock AS fbpStock,",
            "  pso.views_count AS viewsCount,",
            "  pso.units_sold AS unitsSold,",
            "  pso.sales_amount AS salesAmount,",
            "  pso.sales_currency AS salesCurrency,",
            "  DATE_FORMAT(pso.last_synced_at, '%Y-%m-%d %H:%i:%s') AS lastSyncedAt,",
            "  (",
            "    SELECT COALESCE(",
            "      MAX(CASE WHEN pb.is_primary = b'1' THEN pb.barcode END),",
            "      MAX(pb.barcode)",
            "    )",
            "    FROM product_barcode pb",
            "    WHERE pb.variant_id = pv.id",
            "      AND pb.is_deleted = 0",
            "  ) AS barcode",
            "FROM product_variant pv",
            "JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.is_deleted = 0",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.is_deleted = 0",
            "WHERE pv.product_master_id = #{productMasterId}",
            "  AND pv.is_deleted = 0",
            "ORDER BY lss.site, lss.store_code"
    })
    List<Map<String, Object>> selectProductSiteOfferProjectionRows(@Param("productMasterId") Long productMasterId);

    default Long nextProductMasterSnapshotId() {
        return nextProductManagementId("product_master_snapshot", 56000L);
    }

    @Insert({
            "INSERT INTO product_master_snapshot (",
            "  id, product_master_id, snapshot_type, snapshot_hash, snapshot_json, fetched_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productMasterId}, #{snapshotType}, #{snapshotHash}, #{snapshotJson}, #{fetchedAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertProductMasterSnapshot(
            @Param("id") Long id,
            @Param("productMasterId") Long productMasterId,
            @Param("snapshotType") String snapshotType,
            @Param("snapshotHash") String snapshotHash,
            @Param("snapshotJson") String snapshotJson,
            @Param("fetchedAt") LocalDateTime fetchedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  snapshot_type AS snapshotType,",
            "  snapshot_hash AS snapshotHash,",
            "  snapshot_json AS snapshotJson,",
            "  fetched_at AS fetchedAt",
            "FROM product_master_snapshot",
            "WHERE product_master_id = #{productMasterId}",
            "  AND snapshot_type = #{snapshotType}",
            "  AND is_deleted = 0",
            "ORDER BY fetched_at DESC, id DESC",
            "LIMIT 1"
    })
    ProductMasterSnapshotRecord selectLatestProductMasterSnapshot(
            @Param("productMasterId") Long productMasterId,
            @Param("snapshotType") String snapshotType
    );

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  snapshot_type AS snapshotType,",
            "  snapshot_hash AS snapshotHash,",
            "  snapshot_json AS snapshotJson,",
            "  fetched_at AS fetchedAt",
            "FROM product_master_snapshot",
            "WHERE product_master_id = #{productMasterId}",
            "  AND snapshot_type = #{snapshotType}",
            "  AND is_deleted = 0",
            "ORDER BY fetched_at DESC, id DESC",
            "LIMIT #{limit}"
    })
    List<ProductMasterSnapshotRecord> selectRecentProductMasterSnapshots(
            @Param("productMasterId") Long productMasterId,
            @Param("snapshotType") String snapshotType,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  snapshot_type AS snapshotType,",
            "  snapshot_hash AS snapshotHash,",
            "  snapshot_json AS snapshotJson,",
            "  fetched_at AS fetchedAt",
            "FROM product_master_snapshot",
            "WHERE product_master_id = #{productMasterId}",
            "  AND snapshot_type = #{snapshotType}",
            "  AND is_deleted = 0",
            "  AND fetched_at < #{beforeTime}",
            "  AND fetched_at >= DATE_SUB(#{beforeTime}, INTERVAL #{windowMinutes} MINUTE)",
            "  AND (",
            "    JSON_EXTRACT(snapshot_json, '$.siteOffers[0].price') IS NOT NULL",
            "    OR JSON_EXTRACT(snapshot_json, '$.siteOffers[0].salePrice') IS NOT NULL",
            "    OR JSON_EXTRACT(snapshot_json, '$.siteOffers[0].priceMin') IS NOT NULL",
            "    OR JSON_EXTRACT(snapshot_json, '$.siteOffers[0].priceMax') IS NOT NULL",
            "  )",
            "ORDER BY fetched_at DESC, id DESC",
            "LIMIT 1"
    })
    ProductMasterSnapshotRecord selectProductMasterSnapshotBeforeTimeWithOfferPrice(
            @Param("productMasterId") Long productMasterId,
            @Param("snapshotType") String snapshotType,
            @Param("beforeTime") LocalDateTime beforeTime,
            @Param("windowMinutes") int windowMinutes
    );

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  snapshot_type AS snapshotType,",
            "  snapshot_hash AS snapshotHash,",
            "  snapshot_json AS snapshotJson,",
            "  fetched_at AS fetchedAt",
            "FROM product_master_snapshot",
            "WHERE product_master_id = #{productMasterId}",
            "  AND snapshot_type = #{snapshotType}",
            "  AND is_deleted = 0",
            "  AND fetched_at >= DATE_SUB(#{afterTime}, INTERVAL 5 SECOND)",
            "  AND fetched_at <= DATE_ADD(#{afterTime}, INTERVAL #{windowMinutes} MINUTE)",
            "  AND (",
            "    JSON_EXTRACT(snapshot_json, '$.siteOffers[0].price') IS NOT NULL",
            "    OR JSON_EXTRACT(snapshot_json, '$.siteOffers[0].salePrice') IS NOT NULL",
            "    OR JSON_EXTRACT(snapshot_json, '$.siteOffers[0].priceMin') IS NOT NULL",
            "    OR JSON_EXTRACT(snapshot_json, '$.siteOffers[0].priceMax') IS NOT NULL",
            "  )",
            "ORDER BY fetched_at ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<ProductMasterSnapshotRecord> selectProductMasterSnapshotsAfterTimeWithOfferPrice(
            @Param("productMasterId") Long productMasterId,
            @Param("snapshotType") String snapshotType,
            @Param("afterTime") LocalDateTime afterTime,
            @Param("windowMinutes") int windowMinutes,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  snapshot_type AS snapshotType,",
            "  snapshot_hash AS snapshotHash,",
            "  snapshot_json AS snapshotJson,",
            "  fetched_at AS fetchedAt",
            "FROM product_master_snapshot",
            "WHERE id = #{id}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    ProductMasterSnapshotRecord selectProductMasterSnapshotById(@Param("id") Long id);

    default Long nextProductMasterDraftId() {
        return nextProductManagementId("product_master_draft", 57000L);
    }

    @Insert({
            "INSERT INTO product_master_draft (",
            "  id, product_master_id, baseline_snapshot_id, version_no, dirty_site_codes_json, draft_json, saved_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productMasterId}, #{baselineSnapshotId}, #{versionNo}, #{dirtySiteCodesJson}, #{draftJson}, #{savedAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  baseline_snapshot_id = VALUES(baseline_snapshot_id),",
            "  version_no = VALUES(version_no),",
            "  dirty_site_codes_json = VALUES(dirty_site_codes_json),",
            "  draft_json = VALUES(draft_json),",
            "  saved_at = VALUES(saved_at),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductMasterDraft(
            @Param("id") Long id,
            @Param("productMasterId") Long productMasterId,
            @Param("baselineSnapshotId") Long baselineSnapshotId,
            @Param("versionNo") Integer versionNo,
            @Param("dirtySiteCodesJson") String dirtySiteCodesJson,
            @Param("draftJson") String draftJson,
            @Param("savedAt") LocalDateTime savedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  baseline_snapshot_id AS baselineSnapshotId,",
            "  version_no AS versionNo,",
            "  dirty_site_codes_json AS dirtySiteCodesJson,",
            "  draft_json AS draftJson,",
            "  saved_at AS savedAt",
            "FROM product_master_draft",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    ProductMasterDraftRecord selectProductMasterDraftByProductMasterId(@Param("productMasterId") Long productMasterId);

    @Delete({
            "DELETE FROM product_master_draft",
            "WHERE product_master_id = #{productMasterId}"
    })
    int deleteProductMasterDraftByProductMasterId(@Param("productMasterId") Long productMasterId);

    @Delete({
            "DELETE pmd",
            "FROM product_master_draft pmd",
            "JOIN product_master pm",
            "  ON pm.id = pmd.product_master_id",
            " AND pm.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = pm.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = 0",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master_snapshot pms",
            "  ON pms.id = pmd.baseline_snapshot_id",
            " AND pms.is_deleted = 0",
            "WHERE pmd.is_deleted = 0",
            "  AND COALESCE(pmd.draft_json, '') = COALESCE(pms.snapshot_json, '')"
    })
    int deleteNoopProductMasterDraftsByStoreCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Update({
            "UPDATE product_master pm",
            "JOIN logical_store ls",
            "  ON ls.id = pm.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = 0",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master_draft pmd",
            "  ON pmd.product_master_id = pm.id",
            " AND pmd.is_deleted = 0",
            "JOIN product_master_snapshot pms",
            "  ON pms.id = pmd.baseline_snapshot_id",
            " AND pms.is_deleted = 0",
            "SET pm.sync_status = 'draft',",
            "    pm.updated_by = #{updatedBy},",
            "    pm.gmt_updated = NOW()",
            "WHERE pm.is_deleted = 0",
            "  AND pm.sync_status = 'synced'",
            "  AND COALESCE(pmd.draft_json, '') <> COALESCE(pms.snapshot_json, '')"
    })
    int markProductMastersWithMeaningfulDraftsByStoreCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("updatedBy") Long updatedBy
    );

    default Long nextProductActionLogId() {
        return nextProductManagementId("product_action_log", 58000L);
    }

    @Insert({
            "INSERT INTO product_action_log (",
            "  id, product_master_id, baseline_snapshot_id, target_site_id, target_variant_id, action_type,",
            "  overwrite_policy, idempotency_key, stage, result_status, error_code, psku_code, offer_code, retry_count,",
            "  summary_json, blocked_fields_json, request_json, response_json, started_at, finished_at, occurred_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productMasterId}, #{baselineSnapshotId}, #{targetSiteId}, #{targetVariantId}, #{actionType},",
            "  #{overwritePolicy}, #{idempotencyKey}, #{stage}, #{resultStatus}, #{errorCode}, #{pskuCode}, #{offerCode}, #{retryCount},",
            "  #{summaryJson}, #{blockedFieldsJson}, #{requestJson}, #{responseJson}, #{startedAt}, #{finishedAt}, #{occurredAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertProductActionLog(
            @Param("id") Long id,
            @Param("productMasterId") Long productMasterId,
            @Param("baselineSnapshotId") Long baselineSnapshotId,
            @Param("targetSiteId") Long targetSiteId,
            @Param("targetVariantId") Long targetVariantId,
            @Param("actionType") String actionType,
            @Param("overwritePolicy") String overwritePolicy,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("stage") String stage,
            @Param("resultStatus") String resultStatus,
            @Param("errorCode") String errorCode,
            @Param("pskuCode") String pskuCode,
            @Param("offerCode") String offerCode,
            @Param("retryCount") Integer retryCount,
            @Param("summaryJson") String summaryJson,
            @Param("blockedFieldsJson") String blockedFieldsJson,
            @Param("requestJson") String requestJson,
            @Param("responseJson") String responseJson,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM product_action_log",
            "WHERE idempotency_key = #{idempotencyKey}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductActionLogIdByIdempotency(@Param("idempotencyKey") String idempotencyKey);

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  baseline_snapshot_id AS baselineSnapshotId,",
            "  target_site_id AS targetSiteId,",
            "  target_variant_id AS targetVariantId,",
            "  action_type AS actionType,",
            "  stage,",
            "  result_status AS resultStatus,",
            "  error_code AS errorCode,",
            "  psku_code AS pskuCode,",
            "  offer_code AS offerCode,",
            "  retry_count AS retryCount,",
            "  summary_json AS summaryJson,",
            "  occurred_at AS occurredAt",
            "FROM product_action_log",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0",
            "ORDER BY occurred_at DESC, id DESC",
            "LIMIT 20"
    })
    List<ProductActionLogRecord> selectRecentProductActionLogs(@Param("productMasterId") Long productMasterId);

    default Long nextProductKeyContentHistoryId() {
        return nextProductManagementId("product_key_content_history", 59000L);
    }

    @Insert({
            "INSERT INTO product_key_content_history (",
            "  id, product_master_id, baseline_snapshot_id, target_site_id, source_action_type, visibility_status,",
            "  change_types_json, summary_json, published_at, visible_after, visible_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productMasterId}, #{baselineSnapshotId}, #{targetSiteId}, #{sourceActionType}, #{visibilityStatus},",
            "  #{changeTypesJson}, #{summaryJson}, #{publishedAt}, #{visibleAfter}, #{visibleAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertProductKeyContentHistory(
            @Param("id") Long id,
            @Param("productMasterId") Long productMasterId,
            @Param("baselineSnapshotId") Long baselineSnapshotId,
            @Param("targetSiteId") Long targetSiteId,
            @Param("sourceActionType") String sourceActionType,
            @Param("visibilityStatus") String visibilityStatus,
            @Param("changeTypesJson") String changeTypesJson,
            @Param("summaryJson") String summaryJson,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("visibleAfter") LocalDateTime visibleAfter,
            @Param("visibleAt") LocalDateTime visibleAt,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_key_content_history",
            "SET visibility_status = 'visible',",
            "    visible_at = COALESCE(visible_at, #{visibleAt}),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND visibility_status = 'pending'",
            "  AND visible_after <= #{visibleAt}",
            "  AND is_deleted = 0"
    })
    int promoteVisibleProductKeyContentHistory(
            @Param("productMasterId") Long productMasterId,
            @Param("visibleAt") LocalDateTime visibleAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  baseline_snapshot_id AS baselineSnapshotId,",
            "  target_site_id AS targetSiteId,",
            "  source_action_type AS sourceActionType,",
            "  visibility_status AS visibilityStatus,",
            "  change_types_json AS changeTypesJson,",
            "  summary_json AS summaryJson,",
            "  published_at AS publishedAt,",
            "  visible_after AS visibleAfter,",
            "  visible_at AS visibleAt",
            "FROM product_key_content_history",
            "WHERE product_master_id = #{productMasterId}",
            "  AND visibility_status = 'visible'",
            "  AND is_deleted = 0",
            "ORDER BY COALESCE(visible_at, published_at) DESC, id DESC",
            "LIMIT 20"
    })
    List<ProductKeyContentHistoryRecord> selectVisibleProductKeyContentHistories(@Param("productMasterId") Long productMasterId);

    @Select({
            "SELECT",
            "  id,",
            "  product_master_id AS productMasterId,",
            "  baseline_snapshot_id AS baselineSnapshotId,",
            "  target_site_id AS targetSiteId,",
            "  source_action_type AS sourceActionType,",
            "  visibility_status AS visibilityStatus,",
            "  change_types_json AS changeTypesJson,",
            "  summary_json AS summaryJson,",
            "  published_at AS publishedAt,",
            "  visible_after AS visibleAfter,",
            "  visible_at AS visibleAt",
            "FROM product_key_content_history",
            "WHERE product_master_id = #{productMasterId}",
            "  AND visibility_status = 'pending'",
            "  AND is_deleted = 0",
            "ORDER BY COALESCE(published_at, visible_after) DESC, id DESC",
            "LIMIT 20"
    })
    List<ProductKeyContentHistoryRecord> selectPendingProductKeyContentHistories(@Param("productMasterId") Long productMasterId);

    @Select({
            "SELECT COUNT(1)",
            "FROM product_key_content_history",
            "WHERE product_master_id = #{productMasterId}",
            "  AND visibility_status = 'visible'",
            "  AND is_deleted = 0"
    })
    Integer countVisibleProductKeyContentHistories(@Param("productMasterId") Long productMasterId);

    @Select({
            "SELECT COUNT(1)",
            "FROM product_key_content_history",
            "WHERE product_master_id = #{productMasterId}",
            "  AND visibility_status = 'pending'",
            "  AND is_deleted = 0"
    })
    Integer countPendingProductKeyContentHistories(@Param("productMasterId") Long productMasterId);

    @Select({
            "SELECT MIN(visible_after)",
            "FROM product_key_content_history",
            "WHERE product_master_id = #{productMasterId}",
            "  AND visibility_status = 'pending'",
            "  AND is_deleted = 0"
    })
    LocalDateTime selectEarliestPendingProductKeyContentVisibleAfter(@Param("productMasterId") Long productMasterId);
}
