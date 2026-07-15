package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.NoonDataCompletenessMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class NoonDataCompletenessSchemaContractTest {

    @Test
    void migrationShouldCreateCompletenessAndGapTables() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/063_noon_data_completeness_gap_patrol.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_data_completeness`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `noon_data_gap_window`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_noon_data_completeness_scope`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_noon_data_gap_window_natural`"));
        assertTrue(sql.contains("latest_status"));
        assertTrue(sql.contains("history_status"));
        assertTrue(sql.contains("latest_data_date"));
        assertTrue(sql.contains("history_covered_from"));
        assertTrue(sql.contains("history_covered_to"));
        assertTrue(sql.contains("patrol_enabled"));
        assertTrue(sql.contains("next_patrol_at"));
        assertTrue(sql.contains("last_task_id"));
        assertTrue(sql.contains("last_source_batch_id"));
        assertTrue(sql.contains("diagnostic_summary"));
    }

    @Test
    void categoryAndStatusEnumsShouldCoverFirstVersionVocabulary() {
        assertTrue(Arrays.asList(NoonDataCategory.values()).contains(NoonDataCategory.PRODUCT_LIST));
        assertTrue(Arrays.asList(NoonDataCategory.values()).contains(NoonDataCategory.PRODUCT_DETAIL));
        assertTrue(Arrays.asList(NoonDataCategory.values()).contains(NoonDataCategory.SALES_ORDER));
        assertTrue(Arrays.asList(NoonDataCategory.values()).contains(NoonDataCategory.SALES_PRODUCT_VIEWS));

        assertTrue(Arrays.asList(NoonDataLatestStatus.values()).contains(NoonDataLatestStatus.PENDING_CONFIRMATION));
        assertTrue(Arrays.asList(NoonDataLatestStatus.values()).contains(NoonDataLatestStatus.NOT_INTEGRATED));
        assertTrue(Arrays.asList(NoonDataHistoryStatus.values()).contains(NoonDataHistoryStatus.CONFIRMED_EMPTY));
        assertTrue(Arrays.asList(NoonDataHistoryStatus.values()).contains(NoonDataHistoryStatus.PROVIDER_RETENTION_LIMIT));
        assertTrue(Arrays.asList(NoonDataGapStatus.values()).contains(NoonDataGapStatus.WAITING_RETRY));
        assertTrue(Arrays.asList(NoonDataGapStatus.values()).contains(NoonDataGapStatus.PROVIDER_RETENTION_LIMIT));
    }

    @Test
    void mapperShouldPersistCompletenessAndGapRecords() {
        Method insertCompleteness = Arrays.stream(NoonDataCompletenessMapper.class.getDeclaredMethods())
                .filter((candidate) -> "insertCompleteness".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Method listCompleteness = Arrays.stream(NoonDataCompletenessMapper.class.getDeclaredMethods())
                .filter((candidate) -> "listCompleteness".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Method insertGap = Arrays.stream(NoonDataCompletenessMapper.class.getDeclaredMethods())
                .filter((candidate) -> "insertGapWindow".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String insertCompletenessSql = String.join(" ", insertCompleteness.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
        String listCompletenessSql = String.join(" ", listCompleteness.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        String insertGapSql = String.join(" ", insertGap.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertTrue(insertCompletenessSql.contains("noon_data_completeness"));
        assertTrue(insertCompletenessSql.contains("owner_user_id"));
        assertTrue(insertCompletenessSql.contains("data_category"));
        assertTrue(listCompletenessSql.contains("ORDER BY ndc.owner_user_id ASC"));
        assertTrue(insertGapSql.contains("noon_data_gap_window"));
        assertTrue(insertGapSql.contains("linked_pull_task_id"));
        assertTrue(insertGapSql.contains("linked_source_batch_id"));
    }

    @Test
    void auditScopesShouldOnlyIncludeEnabledStoreSites() {
        Method method = Arrays.stream(NoonDataCompletenessMapper.class.getDeclaredMethods())
                .filter((candidate) -> "listAuditScopes".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("FROM logical_store ls"));
        assertTrue(sql.contains("JOIN logical_store_site lss"));
        assertTrue(sql.contains("COALESCE(lss.site_enabled, b'1') = b'1'"));
    }
}
