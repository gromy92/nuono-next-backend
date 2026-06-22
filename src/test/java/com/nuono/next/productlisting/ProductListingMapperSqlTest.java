package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
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
    void activeRealRunLookupShouldScopeByOwnerAndDryRunSource() {
        Method method = mapperMethod("selectRealWriteAttemptTaskBySourceTaskId");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("source_task_id = #{sourceTaskId}"));
        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(sql.contains("status IN ('running', 'submitted', 'succeeded', 'failed')"));
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
    void realRunMigrationShouldEnforceOneWriteAttemptPerDryRun() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/init/114_product_listing_real_run.sql"));

        assertTrue(sql.contains("real_write_attempt_source_task_id"));
        assertTrue(sql.contains("uk_product_listing_real_write_attempt"));
        assertTrue(sql.contains("succeeded"));
        assertTrue(sql.contains("failed"));
    }

    private Method mapperMethod(String name) {
        return Arrays.stream(ProductListingMapper.class.getDeclaredMethods())
                .filter((candidate) -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
    }

    private String compact(String[] sql) {
        return String.join(" ", sql).replaceAll("\\s+", " ").trim();
    }
}
