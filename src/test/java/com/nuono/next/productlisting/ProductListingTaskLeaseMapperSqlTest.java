package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductListingTaskLeaseMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductListingTaskLeaseMapperSqlTest {

    @Test
    void staleRecoveryUsesHeartbeatTimestamp() {
        String sql = updateSql("recoverStaleRunningRealRunTasks");

        assertTrue(sql.contains("status = 'running'"));
        assertTrue(sql.contains("started_at IS NOT NULL"));
        assertTrue(sql.contains("gmt_updated < #{staleBefore}"));
        assertTrue(!sql.contains("started_at < #{staleBefore}"));
    }

    @Test
    void legacyWorkerMethodsRetainV33RunningEpochFence() {
        String heartbeatSql = updateSql("heartbeatRunningRealRunTask");
        assertTrue(heartbeatSql.contains("mode = 'REAL_RUN'"));
        assertTrue(heartbeatSql.contains("status = 'running'"));
        assertTrue(heartbeatSql.contains("started_at = #{startedAt}"));

        String completionSql = updateSql("updateRunningTaskResult");
        assertTrue(completionSql.contains("owner_user_id = #{task.ownerUserId}"));
        assertTrue(completionSql.contains("mode = 'REAL_RUN'"));
        assertTrue(completionSql.contains("status = 'running'"));
        assertTrue(completionSql.contains("started_at = #{task.startedAt}"));
    }

    @Test
    void heartbeatAndCompletionAreFencedByRunningEpoch() {
        for (String methodName : new String[]{
                "heartbeatRunningTask", "checkpointRunningTaskNoonResult", "completeRunningTaskResult"
        }) {
            String sql = updateSql(methodName);
            assertTrue(sql.contains("status = 'running'"));
            assertTrue(sql.contains("started_at = #"));
            assertTrue(sql.contains("owner_user_id = #"));
        }
    }

    @Test
    void manualRecoveryMustClaimTheExpectedPriorStatus() {
        String sql = updateSql("markTaskRecoveryRunning");

        assertTrue(sql.contains("status = #{expectedStatus}"));
        assertTrue(sql.contains("status = 'running'"));
        assertTrue(sql.contains("started_at = #{startedAt}"));
        assertTrue(sql.contains("completed_at = NULL"));
    }

    private String updateSql(String methodName) {
        Method method = Arrays.stream(ProductListingTaskLeaseMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
    }
}
