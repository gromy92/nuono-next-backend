package com.nuono.next.infrastructure.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class FileManagementParseMapperSqlTest {

    @Test
    void retryableFailedParseTaskLookupIncludesAllTransientAiFailures() throws Exception {
        Method method = FileManagementParseMapper.class.getMethod("selectRetryableFailedParseTasks", int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

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
}
