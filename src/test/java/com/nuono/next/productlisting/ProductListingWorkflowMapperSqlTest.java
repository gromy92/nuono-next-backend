package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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


class ProductListingWorkflowMapperSqlTest {

    @Test
    void draftLockLookupShouldBeOwnerScopedAndUseForUpdate() {
        Method method = mapperMethod("selectDraftByIdForUpdate");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_draft"));
        assertTrue(sql.contains("id = #{draftId}"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.endsWith("FOR UPDATE"));
    }

    @Test
    void dryRunLockLookupShouldBeOwnerScopedAndUseForUpdate() {
        Method method = mapperMethod("selectTaskByIdForUpdate");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("id = #{taskId}"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.endsWith("FOR UPDATE"));
    }
    @Test
    void draftAttemptBlockerCoversTerminalAndSucceededAttemptsUntilSafeReopen() {
        Method method = mapperMethod("selectCurrentRealRunTaskByDraftId");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task real_run"));
        assertTrue(sql.contains("LEFT JOIN product_listing_task source_dry_run"));
        assertTrue(sql.contains("real_run.mode = 'REAL_RUN'"));
        assertTrue(sql.contains(
                "COALESCE(source_dry_run.status, '') = 'superseded'"));
        assertTrue(sql.contains("real_run.noon_result_json IS NULL"));
        assertTrue(sql.contains("TRIM(real_run.noon_result_json) = ''"));
        assertTrue(sql.contains(
                "OR CASE WHEN JSON_VALID(real_run.noon_result_json) THEN"));
        assertTrue(sql.contains("JSON_VALID(real_run.noon_result_json)"));
        assertTrue(sql.contains(
                "JSON_TYPE(JSON_EXTRACT(real_run.noon_result_json, '$')) = 'OBJECT'"));
        assertTrue(sql.contains(
                "JSON_EXTRACT(real_run.noon_result_json, '$.success') IS NULL"));
        assertTrue(sql.contains(
                "JSON_TYPE(JSON_EXTRACT(real_run.noon_result_json, '$.success')) = 'BOOLEAN'"));
        assertTrue(sql.contains(
                "JSON_EXTRACT(real_run.noon_result_json, '$.steps') IS NULL"));
        assertTrue(sql.contains(
                "JSON_TYPE(JSON_EXTRACT(real_run.noon_result_json, '$.steps')) = 'ARRAY'"));
        assertTrue(sql.contains("ELSE FALSE END"));
        assertTrue(sql.contains("ELSE 'false'"));
        assertTrue(sql.contains("real_run.status IN ('failed', 'rejected')"));
        assertTrue(sql.contains("real_run.status = 'succeeded'"));
        assertTrue(sql.contains("noon_write_exception"));
        assertTrue(sql.contains("noon_create_outcome_unknown"));
        assertTrue(sql.contains("JSON_TABLE("));
        assertTrue(sql.contains("step_key VARCHAR(64) PATH '$.stepKey'"));
        assertTrue(sql.contains("step_status VARCHAR(32) PATH '$.status'"));
        assertTrue(sql.contains(
                "LOWER(write_evidence.step_key) IN ('create_product', 'resolve_create_reference')"));
        assertTrue(sql.contains("LOWER(write_evidence.step_status) = 'succeeded'"));
        assertTrue(sql.contains(
                "REGEXP_LIKE(write_evidence.external_reference, "
                        + "'(^|;)[[:space:]]*skuparent[[:space:]]*="
                        + "[[:space:]]*[^;[:space:]]', 'i')"));
        assertTrue(sql.contains(
                "REGEXP_LIKE(write_evidence.external_reference, "
                        + "'(^|;)[[:space:]]*pskucode[[:space:]]*="
                        + "[[:space:]]*[^;[:space:]]', 'i')"));
        assertFalse(sql.contains(
                "LOWER(write_evidence.external_reference) LIKE"));
        assertFalse(sql.contains(
                "LOWER(COALESCE(real_run.noon_result_json, '')) LIKE '%skuparent=%'"));
        assertTrue(sql.contains("'noon_auth_required'"));
        assertTrue(sql.contains("'noon_pre_create_failed'"));
        assertTrue(sql.contains("'noon_create_rejected'"));
        assertTrue(sql.contains("'noon_create_not_found_confirmed'"));
        assertTrue(sql.contains("'noon_warehouse_stock_not_supported'"));
        assertFalse(sql.contains("REGEXP 'auth|cookie|session"));
        assertTrue(!sql.contains(
                "real_run.status IN ('submitted', 'running', 'written_verify_failed') AND"));
    }

    @Test
    void reopenCasAlsoSupportsAttemptedValidationFailedDryRun() {
        Method method = mapperMethod("markValidatedDryRunSuperseded");
        Update update = method.getAnnotation(Update.class);
        String sql = compact(update.value());

        assertTrue(sql.contains("dry_run.mode = 'DRY_RUN'"));
        assertTrue(sql.contains(
                "dry_run.status IN ('validated', 'validation_failed')"));
    }

    @Test
    void createOutcomeAuthenticationTransitionIsOptimisticAndUnknownOnly() {
        Method method = mapperMethod(
                "markCreateOutcomeLookupAuthenticationRequired");
        Update update = method.getAnnotation(Update.class);
        String sql = compact(update.value());

        assertTrue(sql.contains("failure_code = 'noon_auth_required'"));
        assertTrue(sql.contains(
                "failure_code IN ('noon_create_outcome_unknown', 'real_run_interrupted')"));
        assertTrue(sql.contains(
                "noon_result_json = #{expectedNoonResultJson}"));
        assertTrue(sql.contains("status = 'written_verify_failed'"));
    }

    @Test
    void realRunAttemptLookupLocksEveryTerminalOutcomeForTheDryRunSource() {
        Method method = mapperMethod("selectRealWriteAttemptTaskBySourceTaskId");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("source_task_id = #{sourceTaskId}"));
        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(!sql.contains("status IN"));
        assertTrue(!sql.contains("status = 'failed'"));
        assertTrue(sql.contains("real_run_already_attempted"));
    }
    @Test
    void runningTaskHeartbeatAndCompletionShouldKeepAndVerifyTheWorkerClaim() {
        Method heartbeatMethod = mapperMethod("heartbeatRunningRealRunTask");
        String heartbeatSql = compact(heartbeatMethod.getAnnotation(Update.class).value());
        assertTrue(heartbeatSql.contains("SET gmt_updated = NOW()"));
        assertTrue(heartbeatSql.contains("status = 'running'"));
        assertTrue(heartbeatSql.contains("started_at = #{startedAt}"));

        Method completionMethod = mapperMethod("updateRunningTaskResult");
        String completionSql = compact(completionMethod.getAnnotation(Update.class).value());
        assertTrue(completionSql.contains("status = #{task.status}"));
        assertTrue(completionSql.contains("mode = 'REAL_RUN'"));
        assertTrue(completionSql.contains("status = 'running'"));
        assertTrue(completionSql.contains("started_at = #{task.startedAt}"));
    }

    @Test
    void identityLocksShouldHashNamespaceInsideMysqlSixtyFourCharacterLimit() {
        Method acquireMethod = mapperMethod("acquireIdentityLock");
        Method releaseMethod = mapperMethod("releaseIdentityLock");
        String acquireSql = compact(acquireMethod.getAnnotation(Select.class).value());
        String releaseSql = compact(releaseMethod.getAnnotation(Select.class).value());

        assertTrue(acquireSql.contains(
                "GET_LOCK(SHA2(CONCAT('product-listing:', #{lockKey}), 256), #{timeoutSeconds})"));
        assertTrue(releaseSql.contains(
                "RELEASE_LOCK(SHA2(CONCAT('product-listing:', #{lockKey}), 256))"));
        assertFalse(acquireSql.contains(
                "GET_LOCK(CONCAT('product-listing:', SHA2(#{lockKey}, 256))"));
        assertFalse(releaseSql.contains(
                "RELEASE_LOCK(CONCAT('product-listing:', SHA2(#{lockKey}, 256)))"));
    }
    @Test
    void workflowAttemptClaimMigrationLocksFailedAndRejectedWithoutRewritingHistory() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/init/204_product_listing_workflow_attempt_claim.sql"
        ));

        assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS `product_listing_real_run_attempt_claim`"));
        assertTrue(sql.contains("PRIMARY KEY (`owner_user_id`, `source_task_id`)"));
        assertTrue(sql.contains("INSERT IGNORE INTO product_listing_real_run_attempt_claim"));
        assertTrue(sql.contains("WHERE `mode` = 'REAL_RUN'"));
        assertTrue(sql.contains("GROUP BY `owner_user_id`, `source_task_id`"));
        assertTrue(!sql.contains("UPDATE product_listing_task"));
    }

    @Test
    void attemptClaimInsertUsesOwnerAndDryRunAsTheAtomicKey() {
        Method method = mapperMethod("claimRealRunAttempt");
        Insert insert = method.getAnnotation(Insert.class);
        String sql = compact(insert.value());

        assertTrue(sql.contains("INSERT IGNORE INTO product_listing_real_run_attempt_claim"));
        assertTrue(sql.contains("#{ownerUserId}"));
        assertTrue(sql.contains("#{sourceTaskId}"));
        assertTrue(sql.contains("#{attemptTaskId}"));
    }

    private Method mapperMethod(String name) {
        return Arrays.stream(ProductListingMapper.class.getMethods())
                .filter((candidate) -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ProductListingMapper method missing: " + name));
    }

    private String compact(String[] sql) {
        return String.join(" ", sql).replaceAll("\\s+", " ").trim();
    }
}
