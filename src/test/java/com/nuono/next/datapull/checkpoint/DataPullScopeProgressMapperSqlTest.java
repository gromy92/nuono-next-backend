package com.nuono.next.datapull.checkpoint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.DataPullScopeProgressMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class DataPullScopeProgressMapperSqlTest {

    @Test
    void initializationNeverOverwritesCommittedProgress() {
        String sql = sql("insertIfAbsent", Insert.class);

        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE operation_code = operation_code"));
        assertFalse(sql.contains("initial_full_completed = VALUES"));
        assertFalse(sql.contains("official_modified_high_water_utc = VALUES"));
    }

    @Test
    void commitIsMonotonicAndRequiresTheLiveTaskFence() {
        String sql = sql("commitCompletedWindow", Update.class);

        assertTrue(sql.contains("JOIN dp_pull_task task ON task.id = #{taskId}"));
        assertTrue(sql.contains("task.state = 'RUNNING'"));
        assertTrue(sql.contains("task.fence_epoch = #{taskFenceEpoch}"));
        assertTrue(sql.contains("task.version_no = #{taskVersion}"));
        assertTrue(sql.contains("BINARY task.lease_owner = BINARY #{leaseOwner}"));
        assertTrue(sql.contains("task.lease_until > #{nowUtc}"));
        assertTrue(sql.contains("progress.version_no = #{expectedProgressVersion}"));
        assertTrue(sql.contains("#{officialModifiedHighWaterUtc} >= progress.official_modified_high_water_utc"));
        assertTrue(sql.contains("WHEN progress.initial_full_completed = b'1' THEN b'1'"));
        assertTrue(sql.contains("last_applied_business_window_key = #{businessWindowKey}"));
    }

    private String sql(String name, Class<? extends Annotation> type) {
        Method method = Arrays.stream(DataPullScopeProgressMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
        if (type == Insert.class) {
            return compact(method.getAnnotation(Insert.class).value());
        }
        return compact(method.getAnnotation(Update.class).value());
    }

    private String compact(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ");
    }
}
