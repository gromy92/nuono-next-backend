package com.nuono.next.infrastructure.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class FileManagementParseMapperSqlTest {

    @Test
    void countTasksKeepsLatestVisibleDocumentGroupContract() throws Exception {
        String sql = selectSql(
                "countTasks",
                String.class,
                Long.class,
                String.class,
                Integer.class,
                boolean.class
        );

        assertThat(sql)
                .contains("SELECT COUNT(DISTINCT COALESCE(t.document_group_id, t.id))");
        assertLatestVisibleTaskScope(sql);
    }

    @Test
    void selectTasksKeepsLatestVisibleDocumentGroupPagingContract() throws Exception {
        String sql = selectSql(
                "selectTasks",
                String.class,
                Long.class,
                String.class,
                Integer.class,
                boolean.class,
                int.class,
                int.class
        );

        assertLatestVisibleTaskScope(sql);
        assertThat(sql)
                .contains("ORDER BY t.gmt_updated DESC, t.id DESC")
                .endsWith("LIMIT #{limit} OFFSET #{offset}");
    }

    @Test
    void retryableFailedParseTaskLookupIncludesAllTransientAiFailures() throws Exception {
        String sql = selectSql("selectRetryableFailedParseTasks", int.class);

        assertThat(sql)
                .contains("'OPENAI_REQUEST_TIMEOUT'")
                .contains("'OPENAI_HTTP_429'")
                .contains("'OPENAI_HTTP_500'")
                .contains("'OPENAI_HTTP_502'")
                .contains("'OPENAI_HTTP_503'")
                .contains("'OPENAI_HTTP_504'")
                .contains("'OPENAI_HTTP_550'")
                .contains("next_run_at <= NOW()")
                .contains("usage_limit_reached")
                .contains("usage limit has been reached");
    }

    @Test
    void insertTaskKeepsCreationScopeLineageAndIdempotencyContract() throws Exception {
        String sql = insertSql(
                "insertTask",
                Long.class,
                String.class,
                String.class,
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                Integer.class,
                String.class,
                String.class,
                String.class,
                Long.class
        );

        assertThat(sql)
                .contains("target_plan_id, standard_version_id, data_scope_type, data_scope_key")
                .contains("status, base_version_id, document_group_id, parent_task_id, iteration_no")
                .contains("remark, idempotency_key, request_hash, created_by, updated_by")
                .contains("#{targetPlanId}, #{standardVersionId}, 'global', 'global:*'")
                .contains("'reading', #{baseVersionId}, #{documentGroupId}, #{parentTaskId}, #{iterationNo}")
                .contains("#{remark}, #{idempotencyKey}, #{requestHash}, #{operatorUserId}, #{operatorUserId}");
    }

    @Test
    void bindFileAssetKeepsDeletedGuardAndSameTaskCompareAndSetContract() throws Exception {
        String sql = updateSql("bindFileAssetToTask", Long.class, Long.class, Long.class);

        assertThat(sql)
                .contains("SET bound_task_id = #{taskId}")
                .contains("updated_by = #{operatorUserId}")
                .contains("WHERE id = #{fileAssetId}")
                .contains("AND is_deleted = b'0'")
                .contains("AND (bound_task_id IS NULL OR bound_task_id = #{taskId})");
    }

    @Test
    void insertTaskInputKeepsNormalizedInputPersistenceContract() throws Exception {
        String sql = insertSql(
                "insertTaskInput",
                Long.class,
                Long.class,
                String.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                Integer.class,
                Long.class
        );

        assertThat(sql)
                .contains("id, task_id, input_type, input_role, file_asset_id, text_content, display_name, sort_no")
                .contains("is_deleted, created_by, updated_by, gmt_create, gmt_updated")
                .contains("#{id}, #{taskId}, #{inputType}, #{inputRole}, #{fileAssetId}, #{textContent}")
                .contains("#{displayName}, #{sortNo}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()");
    }

    @Test
    void maxIterationLookupExcludesDeletedTasks() throws Exception {
        String sql = selectSql("selectMaxIterationNo", Long.class);

        assertThat(sql)
                .contains("SELECT COALESCE(MAX(iteration_no), 1)")
                .contains("WHERE COALESCE(document_group_id, id) = #{documentGroupId}")
                .contains("AND is_deleted = b'0'");
    }

    @Test
    void taskInputLookupKeepsStableSortOrder() throws Exception {
        String sql = selectSql("selectTaskInputs", Long.class);

        assertThat(sql)
                .contains("WHERE ti.task_id = #{taskId}")
                .contains("AND ti.is_deleted = b'0'")
                .endsWith("ORDER BY ti.sort_no ASC, ti.id ASC");
    }

    private static void assertLatestVisibleTaskScope(String sql) {
        assertThat(sql)
                .contains("AND p.is_deleted = b'0'")
                .contains("WHERE t.is_deleted = b'0'")
                .contains("AND (#{keyword} IS NULL OR t.document_title LIKE CONCAT('%', #{keyword}, '%'))")
                .contains("AND (#{targetPlanId} IS NULL OR t.target_plan_id = #{targetPlanId})")
                .contains("AND (#{status} IS NULL OR t.status = #{status})")
                .contains(
                        "AND t.id = ( SELECT latest.id FROM file_mgmt_parse_task latest "
                                + "WHERE latest.is_deleted = b'0' "
                                + "AND COALESCE(latest.document_group_id, latest.id) "
                                + "= COALESCE(t.document_group_id, t.id) "
                                + "ORDER BY latest.gmt_updated DESC, latest.id DESC LIMIT 1 )"
                )
                .contains("#{includeAll} = TRUE")
                .contains("FROM file_mgmt_parse_target_plan_scope scope")
                .contains("WHERE scope.target_plan_id = p.id")
                .contains("AND scope.is_deleted = 0")
                .contains("AND scope.status = 'active'")
                .contains("scope.scope_type = 'all'")
                .contains(
                        "scope.scope_type = 'role_level' "
                                + "AND scope.scope_value = CAST(#{roleLevel} AS CHAR)"
                );
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = FileManagementParseMapper.class.getMethod(methodName, parameterTypes);
        return normalizeSql(method.getAnnotation(Select.class).value());
    }

    private static String insertSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = FileManagementParseMapper.class.getMethod(methodName, parameterTypes);
        return normalizeSql(method.getAnnotation(Insert.class).value());
    }

    private static String updateSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = FileManagementParseMapper.class.getMethod(methodName, parameterTypes);
        return normalizeSql(method.getAnnotation(Update.class).value());
    }

    private static String normalizeSql(String[] statements) {
        return String.join(" ", statements)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
