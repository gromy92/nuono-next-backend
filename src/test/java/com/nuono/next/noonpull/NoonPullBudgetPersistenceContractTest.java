package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.NoonPullMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class NoonPullBudgetPersistenceContractTest {

    @Test
    void planShouldPersistRequestBudgetFields() {
        Method insertPlan = Arrays.stream(NoonPullMapper.class.getDeclaredMethods())
                .filter((candidate) -> "insertPlan".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", insertPlan.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("max_pages_per_run"));
        assertTrue(sql.contains("max_products_per_run"));
        assertTrue(sql.contains("max_detail_fetches_per_run"));
        assertTrue(sql.contains("max_requests_per_run"));
        assertTrue(sql.contains("cooldown_seconds"));
        assertTrue(sql.contains("concurrency_limit"));
    }

    @Test
    void taskShouldPersistCheckpointAndLargeStoreReadinessFields() {
        Method updateTask = Arrays.stream(NoonPullMapper.class.getDeclaredMethods())
                .filter((candidate) -> "updateTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", updateTask.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("checkpoint_cursor = #{checkpointCursor}"));
        assertTrue(sql.contains("processed_item_count = #{processedItemCount}"));
        assertTrue(sql.contains("request_count = #{requestCount}"));
        assertTrue(sql.contains("next_resume_position = #{nextResumePosition}"));
        assertTrue(sql.contains("last_safe_response_summary = #{lastSafeResponseSummary}"));
        assertTrue(sql.contains("readiness_state = #{readinessState}"));
    }

    @Test
    void migrationShouldCreateBudgetAndCheckpointColumns() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/058_noon_pull_foundation.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("max_pages_per_run"));
        assertTrue(sql.contains("max_detail_fetches_per_run"));
        assertTrue(sql.contains("checkpoint_cursor"));
        assertTrue(sql.contains("next_resume_position"));
        assertTrue(sql.contains("large_store_backfill_in_progress") || sql.contains("readiness_state"));
    }
}
