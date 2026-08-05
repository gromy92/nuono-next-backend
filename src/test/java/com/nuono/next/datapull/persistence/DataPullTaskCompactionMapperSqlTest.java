package com.nuono.next.datapull.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class DataPullTaskCompactionMapperSqlTest {

    @Test
    void compactionUsesOneDurableTransactionAnchorBeforeTaskRowLocks() {
        String anchor = sql("lockCompactionAnchor", Select.class);
        String candidates = sql("lockStrictlyNeverStarted", Select.class);

        assertTrue(anchor.contains("FROM noon_pull_id_sequence"));
        assertTrue(anchor.contains("sequence_name = 'dp_pull_task'"));
        assertTrue(anchor.contains("FOR UPDATE"));
        assertTrue(candidates.contains("operation_code = #{operationCode}"));
        assertTrue(candidates.contains("BINARY scope_key = BINARY #{scopeKey}"));
        assertTrue(candidates.contains("ORDER BY schedule_slot ASC, id ASC"));
        assertTrue(candidates.contains("FOR UPDATE"));
        assertStrictNeverStarted(candidates);
    }

    @Test
    void supersedeIsAReservedVersionCasOverStrictlyNeverStartedTask() {
        String sql = sql("supersedeStrictlyNeverStarted", Update.class);

        assertTrue(sql.contains("SET state = 'SUPERSEDED'"));
        assertTrue(sql.contains("finished_at = #{now}"));
        assertTrue(sql.contains("version_no = version_no + 1"));
        assertTrue(sql.contains("version_no = #{expectedVersion}"));
        assertStrictNeverStarted(sql);
        assertFalse(sql.contains("state = 'RUNNING'"));
    }

    private void assertStrictNeverStarted(String sql) {
        assertTrue(sql.contains("state = 'QUEUED'"));
        assertTrue(sql.contains("fence_epoch = 0"));
        assertTrue(sql.contains("checkpoint IS NULL"));
        assertTrue(sql.contains("remote_handle IS NULL"));
        assertTrue(sql.contains("lease_owner IS NULL"));
        assertTrue(sql.contains("lease_until IS NULL"));
    }

    private String sql(String methodName, Class<? extends Annotation> annotationType) {
        Method method = Arrays.stream(DataPullTaskCompactionMapper.class.getDeclaredMethods())
                .filter((candidate) -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        if (annotationType == Select.class) {
            return compact(method.getAnnotation(Select.class).value());
        }
        return compact(method.getAnnotation(Update.class).value());
    }

    private String compact(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ");
    }
}
