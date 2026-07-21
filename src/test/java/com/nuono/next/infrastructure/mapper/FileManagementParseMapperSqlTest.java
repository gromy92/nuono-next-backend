package com.nuono.next.infrastructure.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
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
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .trim();
    }
}
