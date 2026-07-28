package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.ProductPublishRetryMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductPublishRetryMapperSqlTest {

    @Test
    void legacyFailedRecoveryShouldUseOnlyTransientTransportWhitelist() throws Exception {
        Method method = ProductPublishRetryMapper.class.getMethod(
                "recoverRetryableFailedNoonWriteProductPublishTasks",
                int.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("MAX(candidate.id) AS id"));
        assertTrue(sql.contains("candidate.status = 'failed'"));
        assertTrue(sql.contains("candidate.error_code IN ('noon_write_failed', 'publish_task_failed', 'noon_request_failed')"));
        assertTrue(sql.contains("http([/][0-9]+([.][0-9]+)?)?[[:space:]]+(408|500|502|503|504)"));
        assertTrue(sql.contains("REGEXP '(^|[^a-z])eof(exception)?([^a-z]|$)'"));
        assertTrue(sql.contains("(connection|connect)[[:space:]_-]+(timeout|timed[[:space:]_-]+out)"));
        assertTrue(sql.contains(
                "NOT REGEXP 'http([/][0-9]+([.][0-9]+)?)?[[:space:]]+[0-9]{3}([^0-9]|$)'"
        ));
        assertFalse(sql.contains("request[[:space:]_-]+(timeout|timed[[:space:]_-]+out)"));
        assertFalse(sql.contains("read[[:space:]_-]+(timeout|timed[[:space:]_-]+out)"));
        assertFalse(sql.contains("LIKE '%connection reset%'"));
        assertFalse(sql.contains("(408|429|500|502|503|504)"));
        assertTrue(sql.contains(
                "NOT REGEXP 'http([/][0-9]+([.][0-9]+)?)?[[:space:]]+"
                        + "(1[0-9]{2}|2[0-9]{2}|3[0-9]{2}|400|40[1-7]|409|4[1-9][0-9]|"
                        + "501|50[5-9]|5[1-9][0-9]|[6-9][0-9]{2})([^0-9]|$)'"
        ));
        assertTrue(sql.contains("NOT REGEXP 'auth[_ -]?required|authentication|authorization|credential|password'"));
        assertTrue(sql.contains("NOT REGEXP 'project[_ -]?(scope|access)|current[[:space:]]+project|scope[_ -]?mismatch'"));
        assertTrue(sql.contains("NOT REGEXP 'access[[:space:]]+denied|permission|forbidden|unauthorized'"));
        assertTrue(sql.contains("NOT REGEXP 'validation|invalid[[:space:]_-]+(parameter|request|payload|value|partner.?sku|barcode)|bad[[:space:]_-]+request|unsupported|duplicate|conflict|not[[:space:]_-]+found'"));
        assertTrue(sql.contains("candidate.id = ( SELECT MAX(latest.id)"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("WHEN t.task_type = 'product-delete' THEN 'product_delete_write_retry_scheduled'"));
        assertTrue(sql.contains("ELSE 'write_retry_scheduled'"));
        assertTrue(sql.contains("t.active_lock_key = CONCAT('product:', t.product_master_id)"));
    }

    @Test
    void claimShouldUseLegacyDeleteMarkersForDatabaseRunningStatus() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter(candidate -> "tryStartProductPublishTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("UPDATE product_publish_task target"));
        assertTrue(sql.contains("THEN 'product_delete_running'"));
        assertTrue(sql.contains("LOWER(TRIM(COALESCE(target.task_type, ''))) = 'product-delete'"));
        assertTrue(sql.contains("$.action"));
        assertTrue(sql.contains("target.idempotency_key"));
        assertTrue(sql.contains("target.changed_domains_json"));
        assertTrue(sql.contains("target.draft_json"));
        assertTrue(sql.contains("target.baseline_json"));
    }

    @Test
    void manualRetryShouldPreserveDeleteStageAndIgnoreAutomaticBudget() {
        Method method = Arrays.stream(ProductPublishRetryMapper.class.getDeclaredMethods())
                .filter(candidate -> "retryProductPublishTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value())
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("target.status = CASE"));
        assertTrue(sql.contains("target.task_type = CASE"));
        assertTrue(sql.contains("THEN 'product-delete'"));
        assertTrue(sql.contains("LOWER(TRIM(COALESCE(target.task_type, ''))) = 'product-delete'"));
        assertTrue(sql.contains("$.action"));
        assertTrue(sql.contains("target.idempotency_key"));
        assertTrue(sql.contains("JSON_CONTAINS"));
        assertTrue(sql.contains("target.changed_domains_json"));
        assertTrue(sql.contains("target.draft_json"));
        assertTrue(sql.contains("target.baseline_json"));
        assertTrue(sql.contains("$.mode"));
        assertTrue(sql.contains("THEN 'product_delete_queued'"));
        assertTrue(sql.contains("target.result_json = CASE"));
        assertTrue(sql.contains("THEN target.result_json"));
        String resultCheckpointSql = sql.substring(sql.indexOf("target.result_json = CASE"));
        assertTrue(resultCheckpointSql.contains("target.idempotency_key"));
        assertTrue(resultCheckpointSql.contains("target.changed_domains_json"));
        assertTrue(resultCheckpointSql.contains("target.draft_json"));
        assertTrue(resultCheckpointSql.contains("target.baseline_json"));
        assertTrue(sql.contains("WHEN target.error_code = 'noon_auth_recovery_pending' THEN target.retry_count"));
        assertTrue(sql.contains("target.status = 'pending_manual_check'"));
        assertTrue(sql.contains("OR target.retry_count < target.max_retry_count"));
        assertTrue(sql.contains("target.error_code, ''))) NOT IN ("));
        assertTrue(sql.contains("'product_write_outcome_unknown'"));
        assertTrue(sql.contains("'group_partial_write_unknown'"));
        assertTrue(sql.contains("'noon_effect_not_confirmed'"));
        assertTrue(sql.contains("$.writeMayHaveOccurred"));
        assertTrue(sql.contains("JSON_VALID(target.result_json)"));
        assertTrue(sql.contains("JSON_TYPE(target.result_json) = 'OBJECT'"));
        assertTrue(sql.contains("JSON_TYPE(JSON_EXTRACT(target.result_json, '$.writeMayHaveOccurred')) = 'BOOLEAN'"));
        assertTrue(sql.contains("target.finished_at = NULL"));
        assertTrue(sql.contains("LEFT JOIN product_publish_task newer"));
        assertTrue(sql.contains("newer.id > target.id"));
        assertTrue(sql.contains("newer.id IS NULL"));
    }

    @Test
    void exhaustedHistoricalDeleteRetriesShouldStopBeforeAnotherProviderCall() throws Exception {
        Method method = ProductPublishRetryMapper.class.getMethod(
                "stopExhaustedProductDeleteRetries",
                int.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("target.status = 'pending_manual_check'"));
        assertTrue(sql.contains("SET target.task_type = 'product-delete'"));
        assertTrue(sql.contains("target.error_code = 'product_delete_retry_exhausted'"));
        assertTrue(sql.contains(
                "target.status IN ('write_retry_scheduled', 'product_delete_write_retry_scheduled')"
        ));
        assertTrue(sql.contains("LOWER(TRIM(COALESCE(target.task_type, ''))) = 'product-delete'"));
        assertTrue(sql.contains("$.action"));
        assertTrue(sql.contains("target.idempotency_key"));
        assertTrue(sql.contains("target.changed_domains_json"));
        assertTrue(sql.contains("target.draft_json"));
        assertTrue(sql.contains("target.baseline_json"));
        assertTrue(sql.contains("COALESCE(target.retry_count, 0) >= LEAST("));
        assertTrue(sql.contains("GREATEST(COALESCE(target.max_retry_count, 3), 0)"));
        assertTrue(sql.contains("#{automaticRetryLimit}"));
        assertTrue(sql.contains("target.locked_at IS NULL"));
        assertTrue(sql.contains("target.active_lock_key = NULL"));
    }
}
