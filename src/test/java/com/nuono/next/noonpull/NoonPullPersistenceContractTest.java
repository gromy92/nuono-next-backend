package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.nuono.next.infrastructure.mapper.NoonPullMapper;
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
    }
}
