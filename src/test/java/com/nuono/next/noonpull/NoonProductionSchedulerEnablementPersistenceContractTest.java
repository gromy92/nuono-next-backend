package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.NoonProductionSchedulerEnablementMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class NoonProductionSchedulerEnablementPersistenceContractTest {

    @Test
    void insertDecisionShouldPersistScopeEvidenceScheduleAndOperator() {
        Method method = Arrays.stream(NoonProductionSchedulerEnablementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "insertRecord".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("noon_production_scheduler_enablement"));
        assertTrue(sql.contains("target_environment"));
        assertTrue(sql.contains("owner_user_id"));
        assertTrue(sql.contains("store_code"));
        assertTrue(sql.contains("site_code"));
        assertTrue(sql.contains("enabled_domains"));
        assertTrue(sql.contains("schedule_boundaries"));
        assertTrue(sql.contains("operator_user_id"));
        assertTrue(sql.contains("smoke_run_id"));
        assertTrue(sql.contains("decision"));
        assertTrue(sql.contains("plan_ids"));
    }

    @Test
    void recentDecisionsShouldBeReloadableForAudit() {
        Method method = Arrays.stream(NoonProductionSchedulerEnablementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectRecent".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("FROM noon_production_scheduler_enablement"));
        assertTrue(sql.contains("ORDER BY id DESC"));
        assertTrue(sql.contains("LIMIT #{limit}"));
    }

    @Test
    void migrationShouldCreateEnablementAuditTable() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/062_noon_production_scheduler_enablement_gate.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_production_scheduler_enablement`"));
        assertTrue(sql.contains("target_environment"));
        assertTrue(sql.contains("schedule_boundaries"));
        assertTrue(sql.contains("operator_user_id"));
        assertTrue(sql.contains("smoke_run_id"));
        assertTrue(sql.contains("plan_ids"));
    }
}
