package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductListingMapperSqlTest {

    @Test
    void insertDraftShouldPersistOptionalPurchaseOrderAndDraftPayload() {
        Method method = mapperMethod("insertDraft");
        Insert insert = method.getAnnotation(Insert.class);
        String sql = compact(insert.value());

        assertTrue(sql.contains("INSERT INTO product_listing_draft"));
        assertTrue(sql.contains("optional_purchase_order_id"));
        assertTrue(sql.contains("draft_json"));
        assertTrue(sql.contains("#{draft.optionalPurchaseOrderId}"));
    }

    @Test
    void insertTaskShouldPersistDryRunModeAndValidationSnapshot() {
        Method method = mapperMethod("insertTask");
        Insert insert = method.getAnnotation(Insert.class);
        String sql = compact(insert.value());

        assertTrue(sql.contains("INSERT INTO product_listing_task"));
        assertTrue(sql.contains("mode"));
        assertTrue(sql.contains("input_snapshot_json"));
        assertTrue(sql.contains("validation_json"));
        assertTrue(sql.contains("#{task.mode}"));
    }

    @Test
    void insertTaskShouldPersistRealRunAuditFields() {
        Method method = mapperMethod("insertTask");
        Insert insert = method.getAnnotation(Insert.class);
        String sql = compact(insert.value());

        assertTrue(sql.contains("source_task_id"));
        assertTrue(sql.contains("confirmation_json"));
        assertTrue(sql.contains("noon_result_json"));
        assertTrue(sql.contains("failure_category"));
        assertTrue(sql.contains("started_at"));
        assertTrue(sql.contains("#{task.sourceTaskId}"));
        assertTrue(sql.contains("#{task.confirmationJson}"));
        assertTrue(sql.contains("#{task.noonResultJson}"));
        assertTrue(sql.contains("#{task.failureCategory}"));
        assertTrue(sql.contains("#{task.startedAt}"));
    }

    @Test
    void taskLookupShouldStayScopedToOwner() {
        Method method = mapperMethod("selectTaskById");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("id = #{taskId}"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
    }

    @Test
    void recentTaskLookupCanBeScopedDirectlyToDraft() {
        Method method = mapperMethod("selectRecentTasksByDraftId");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("store_code = #{storeCode}"));
        assertTrue(sql.contains("draft_id = #{draftId}"));
        assertTrue(sql.contains("LIMIT #{limit}"));
    }

    @Test
    void activeRealRunLookupShouldScopeByOwnerAndDryRunSource() {
        Method method = mapperMethod("selectRealWriteAttemptTaskBySourceTaskId");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("source_task_id = #{sourceTaskId}"));
        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(sql.contains("status IN ('running', 'submitted', 'succeeded', 'written_verify_failed')"));
        assertTrue(sql.contains("status = 'failed' AND failure_code = 'partner_sku_already_exists'"));
    }

    @Test
    void listedPartnerSkuLookupShouldUseOwnerStoreSkuAndKnownWrittenStates() {
        Method method = mapperMethod("selectListedPartnerSkuTask");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("store_code = #{storeCode}"));
        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(sql.contains("status IN ('submitted', 'running', 'succeeded', 'written_verify_failed')"));
        assertTrue(sql.contains("status = 'failed' AND failure_code = 'partner_sku_already_exists'"));
        assertTrue(sql.contains("JSON_EXTRACT(input_snapshot_json, '$.psku')"));
        assertTrue(sql.contains("UPPER(TRIM(#{partnerSku}))"));
    }

    @Test
    void listedPartnerSkuLookupShouldIgnoreSkuDeletedAfterListingSuccess() {
        Method method = mapperMethod("selectListedPartnerSkuTask");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("FROM product_publish_task delete_task"));
        assertTrue(sql.contains("delete_task.task_type = 'product-delete'"));
        assertTrue(sql.contains("delete_task.status = 'synced'"));
        assertTrue(sql.contains("delete_task.finished_at >= product_listing_task.completed_at"));
    }

    @Test
    void reservedBarcodeLookupShouldIgnoreProductDeletedAfterListingSuccess() {
        Method method = mapperMethod("selectReservedBarcodeTask");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("JSON_EXTRACT(input_snapshot_json, '$.barcode')"));
        assertTrue(sql.contains("FROM product_publish_task delete_task"));
        assertTrue(sql.contains("JSON_EXTRACT(product_listing_task.input_snapshot_json, '$.psku')"));
        assertTrue(sql.contains("delete_task.status = 'synced'"));
        assertTrue(sql.contains("delete_task.finished_at >= product_listing_task.completed_at"));
    }

    @Test
    void localProductPartnerSkuLookupShouldUseOwnerLogicalStoreAndActiveProductCatalog() {
        Method method = mapperMethod("selectLocalProductIdByPartnerSku");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM logical_store_site lss"));
        assertTrue(sql.contains("JOIN logical_store ls"));
        assertTrue(sql.contains("JOIN product_master pm"));
        assertTrue(sql.contains("LEFT JOIN product_variant pv"));
        assertTrue(sql.contains("LEFT JOIN product_master_draft pmd"));
        assertTrue(sql.contains("LEFT JOIN product_master_snapshot pms"));
        assertTrue(sql.contains("pms.snapshot_type = 'baseline'"));
        assertTrue(sql.contains("SELECT MAX(pms_latest.id)"));
        assertTrue(sql.contains("ls.owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("UPPER(lss.store_code) = UPPER(#{storeCode})"));
        assertTrue(sql.contains("pm.logical_store_id = lss.logical_store_id"));
        assertTrue(sql.contains("UPPER(TRIM(COALESCE(NULLIF(pm.partner_sku, ''), pv.partner_sku))) = UPPER(TRIM(#{partnerSku}))"));
        assertTrue(sql.contains("JSON_EXTRACT(pmd.draft_json, '$.identity.listingDraftId')"));
        assertTrue(sql.contains("JSON_EXTRACT(pms.snapshot_json, '$.identity.listingDraftId')"));
        assertTrue(sql.contains("#{excludeListingDraftId}"));
    }

    @Test
    void localProductBarcodeLookupShouldUseOwnerLogicalStoreAndActiveBarcodes() {
        Method method = mapperMethod("selectLocalProductIdByBarcode");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM logical_store_site lss"));
        assertTrue(sql.contains("JOIN logical_store ls"));
        assertTrue(sql.contains("JOIN product_master pm"));
        assertTrue(sql.contains("JOIN product_variant pv"));
        assertTrue(sql.contains("JOIN product_barcode pb"));
        assertTrue(sql.contains("LEFT JOIN product_master_draft pmd"));
        assertTrue(sql.contains("LEFT JOIN product_master_snapshot pms"));
        assertTrue(sql.contains("pms.snapshot_type = 'baseline'"));
        assertTrue(sql.contains("SELECT MAX(pms_latest.id)"));
        assertTrue(sql.contains("ls.owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("UPPER(lss.store_code) = UPPER(#{storeCode})"));
        assertTrue(sql.contains("pm.logical_store_id = lss.logical_store_id"));
        assertTrue(sql.contains("UPPER(TRIM(pb.barcode)) = UPPER(TRIM(#{barcode}))"));
        assertTrue(sql.contains("JSON_EXTRACT(pmd.draft_json, '$.identity.listingDraftId')"));
        assertTrue(sql.contains("JSON_EXTRACT(pms.snapshot_json, '$.identity.listingDraftId')"));
        assertTrue(sql.contains("#{excludeListingDraftId}"));
    }

    @Test
    void workerTaskLookupShouldUseOnlyTaskIdAfterTaskWasCreatedByGuardedApi() {
        Method method = mapperMethod("selectTaskByIdForWorker");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("id = #{taskId}"));
        assertTrue(!sql.contains("owner_user_id = #{ownerUserId}"));
    }

    @Test
    void markTaskRunningShouldClaimOnlySubmittedRealRun() {
        Method method = mapperMethod("markTaskRunning");
        Update update = method.getAnnotation(Update.class);
        String sql = compact(update.value());

        assertTrue(sql.contains("status = 'running'"));
        assertTrue(sql.contains("started_at = #{startedAt}"));
        assertTrue(sql.contains("id = #{taskId}"));
        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(sql.contains("status = 'submitted'"));
    }

    @Test
    void runnableRealRunLookupShouldSelectSubmittedTasksOldestFirst() {
        Method method = mapperMethod("selectRunnableRealRunTasks");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(sql.contains("status = 'submitted'"));
        assertTrue(sql.contains("ORDER BY submitted_at ASC, id ASC"));
        assertTrue(sql.contains("LIMIT #{limit}"));
    }

    @Test
    void staleRunningRecoveryShouldRequireManualVerificationWithoutReplayingNoonWrites() {
        Method method = mapperMethod("recoverStaleRunningRealRunTasks");
        Update update = method.getAnnotation(Update.class);
        String sql = compact(update.value());

        assertTrue(sql.contains("UPDATE product_listing_task"));
        assertTrue(sql.contains("status = 'written_verify_failed'"));
        assertTrue(sql.contains("failure_code = 'real_run_interrupted'"));
        assertTrue(sql.contains("completed_at = NOW()"));
        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(sql.contains("status = 'running'"));
        assertTrue(sql.contains("started_at < #{staleBefore}"));
    }

    @Test
    void identityLocksShouldUseNamespacedHashedMysqlAdvisoryLocks() {
        Method acquireMethod = mapperMethod("acquireIdentityLock");
        Method releaseMethod = mapperMethod("releaseIdentityLock");
        String acquireSql = compact(acquireMethod.getAnnotation(Select.class).value());
        String releaseSql = compact(releaseMethod.getAnnotation(Select.class).value());

        assertTrue(acquireSql.contains("GET_LOCK"));
        assertTrue(acquireSql.contains("product-listing:"));
        assertTrue(acquireSql.contains("SHA2(#{lockKey}, 256)"));
        assertTrue(releaseSql.contains("RELEASE_LOCK"));
        assertTrue(releaseSql.contains("SHA2(#{lockKey}, 256)"));
    }

    @Test
    void updateTaskResultShouldPersistNoonResultAndFailureCategory() {
        Method method = mapperMethod("updateTaskResult");
        Update update = method.getAnnotation(Update.class);
        String sql = compact(update.value());

        assertTrue(sql.contains("status = #{task.status}"));
        assertTrue(sql.contains("noon_result_json = #{task.noonResultJson}"));
        assertTrue(sql.contains("failure_category = #{task.failureCategory}"));
        assertTrue(sql.contains("failure_code = #{task.failureCode}"));
        assertTrue(sql.contains("failure_message = #{task.failureMessage}"));
        assertTrue(sql.contains("completed_at = #{task.completedAt}"));
        assertTrue(sql.contains("WHERE id = #{task.id}"));
        assertTrue(sql.contains("owner_user_id = #{task.ownerUserId}"));
    }

    @Test
    void activeDraftLookupShouldUseDryRunReadyStatus() {
        Method method = mapperMethod("findActiveDraftId");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("ready_for_dry_run"));
    }

    @Test
    void recentDraftLookupShouldScopeByOwnerStoreAndEditableStatuses() {
        Method method = mapperMethod("selectRecentDrafts");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_draft"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("store_code = #{storeCode}"));
        assertTrue(sql.contains("status IN ('draft', 'validation_failed', 'ready_for_dry_run')"));
        assertTrue(sql.contains("ORDER BY gmt_updated DESC"));
        assertTrue(sql.contains("LIMIT #{limit}"));
    }

    @Test
    void realRunRetryGuardMigrationShouldEnforceOneWriteAttemptPerDryRun() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/init/186_product_listing_real_run_retry_guard.sql"));

        assertTrue(sql.contains("real_write_attempt_source_task_id"));
        assertTrue(sql.contains("uk_product_listing_real_write_attempt"));
        assertTrue(sql.contains("failure_message LIKE '%Partner skus already exists%'"));
        assertTrue(sql.contains("partner_sku_already_exists_superseded"));
        assertTrue(sql.contains("succeeded"));
        assertTrue(sql.contains("written_verify_failed"));
        assertTrue(sql.contains("failure_code` = 'partner_sku_already_exists'"));
    }

    private Method mapperMethod(String name) {
        return Arrays.stream(ProductListingMapper.class.getDeclaredMethods())
                .filter((candidate) -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ProductListingMapper method missing: " + name));
    }

    private String compact(String[] sql) {
        return String.join(" ", sql).replaceAll("\\s+", " ").trim();
    }
}
