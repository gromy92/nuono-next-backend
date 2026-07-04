package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nuono.next.infrastructure.mapper.NoonPullMapper;
import com.nuono.next.infrastructure.mapper.NoonRiskBackoffMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.Test;

class NoonPullPersistenceContractTest {

    @Test
    void insertTaskShouldPersistScopeSourceBatchAndDiagnostics() {
        Method method = Arrays.stream(NoonPullMapper.class.getDeclaredMethods())
                .filter((candidate) -> "insertTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Insert insert = method.getAnnotation(Insert.class);
        String sql = String.join(" ", insert.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("noon_pull_task"));
        assertTrue(sql.contains("owner_user_id"));
        assertTrue(sql.contains("store_code"));
        assertTrue(sql.contains("site_code"));
        assertTrue(sql.contains("source_batch_id"));
        assertTrue(sql.contains("diagnostic_summary"));
        assertTrue(sql.contains("retry_action"));
        assertTrue(sql.contains("retryable"));
        assertTrue(sql.contains("requires_manual_action"));
        assertTrue(sql.contains("active_lock_key"));
        assertTrue(sql.contains("report_export_id"));
        assertTrue(sql.contains("report_export_status"));
        assertTrue(sql.contains("report_download_url"));
        assertTrue(sql.contains("report_total_rows"));
        assertTrue(sql.contains("report_last_poll_at"));
        assertTrue(sql.contains("report_next_poll_at"));
        assertTrue(sql.contains("report_poll_attempts"));
    }

    @Test
    void taskRecordBooleanWrapperPropertiesShouldNotHaveAmbiguousMyBatisGetters() {
        Reflector reflector = new Reflector(NoonPullTaskRecord.class);
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setRetryable(Boolean.TRUE);
        task.setRequiresManualAction(Boolean.FALSE);

        assertDoesNotThrow(() -> reflector.getGetInvoker("retryable").invoke(task, new Object[0]));
        assertDoesNotThrow(() -> reflector.getGetInvoker("requiresManualAction").invoke(task, new Object[0]));
    }

    @Test
    void taskRecordShouldExposeReportExportStatePropertiesForMyBatis() {
        Reflector reflector = new Reflector(NoonPullTaskRecord.class);

        assertNotNull(reflector.getGetInvoker("reportExportId"));
        assertNotNull(reflector.getGetInvoker("reportExportStatus"));
        assertNotNull(reflector.getGetInvoker("reportDownloadUrl"));
        assertNotNull(reflector.getGetInvoker("reportTotalRows"));
        assertNotNull(reflector.getGetInvoker("reportLastPollAt"));
        assertNotNull(reflector.getGetInvoker("reportNextPollAt"));
        assertNotNull(reflector.getGetInvoker("reportPollAttempts"));
    }

    @Test
    void activeTaskLookupShouldOnlyReturnQueuedOrRunningLocks() {
        Method method = Arrays.stream(NoonPullMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectActiveTaskByLockKey".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("active_lock_key = #{activeLockKey}"));
        assertTrue(sql.contains("'QUEUED'"));
        assertTrue(sql.contains("'RUNNING'"));
        assertTrue(sql.contains("is_deleted = b'0'"));
    }

    @Test
    void updatePlanAndTaskShouldPersistLifecycleAndRetryFields() {
        Method updatePlan = Arrays.stream(NoonPullMapper.class.getDeclaredMethods())
                .filter((candidate) -> "updatePlan".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Method updateTask = Arrays.stream(NoonPullMapper.class.getDeclaredMethods())
                .filter((candidate) -> "updateTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String planSql = String.join(" ", updatePlan.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");
        String taskSql = String.join(" ", updateTask.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");

        assertTrue(planSql.contains("paused = #{paused}"));
        assertTrue(planSql.contains("latest_success_at = #{latestSuccessAt}"));
        assertTrue(planSql.contains("latest_failure_type = #{latestFailureType}"));
        assertTrue(planSql.contains("next_retry_at = #{nextRetryAt}"));
        assertTrue(taskSql.contains("status = #{status}"));
        assertTrue(taskSql.contains("source_batch_id = #{sourceBatchId}"));
        assertTrue(taskSql.contains("failure_type = #{failureType}"));
        assertTrue(taskSql.contains("retry_action = #{retryAction}"));
        assertTrue(taskSql.contains("retryable = #{retryable}"));
        assertTrue(taskSql.contains("requires_manual_action = #{requiresManualAction}"));
        assertTrue(taskSql.contains("diagnostic_summary = #{diagnosticSummary}"));
        assertTrue(taskSql.contains("report_export_id = #{reportExportId}"));
        assertTrue(taskSql.contains("report_export_status = #{reportExportStatus}"));
        assertTrue(taskSql.contains("report_download_url = #{reportDownloadUrl}"));
        assertTrue(taskSql.contains("report_total_rows = #{reportTotalRows}"));
        assertTrue(taskSql.contains("report_last_poll_at = #{reportLastPollAt}"));
        assertTrue(taskSql.contains("report_next_poll_at = #{reportNextPollAt}"));
        assertTrue(taskSql.contains("report_poll_attempts = #{reportPollAttempts}"));
    }

    @Test
    void migrationShouldCreatePlanTaskAndSequenceTables() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/058_noon_pull_foundation.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_pull_id_sequence`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_pull_plan`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_pull_task`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_noon_pull_active_lock`"));
        assertTrue(sql.contains("source_batch_id"));
        assertTrue(sql.contains("diagnostic_summary"));
        assertTrue(sql.contains("retry_action"));
        assertTrue(sql.contains("retryable"));
        assertTrue(sql.contains("requires_manual_action"));
        assertTrue(sql.contains("report_export_id"));
        assertTrue(sql.contains("report_export_status"));
        assertTrue(sql.contains("report_download_url"));
        assertTrue(sql.contains("report_total_rows"));
        assertTrue(sql.contains("report_last_poll_at"));
        assertTrue(sql.contains("report_next_poll_at"));
        assertTrue(sql.contains("report_poll_attempts"));
    }

    @Test
    void migrationShouldAddReportExportResumeColumns() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/139_noon_pull_task_report_export_state.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("ALTER TABLE `noon_pull_task`"));
        assertTrue(sql.contains("information_schema.COLUMNS"));
        assertTrue(sql.contains("COLUMN_NAME = 'report_export_id'"));
        assertTrue(sql.contains("COLUMN_NAME = 'report_export_status'"));
        assertTrue(sql.contains("COLUMN_NAME = 'report_download_url'"));
        assertTrue(sql.contains("COLUMN_NAME = 'report_total_rows'"));
        assertTrue(sql.contains("COLUMN_NAME = 'report_last_poll_at'"));
        assertTrue(sql.contains("COLUMN_NAME = 'report_next_poll_at'"));
        assertTrue(sql.contains("COLUMN_NAME = 'report_poll_attempts'"));
        assertTrue(sql.contains("idx_noon_pull_task_report_export"));
        assertTrue(sql.contains("idx_noon_pull_task_next_poll"));
    }

    @Test
    void riskBackoffMapperShouldPersistAndLookupActiveReportScopeHolds() {
        Method upsert = Arrays.stream(NoonRiskBackoffMapper.class.getDeclaredMethods())
                .filter((candidate) -> "upsert".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Method selectActiveHold = Arrays.stream(NoonRiskBackoffMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectActiveHold".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Method selectLatestHold = Arrays.stream(NoonRiskBackoffMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectLatestHold".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String insertSql = String.join(" ", upsert.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");
        String activeSql = String.join(" ", selectActiveHold.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        String latestSql = String.join(" ", selectLatestHold.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertTrue(insertSql.contains("noon_risk_backoff_state"));
        assertTrue(insertSql.contains("scope_key"));
        assertTrue(insertSql.contains("risk_type"));
        assertTrue(insertSql.contains("source_domain"));
        assertTrue(insertSql.contains("source_task_id"));
        assertTrue(insertSql.contains("blocked_until"));
        assertTrue(insertSql.contains("attempt_count"));
        assertTrue(activeSql.contains("scope_key = #{scopeKey}"));
        assertTrue(activeSql.contains("blocked_until > #{now}"));
        assertTrue(activeSql.contains("is_deleted = b'0'"));
        assertTrue(latestSql.contains("ORDER BY gmt_updated DESC"));
    }

    @Test
    void migrationShouldCreateRiskBackoffStateTableWithAttemptCount() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/169_noon_risk_backoff_state.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_risk_backoff_state`"));
        assertTrue(sql.contains("`scope_key`"));
        assertTrue(sql.contains("`risk_type`"));
        assertTrue(sql.contains("`source_domain`"));
        assertTrue(sql.contains("`source_task_id`"));
        assertTrue(sql.contains("`blocked_until`"));
        assertTrue(sql.contains("`attempt_count`"));
        assertTrue(sql.contains("KEY `idx_noon_risk_backoff_active`"));
        assertTrue(sql.contains("KEY `idx_noon_risk_backoff_latest`"));
    }
}
