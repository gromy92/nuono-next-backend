package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.NoonPullSmokeRunMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class NoonPullSmokeEvidencePersistenceContractTest {

    @Test
    void insertSmokeRunShouldPersistGateDecisionAndSafeReviewScope() {
        Method method = Arrays.stream(NoonPullSmokeRunMapper.class.getDeclaredMethods())
                .filter((candidate) -> "insertRun".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("noon_pull_smoke_run"));
        assertTrue(sql.contains("target_environment"));
        assertTrue(sql.contains("owner_user_id"));
        assertTrue(sql.contains("store_code"));
        assertTrue(sql.contains("site_code"));
        assertTrue(sql.contains("rollback_global_pause_strategy"));
        assertTrue(sql.contains("requested_domains"));
        assertTrue(sql.contains("missing_requirements"));
        assertTrue(sql.contains("evidence_gate_satisfied"));
        assertTrue(sql.contains("production_scheduling_allowed"));
    }

    @Test
    void insertSmokeEvidenceShouldPersistControlledFailuresWithoutSourceBatch() {
        Method method = Arrays.stream(NoonPullSmokeRunMapper.class.getDeclaredMethods())
                .filter((candidate) -> "insertEvidence".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("noon_pull_smoke_evidence"));
        assertTrue(sql.contains("data_domain"));
        assertTrue(sql.contains("target_identity"));
        assertTrue(sql.contains("row_or_item_count"));
        assertTrue(sql.contains("task_id"));
        assertTrue(sql.contains("source_batch_id"));
        assertTrue(sql.contains("elapsed_millis"));
        assertTrue(sql.contains("latest_fact_date"));
        assertTrue(sql.contains("failure_classification"));
    }

    @Test
    void recentSmokeRunsShouldBeReloadableForRestartVisibility() {
        Method selectRuns = Arrays.stream(NoonPullSmokeRunMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectRecentRuns".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Method selectEvidence = Arrays.stream(NoonPullSmokeRunMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectEvidenceByRunId".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String runSql = String.join(" ", selectRuns.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        String evidenceSql = String.join(" ", selectEvidence.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertTrue(runSql.contains("FROM noon_pull_smoke_run"));
        assertTrue(runSql.contains("ORDER BY id DESC"));
        assertTrue(evidenceSql.contains("FROM noon_pull_smoke_evidence"));
        assertTrue(evidenceSql.contains("run_id = #{runId}"));
        assertTrue(evidenceSql.contains("ORDER BY sequence_no ASC"));
    }

    @Test
    void migrationShouldCreateSmokeEvidenceTables() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/061_noon_smoke_evidence_persistence.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_pull_smoke_run`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_pull_smoke_evidence`"));
        assertTrue(sql.contains("rollback_global_pause_strategy"));
        assertTrue(sql.contains("failure_classification"));
        assertTrue(sql.contains("source_batch_id"));
        assertTrue(sql.contains("production_scheduling_allowed"));
    }
}
